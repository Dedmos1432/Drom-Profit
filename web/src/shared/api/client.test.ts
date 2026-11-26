import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from './client';

describe('API client', () => {
  it('uses the base URL, bearer token and signal and handles 204 without parsing', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }));
    const signal = new AbortController().signal;
    const api = createApiClient({
      baseUrl: 'https://api.example.test/prefix/',
      getToken: () => 'fixture-token',
      fetcher,
    });

    await expect(
      api.request<void>('/api/cars/example', { method: 'DELETE', signal }),
    ).resolves.toBeUndefined();
    expect(fetcher).toHaveBeenCalledWith(
      'https://api.example.test/prefix/api/cars/example',
      expect.objectContaining({ method: 'DELETE', signal }),
    );
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer fixture-token',
    );
  });

  it('returns HTTP status and the contract code and message', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        Response.json(
          { code: 'listing_unavailable', message: 'Объявление недоступно.' },
          { status: 404 },
        ),
      );
    const api = createApiClient({ baseUrl: 'https://api.example.test', fetcher });
    await expect(
      api.request('/api/cars/import', {
        method: 'POST',
        body: { url: 'https://example.test/car' },
      }),
    ).rejects.toMatchObject({
      name: 'ApiError',
      kind: 'http',
      status: 404,
      code: 'listing_unavailable',
      message: 'Объявление недоступно.',
    });
    const options = fetcher.mock.calls[0]?.[1];
    expect(new Headers(options?.headers).get('Content-Type')).toBe('application/json');
    expect(options?.body).toBe('{"url":"https://example.test/car"}');
  });

  it('distinguishes malformed success JSON from an empty 204', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response('bad JSON', { status: 200 }));
    const api = createApiClient({ baseUrl: 'https://api.example.test', fetcher });
    await expect(api.request('/api/cars')).rejects.toMatchObject({
      kind: 'invalid_response',
      code: 'invalid_json',
    });
  });
});
