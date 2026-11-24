import { expect, it } from 'vitest';
import fixtures from '../../../shared/fixtures/calculation-tests.json';
import { fixtureSummary } from './summary';

it.each(fixtures)('matches Summary fields in the shared $name vector', ({ cars, expected }) => {
  for (const car of cars) {
    const row = expected.items.find(({ car_id }) => car_id === car.id);
    expect(row).toBeDefined();
    const expectedSummary = Object.fromEntries(
      Object.entries(row ?? {}).filter(([key]) => key !== 'difference_from_min'),
    );
    expect(fixtureSummary(car)).toEqual(expectedSummary);
  }
});

it('keeps zero distinct from null and preserves large exact amounts', () => {
  const car = {
    id: 'zero',
    price: 12_345_678_901,
    expenses_reviewed: true,
    benefits_reviewed: true,
    expenses: [{ amount: 0 }],
    benefits: [{ amount: 0 }],
  };
  expect(fixtureSummary(car)).toMatchObject({
    total_expenses: 0,
    total_benefits: 0,
    acquisition_cost: car.price,
    adjusted_cost: car.price,
    unknown_expenses: 0,
    assessment_status: 'complete',
  });
  expect(fixtureSummary({ ...car, expenses: [{ amount: null }] })).toMatchObject({
    total_expenses: 0,
    unknown_expenses: 1,
    assessment_status: 'incomplete',
  });
  expect(() => fixtureSummary({ ...car, expenses: [{ amount: Number.MAX_SAFE_INTEGER }] })).toThrow(
    RangeError,
  );
});
