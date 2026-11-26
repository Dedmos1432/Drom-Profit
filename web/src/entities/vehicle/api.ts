import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { sessionDataKey } from '@/entities/session/session-store';
import { useSession } from '@/entities/session/SessionBoundary';
import { ApiError } from '@/shared/api/client';
import {
  acquisitionCost,
  adjustedCost,
  carMoney,
  listingMoney,
  moneyAmount,
  positiveMoney,
} from '@/shared/api/money';
import type { createApiClient } from '@/shared/api/client';
import type { components, paths } from '@/shared/api/schema';
import { carWithoutGallery, photoWriteSchema, readGallery } from './photos';

export type Car = components['schemas']['Car'];
export type CarDetail = components['schemas']['CarDetail'];
export type CarInput = paths['/api/cars']['post']['requestBody']['content']['application/json'];
export type CarPatch =
  paths['/api/cars/{car_id}']['patch']['requestBody']['content']['application/json'];
export type Listing =
  paths['/api/cars/import']['post']['responses'][200]['content']['application/json'];
export type ImportInput =
  paths['/api/cars/import']['post']['requestBody']['content']['application/json'];
export type Summary = components['schemas']['Summary'];

const nonnegative = z.int().nonnegative();
export const summarySchema = z.object({
  car_id: z.string(),
  listing_price: positiveMoney,
  total_expenses: moneyAmount,
  total_benefits: moneyAmount,
  acquisition_cost: acquisitionCost,
  adjusted_cost: adjustedCost,
  assessment_status: z.enum(['complete', 'incomplete']),
  unknown_expenses: nonnegative,
  unknown_benefits: nonnegative,
}) satisfies z.ZodType<Summary>;

export type VehicleClient = ReturnType<typeof createApiClient>;
type Api = VehicleClient;
const carsKey = [...sessionDataKey, 'cars'] as const;
export const vehiclePath = (id: string) => `/api/cars/${encodeURIComponent(id)}` as const;

export const vehicleKeys = {
  list: [...carsKey, 'list'] as const,
  detail: (id: string) => [...carsKey, 'detail', id] as const,
  summary: (id: string) => [...carsKey, 'summary', id] as const,
};

export function checkedMoneyResponse<T>(schema: z.ZodType, payload: T): T {
  if (!schema.safeParse(payload).success) {
    throw new ApiError('invalid_response', 'invalid_money', 'Сервер вернул некорректную сумму.');
  }
  return payload;
}

const carsMoney = z.array(carMoney);

function checkPhotoWrite(body: CarInput | CarPatch) {
  const result = photoWriteSchema.safeParse(body);
  if (!result.success) {
    throw new ApiError('http', 'validation_error', result.error.issues[0]!.message, 422);
  }
}

export const vehicleApi = {
  list: async (api: Api) =>
    checkedMoneyResponse(carsMoney, await api.request<Car[]>('/api/cars', { money: true })).map(
      carWithoutGallery,
    ),
  detail: async (api: Api, id: string, signal: AbortSignal) => {
    const car = checkedMoneyResponse(
      carMoney,
      await api.request<CarDetail>(vehiclePath(id), { signal, money: true }),
    );
    return { ...car, ...readGallery(car) };
  },
  async summary(api: Api, id: string, signal: AbortSignal) {
    const payload = await api.request<unknown>(`${vehiclePath(id)}/summary`, {
      signal,
      money: true,
    });
    const result = summarySchema.safeParse(payload);
    if (!result.success || result.data.car_id !== id) {
      throw new ApiError(
        'invalid_response',
        'invalid_summary',
        'Получен некорректный результат оценки.',
      );
    }
    return result.data;
  },
  import: async (api: Api, body: ImportInput) => {
    const listing = checkedMoneyResponse(
      listingMoney,
      await api.request<Listing>('/api/cars/import', { method: 'POST', body, money: true }),
    );
    return { ...listing, ...readGallery(listing) };
  },
  create: async (api: Api, body: CarInput) => {
    checkPhotoWrite(body);
    return carWithoutGallery(
      checkedMoneyResponse(
        carMoney,
        await api.request<Car>('/api/cars', { method: 'POST', body, money: true }),
      ),
    );
  },
  patch: async (api: Api, id: string, body: CarPatch) => {
    checkPhotoWrite(body);
    return carWithoutGallery(
      checkedMoneyResponse(
        carMoney,
        await api.request<Car>(vehiclePath(id), { method: 'PATCH', body, money: true }),
      ),
    );
  },
  remove: (api: Api, id: string) => api.request<void>(vehiclePath(id), { method: 'DELETE' }),
};

export function useCars(refreshOnMount = false) {
  const { api } = useSession();
  return useQuery({
    queryKey: vehicleKeys.list,
    queryFn: () => vehicleApi.list(api),
    ...(refreshOnMount ? { refetchOnMount: 'always' as const } : {}),
  });
}

export function useCar(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: vehicleKeys.detail(id),
    queryFn: ({ signal }) => vehicleApi.detail(api, id, signal),
  });
}

export function useCarSummary(id: string) {
  const { api } = useSession();
  const query = useQuery({
    queryKey: vehicleKeys.summary(id),
    queryFn: ({ signal }) => vehicleApi.summary(api, id, signal),
  });
  const updating = query.isFetching;
  const issue = query.isPaused ? 'Ожидаем подключения к сети.' : summaryIssue(query.error);
  const current = updating || issue ? undefined : query.data;
  return { current, updating, issue, paused: query.isPaused, retry: query.refetch };
}

function summaryIssue(error: Error | null) {
  if (!error) return undefined;
  if (error instanceof ApiError) {
    if (error.kind === 'network') return 'Не удалось подключиться к серверу.';
    if (error.kind === 'invalid_response') return 'Сервер вернул некорректную оценку.';
    if (error.status === 404) return 'Автомобиль недоступен.';
    if (error.code === 'amount_out_of_range') return 'Суммы выходят за пределы расчёта.';
  }
  return 'Сервер не смог загрузить оценку.';
}
