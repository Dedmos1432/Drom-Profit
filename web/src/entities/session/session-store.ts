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
  const scopeKey = key + '.scope';
  const selectionKey = (scope: string) => key + '.comparison.' + scope;

  function clearScope() {
    const scope = storage.getItem(scopeKey);
    if (scope) storage.removeItem(selectionKey(scope));
    storage.removeItem(scopeKey);
  }

  async function load() {
    const saved = storage.getItem(key);
    const token = saved || (await issue());
    if (!saved) {
      clearScope();
      storage.setItem(key, token);
    }
    let scope = storage.getItem(scopeKey);
    if (!scope) {
      scope = crypto.getRandomValues(new Uint32Array(4)).join('-');
      storage.setItem(scopeKey, scope);
    }
    return { token, selectionKey: selectionKey(scope) };
  }

  async function loadExclusive() {
    return locks ? await locks.request(key, load) : await load();
  }

  let pending: ReturnType<typeof load> | undefined;
  return {
    load() {
      pending ??= loadExclusive().finally(() => {
        pending = undefined;
      });
      return pending;
    },
    clear() {
      storage.removeItem(key);
      clearScope();
    },
  };
}
