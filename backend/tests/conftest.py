import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def db_path(tmp_path):
    return tmp_path / "test.sqlite3"


@pytest.fixture
def client(db_path):
    with TestClient(create_app(db_path)) as client:
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
