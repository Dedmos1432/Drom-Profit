import { setupServer } from 'msw/node';
import { afterAll, beforeAll, beforeEach, expect, it } from 'vitest';
import vectors from '../../../shared/fixtures/money-contract-tests.json';
import { vehicleApi } from '@/entities/vehicle/api';
import { createApiClient } from '@/shared/api/client';
import { MONEY_LIMIT, carMoney, listingMoney } from '@/shared/api/money';
import { handlers } from './handlers';

const config = { mode: 'mock', scenario: 'empty', baseUrl: 'https://mock.test' } as const;
const server = setupServer();
const input = { source: 'manual', brand: 'Демо', model: 'Проверка' };
const flags = { expenses_reviewed: true, benefits_reviewed: true };
const groups = ['expenses', 'benefits'] as const;

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => {
  localStorage.clear();
  server.resetHandlers(...handlers(config, localStorage));
});
afterAll(() => server.close());

function literalBody(data: object, field: string, literal: string | null) {
  const body = JSON.stringify(data);
  return literal === null
    ? body
    : body.slice(0, -1) +
        (Object.keys(data).length ? ',' : '') +
        JSON.stringify(field) +
        ':' +
        literal +
        '}';
}

async function request(method: string, path: string, token?: string, body?: string) {
  const response = await fetch(config.baseUrl + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body,
  });
  return { status: response.status, data: response.status === 204 ? null : await response.json() };
}

async function session() {
  const result = await request('POST', '/api/session');
  expect(result.status).toBe(201);
  return result.data.token as string;
}

async function addCar(token: string, price = 1) {
  const saved = await request('POST', '/api/cars', token, JSON.stringify({ ...input, price }));
  expect(saved.status).toBe(201);
  return '/api/cars/' + (saved.data.id as string);
}

function factor(group: (typeof groups)[number]) {
  return group === 'expenses' ? { title: 'Фактор', kind: 'repair' } : { title: 'Фактор' };
}

function outcome(result: Awaited<ReturnType<typeof request>>, status: number, code: string | null) {
  expect(result.status).toBe(status);
  if (code !== null) expect(result.data.code).toBe(code);
}

it.each(vectors.inputs)('matches Core for the $id literal', async (row) => {
  expect(MONEY_LIMIT).toBe(vectors.limit);
  const token = await session();
  const p = row.expect.price as 'validation_error' | 'amount_out_of_range' | null;
  const m = row.expect.market_valuation as typeof p;
  const a = row.expect.amount as typeof p;
  const created = await request(
    'POST',
    '/api/cars',
    token,
    literalBody(input, 'price', row.literal),
  );
  outcome(created, vectors.http[p ?? 'price_success'], p);
  if (p === null) {
    expect(created.data.price).toBe(JSON.parse(row.literal!));
  } else {
    expect((await request('GET', '/api/cars', token)).data).toEqual([]);
  }
  const car = await addCar(token);
  const valuation = await request(
    'PATCH',
    car,
    token,
    literalBody({}, 'market_valuation', row.literal),
  );
  outcome(valuation, vectors.http[m ?? 'market_valuation_success'], m);
  if (m === null && row.literal !== null)
    expect(valuation.data.market_valuation).toBe(JSON.parse(row.literal));

  for (const group of groups) {
    await request('PATCH', car, token, JSON.stringify(flags));
    const written = await request(
      'POST',
      car + '/' + group,
      token,
      literalBody(factor(group), 'amount', row.literal),
    );
    outcome(written, vectors.http[a ?? 'factor_success'], a);
    const state = (await request('GET', car, token)).data;
    if (a === null) {
      expect(state[group][0].amount).toBe(
        JSON.parse(row.literal!) || (row.literal === 'null' ? null : 0),
      );
      expect(state[group + '_reviewed']).toBe(false);
    } else {
      expect(state[group]).toEqual([]);
      expect(state[group + '_reviewed']).toBe(true);
    }
  }
});

