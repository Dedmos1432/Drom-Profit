import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useContext, useMemo, useRef, useState } from 'react';
import { Outlet } from 'react-router';
import { z } from 'zod';
import { createApiClient } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';
import type { AppConfig } from '@/shared/config/env';
import { Button, ErrorState, Spinner, Stack } from '@/shared/ui';
import { createSessionStore, sessionDataKey, sessionStorageKey } from './session-store';

const sessionSchema = z.object({
  token: z.string().trim().min(1),
  token_type: z.literal('bearer'),
}) satisfies z.ZodType<components['schemas']['Session']>;

const SessionContext = createContext<{
  api: ReturnType<typeof createApiClient>;
  config: AppConfig;
} | null>(null);

export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error('SessionBoundary is missing');
  return value;
}

export function SessionBoundary({ config }: { config: AppConfig }) {
  const { baseUrl } = config;
  const storageKey = sessionStorageKey(config);
  const queryKey = ['session', storageKey] as const;
  const queryClient = useQueryClient();
  const resetting = useRef(false);
  const [resetFailed, setResetFailed] = useState(false);
  const [invalidTokens, setInvalidTokens] = useState<ReadonlySet<string>>(new Set());
  const store = useMemo(() => {
    const publicApi = createApiClient({ baseUrl });
    return createSessionStore(
      window.localStorage,
      storageKey,
      async () => {
        const response = await publicApi.request<unknown>('/api/session', { method: 'POST' });
        return sessionSchema.parse(response).token;
      },
      navigator.locks,
    );
  }, [baseUrl, storageKey]);
  const session = useQuery({ queryKey, queryFn: store.load, staleTime: Infinity, retry: false });
  const token = session.data;
  const api = useMemo(
    () =>
      createApiClient({
        baseUrl,
        getToken: () => token ?? null,
        onUnauthorized: () => {
          if (token)
            setInvalidTokens((previous) =>
              previous.has(token) ? previous : new Set([...previous, token]),
            );
        },
      }),
    [baseUrl, token],
  );

  async function startAgain() {
    if (resetting.current) return;
    resetting.current = true;
    setResetFailed(false);
    try {
      store.clear();
      await queryClient.cancelQueries({ queryKey: sessionDataKey });
      queryClient.removeQueries({ queryKey: sessionDataKey });
      await queryClient.resetQueries({ queryKey, exact: true });
    } catch {
      setResetFailed(true);
    } finally {
      resetting.current = false;
    }
  }

  if (session.isPending) return <Spinner label="Восстанавливаем анонимную сессию…" />;

  if (session.isError)
    return (
      <ErrorState
        title="Не удалось начать анонимную сессию"
        description="Проверьте соединение и доступ к локальному хранилищу браузера."
        action={
          <Button
            onClick={() => void session.refetch({ cancelRefetch: false })}
            loading={session.isFetching}
          >
            Повторить
          </Button>
        }
      />
    );

  if (token && invalidTokens.has(token))
    return (
      <Stack>
        <ErrorState
          title="Сессия больше не доступна"
          description="Сервер не принял сохранённую сессию. Новая сессия создаст отдельную выборку."
          action={<Button onClick={() => void startAgain()}>Начать новую сессию</Button>}
        />
        {resetFailed && (
          <p role="alert">
            Не удалось очистить сохранённую сессию. Проверьте доступ браузера к хранилищу.
          </p>
        )}
      </Stack>
    );

  return (
    <SessionContext.Provider value={{ api, config }}>
      <Outlet />
    </SessionContext.Provider>
  );
}
