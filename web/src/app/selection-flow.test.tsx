import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
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
    await user.click(screen.getByRole('button', { name: 'Добавить вручную' }));
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

it('selects the available cars, allows a partial selection and clears it without comparing', async () => {
  const config = { mode: 'mock', scenario: 'showcase', baseUrl: 'https://selection.test' } as const;
  const server = setupServer(...handlers(config, localStorage));
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity } },
  });
  let comparisons = 0;
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/comparison') {
      comparisons++;
    }
  });
  server.listen({ onUnhandledRequest: 'error' });

  try {
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AppRoutes config={config} />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const selectAll = await screen.findByRole('button', { name: 'Выбрать все' });
    const checkboxes = screen.getAllByRole('checkbox');
    expect(checkboxes).toHaveLength(5);

    await user.click(selectAll);
    expect(screen.getByText('Выбрано 5 из 5')).toBeInTheDocument();
    expect(selectAll).toBeDisabled();
    checkboxes.forEach((checkbox) => expect(checkbox).toBeChecked());

    await user.click(checkboxes[0]!);
    expect(screen.getByText('Выбрано 4 из 5')).toBeInTheDocument();
    expect(selectAll).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Очистить выбор' }));
    expect(screen.getByText('Выбрано 0 из 5')).toBeInTheDocument();
    checkboxes.forEach((checkbox) => expect(checkbox).not.toBeChecked());
    expect(screen.getByRole('button', { name: 'Сравнить автомобили' })).toBeDisabled();
    expect(comparisons).toBe(0);
  } finally {
    queryClient.clear();
    server.close();
  }
});

it('treats malformed Summary responses as errors and reloads evaluations without replacing the selection', async () => {
  const config = { mode: 'mock', scenario: 'seeded', baseUrl: 'https://evaluation.test' } as const;
  const server = setupServer(...handlers(config, localStorage));
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity } },
  });
  let sessions = 0;
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/session') sessions++;
  });
  server.listen({ onUnhandledRequest: 'error' });
  server.use(http.get(config.baseUrl + '/api/cars/:id/summary', () => HttpResponse.json({})));

  try {
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AppRoutes config={config} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    const cards = await screen.findAllByRole('article');
    for (const card of cards) {
      await within(card).findByText('Сервер вернул некорректную оценку.');
      expect(within(card).getByRole('button', { name: 'Открыть карточку' })).toBeInTheDocument();
    }
    expect(cards).toHaveLength(3);
    server.resetHandlers();
    for (const card of cards) {
      await user.click(within(card).getByRole('button', { name: 'Повторить' }));
      await within(card).findByText('Все данные рассмотрены');
    }
    expect(sessions).toBe(1);
  } finally {
    queryClient.clear();
    server.close();
  }
});
