from typing import TypeVar

from fastapi import APIRouter, Response
from sqlalchemy.orm import Session

from .. import schemas
from ..db import Database
from ..errors import APIError, not_found
from ..models import Benefit, Car, Expense, Note
from ..money import MONEY_LIMIT, MONEY_LIMIT_LABEL
from ..services.calculation import missing_equipment_keys
from .cars import require_car
from .sessions import Owner


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


def validate_group_amount(car: Car, item: Expense | Benefit, amount: int | None) -> None:
    group = car.expenses if isinstance(item, Expense) else car.benefits
    total = sum(other.amount or 0 for other in group if other is not item) + (amount or 0)
    if total > MONEY_LIMIT:
        name = "расходов" if isinstance(item, Expense) else "преимуществ"
        raise APIError(422, "amount_out_of_range", f"Сумма {name} не должна превышать {MONEY_LIMIT_LABEL}.")


def reset_review(car: Car, item: Expense | Benefit | Note) -> None:
    if isinstance(item, Expense):
        car.expenses_reviewed = False
    elif isinstance(item, Benefit):
        car.benefits_reviewed = False


def add_item(db: Session, car: Car, items: list[Item], item: Item) -> Item:
    if not isinstance(item, Note):
        validate_group_amount(car, item, item.amount)
    items.append(item)
    if not isinstance(item, Note):
        validate_factors(car)
    reset_review(car, item)
    db.commit()
    return item


def update_item(db: Session, car: Car, item: Item, changes: dict) -> Item:
    if any(getattr(item, field) != value for field, value in changes.items()):
        if not isinstance(item, Note) and "amount" in changes:
            validate_group_amount(car, item, changes["amount"])
        for field, value in changes.items():
            setattr(item, field, value)
        if not isinstance(item, Note):
            validate_factors(car)
        reset_review(car, item)
    # A no-op still holds the car lock acquired by the caller until the transaction ends.
    db.commit()
    return item


def remove_item(db: Session, car: Car, item: Expense | Benefit | Note) -> Response:
    reset_review(car, item)
    db.delete(item)
    db.commit()
    return Response(status_code=204)


@router.post("/expenses", status_code=201)
def create_expense(body: schemas.ExpenseInput, car_id: str, db: Database, owner: Owner) -> schemas.Expense:
    car = require_car(car_id, db, owner, lock=True)
    expense = add_item(db, car, car.expenses, Expense(**body.model_dump()))
    return schemas.Expense.model_validate(expense)


@router.patch("/expenses/{item_id}")
def patch_expense(
    item_id: str, body: schemas.ExpensePatch, car_id: str, db: Database, owner: Owner
) -> schemas.Expense:
    car = require_car(car_id, db, owner, lock=True)
    expense = update_item(db, car, require_item(car.expenses, item_id), body)
    return schemas.Expense.model_validate(expense)


@router.delete("/expenses/{item_id}", status_code=204)
def delete_expense(item_id: str, car_id: str, db: Database, owner: Owner) -> Response:
    car = require_car(car_id, db, owner, lock=True)
    return remove_item(db, car, require_item(car.expenses, item_id))


@router.post("/benefits", status_code=201)
def create_benefit(body: schemas.BenefitInput, car_id: str, db: Database, owner: Owner) -> schemas.Benefit:
    car = require_car(car_id, db, owner, lock=True)
    benefit = add_item(db, car, car.benefits, Benefit(**body.model_dump()))
    return schemas.Benefit.model_validate(benefit)


@router.patch("/benefits/{item_id}")
def patch_benefit(
    item_id: str, body: schemas.BenefitPatch, car_id: str, db: Database, owner: Owner
) -> schemas.Benefit:
    car = require_car(car_id, db, owner, lock=True)
    benefit = update_item(db, car, require_item(car.benefits, item_id), body)
    return schemas.Benefit.model_validate(benefit)


@router.delete("/benefits/{item_id}", status_code=204)
def delete_benefit(item_id: str, car_id: str, db: Database, owner: Owner) -> Response:
    car = require_car(car_id, db, owner, lock=True)
    return remove_item(db, car, require_item(car.benefits, item_id))


@router.post("/notes", status_code=201)
def create_note(body: schemas.NoteInput, car_id: str, db: Database, owner: Owner) -> schemas.Note:
    car = require_car(car_id, db, owner, lock=True)
    note = add_item(db, car, car.notes, Note(**body.model_dump()))
    return schemas.Note.model_validate(note)


@router.patch("/notes/{item_id}")
def patch_note(item_id: str, body: schemas.NotePatch, car_id: str, db: Database, owner: Owner) -> schemas.Note:
    car = require_car(car_id, db, owner, lock=True)
    note = update_item(db, car, require_item(car.notes, item_id), body)
    return schemas.Note.model_validate(note)


@router.delete("/notes/{item_id}", status_code=204)
def delete_note(item_id: str, car_id: str, db: Database, owner: Owner) -> Response:
    car = require_car(car_id, db, owner, lock=True)
    return remove_item(db, car, require_item(car.notes, item_id))
