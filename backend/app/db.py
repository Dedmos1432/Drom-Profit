import sqlite3
from collections.abc import Iterator
from pathlib import Path
from typing import Annotated

from fastapi import Depends, Request
from sqlalchemy import Engine, URL, create_engine, event
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

from .errors import APIError


def make_engine(path: Path) -> Engine:
    engine = create_engine(URL.create("sqlite+pysqlite", database=str(path)), hide_parameters=True)

    @event.listens_for(engine, "connect")
    def configure_sqlite(connection, _record):
        connection.isolation_level = None
        connection.execute("PRAGMA foreign_keys = ON")

    return engine


def get_db(request: Request) -> Iterator[Session]:
    read_only = request.scope["route"].name in {"comparison", "preview_listing"}
    write = request.method in {"POST", "PATCH", "DELETE"} and not read_only
    with Session(request.app.state.engine, expire_on_commit=False) as session:
        # Acquire the SQLite write lock before reading data used to validate a change.
        try:
            session.connection().exec_driver_sql("BEGIN IMMEDIATE" if write else "BEGIN")
            yield session
        except OperationalError as error:
            code = getattr(error.orig, "sqlite_errorcode", 0)
            if code & 0xff != sqlite3.SQLITE_BUSY:
                raise
            # A failed SQLite COMMIT can leave the physical transaction open.
            session.invalidate()
            raise APIError(
                500, "internal_error", "База данных временно занята. Повторите запрос"
            ) from error


Database = Annotated[Session, Depends(get_db)]
