import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { comparisonsWithVehicle } from '@/entities/comparison/keys';
import { useSession } from '@/entities/session/SessionBoundary';
import { vehicleApi, vehicleKeys } from '@/entities/vehicle/api';
import type { Car } from '@/entities/vehicle/api';
import { vehicleName } from '@/entities/vehicle/presentation';
import { Button, ConfirmDialog } from '@/shared/ui';
import styles from './DeleteVehicleButton.module.css';

export function DeleteVehicleButton({
  car,
  onRemoved,
}: {
  car: Car;
  onRemoved: (id: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const { api } = useSession();
  const queryClient = useQueryClient();
  const removal = useMutation({
    mutationFn: () => vehicleApi.remove(api, car.id),
    async onSuccess() {
      await queryClient.cancelQueries({ queryKey: vehicleKeys.list, exact: true });
      queryClient.setQueryData<Car[]>(vehicleKeys.list, (cars) =>
        cars?.filter(({ id }) => id !== car.id),
      );
      queryClient.removeQueries({ queryKey: vehicleKeys.detail(car.id) });
      queryClient.removeQueries({ queryKey: vehicleKeys.summary(car.id) });
      onRemoved(car.id);
      queryClient.removeQueries(comparisonsWithVehicle(car.id));
      await queryClient.invalidateQueries({ queryKey: vehicleKeys.list, exact: true });
    },
  });

  return (
    <>
      <Button
        variant="danger"
        className={styles.trigger}
        disabled={removal.isPending}
        onClick={() => setOpen(true)}
      >
        Удалить
      </Button>
      <ConfirmDialog
        open={open}
        onClose={() => setOpen(false)}
        onConfirm={async () => {
          await removal.mutateAsync();
        }}
        title="Удалить автомобиль из выборки?"
        description={vehicleName(car)}
        confirmLabel="Удалить"
      />
    </>
  );
}
