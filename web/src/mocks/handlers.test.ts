import { existsSync } from 'node:fs';
import { URL } from 'node:url';
import { setupServer } from 'msw/node';
import { afterAll, beforeAll, beforeEach, expect, it } from 'vitest';
import { vehiclePath } from '@/entities/vehicle/api';
import type { Car, Listing } from '@/entities/vehicle/api';
import type { Comparison } from '@/entities/comparison/api';
import { updateRecord } from '@/entities/vehicle/records';
import type { Benefit, Expense, Note } from '@/entities/vehicle/records';
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

it('uses the same five models for showcase and editable imports while keeping assessments separate', async () => {
  const showcase = { ...config, scenario: 'showcase' } as const;
  server.resetHandlers(...handlers(showcase, localStorage));
  const api = await openSession();
  const cars = await api.request<Car[]>('/api/cars');
  const models = ['Toyota Camry', 'Honda Accord', 'Mazda 6', 'Nissan X-Trail', 'Toyota RAV4'];
  expect(cars.map(({ brand, model }) => brand + ' ' + model)).toEqual(models);
  expect(new Set(cars.map(({ image_url }) => image_url)).size).toBe(5);

  for (const [index, car] of cars.entries()) {
    const url = 'https://auto.drom.ru/demo/' + (101 + index) + '.html';
    const listing = await api.request<Listing>('/api/cars/import', {
      method: 'POST',
      body: { url },
    });
    expect(listing).toEqual(
      expect.objectContaining({
        brand: car.brand,
        model: car.model,
        year: car.year,
        price: car.price,
        mileage_km: car.mileage_km,
        image_url: car.image_url,
        listing_url: url,
      }),
    );
    expect(listing.image_url).toContain('/demo/vehicles/');
    expect(listing.image_urls).toHaveLength(3);
    expect(listing.image_url).toBe(listing.image_urls[0]);
    for (const path of listing.image_urls) {
      expect(existsSync(new URL('../../public' + path, import.meta.url)), path).toBe(true);
    }
    expect(await api.request(vehiclePath(car.id))).toEqual({
      ...car,
      image_urls: listing.image_urls,
    });
    expect(car).not.toHaveProperty('image_urls');
    expect(listing).not.toHaveProperty('expenses');
  }
  expect(await api.request('/api/cars')).toEqual(cars);
  const comparison = await api.request<Comparison>('/api/comparison', {
    method: 'POST',
    body: { car_ids: cars.map(({ id }) => id) },
  });
  expect(comparison.items.map(({ assessment_status }) => assessment_status)).toEqual([
    'complete',
    'incomplete',
    'complete',
    'incomplete',
    'complete',
  ]);
  expect(comparison.items.map(({ difference_from_min }) => difference_from_min)).toEqual([
    0, 147_000, 232_000, 353_000, 631_000,
  ]);

  server.resetHandlers(...handlers({ ...config, scenario: 'seeded' }, localStorage));
  const seeded = await openSession();
  expect(await seeded.request('/api/cars')).toHaveLength(3);
  const legacy = await seeded.request<Listing>('/api/cars/import', {
    method: 'POST',
    body: { url: 'https://auto.drom.ru/demo/1.html' },
  });
  expect(legacy.model).toBe('Camry');
  server.resetHandlers(...handlers(showcase, localStorage));
  expect(await api.request('/api/cars')).toEqual(cars);
});

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
  expect(await owner.request(detail)).toEqual({ ...car, image_urls: [] });
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

