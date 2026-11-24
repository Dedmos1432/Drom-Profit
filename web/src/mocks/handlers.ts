import { http, HttpResponse } from 'msw';
import demoCars from '../../../shared/fixtures/demo-cars.json';
import { apiUrl } from '@/shared/api/client';
import type { components, paths } from '@/shared/api/schema';

type CarsResponse = paths['/api/cars']['get']['responses'][200]['content']['application/json'];
type Session = components['schemas']['Session'];
type ContractError = components['schemas']['Error'];

const demoTokenPrefix = 'drom-demo-';

export function handlers(baseUrl: string) {
  return [
    http.post(apiUrl(baseUrl, '/api/session'), () =>
      HttpResponse.json<Session>(
        { token: demoTokenPrefix + crypto.randomUUID(), token_type: 'bearer' },
        { status: 201 },
      ),
    ),
    http.get(apiUrl(baseUrl, '/api/cars'), ({ request }) => {
      if (!request.headers.get('Authorization')?.startsWith('Bearer ' + demoTokenPrefix)) {
        return HttpResponse.json<CarsResponse | ContractError>(
          { code: 'unauthorized', message: 'Откройте демонстрационную сессию.' },
          { status: 401 },
        );
      }
      return HttpResponse.json<CarsResponse | ContractError>(demoCars as CarsResponse);
    }),
  ];
}
