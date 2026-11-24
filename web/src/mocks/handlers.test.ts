import { setupServer } from 'msw/node';
import { afterAll, beforeAll, beforeEach, expect, it } from 'vitest';
import type { Car, Listing } from '@/entities/vehicle/api';
import { createApiClient } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';
import { handlers } from './handlers';

const config = { mode: 'mock', scenario: 'empty', baseUrl: 'https://mock.test' } as const;
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => {
  localStorage.clear();
  server.resetHandlers(...handlers(config, localStorage));
});
afterAll(() => server.close());

async function openSession() {
  const publicApi = createApiClient({ baseUrl: config.baseUrl });
  const { token } = await publicApi.request<components['schemas']['Session']>('/api/session', {
    method: 'POST',
  });
  return createApiClient({ baseUrl: config.baseUrl, getToken: () => token });
}

it('previews without saving, persists a created car, enforces ownership and deletes it', async () => {
  const owner = await openSession();
  const other = await openSession();
  expect(await owner.request('/api/cars')).toEqual([]);

  const listing = await owner.request<Listing>('/api/cars/import', {
    method: 'POST',
    body: { url: 'https://auto.drom.ru/demo/1.html' },
  });
  expect(listing.source).toBe('demo');
  expect(await owner.request('/api/cars')).toEqual([]);

  const car = await owner.request<Car>('/api/cars', {
    method: 'POST',
    body: { source: 'manual', brand: 'Honda', model: 'Accord', price: 900000, mileage_km: 0 },
  });
  expect(car).toMatchObject({ mileage_km: 0, year: null, expenses_reviewed: false });
  const detail = `/api/cars/${car.id}` as const;
  const summary = `${detail}/summary` as const;

  server.resetHandlers(...handlers(config, localStorage));
  expect(await owner.request('/api/cars')).toEqual([car]);
  expect(await owner.request(detail)).toEqual(car);
  expect(await owner.request(summary)).toMatchObject({
    listing_price: 900000,
    assessment_status: 'incomplete',
  });
  expect(await other.request('/api/cars')).toEqual([]);
  await expect(other.request(detail)).rejects.toMatchObject({ status: 404 });
  await expect(other.request(detail, { method: 'DELETE' })).rejects.toMatchObject({ status: 404 });

  await expect(owner.request(detail, { method: 'DELETE' })).resolves.toBeUndefined();
  server.resetHandlers(...handlers(config, localStorage));
  expect(await owner.request('/api/cars')).toEqual([]);
  await expect(owner.request(detail)).rejects.toMatchObject({ status: 404 });
});