it('prioritizes structural errors over monetary range errors', async () => {
  const token = await session();
  const car = await addCar(token);
  const { price_literal, market_valuation_literal, status, code } = vectors.mixed;
  outcome(
    await request(
      'PATCH',
      car,
      token,
      '{"price":' + price_literal + ',"market_valuation":' + market_valuation_literal + '}',
    ),
    status,
    code,
  );
});

it.each(groups)(
  'enforces the %s group cap atomically before writing or clearing review',
  async (group) => {
    const token = await session();
    const car = await addCar(token);
    const path = car + '/' + group;
    const { first, remainder, overflow, reject_code, reject_status } = vectors.aggregates;
    const a = await request(
      'POST',
      path,
      token,
      JSON.stringify({ ...factor(group), amount: first }),
    );
    const b = await request(
      'POST',
      path,
      token,
      JSON.stringify({ ...factor(group), amount: remainder }),
    );
    expect([a.status, b.status]).toEqual([201, 201]);
    const item = path + '/' + (a.data.id as string);
    await request('PATCH', car, token, JSON.stringify(flags));
    const baseline = (await request('GET', car, token)).data;
    outcome(await request('PATCH', item, token, JSON.stringify({ amount: first })), 200, null);
    outcome(
      await request('POST', path, token, JSON.stringify({ ...factor(group), amount: overflow })),
      reject_status,
      reject_code,
    );
    outcome(
      await request('PATCH', item, token, JSON.stringify({ amount: first + 1 })),
      reject_status,
      reject_code,
    );
    expect((await request('GET', car, token)).data).toEqual(baseline);
    expect(baseline[group + '_reviewed']).toBe(vectors.aggregates.preserve_reviewed);
    outcome(await request('PATCH', item, token, '{"amount":null}'), 200, null);
    outcome(await request('PATCH', item, token, '{"amount":0}'), 200, null);
    outcome(await request('DELETE', path + '/' + (b.data.id as string), token), 204, null);
    expect((await request('GET', car + '/summary', token)).data['total_' + group]).toBe(0);
  },
);

it('matches Core at the exact derivative bounds', async () => {
  const token = await session();
  const expected = vectors.derivatives;
  const high = await addCar(token, expected.max_price);
  const low = await addCar(token, expected.min_price);
  await request(
    'POST',
    high + '/expenses',
    token,
    JSON.stringify({ ...factor('expenses'), amount: expected.max_expenses }),
  );
  await request(
    'POST',
    low + '/benefits',
    token,
    JSON.stringify({ ...factor('benefits'), amount: expected.max_benefits }),
  );
  const result = await request(
    'POST',
    '/api/comparison',
    token,
    JSON.stringify({ car_ids: [high.split('/').at(-1), low.split('/').at(-1)] }),
  );
  expect(result.status).toBe(200);
  expect(result.data.items[0]).toMatchObject({
    acquisition_cost: expected.acquisition_cost,
    adjusted_cost: expected.adjusted_max,
    difference_from_min: expected.difference_max,
  });
  expect(result.data.items[1]).toMatchObject({
    adjusted_cost: expected.adjusted_min,
    difference_from_min: 0,
  });
});

it.each(['2.0', '1e3', '1.0000000000000001', '1000000000001', '9007199254740993'])(
  'rejects a raw response containing money token %s before caching',
  async (raw) => {
    const car =
      '{"price":1,"market_valuation":null,"expenses":[{"amount":' + raw + '}],"benefits":[]}';
    const api = createApiClient({
      baseUrl: config.baseUrl,
      fetcher: async () => new Response('[' + car + ']'),
    });
    await expect(vehicleApi.list(api)).rejects.toMatchObject({ code: 'invalid_money' });
    expect(carMoney.safeParse(JSON.parse(car)).success).toBe(Number(raw) <= MONEY_LIMIT);
  },
);

it('preserves exact nullable preview fields', () => {
  expect(listingMoney.safeParse({ price: null, market_valuation: null }).success).toBe(true);
  expect(listingMoney.safeParse({ price: null, market_valuation: 0 }).success).toBe(false);
});
