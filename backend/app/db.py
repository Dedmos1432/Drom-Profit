from collections.abc import Iterator
from contextlib import contextmanager
from os import environ
from typing import Annotated

from fastapi import Depends, Request
from sqlalchemy import Engine, URL, create_engine, make_url
from sqlalchemy.exc import DBAPIError, TimeoutError as PoolTimeout
from sqlalchemy.orm import Session

from .errors import APIError


def database_url(value: str | URL | None = None) -> URL:
    if value is None:
        value = environ.get("DROM_DATABASE_URL")
    if not value:
        raise ValueError("Задайте DROM_DATABASE_URL")
    url = make_url(value)
    if url.drivername != "postgresql+psycopg":
        raise ValueError("Требуется URL postgresql+psycopg")
    return url


def make_engine(value: str | URL | None = None) -> Engine:
    size = int(environ.get("DROM_DB_POOL_SIZE", "10"))
    overflow = int(environ.get("DROM_DB_MAX_OVERFLOW", "5"))
    if size < 1 or overflow < 0:
        raise ValueError("Пул требует pool_size >= 1 и max_overflow >= 0")
    return create_engine(
        database_url(value), isolation_level="READ COMMITTED", pool_pre_ping=True,
        pool_size=size, max_overflow=overflow, pool_timeout=5,
        connect_args={"connect_timeout": 5}, hide_parameters=True,
    )


@contextmanager
def session_for(engine: Engine) -> Iterator[Session]:
    # Closing the session rolls back an unfinished transaction before pool check-in.
    try:
        with Session(engine, expire_on_commit=False) as session:
            yield session
    except PoolTimeout as error:
        raise APIError(500, "internal_error", "Нет свободного соединения с базой данных") from error
    except DBAPIError as error:
        state = getattr(error.orig, "sqlstate", None)
        if state in {"57P03", "53300"}:
            raise APIError(500, "internal_error", "База данных временно недоступна") from error
        if state == "55P03":
            raise APIError(500, "internal_error", "База данных временно занята. Повторите запрос") from error
        if state in {"40001", "40P01"}:
            raise APIError(500, "internal_error", "Транзакция отменена из-за конфликта изменений") from error
        if state == "23505" and getattr(error.orig.diag, "constraint_name", None) in {
            "uq_expenses_car_kind_key", "uq_benefits_car_key",
        }:
            raise APIError(422, "duplicate_factor", "Этот фактор уже добавлен в карточку") from error
        # Includes unexpected integrity errors and connection failures, including
        # a lost connection during COMMIT for which the outcome is unknown.
        raise


def get_db(request: Request) -> Iterator[Session]:
    with session_for(request.app.state.engine) as session:
        yield session


def get_snapshot(request: Request) -> Iterator[Session]:
    with session_for(request.app.state.snapshot_engine) as session:
        yield session


Database = Annotated[Session, Depends(get_db, scope="function")]
Snapshot = Annotated[Session, Depends(get_snapshot, scope="function")]
