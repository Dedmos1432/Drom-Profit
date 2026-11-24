import { useMutation, useQueryClient } from '@tanstack/react-query';
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
      const keys = affectsSummary
        ? [vehicleKeys.detail(id), vehicleKeys.summary(id), vehicleKeys.list]
        : [vehicleKeys.detail(id)];
      await Promise.all(
        keys.map((queryKey) => queryClient.cancelQueries({ queryKey, exact: true })),
      );
      await Promise.all(
        keys.map((queryKey) => queryClient.invalidateQueries({ queryKey, exact: true })),
      );
    },
  });
}

export function useCarPatch(id: string) {
  return useVehicleMutation(id, vehicleApi.patch);
}
