from typing import TypeVar

from fastapi import APIRouter, Response
from sqlalchemy.orm import Session

from .. import schemas
from ..db import Database
from ..errors import APIError, not_found
from ..models import Benefit, Car, Expense, Note
from ..services.calculation import missing_equipment_keys
from .cars import OwnedCar


router = APIRouter(prefix="/cars/{car_id}")
Item = TypeVar("Item", Expense, Benefit, Note)


def require_item(items: list[Item], item_id: str) -> Item:
    for item in items:
        if item.id == item_id:
            return item
    raise not_found()


def validate_factors(car: Car) -> None:
    expense_keys = [
        (expense.kind, expense.factor_key)
        for expense in car.expenses
        if expense.factor_key is not None
    ]
    benefit_keys = [benefit.factor_key for benefit in car.benefits if benefit.factor_key is not None]
    if len(expense_keys) != len(set(expense_keys)) or len(benefit_keys) != len(set(benefit_keys)):
        raise APIError(422, "duplicate_factor", "Этот фактор уже добавлен в карточку")
    if missing_equipment_keys(car.expenses).intersection(benefit_keys):
        raise APIError(422, "conflicting_equipment", "Учитывайте оснащение в карточке одним способом")


def reset_review(car: Car, item: Expense | Benefit | Note) -> None:
    if isinstance(item, Expense):
        car.expenses_reviewed = False
    elif isinstance(item, Benefit):
        car.benefits_reviewed = False


def add_item(db: Session, car: Car, items: list[Item], item: Item) -> Item:
    items.append(item)
    if not isinstance(item, Note):
        validate_factors(car)
    reset_review(car, item)
    db.commit()
    return item


def update_item(db: Session, car: Car, item: Item, changes: dict) -> Item:
    if any(getattr(item, field) != value for field, value in changes.items()):
        for field, value in changes.items():
            setattr(item, field, value)
        if not isinstance(item, Note):
            validate_factors(car)
        reset_review(car, item)
        db.commit()
    return item


def remove_item(db: Session, car: Car, item: Expense | Benefit | Note) -> Response:
    reset_review(car, item)
    db.delete(item)
    db.commit()
    return Response(status_code=204)


@router.post("/expenses", status_code=201)
def create_expense(body: schemas.ExpenseInput, car: OwnedCar, db: Database) -> schemas.Expense:
    expense = add_item(db, car, car.expenses, Expense(**body.model_dump()))
    return schemas.Expense.model_validate(expense)


@router.patch("/expenses/{item_id}")
def patch_expense(
    item_id: str, body: schemas.ExpensePatch, car: OwnedCar, db: Database
) -> schemas.Expense:
    expense = update_item(db, car, require_item(car.expenses, item_id), body)
    return schemas.Expense.model_validate(expense)


@router.delete("/expenses/{item_id}", status_code=204)
def delete_expense(item_id: str, car: OwnedCar, db: Database) -> Response:
    return remove_item(db, car, require_item(car.expenses, item_id))


@router.post("/benefits", status_code=201)
def create_benefit(body: schemas.BenefitInput, car: OwnedCar, db: Database) -> schemas.Benefit:
    benefit = add_item(db, car, car.benefits, Benefit(**body.model_dump()))
    return schemas.Benefit.model_validate(benefit)


@router.patch("/benefits/{item_id}")
def patch_benefit(
    item_id: str, body: schemas.BenefitPatch, car: OwnedCar, db: Database
) -> schemas.Benefit:
    benefit = update_item(db, car, require_item(car.benefits, item_id), body)
    return schemas.Benefit.model_validate(benefit)


@router.delete("/benefits/{item_id}", status_code=204)
def delete_benefit(item_id: str, car: OwnedCar, db: Database) -> Response:
    return remove_item(db, car, require_item(car.benefits, item_id))


@router.post("/notes", status_code=201)
def create_note(body: schemas.NoteInput, car: OwnedCar, db: Database) -> schemas.Note:
    note = add_item(db, car, car.notes, Note(**body.model_dump()))
    return schemas.Note.model_validate(note)


@router.patch("/notes/{item_id}")
def patch_note(item_id: str, body: schemas.NotePatch, car: OwnedCar, db: Database) -> schemas.Note:
    note = update_item(db, car, require_item(car.notes, item_id), body)
    return schemas.Note.model_validate(note)


@router.delete("/notes/{item_id}", status_code=204)
def delete_note(item_id: str, car: OwnedCar, db: Database) -> Response:
    return remove_item(db, car, require_item(car.notes, item_id))
