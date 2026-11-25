from fastapi import APIRouter, Request, Response
from fastapi.encoders import jsonable_encoder
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from .. import schemas
from ..db import Database, session_for
from ..errors import APIError, not_found
from ..formats import validate_uri
from ..models import Car
from ..services.calculation import compare, summarize
from ..services.listing_import import ListingImportError
from .sessions import Owner


router = APIRouter()

IMPORT_ERRORS = {
    "invalid_url": (400, "URL объявления не поддерживается"),
    "listing_unavailable": (404, "Объявление недоступно"),
    "source_forbidden": (403, "Источник запретил доступ к объявлению"),
    "parse_error": (502, "Не удалось обработать данные объявления"),
    "source_unavailable": (503, "Источник объявлений временно недоступен"),
}


@router.post("/cars/import")
async def preview_listing(
    body: schemas.ImportRequest, request: Request, _owner: Owner
) -> schemas.ListingData:
    try:
        validate_uri(body.url)
    except ValueError:
        status, message = IMPORT_ERRORS["invalid_url"]
        raise APIError(status, "invalid_url", message) from None
    try:
        return await request.app.state.import_listing(body.url)
    except ListingImportError as error:
        status, message = IMPORT_ERRORS[error.code]
        raise APIError(status, error.code, message) from error


@router.post("/cars", status_code=201)
def create_car(body: schemas.CarInput, db: Database, owner: Owner) -> schemas.Car:
    car = Car(session_id=owner, **body.model_dump(mode="json"))
    db.add(car)
    db.flush()
    result = schemas.Car.model_validate(car)
    db.commit()
    return result


@router.get("/cars")
def list_cars(request: Request, owner: Owner) -> list[schemas.Car]:
    # Materialize the result and return the connection before FastAPI schedules response validation.
    with session_for(request.app.state.snapshot_engine) as db:
        cars = db.scalars(select(Car).where(Car.session_id == owner).order_by(Car.id))
        return [schemas.Car.model_validate(car) for car in cars]


def require_car(car_id: str, db: Session, owner: str, *, lock: bool = False) -> Car:
    query = select(Car).where(Car.id == car_id, Car.session_id == owner)
    if lock:
        db.execute(text("SET LOCAL lock_timeout = '3s'"))
        query = query.with_for_update(of=Car)
    car = db.scalar(query)
    if car is None:
        raise not_found()
    return car


@router.get("/cars/{car_id}")
def get_car(car_id: str, request: Request, owner: Owner) -> schemas.Car:
    with session_for(request.app.state.snapshot_engine) as db:
        return schemas.Car.model_validate(require_car(car_id, db, owner))


@router.patch("/cars/{car_id}")
def patch_car(body: schemas.CarPatch, car_id: str, db: Database, owner: Owner) -> schemas.Car:
    car = require_car(car_id, db, owner, lock=True)
    reviewed_fields = ("expenses_reviewed", "benefits_reviewed")
    changes = jsonable_encoder(body)
    flags = {field: changes.pop(field) for field in reviewed_fields if field in changes}
    if any(getattr(car, field) != value for field, value in changes.items()):
        car.expenses_reviewed = car.benefits_reviewed = False
    for field, value in (changes | flags).items():
        setattr(car, field, value)
    result = schemas.Car.model_validate(car)
    db.commit()
    return result


@router.delete("/cars/{car_id}", status_code=204)
def delete_car(car_id: str, db: Database, owner: Owner) -> Response:
    car = require_car(car_id, db, owner, lock=True)
    db.delete(car)
    db.commit()
    return Response(status_code=204)


@router.get("/cars/{car_id}/summary")
def car_summary(car_id: str, request: Request, owner: Owner) -> schemas.Summary:
    with session_for(request.app.state.snapshot_engine) as db:
        return summarize(require_car(car_id, db, owner))


@router.post("/comparison")
def comparison(body: schemas.ComparisonRequest, request: Request, owner: Owner) -> schemas.Comparison:
    with session_for(request.app.state.snapshot_engine) as db:
        cars = db.scalars(
            select(Car).where(Car.session_id == owner, Car.id.in_(body.car_ids))
        )
        by_id = {car.id: car for car in cars}
        if len(by_id) != len(body.car_ids):
            raise not_found()
        return compare([by_id[car_id] for car_id in body.car_ids])
