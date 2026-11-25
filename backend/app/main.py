from contextlib import asynccontextmanager
from os import environ
from pathlib import Path

import yaml
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import URL, text
from starlette.concurrency import run_in_threadpool
from starlette.exceptions import HTTPException

from .api import car_items, cars, sessions
from .db import make_engine
from .errors import APIError
from .money import MONEY_INPUT_FIELDS, MONEY_RANGE_MESSAGE
from .schemas import Error
from .services.listing_import import ListingImporter, make_parser_importer


BACKEND = Path(__file__).resolve().parents[1]
SHARED = BACKEND.parent / "shared"


def error_response(status: int, code: str, message: str, headers=None) -> JSONResponse:
    return JSONResponse(
        status_code=status, content=Error(code=code, message=message).model_dump(), headers=headers
    )


def create_app(db_url: str | URL | None = None, importer: ListingImporter | None = None) -> FastAPI:
    contract = yaml.safe_load((SHARED / "openapi.yaml").read_text("utf-8"))
    if importer is None:
        importer = make_parser_importer()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        engine = make_engine(db_url)
        app.state.engine = engine
        app.state.snapshot_engine = engine.execution_options(
            isolation_level="REPEATABLE READ", postgresql_readonly=True,
        )

        def check_schema():
            with engine.connect() as connection:
                connection.execute(text("SELECT id FROM sessions LIMIT 0"))

        try:
            await run_in_threadpool(check_schema)
            yield
        finally:
            await run_in_threadpool(engine.dispose)

    app = FastAPI(
        title=contract["info"]["title"], version=contract["info"]["version"], lifespan=lifespan
    )
    app.openapi = lambda: contract
    app.state.import_listing = importer
    origins = [
        origin.strip()
        for origin in environ.get("DROM_CORS_ORIGINS", "").split(",")
        if origin.strip()
    ]
    if "*" in origins:
        raise ValueError("DROM_CORS_ORIGINS требует список точных origins")
    if origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=origins,
            allow_methods=["GET", "POST", "PATCH", "DELETE"],
            allow_headers=["Authorization", "Content-Type"],
        )

    @app.exception_handler(HTTPException)
    async def handle_http_error(_request: Request, error: HTTPException) -> JSONResponse:
        code = error.code if isinstance(error, APIError) else f"http_{error.status_code}"
        return error_response(error.status_code, code, error.detail, error.headers)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(
        _request: Request, _error: RequestValidationError
    ) -> JSONResponse:
        errors = _error.errors()
        if errors and all(
            entry["type"] in {"greater_than_equal", "less_than_equal"}
            and len(entry["loc"]) == 2
            and entry["loc"][0] == "body"
            and entry["loc"][1] in MONEY_INPUT_FIELDS
            for entry in errors
        ):
            return error_response(422, "amount_out_of_range", MONEY_RANGE_MESSAGE)
        return error_response(422, "validation_error", "Проверьте поля запроса")

    @app.exception_handler(Exception)
    async def handle_server_error(request: Request, _error: Exception) -> JSONResponse:
        response = error_response(500, "internal_error", "Внутренняя ошибка сервера")
        # Starlette renders unhandled exceptions outside the CORS middleware.
        if origins:
            response.headers.add_vary_header("Origin")
            origin = request.headers.get("origin")
            if origin in origins:
                response.headers["Access-Control-Allow-Origin"] = origin
        return response

    for router in (sessions.router, cars.router, car_items.router):
        app.include_router(router, prefix="/api")
    return app


app = create_app()
