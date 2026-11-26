import { onlineManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { MemoryRouter } from 'react-router';
import { afterAll, afterEach, beforeAll, beforeEach, expect, it } from 'vitest';
import listings from '../../../shared/fixtures/import-listings.json';
import { sessionStorageKey } from '@/entities/session/session-store';
import { vehicleApi } from '@/entities/vehicle/api';
import { recordError } from '@/features/manage-vehicle-records/record-actions';
import { ApiError, createApiClient } from '@/shared/api/client';
import { MONEY_LIMIT } from '@/shared/api/money';
import { AppRoutes } from './router';

const config = { mode: 'real', scenario: 'empty', baseUrl: 'http://127.0.0.1:8000' } as const;
const previewUrl = config.baseUrl + '/api/cars/import';
const canonical = listings[0]!.listing_url;
const token = 'integration-test-session';
const storedSession = sessionStorageKey(config);
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => {
  localStorage.clear();
  server.resetHandlers(
    http.post(config.baseUrl + '/api/session', () =>
      HttpResponse.json({ token, token_type: 'bearer' }, { status: 201 }),
    ),
  );
});
afterAll(() => server.close());
afterEach(() => onlineManager.setOnline(true));

function openPage(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes config={config} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

it('identifies the previous preview and retains its edits after an unsuccessful reimport', async () => {
  const requested: unknown[] = [];
  server.use(
    http.post(previewUrl, async ({ request }) => {
      const body = await request.json();
      requested.push(body);
      return requested.length === 1
        ? HttpResponse.json(listings[0])
        : HttpResponse.json(
            { code: 'listing_unavailable', message: 'Private detail' },
            { status: 404 },
          );
    }),
  );
  const user = openPage('/cars/new');
  const input = await screen.findByRole('textbox', { name: 'Ссылка на объявление' });
  await user.type(input, canonical + '?ref=review#photo');
  await user.click(screen.getByRole('button', { name: 'Получить данные' }));
  const preview = await screen.findByRole('region', { name: 'Предпросмотр автомобиля' });
  const price = within(preview).getByRole('textbox', { name: 'Цена, ₽' });
  await user.clear(price);
  await user.type(price, '1234567');
  await user.clear(input);
  await user.type(input, 'https://auto.drom.ru/demo/999.html');
  await user.click(screen.getByRole('button', { name: 'Получить данные' }));

  expect(await screen.findByText(/Объявление недоступно/)).toBeInTheDocument();
  expect(
    within(preview).getByText(/Предыдущий предпросмотр и ваши правки сохранены/),
  ).toBeInTheDocument();
  expect(within(preview).getByText(canonical)).toBeInTheDocument();
  expect(price).toHaveValue('1234567');
  expect(requested).toEqual([
    { url: canonical + '?ref=review#photo' },
    { url: 'https://auto.drom.ru/demo/999.html' },
  ]);
});

it('reads an old one-photo preview and discards unsafe or inconsistent references before caching', async () => {
  const [fixture] = listings;
  const image = 'https://images.example.test/camry.jpg';
  const second = 'https://images.example.test/rear.jpg';
  const legacy = { ...fixture, image_url: image, image_urls: undefined };
  const api = createApiClient({ baseUrl: config.baseUrl });
  server.use(http.post(previewUrl, () => HttpResponse.json(legacy)));
  await expect(vehicleApi.import(api, { url: canonical })).resolves.toMatchObject({
    image_url: image,
    image_urls: [image],
  });
  server.use(
    http.post(previewUrl, () =>
      HttpResponse.json({
        ...legacy,
        image_urls: [second, 'file:///tmp/car.jpg'],
      }),
    ),
  );
  await expect(vehicleApi.import(api, { url: canonical })).resolves.toMatchObject({
    image_url: image,
    image_urls: [image],
  });
  server.use(
    http.post(previewUrl, () =>
      HttpResponse.json({
        ...legacy,
        image_url: null,
        image_urls: [image, 'https://127.0.0.1/photo.jpg', second],
      }),
    ),
  );
  await expect(vehicleApi.import(api, { url: canonical })).resolves.toMatchObject({
    image_url: image,
    image_urls: [image, second],
  });
});

it.each([
  [400, 'invalid_url', 'Укажите ссылку на объявление Дрома.'],
  [403, 'source_forbidden', /У источника нет доступных данных/],
  [422, 'validation_error', /Не удалось получить данные\. Повторите попытку/],
  [502, 'parse_error', /Не удалось прочитать данные объявления/],
  [503, 'source_unavailable', /Источник временно недоступен/],
  [0, 'network_error', /Не удалось связаться с сервером\. Повторите попытку/],
])(
  'shows a real import failure (%s %s) and supports retry and manual entry',
  async (status, code, message) => {
    const mockKey = sessionStorageKey({ ...config, mode: 'mock' });
    localStorage.setItem(mockKey, 'drom-demo-test');
    const tokens: (string | null)[] = [];
    server.use(
      http.post(previewUrl, ({ request }) => {
        tokens.push(request.headers.get('Authorization'));
        return status
          ? HttpResponse.json({ code, message: 'Private detail' }, { status })
          : HttpResponse.error();
      }),
    );

    const user = openPage('/cars/new');
    const input = await screen.findByRole('textbox', { name: 'Ссылка на объявление' });
    const panel = screen.getByRole('region', { name: 'Получение данных объявления' });
    for (const listing of listings) {
      expect(within(panel).getByText(listing.listing_url)).toBeInTheDocument();
    }
    expect(within(panel).queryByText(/demo\/10[1-5]/)).not.toBeInTheDocument();
    if (!status) onlineManager.setOnline(false);
    await user.type(input, canonical);
    await user.click(within(panel).getByRole('button', { name: 'Получить данные' }));
    await within(panel).findByText(message);
    expect(
      screen.queryByRole('region', { name: 'Предпросмотр автомобиля' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Private detail')).not.toBeInTheDocument();
    await user.click(within(panel).getByRole('button', { name: 'Получить данные' }));
    await within(panel).findByText(message);
    expect(tokens).toEqual(['Bearer ' + token, 'Bearer ' + token]);
    await user.click(screen.getByRole('button', { name: 'Ввести данные вручную' }));
    expect(screen.getByRole('heading', { name: 'Ручное добавление' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Марка' })).toHaveValue('');
  },
);

const ids = ['first-car', 'second-car'];
const summary = {
  car_id: ids[0],
  listing_price: MONEY_LIMIT,
  total_expenses: 2,
  total_benefits: 0,
  acquisition_cost: 1_200_002,
  adjusted_cost: 1_200_002,
  unknown_expenses: 0,
  unknown_benefits: 0,
  assessment_status: 'complete',
};
const unsafe = Number.MAX_SAFE_INTEGER + 1;

it('rejects a Summary with an unsafe derived amount', async () => {
  server.use(
    http.get(
      config.baseUrl + '/api/cars/first-car/summary',
      () =>
        new HttpResponse(
          JSON.stringify({ ...summary, acquisition_cost: '__total__' }).replace(
            '"__total__"',
            '9007199254740993',
          ),
          {
            headers: { 'Content-Type': 'application/json' },
          },
        ),
    ),
  );
  const api = createApiClient({ baseUrl: config.baseUrl });
  await expect(
    vehicleApi.summary(api, ids[0]!, new AbortController().signal),
  ).rejects.toMatchObject({
    code: 'invalid_summary',
  });
});

it.each([
  { adjusted_cost: -unsafe, difference_from_min: 0 },
  { adjusted_cost: 1, difference_from_min: unsafe },
])('withholds the comparison table for unsafe amounts %j', async (changes) => {
  const scope = 'test-comparison-scope';
  localStorage.setItem(storedSession, token);
  localStorage.setItem(storedSession + '.scope', scope);
  localStorage.setItem(storedSession + '.comparison.' + scope, JSON.stringify(ids));
  server.use(
    http.get(config.baseUrl + '/api/cars', () =>
      HttpResponse.json(
        ids.map((id) => ({
          id,
          price: 1,
          market_valuation: null,
          expenses: [],
          benefits: [],
        })),
      ),
    ),
    http.post(config.baseUrl + '/api/comparison', () =>
      HttpResponse.json({
        items: [
          { ...summary, ...changes },
          { ...summary, car_id: ids[1], difference_from_min: 1 },
        ],
        warnings: [],
        provisional: false,
      }),
    ),
  );
  openPage('/comparison');
  expect(await screen.findByText('Сервер вернул некорректное сравнение.')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

it.each([
  ['duplicate_factor', /уже учтено в этой группе/],
  ['conflicting_equipment', /как расход и преимущество/],
])('explains the Core response %s for an invalid equipment factor', (code, expected) => {
  expect(recordError(new ApiError('http', code, 'Private detail', 422))).toMatch(expected);
});
