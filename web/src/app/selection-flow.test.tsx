import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { setupServer } from 'msw/node';
import { StrictMode } from 'react';
import { MemoryRouter } from 'react-router';
import { expect, it } from 'vitest';
import { handlers } from '@/mocks/handlers';
import { AppRoutes } from './router';

it('creates one session in StrictMode, retries a failed list and updates the selection through the form', async () => {
  const config = {
    mode: 'mock',
    scenario: 'list-error',
    baseUrl: 'https://selection.test',
  } as const;
  const server = setupServer(...handlers(config, localStorage));
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity } },
  });
  let sessions = 0;
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/session') sessions++;
  });
  server.listen({ onUnhandledRequest: 'error' });

  try {
    const user = userEvent.setup();
    render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter>
            <AppRoutes config={config} />
          </MemoryRouter>
        </QueryClientProvider>
      </StrictMode>,
    );
    await screen.findByText('Не удалось загрузить выборку');
    await user.click(screen.getByRole('button', { name: 'Повторить' }));
    await screen.findByText('Пока нет автомобилей');
    await user.click(screen.getByRole('link', { name: 'Добавить вручную' }));
    await user.type(screen.getByRole('textbox', { name: 'Марка' }), 'Honda');
    await user.type(screen.getByRole('textbox', { name: 'Модель' }), 'Accord');
    await user.type(screen.getByRole('textbox', { name: 'Цена, ₽' }), '940000');
    await user.click(screen.getByRole('button', { name: 'Сохранить автомобиль' }));

    expect(await screen.findByRole('link', { name: 'Honda Accord' })).toBeInTheDocument();
    expect(sessions).toBe(1);
  } finally {
    queryClient.clear();
    server.close();
  }
});
