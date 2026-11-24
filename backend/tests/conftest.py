import os
import sys
from ipaddress import ip_address
from pathlib import Path
from socket import AF_UNIX
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, pool, text
from sqlalchemy.schema import CreateSchema, DropSchema

from app.db import database_url
from app.main import create_app


def pytest_sessionstart(session):
    # TestClient uses a socketpair. The HTTP benchmark uses numeric loopback.
    def local_network_only(event, args):
        if event == "socket.connect" and args[0].family != AF_UNIX:
            host = args[1][0]
        elif event in {"socket.getaddrinfo", "socket.gethostbyname", "socket.gethostbyaddr"}:
            host = args[0]
        else:
            return
        if not ip_address(host).is_loopback:
            raise RuntimeError("Tests require local connections")

    sys.addaudithook(local_network_only)


@pytest.fixture(scope="session")
def test_engine():
    value = os.environ.get("DROM_TEST_DATABASE_URL")
    if not value:
        pytest.fail("Set DROM_TEST_DATABASE_URL to the separate local drom_profit_test database")
    url = database_url(value)
    if (url.host not in {"127.0.0.1", "::1"} or url.database != "drom_profit_test"
            or url.query or os.environ.get("PGSERVICE") or os.environ.get("PGHOSTADDR")):
        pytest.fail("Tests need a numeric loopback URL for drom_profit_test without overrides")
    if os.environ.get("DROM_DATABASE_URL") and database_url().database == url.database:
        pytest.fail("Application and test database names must differ")
    # libpq connects in native code. Pin its numeric address independently of Python sockets.
    url = url.update_query_dict({"hostaddr": url.host, "sslmode": "disable", "gssencmode": "disable"})
    engine = create_engine(url, poolclass=pool.NullPool, hide_parameters=True)
    try:
        with engine.connect() as conn:
            assert conn.scalar(text("select current_database()")) == "drom_profit_test"
        yield engine
    finally:
        engine.dispose()


@pytest.fixture
def db_url(test_engine):
    from alembic import command
    from alembic.config import Config

    schema = "test_" + uuid4().hex
    with test_engine.begin() as conn:
        conn.execute(CreateSchema(schema))
    url = test_engine.url.update_query_dict({"options": "-csearch_path=" + schema})
    engine = create_engine(url, poolclass=pool.NullPool)
    try:
        with engine.begin() as conn:
            assert conn.scalar(text("select current_schema()")) == schema
            config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
            config.attributes["connection"] = conn
            command.upgrade(config, "head")
        yield url
    finally:
        engine.dispose()
        with test_engine.begin() as conn:
            conn.execute(DropSchema(schema, cascade=True))


@pytest.fixture
def sql_engine(db_url):
    engine = create_engine(db_url, poolclass=pool.NullPool)
    try:
        yield engine
    finally:
        engine.dispose()


@pytest.fixture
def client(db_url):
    with TestClient(create_app(db_url)) as client:
        yield client


@pytest.fixture
def new_session(client):
    def create():
        response = client.post("/api/session")
        assert response.status_code == 201
        assert response.headers["cache-control"] == "no-store"
        assert response.json()["token_type"] == "bearer"
        return {"Authorization": "Bearer " + response.json()["token"]}

    return create


@pytest.fixture
def headers(new_session):
    return new_session()


@pytest.fixture
def new_car(client, headers):
    def create(owner=None, **changes):
        response = client.post(
            "/api/cars",
            headers=headers if owner is None else owner,
            json={"brand": "Toyota", "model": "Camry", "source": "manual", "price": 100000} | changes,
        )
        assert response.status_code == 201, response.text
        return "/api/cars/" + response.json()["id"]

    return create
