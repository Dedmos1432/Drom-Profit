import { useVehicleMutation } from '@/entities/vehicle/mutations';
import { updateRecord } from '@/entities/vehicle/records';
import type { Collection, RecordChange } from '@/entities/vehicle/records';
import { ApiError } from '@/shared/api/client';
import { Button, Inline } from '@/shared/ui';

export function useRecordMutation<K extends Collection>(id: string, collection: K) {
  return useVehicleMutation(
    id,
    (api, carId, change: RecordChange<K>) => updateRecord(api, carId, collection, change),
    collection !== 'notes',
  );
}

export function recordError(error: unknown) {
  if (error instanceof ApiError && error.status === 404) {
    return 'Запись недоступна. Закройте форму и обновите страницу.';
  }
  if (error instanceof ApiError && error.status === 400) return error.message;
  return 'Введённые данные сохранены в форме. Повторите попытку.';
}

export function RecordActions({ onEdit, onDelete }: { onEdit: () => void; onDelete: () => void }) {
  return (
    <Inline gap="tight">
      <Button variant="ghost" onClick={onEdit}>
        Редактировать
      </Button>
      <Button variant="ghost" onClick={onDelete}>
        Удалить
      </Button>
    </Inline>
  );
}
