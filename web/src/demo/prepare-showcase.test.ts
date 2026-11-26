import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, beforeAll, beforeEach, expect, it, vi } from 'vitest';
import { sessionStorageKey } from '@/entities/session/session-store';
import { vehicleApi } from '@/entities/vehicle/api';
import { handlers } from '@/mocks/handlers';
import { createApiClient } from '@/shared/api/client';
import type { AppConfig } from '@/shared/config/env';
import { prepareShowcase } from './prepare-showcase';

const config: AppConfig = { baseUrl: 'http://127.0.0.1:8000', mode: 'real', scenario: 'empty' };
const key = sessionStorageKey(config);
const locks = {
  request: (_key: string, callback: () => Promise<unknown>) => callback(),
} as LockManager;
const server = setupServer(...handlers({ ...config, mode: 'mock' }, localStorage));
const requests = vi.fn();
server.events.on('request:start', ({ request }) => requests(request.method));
const prepare = () => prepareShowcase(config, localStorage, locks);
const api = createApiClient({
  baseUrl: config.baseUrl,
  getToken: () => localStorage.getItem(key),
  fetcher: (input, init) => fetch(input, init),
});

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterAll(() => server.close());
beforeEach(() => {
  localStorage.clear();
  requests.mockClear();
  server.resetHandlers();
});

it('keeps user changes, deletions and additions when preparation is run again', async () => {
  expect(await prepare()).toBe('created');
  const prepared = await vehicleApi.list(api);
  expect(prepared).toHaveLength(5);

  const camry = prepared.find(({ listing_id }) => listing_id === '101')!;
  const rav4 = prepared.find(({ listing_id }) => listing_id === '105')!;
  await vehicleApi.patch(api, camry.id, { trim: 'Уточнено после осмотра' });
  await vehicleApi.remove(api, rav4.id);
  const extra = await vehicleApi.create(api, {
    brand: 'Suzuki',
    model: 'Vitara',
    price: 800_000,
    source: 'manual',
  });
  const before = await vehicleApi.list(api);
  requests.mockClear();

  expect(await prepare()).toBe('existing');
  expect(requests).not.toHaveBeenCalled();
  expect(await vehicleApi.list(api)).toEqual(before);
  expect(before).toHaveLength(5);
  expect(before).toContainEqual(expect.objectContaining({ id: extra.id }));
  expect(before).toContainEqual(
    expect.objectContaining({ id: camry.id, trim: 'Уточнено после осмотра' }),
  );
  expect(before.some(({ id }) => id === rav4.id)).toBe(false);
});

it('leaves an existing ordinary session alone', async () => {
  localStorage.setItem(key, 'existing-test-session');
  await expect(prepare()).rejects.toThrow('уже есть сессия');
  expect(requests).not.toHaveBeenCalled();
  expect(localStorage.getItem(key)).toBe('existing-test-session');
});

it('does not repeat writes after an interrupted preparation', async () => {
  server.use(
    http.post(config.baseUrl + '/api/cars/:id/expenses', () =>
      HttpResponse.json({ code: 'internal_error', message: 'Тестовый сбой' }, { status: 500 }),
    ),
  );
  await expect(prepare()).rejects.toThrow('Тестовый сбой');
  const partial = await vehicleApi.list(api);
  expect(partial).toHaveLength(1);
  requests.mockClear();

  await expect(prepare()).rejects.toThrow('ранее прервалась');
  expect(requests).not.toHaveBeenCalled();
  expect(await vehicleApi.list(api)).toEqual(partial);
});
