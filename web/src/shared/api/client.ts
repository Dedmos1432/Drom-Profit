import type { components } from './schema';

type ContractError = components['schemas']['Error'];
type ErrorKind = 'http' | 'network' | 'invalid_response';
type ApiPath = `/api/${string}`;
type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
};

export class ApiError extends Error {
  constructor(
    public readonly kind: ErrorKind,
    public readonly code: string,
    message: string,
    public readonly status?: number,
    cause?: unknown,
  ) {
    super(message, { cause });
    this.name = 'ApiError';
  }
}

function isContractError(value: unknown): value is ContractError {
  return (
    value !== null &&
    typeof value === 'object' &&
    'code' in value &&
    typeof value.code === 'string' &&
    'message' in value &&
    typeof value.message === 'string'
  );
}

type ApiClientOptions = {
  baseUrl: string;
  getToken?: () => string | null;
  onUnauthorized?: () => void;
  fetcher?: typeof fetch;
};

export function apiUrl(baseUrl: string, path: string) {
  return baseUrl.replace(/\/+$/, '') + path;
}

export function createApiClient({
  baseUrl,
  getToken,
  onUnauthorized,
  fetcher = fetch,
}: ApiClientOptions) {
  async function request<Result>(
    path: ApiPath,
    { method = 'GET', body, signal }: RequestOptions = {},
  ): Promise<Result> {
    const headers = new Headers({ Accept: 'application/json' });
    const token = getToken?.();
    if (token) headers.set('Authorization', 'Bearer ' + token);
    if (body !== undefined) headers.set('Content-Type', 'application/json');

    let response: Response;
    try {
      response = await fetcher(apiUrl(baseUrl, path), {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal,
      });
    } catch (cause) {
      if (signal?.aborted) throw cause;
      throw new ApiError(
        'network',
        'network_error',
        'Не удалось связаться с сервером.',
        undefined,
        cause,
      );
    }

    if (response.status === 204) return undefined as Result;

    let payload: unknown;
    try {
      payload = await response.json();
    } catch (cause) {
      if (signal?.aborted) throw cause;
      if (response.ok) {
        throw new ApiError(
          'invalid_response',
          'invalid_json',
          'Сервер вернул некорректный ответ.',
          response.status,
          cause,
        );
      }
    }

    if (!response.ok) {
      if (response.status === 401 && token) onUnauthorized?.();
      const code = isContractError(payload) ? payload.code : 'http_' + response.status;
      const message = isContractError(payload)
        ? payload.message
        : 'Сервер не смог выполнить запрос.';
      throw new ApiError('http', code, message, response.status);
    }

    return payload as Result;
  }

  return { request };
}
