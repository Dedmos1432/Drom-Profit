from collections.abc import Sequence

from ..models import Car, Expense
from ..schemas import Comparison, ComparisonItem, Summary, Warning


def summarize(car: Car) -> Summary:
    expenses = [expense.amount for expense in car.expenses]
    benefits = [benefit.amount for benefit in car.benefits]
    total_expenses = sum(amount for amount in expenses if amount is not None)
    total_benefits = sum(amount for amount in benefits if amount is not None)
    unknown_expenses = expenses.count(None)
    unknown_benefits = benefits.count(None)
    complete = (
        car.expenses_reviewed and car.benefits_reviewed and not unknown_expenses and not unknown_benefits
    )
    acquisition_cost = car.price + total_expenses
    return Summary(
        car_id=car.id,
        listing_price=car.price,
        total_expenses=total_expenses,
        total_benefits=total_benefits,
        acquisition_cost=acquisition_cost,
        adjusted_cost=acquisition_cost - total_benefits,
        assessment_status="complete" if complete else "incomplete",
        unknown_expenses=unknown_expenses,
        unknown_benefits=unknown_benefits,
    )


def missing_equipment_keys(expenses: Sequence[Expense]) -> set[str]:
    return {
        expense.factor_key
        for expense in expenses
        if expense.kind == "missing_equipment" and expense.factor_key is not None
    }


def compare(cars: Sequence[Car]) -> Comparison:
    summaries = [summarize(car) for car in cars]
    minimum = min(item.adjusted_cost for item in summaries)
    missing_keys = set().union(*(missing_equipment_keys(car.expenses) for car in cars))
    benefit_keys = {
        benefit.factor_key
        for car in cars
        for benefit in car.benefits
        if benefit.factor_key is not None
    }
    conflicts = missing_keys & benefit_keys
    warnings = [
        Warning(
            code="double_count",
            message="Учтите оснащение одним способом в выбранных автомобилях",
            factor_key=key,
        )
        for key in sorted(conflicts)
    ]
    if any(item.assessment_status == "incomplete" for item in summaries):
        warnings.append(Warning(code="incomplete", message="Часть оценок еще не завершена"))
    if minimum < 0:
        warnings.append(
            Warning(
                code="negative_adjusted_cost",
                message="Скорректированная стоимость отрицательна. Проверьте оценку преимуществ",
            )
        )
    return Comparison(
        items=[
            ComparisonItem(
                **item.model_dump(),
                difference_from_min=None if conflicts else item.adjusted_cost - minimum,
            )
            for item in summaries
        ],
        warnings=warnings,
        provisional=bool(warnings),
    )
