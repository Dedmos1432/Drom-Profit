import { describe, expect, it, vi } from 'vitest';
import { createSessionStore, sessionStorageKey } from './session-store';

const baseUrl = 'http://localhost:8000';
const config = { mode: 'mock', scenario: 'empty', baseUrl } as const;

describe('anonymous session', () => {
  it('coalesces concurrent creation and restores the saved token on initialization', async () => {
    const values = new Map<string, string>();
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => {
        values.set(key, value);
      },
      removeItem: (key: string) => {
        values.delete(key);
      },
    };
    const issue = vi.fn(async () => 'fixture-token');
    const key = sessionStorageKey(config);
    const store = createSessionStore(storage, key, issue);

    expect(await Promise.all([store.load(), store.load()])).toEqual([
      'fixture-token',
      'fixture-token',
    ]);
    expect(await createSessionStore(storage, key, issue).load()).toBe('fixture-token');
    expect(issue).toHaveBeenCalledOnce();
  });

  it('isolates mock, real and seeded sessions', () => {
    expect(
      new Set([
        sessionStorageKey(config),
        sessionStorageKey({ ...config, mode: 'real' }),
        sessionStorageKey({ ...config, scenario: 'seeded' }),
      ]).size,
    ).toBe(3);
  });
});
