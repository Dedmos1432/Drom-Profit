import { z } from 'zod';
import { comparisonDifference } from '@/shared/api/money';
import type { Comparison, ComparisonRequest, ComparisonWarning } from '@/entities/comparison/api';
import type { Benefit, Expense } from '@/entities/vehicle/records';
import { fixtureSummary } from './summary';
import type { SummaryInput } from './summary';

type ComparisonCar = Omit<SummaryInput, 'expenses' | 'benefits'> & {
  expenses: readonly Pick<Expense, 'amount' | 'kind' | 'factor_key'>[];
  benefits: readonly Pick<Benefit, 'amount' | 'factor_key'>[];
};

export const comparisonRequestSchema = z.strictObject({
  car_ids: z
    .array(z.string())
    .min(2)
    .refine((ids) => ids.length === new Set(ids).size),
}) satisfies z.ZodType<ComparisonRequest>;

export function fixtureComparison(cars: readonly ComparisonCar[]): Comparison {
  const summaries = cars.map(fixtureSummary);
  const minimum = summaries.reduce(
    (min, { adjusted_cost }) => Math.min(min, adjusted_cost),
    Infinity,
  );
  const warnings: ComparisonWarning[] = [];
  if (summaries.some(({ assessment_status }) => assessment_status === 'incomplete')) {
    warnings.push({
      code: 'incomplete',
      message: 'Некоторые расходы или преимущества требуют уточнения.',
    });
  }
  const benefits = new Set(cars.flatMap((car) => car.benefits.map(({ factor_key }) => factor_key)));
  const conflicts = new Set(
    cars.flatMap((car) =>
      car.expenses.flatMap(({ kind, factor_key }) =>
        kind === 'missing_equipment' && factor_key && benefits.has(factor_key) ? [factor_key] : [],
      ),
    ),
  );
  for (const factor_key of conflicts) {
    warnings.push({
      code: 'double_count',
      message: 'Обнаружен двойной учёт оснащения.',
      factor_key,
    });
  }
  if (minimum < 0) {
    warnings.push({
      code: 'negative_adjusted_cost',
      message: 'Проверьте персональную ценность преимуществ.',
    });
  }
  return {
    provisional: warnings.length > 0,
    warnings,
    items: summaries.map((summary) => {
      const difference = conflicts.size ? null : summary.adjusted_cost - minimum;
      if (difference !== null && !comparisonDifference.safeParse(difference).success) {
        throw new RangeError('Разница превышает точный диапазон целых рублей.');
      }
      return { ...summary, difference_from_min: difference };
    }),
  };
}
