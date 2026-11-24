import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@/app/App';
import { readConfig } from '@/shared/config/env';
import { Alert, Container } from '@/shared/ui';
import '@/shared/styles/globals.css';

const element = document.getElementById('root');
if (!element) throw new Error('Missing application root');
const root = createRoot(element);

async function start() {
  const config = readConfig();
  if (config.mode === 'mock') {
    const { startMockApi } = await import('@/mocks/browser');
    await startMockApi(config.baseUrl);
  }

  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

void start().catch(() => {
  root.render(
    <Container>
      <Alert tone="error" title="Не удалось запустить приложение" role="alert">
        Проверьте переменные API и доступность Service Worker в режиме mock.
      </Alert>
    </Container>,
  );
});
