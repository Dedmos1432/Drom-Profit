import { http, HttpResponse } from 'msw';
import listings from '../../../shared/fixtures/import-listings.json';
import type { Car, Listing, Summary } from '@/entities/vehicle/api';
import { carInputSchema, listingUrlSchema } from '@/entities/vehicle/validation';
import { apiUrl } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';
import type { AppConfig } from '@/shared/config/env';
import { createMockStore } from './store';
import { fixtureSummary } from './summary';
import { vehicleWriteHandlers } from './vehicle-writes';

type ContractError = components['schemas']['Error'];
type Session = components['schemas']['Session'];
type Payload = ContractError | Car | Car[] | Listing | Session | Summary;

function response(body: Payload, status = 200) {
  return HttpResponse.json<Payload>(body, { status });
}

function error(status: number, code: string, message: string) {
  return response({ code, message }, status);
}

const unauthorized = () => error(401, 'unauthorized', 'Сессия недоступна.');
const missingCar = () => error(404, 'car_not_found', 'Автомобиль не найден.');
const unavailable = () => error(404, 'listing_unavailable', 'Объявление недоступно.');
const failures = new Map([
  [
    'https://auto.drom.ru/demo/forbidden.html',
    () => error(403, 'source_forbidden', 'Источник недоступен для импорта.'),
  ],
  [
    'https://auto.drom.ru/demo/parse-error.html',
    () => error(502, 'parse_error', 'Не удалось прочитать объявление.'),
  ],
  [
    'https://auto.drom.ru/demo/source-unavailable.html',
    () => error(503, 'source_unavailable', 'Источник временно недоступен.'),
  ],
]);

export function handlers(config: AppConfig, storage: Storage, locks?: LockManager) {
  const store = createMockStore(storage, config, locks);
  const url = (path: string) => apiUrl(config.baseUrl, path);
  let failNextList = config.scenario === 'list-error';

  function sessionFor(request: Request) {
    const token = request.headers.get('Authorization')?.match(/^Bearer (drom-demo-[\w-]+)$/i)?.[1];
    const cars = token ? store.list(token) : null;
    return token && cars ? { token, cars } : null;
  }

  async function mutateCar(request: Request, id: string, apply: (car: Car) => Response) {
    const session = sessionFor(request);
    if (!session) return unauthorized();
    const result = await store.mutateCar(session.token, id, apply);
    return result === null ? unauthorized() : (result ?? missingCar());
  }

  return [
    http.post(url('/api/session'), () =>
      response(
        {
          token: store.createSession(),
          token_type: 'bearer',
        },
        201,
      ),
    ),
    http.get(url('/api/cars'), ({ request }) => {
      const session = sessionFor(request);
      if (!session) return unauthorized();
      if (failNextList) {
        failNextList = false;
        return error(500, 'mock_list_error', 'Не удалось загрузить выборку.');
      }
      return response(session.cars);
    }),
    http.post(url('/api/cars/import'), async ({ request }) => {
      if (!sessionFor(request)) return unauthorized();
      const raw = await request.json().catch(() => null);
      const value = raw && typeof raw === 'object' && 'url' in raw ? raw.url : null;
      const parsed = listingUrlSchema.safeParse(value);
      if (!parsed.success)
        return error(400, 'invalid_url', 'Ссылка должна вести на объявление Дрома.');

      const lookup = new URL(parsed.data).href;
      const failure = failures.get(lookup);
      if (failure) return failure();
      const listing = (listings as Listing[]).find((item) => item.listing_url === lookup);
      return listing ? response(listing) : unavailable();
    }),
    http.post(url('/api/cars'), async ({ request }) => {
      const session = sessionFor(request);
      if (!session) return unauthorized();
      const parsed = carInputSchema.safeParse(await request.json().catch(() => null));
      if (!parsed.success)
        return error(422, 'validation_error', 'Проверьте характеристики автомобиля.');
      const created = await store.add(session.token, parsed.data);
      return created ? response(created, 201) : unauthorized();
    }),
    http.get(url('/api/cars/:carId/summary'), ({ params, request }) => {
      const session = sessionFor(request);
      if (!session) return unauthorized();
      const car = session.cars.find(({ id }) => id === params.carId);
      if (!car) return missingCar();
      try {
        return response(fixtureSummary(car));
      } catch (reason) {
        if (!(reason instanceof RangeError)) throw reason;
        return error(422, 'amount_out_of_range', reason.message);
      }
    }),
    http.get(url('/api/cars/:carId'), ({ params, request }) => {
      const session = sessionFor(request);
      if (!session) return unauthorized();
      const car = session.cars.find(({ id }) => id === params.carId);
      return car ? response(car) : missingCar();
    }),
    ...vehicleWriteHandlers(url, mutateCar),
    http.delete(url('/api/cars/:carId'), async ({ params, request }) => {
      const session = sessionFor(request);
      if (!session) return unauthorized();
      const removed = await store.remove(session.token, String(params.carId));
      if (removed === null) return unauthorized();
      return removed ? new Response(null, { status: 204 }) : missingCar();
    }),
  ];
}
