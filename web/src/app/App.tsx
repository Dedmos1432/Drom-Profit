import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { BrowserRouter } from 'react-router';
import type { AppConfig } from '@/shared/config/env';
import { AppRoutes } from './router';

export function App({ config }: { config: AppConfig }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter basename={import.meta.env.BASE_URL}>
        <AppRoutes config={config} />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
