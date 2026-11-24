import type { Car, Summary } from '@/entities/vehicle/api';

export function fixtureSummary(car: Car): Summary {
  function summarize(items: { amount: number | null }[]) {
    return items.reduce(
      (result, { amount }) => ({
        total: result.total + (amount ?? 0),
        unknown: result.unknown + Number(amount === null),
      }),
      { total: 0, unknown: 0 },
    );
  }

  const expenses = summarize(car.expenses);
  const benefits = summarize(car.benefits);
  const acquisitionCost = car.price + expenses.total;
  const complete =
    car.expenses_reviewed && car.benefits_reviewed && !expenses.unknown && !benefits.unknown;

  return {
    car_id: car.id,
    listing_price: car.price,
    total_expenses: expenses.total,
    total_benefits: benefits.total,
    acquisition_cost: acquisitionCost,
    adjusted_cost: acquisitionCost - benefits.total,
    assessment_status: complete ? 'complete' : 'incomplete',
    unknown_expenses: expenses.unknown,
    unknown_benefits: benefits.unknown,
  };
}
