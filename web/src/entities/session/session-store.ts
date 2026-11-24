import type { AppConfig } from '@/shared/config/env';

export const sessionDataKey = ['session-data'] as const;

export function sessionStorageKey({ mode, baseUrl, scenario }: AppConfig) {
  return [
    'drom-profit.session.v1',
    mode,
    scenario,
    encodeURIComponent(baseUrl.replace(/\/+$/, '')),
  ].join('.');
}

export function createSessionStore(
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>,
  key: string,
  issue: () => Promise<string>,
  locks?: LockManager,
) {
  let pending: Promise<string> | undefined;

  async function restoreOrCreate() {
    const saved = storage.getItem(key);
    if (saved) return saved;

    const token = await issue();
    storage.setItem(key, token);
    return token;
  }

  async function loadExclusive() {
    return locks ? await locks.request(key, restoreOrCreate) : await restoreOrCreate();
  }

  return {
    load() {
      pending ??= loadExclusive().finally(() => {
        pending = undefined;
      });
      return pending;
    },
    clear() {
      storage.removeItem(key);
    },
  };
}
