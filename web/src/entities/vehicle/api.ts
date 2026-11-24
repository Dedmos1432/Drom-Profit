import { useQuery } from '@tanstack/react-query';
import { sessionDataKey } from '@/entities/session/session-store';
import { useSession } from '@/entities/session/SessionBoundary';
import type { createApiClient } from '@/shared/api/client';
import type { components, paths } from '@/shared/api/schema';

export type Car = components['schemas']['Car'];
export type CarInput = paths['/api/cars']['post']['requestBody']['content']['application/json'];
export type CarPatch =
  paths['/api/cars/{car_id}']['patch']['requestBody']['content']['application/json'];
export type Listing =
  paths['/api/cars/import']['post']['responses'][200]['content']['application/json'];
export type ImportInput =
  paths['/api/cars/import']['post']['requestBody']['content']['application/json'];
export type Summary = components['schemas']['Summary'];

export type VehicleClient = ReturnType<typeof createApiClient>;
type Api = VehicleClient;
const carsKey = [...sessionDataKey, 'cars'] as const;
export const vehiclePath = (id: string) => `/api/cars/${encodeURIComponent(id)}` as const;

export const vehicleKeys = {
  list: [...carsKey, 'list'] as const,
  detail: (id: string) => [...carsKey, 'detail', id] as const,
  summary: (id: string) => [...carsKey, 'summary', id] as const,
};

export const vehicleApi = {
  list: (api: Api) => api.request<Car[]>('/api/cars'),
  detail: (api: Api, id: string, signal: AbortSignal) =>
    api.request<Car>(vehiclePath(id), { signal }),
  summary: (api: Api, id: string, signal: AbortSignal) =>
    api.request<Summary>(`${vehiclePath(id)}/summary`, { signal }),
  import: (api: Api, body: ImportInput) =>
    api.request<Listing>('/api/cars/import', { method: 'POST', body }),
  create: (api: Api, body: CarInput) => api.request<Car>('/api/cars', { method: 'POST', body }),
  patch: (api: Api, id: string, body: CarPatch) =>
    api.request<Car>(vehiclePath(id), { method: 'PATCH', body }),
  remove: (api: Api, id: string) => api.request<void>(vehiclePath(id), { method: 'DELETE' }),
};

export function useCars() {
  const { api } = useSession();
  return useQuery({ queryKey: vehicleKeys.list, queryFn: () => vehicleApi.list(api) });
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
  const updating = query.isPending || query.isFetching;
  const current = updating || query.isError || query.isPaused ? undefined : query.data;
  return { current, updating, retry: query.refetch };
}