it('applies the gallery write rules and keeps confirmation flags on photo-only changes', async () => {
  const api = await openSession();
  const listing = await api.request<Listing>('/api/cars/import', {
    method: 'POST',
    body: { url: 'https://auto.drom.ru/demo/1.html' },
  });
  const created = await api.request<Car>('/api/cars', { method: 'POST', body: listing });
  const path = vehiclePath(created.id);
  const [first, second, third] = listing.image_urls;
  const patch = (body: unknown) => api.request<Car>(path, { method: 'PATCH', body });
  await patch({ expenses_reviewed: true, benefits_reviewed: true });
  await expect(patch({ image_url: second })).resolves.toMatchObject({
    image_url: second,
    expenses_reviewed: true,
    benefits_reviewed: true,
  });
  await expect(patch({ image_url: null, image_urls: [first] })).rejects.toMatchObject({
    status: 422,
    code: 'validation_error',
  });
  expect(await api.request(path)).toMatchObject({ image_urls: [second, first, third] });
  await patch({ image_urls: [third, third, first] });
  expect(await api.request(path)).toMatchObject({ image_url: third, image_urls: [third, first] });
  await patch({ image_urls: [] });
  expect(await api.request(path)).toMatchObject({ image_url: null, image_urls: [] });
});

it('canonicalizes imports and keeps request validation distinct from URL errors', async () => {
  const api = await openSession();
  const preview = (body: unknown) =>
    api.request<Listing>('/api/cars/import', { method: 'POST', body });

  await expect(
    preview({ url: 'https://AUTO.DROM.RU/demo/1.html?from=web#photo' }),
  ).resolves.toMatchObject({
    listing_id: 'demo-001',
    listing_url: 'https://auto.drom.ru/demo/1.html',
  });
  await expect(
    preview({ url: 'https://auto.drom.ru/demo/101.html?ref=local' }),
  ).resolves.toMatchObject({
    listing_url: 'https://auto.drom.ru/demo/101.html',
  });
  await expect(preview({ url: 'https://auto.drom.ru:443/demo/1.html' })).rejects.toMatchObject({
    status: 400,
    code: 'invalid_url',
  });
  await expect(
    preview({ url: 'https://auto.drom.ru/demo/999.html#missing' }),
  ).rejects.toMatchObject({
    status: 404,
    code: 'listing_unavailable',
  });
  await expect(preview({ url: 123 })).rejects.toMatchObject({
    status: 422,
    code: 'validation_error',
  });
  await expect(preview({ url: 'invalid', extra: true })).rejects.toMatchObject({
    status: 422,
    code: 'validation_error',
  });
});

async function createCar() {
  const api = await openSession();
  const car = await api.request<Car>('/api/cars', {
    method: 'POST',
    body: { source: 'manual', brand: 'Toyota', model: 'Camry', price: 100000, year: 2010 },
  });
  return { api, car, path: vehiclePath(car.id) };
}

it('patches only supplied car fields and applies explicit review flags after a reset', async () => {
  const { api, path } = await createCar();
  await api.request(path, {
    method: 'PATCH',
    body: { expenses_reviewed: true, benefits_reviewed: true },
  });
  expect(await api.request(`${path}/summary`)).toMatchObject({ assessment_status: 'complete' });
  expect(await api.request(path, { method: 'PATCH', body: { price: 100000 } })).toMatchObject({
    expenses_reviewed: true,
    benefits_reviewed: true,
  });
  expect(
    await api.request(path, { method: 'PATCH', body: { year: null, benefits_reviewed: true } }),
  ).toMatchObject({
    brand: 'Toyota',
    year: null,
    price: 100000,
    expenses_reviewed: false,
    benefits_reviewed: true,
  });
});

