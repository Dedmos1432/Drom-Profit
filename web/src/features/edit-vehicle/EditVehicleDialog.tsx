import type { Car, CarPatch } from '@/entities/vehicle/api';
import { useCarPatch } from '@/entities/vehicle/mutations';
import { VehicleForm } from '@/features/add-vehicle/VehicleForm';
import { Modal } from '@/shared/ui';

export function EditVehicleDialog({ car, onClose }: { car: Car; onClose: () => void }) {
  const update = useCarPatch(car.id);

  async function save(patch: CarPatch) {
    try {
      if (Object.keys(patch).length) await update.mutateAsync(patch);
      onClose();
    } catch {
      // The same form stays mounted after a failed request.
    }
  }

  return (
    <Modal
      open
      title="Редактировать характеристики"
      onClose={onClose}
      closeDisabled={update.isPending}
    >
      <VehicleForm
        car={car}
        saving={update.isPending}
        failed={update.isError}
        onSave={save}
        onCancel={onClose}
      />
    </Modal>
  );
}
