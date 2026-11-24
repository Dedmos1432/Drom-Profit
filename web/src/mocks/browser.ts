import { setupWorker } from 'msw/browser';
import { apiUrl } from '@/shared/api/client';
import type { AppConfig } from '@/shared/config/env';
import { handlers } from './handlers';

export function startMockApi(config: AppConfig) {
  const worker = setupWorker(...handlers(config, window.localStorage, navigator.locks));
  const apiPrefix = apiUrl(config.baseUrl, '/api/');

  return worker.start({
    quiet: true,
    serviceWorker: { url: import.meta.env.BASE_URL + 'mockServiceWorker.js' },
    onUnhandledRequest(request, print) {
      if (request.url.startsWith(apiPrefix)) print.error();
    },
  });
}
