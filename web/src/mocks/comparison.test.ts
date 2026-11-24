import { setupServer } from 'msw/node';
import { afterAll, beforeAll, beforeEach, expect, it } from 'vitest';
import fixtures from '../../../shared/fixtures/calculation-tests.json';
import type { Comparison } from '@/entities/comparison/api';
import { vehiclePath } from '@/entities/vehicle/api';
import type { Expense } from '@/entities/vehicle/records';
import { createApiClient } from '@/shared/api/client';
import { comparisonColumns } from '@/widgets/comparison-table/columns';
import { handlers } from './handlers';
import { createMockStore } from './store';

const config = { mode: 'mock', scenario: 'empty', baseUrl: 'https://comparison.test' } as const;
const server = setupServer(...handlers(config, localStorage));
const store = createMockStore(localStorage, config);
const client = (token = '') => createApiClient({ baseUrl: config.baseUrl, getToken: () => token });

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => localStorage.clear());
afterAll(() => server.close());

async function createCar(token: string, price: number) {
  const car = await store.add(token, {
    source: 'manual',
    brand: 'Toyota',
    model: 'Camry',
    year: 2015,
    price,
  });
  if (!car) throw new Error('Missing test session');
  return car;
}

function compare(api: ReturnType<typeof client>, ...ids: string[]) {
  return api.request<Comparison>('/api/comparison', { method: 'POST', body: { car_ids: ids } });
}

it.each(fixtures)(
  'matches Comparison API fields for the shared $name vector',
  async ({ name, cars, expected }) => {
    const token = store.createSession();
    for (const car of cars) {
      const saved = await createCar(token, car.price);
      await store.mutateCar(token, saved.id, (target) => {
        Object.assign(target, {
          id: car.id,
          expenses_reviewed: car.expenses_reviewed,
          benefits_reviewed: car.benefits_reviewed,
          expenses: car.expenses.map((expense, index) => ({
            title: 'Расход',
            comment: null,
            ...expense,
            id: String(index),
            kind: expense.kind as Expense['kind'],
          })),
          benefits: car.benefits.map((benefit, index) => ({
            title: 'Преимущество',
            comment: null,
            ...benefit,
            id: String(index),
          })),
        });
      });
    }
    const result = await compare(client(token), ...cars.map(({ id }) => id));
    expect(result.items).toHaveLength(expected.items.length);
    for (const expectedItem of expected.items) {
      expect(result.items.find(({ car_id }) => car_id === expectedItem.car_id)).toEqual(
        expectedItem,
      );
    }
    expect(result.provisional).toBe(expected.provisional);
    expect(result.warnings.map(({ code }) => code)).toEqual(expected.warning_codes);
    if (name === 'double_count') {
      expect(result.warnings).toContainEqual(
        expect.objectContaining({ code: 'double_count', factor_key: 'winter_tires' }),
      );
    }
  },
);

it('rejects duplicate or missing IDs and requires ownership for every car', async () => {
  const token = store.createSession();
  const first = await createCar(token, 250_000);
  const second = await createCar(token, 220_000);
  const outsider = await createCar(store.createSession(), 180_000);
  const api = client(token);

  await expect(compare(api, first.id)).rejects.toMatchObject({ status: 422 });
  await expect(compare(api, first.id, first.id)).rejects.toMatchObject({ status: 422 });
  await expect(compare(client(), first.id, second.id)).rejects.toMatchObject({ status: 401 });
  await expect(compare(api, first.id, outsider.id)).rejects.toMatchObject({ status: 404 });
  await api.request(vehiclePath(second.id), { method: 'DELETE' });
  await expect(compare(api, first.id, second.id)).rejects.toMatchObject({ status: 404 });
});

it('uses the latest factors and associates columns by ID in the returned API order', async () => {
  const token = store.createSession();
  const first = await createCar(token, 250_000);
  const second = await createCar(token, 220_000);
  const api = client(token);
  const before = await compare(api, second.id, first.id);
  expect(before.items.map(({ difference_from_min }) => difference_from_min)).toEqual([0, 30_000]);

  await api.request(`/api/cars/${second.id}/expenses`, {
    method: 'POST',
    body: { kind: 'repair', amount: 50_000, title: 'Ремонт' },
  });
  const after = await compare(api, second.id, first.id);
  const columns = comparisonColumns([first, second], after.items);
  expect(
    columns.map(({ car, item }) => [car.id, car.price, item.car_id, item.difference_from_min]),
  ).toEqual([
    [second.id, 220_000, second.id, 20_000],
    [first.id, 250_000, first.id, 0],
  ]);
});
