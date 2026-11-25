import {
  acquisitionCost as costSchema,
  adjustedCost as adjustedSchema,
  moneyAmount,
  positiveMoney,
} from '@/shared/api/money';
import type { Car, Summary } from '@/entities/vehicle/api';

export type SummaryInput = Pick<Car, 'id' | 'price' | 'expenses_reviewed' | 'benefits_reviewed'> & {
  expenses: readonly Pick<Car['expenses'][number], 'amount'>[];
  benefits: readonly Pick<Car['benefits'][number], 'amount'>[];
};

export function fixtureSummary(car: SummaryInput): Summary {
  function summarize(items: readonly { amount: number | null }[]) {
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
  const adjustedCost = acquisitionCost - benefits.total;
  if (
    !positiveMoney.safeParse(car.price).success ||
    ![...car.expenses, ...car.benefits].every(
      ({ amount }) => amount === null || moneyAmount.safeParse(amount).success,
    ) ||
    !moneyAmount.safeParse(expenses.total).success ||
    !moneyAmount.safeParse(benefits.total).success ||
    !costSchema.safeParse(acquisitionCost).success ||
    !adjustedSchema.safeParse(adjustedCost).success
  ) {
    throw new RangeError('Денежный итог превышает точный диапазон целых чисел.');
  }
  const complete =
    car.expenses_reviewed && car.benefits_reviewed && !expenses.unknown && !benefits.unknown;

  return {
    car_id: car.id,
    listing_price: car.price,
    total_expenses: expenses.total,
    total_benefits: benefits.total,
    acquisition_cost: acquisitionCost,
    adjusted_cost: adjustedCost,
    assessment_status: complete ? 'complete' : 'incomplete',
    unknown_expenses: expenses.unknown,
    unknown_benefits: benefits.unknown,
  };
}
