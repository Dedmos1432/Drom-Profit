import { z } from 'zod';

const apiMode = z.enum(['mock', 'real']);
const apiBaseUrl = z.url({ protocol: /^https?$/ });
const mockScenario = z.enum(['empty', 'seeded', 'showcase', 'list-error']);

export function readConfig() {
  const mode = apiMode.parse(
    import.meta.env.VITE_API_MODE || (import.meta.env.DEV ? 'mock' : 'real'),
  );
  const baseUrl = apiBaseUrl.parse(
    import.meta.env.VITE_API_BASE_URL?.trim() ||
      (mode === 'mock' ? window.location.origin : 'http://localhost:8000'),
  );
  const scenario =
    mode === 'mock' ? mockScenario.parse(import.meta.env.VITE_MOCK_SCENARIO || 'empty') : 'empty';
  return { mode, baseUrl, scenario };
}

export type AppConfig = ReturnType<typeof readConfig>;
