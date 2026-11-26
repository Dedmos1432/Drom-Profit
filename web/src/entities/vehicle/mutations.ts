import { useMutation, useQueryClient } from '@tanstack/react-query';
import { comparisonsWithVehicle } from '@/entities/comparison/keys';
import { useSession } from '@/entities/session/SessionBoundary';
import { vehicleApi, vehicleKeys } from './api';
import type { VehicleClient } from './api';

export function useVehicleMutation<Input, Result>(
  id: string,
  update: (api: VehicleClient, id: string, input: Input) => Promise<Result>,
  affectsSummary = true,
) {
  const { api } = useSession();
  const queryClient = useQueryClient();

  return useMutation({
    scope: { id: 'vehicle:' + id },
    mutationFn: (input: Input) => update(api, id, input),
    async onSuccess() {
      const detail = { queryKey: vehicleKeys.detail(id), exact: true };
      const filters = affectsSummary
        ? [
            detail,
            { queryKey: vehicleKeys.summary(id), exact: true },
            { queryKey: vehicleKeys.list, exact: true },
            comparisonsWithVehicle(id),
          ]
        : [detail];
      await Promise.all(filters.map((filter) => queryClient.cancelQueries(filter)));
      await Promise.all(filters.map((filter) => queryClient.invalidateQueries(filter)));
    },
  });
}

export function useCarPatch(id: string) {
  return useVehicleMutation(id, vehicleApi.patch);
}
