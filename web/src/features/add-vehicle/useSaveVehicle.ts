import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useSession } from '@/entities/session/SessionBoundary';
import { vehicleApi, vehicleKeys } from '@/entities/vehicle/api';
import type { Car, CarInput } from '@/entities/vehicle/api';

export function useSaveVehicle() {
  const { api } = useSession();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: CarInput) => vehicleApi.create(api, input),
    async onSuccess(car) {
      await queryClient.cancelQueries({ queryKey: vehicleKeys.list, exact: true });
      queryClient.setQueryData<Car[]>(vehicleKeys.list, (cars) =>
        cars ? [car, ...cars.filter(({ id }) => id !== car.id)] : undefined,
      );
      await queryClient.invalidateQueries({ queryKey: vehicleKeys.list, exact: true });
    },
  });
}
