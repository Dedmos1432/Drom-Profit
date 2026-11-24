import { setupWorker } from 'msw/browser';
import { apiUrl } from '@/shared/api/client';
import { handlers } from './handlers';

export function startMockApi(baseUrl: string) {
  const worker = setupWorker(...handlers(baseUrl));
  const apiPrefix = apiUrl(baseUrl, '/api/');

  return worker.start({
    serviceWorker: { url: import.meta.env.BASE_URL + 'mockServiceWorker.js' },
    onUnhandledRequest(request, print) {
      if (request.url.startsWith(apiPrefix)) print.error();
    },
  });
}