it('handles expense and benefit CRUD, resets only the affected flag and rejects double accounting', async () => {
  const { api, car, path } = await createCar();
  const id = car.id;
  const summary = `${path}/summary` as const;
  await api.request(path, {
    method: 'PATCH',
    body: { expenses_reviewed: true, benefits_reviewed: true },
  });
  const expense = (await updateRecord(api, id, 'expenses', {
    action: 'create',
    body: { kind: 'repair', title: 'Ремонт', amount: null },
  })) as Expense;
  expect(await api.request(path)).toMatchObject({
    expenses_reviewed: false,
    benefits_reviewed: true,
  });
  expect(await api.request(summary)).toMatchObject({ total_expenses: 0, unknown_expenses: 1 });
  await updateRecord(api, id, 'expenses', {
    action: 'edit',
    itemId: expense.id,
    body: { amount: 5000 },
  });
  await api.request(path, { method: 'PATCH', body: { expenses_reviewed: true } });

  const benefit = (await updateRecord(api, id, 'benefits', {
    action: 'create',
    body: { title: 'Шины', amount: 12000, factor_key: 'winter_tires' },
  })) as Benefit;
  expect(await api.request(path)).toMatchObject({
    expenses_reviewed: true,
    benefits_reviewed: false,
  });
  expect(await api.request(summary)).toMatchObject({
    acquisition_cost: 105000,
    adjusted_cost: 93000,
    unknown_expenses: 0,
  });
  await expect(
    updateRecord(api, id, 'expenses', {
      action: 'create',
      body: { kind: 'missing_equipment', title: 'Шины', amount: 0, factor_key: 'winter_tires' },
    }),
  ).rejects.toMatchObject({ status: 400, code: 'double_count' });
  await expect(
    updateRecord(api, id, 'benefits', {
      action: 'create',
      body: { title: 'Шины', amount: null, factor_key: 'winter_tires' },
    }),
  ).rejects.toMatchObject({ status: 400, code: 'duplicate_factor' });
  await updateRecord(api, id, 'benefits', {
    action: 'edit',
    itemId: benefit.id,
    body: { amount: 0 },
  });
  expect(await api.request(summary)).toMatchObject({ adjusted_cost: 105000, unknown_benefits: 0 });
  await api.request(path, { method: 'PATCH', body: { benefits_reviewed: true } });
  await updateRecord(api, id, 'benefits', { action: 'delete', itemId: benefit.id });
  expect(await api.request(path)).toMatchObject({
    expenses_reviewed: true,
    benefits_reviewed: false,
  });
  await updateRecord(api, id, 'expenses', { action: 'delete', itemId: expense.id });
  server.resetHandlers(...handlers(config, localStorage));
  expect(await api.request(path)).toMatchObject({
    expenses: [],
    benefits: [],
    expenses_reviewed: false,
    benefits_reviewed: false,
  });
  expect(await api.request(summary)).toMatchObject({
    acquisition_cost: 100000,
    adjusted_cost: 100000,
  });
});

it('persists note edits without changing a summary or giving other sessions write access', async () => {
  const { api, car, path } = await createCar();
  const other = await openSession();
  const summary = `${path}/summary` as const;
  await api.request(path, {
    method: 'PATCH',
    body: { expenses_reviewed: true, benefits_reviewed: true },
  });
  const before = await api.request(summary);
  const note = (await updateRecord(api, car.id, 'notes', {
    action: 'create',
    body: { kind: 'report', text: 'Есть сервисные записи.' },
  })) as Note;
  await expect(other.request(path, { method: 'PATCH', body: { price: 1 } })).rejects.toMatchObject({
    status: 404,
  });
  await expect(
    updateRecord(other, car.id, 'notes', {
      action: 'delete',
      itemId: note.id,
    }),
  ).rejects.toMatchObject({ status: 404 });
  await updateRecord(api, car.id, 'notes', {
    action: 'edit',
    itemId: note.id,
    body: { kind: 'inspection', text: 'Осмотр пройден.' },
  });
  server.resetHandlers(...handlers(config, localStorage));
  expect(await api.request(path)).toMatchObject({
    notes: [{ id: note.id, kind: 'inspection', text: 'Осмотр пройден.' }],
    expenses_reviewed: true,
    benefits_reviewed: true,
  });
  await updateRecord(api, car.id, 'notes', { action: 'delete', itemId: note.id });
  await expect(
    updateRecord(api, car.id, 'notes', { action: 'delete', itemId: note.id }),
  ).rejects.toMatchObject({ status: 404 });
  expect(await api.request(path)).toMatchObject({ notes: [] });
  expect(await api.request(summary)).toEqual(before);
});
