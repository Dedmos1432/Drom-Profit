import { zodResolver } from '@hookform/resolvers/zod';
import { useRef } from 'react';
import { useForm } from 'react-hook-form';
import { noteInputSchema } from '@/entities/vehicle/record-validation';
import { noteLabels } from '@/entities/vehicle/records';
import type { Note, NoteInput } from '@/entities/vehicle/records';
import { Alert, Button, Inline, Modal, Select, Textarea } from '@/shared/ui';
import styles from './RecordForms.module.css';

export function NoteDialog({
  item,
  saving,
  error,
  onClose,
  onSave,
}: {
  item: Note | null;
  saving: boolean;
  error?: string;
  onClose: () => void;
  onSave: (value: NoteInput) => Promise<void>;
}) {
  const form = useForm<NoteInput>({
    resolver: zodResolver(noteInputSchema),
    defaultValues: { kind: item?.kind ?? 'inspection', text: item?.text ?? '' },
  });
  const locked = useRef(false);
  const busy = saving || form.formState.isSubmitting;

  return (
    <Modal
      open
      title={item ? 'Редактировать заметку' : 'Добавить заметку'}
      onClose={onClose}
      closeDisabled={busy}
    >
      <form
        noValidate
        onSubmit={form.handleSubmit(async (value) => {
          if (locked.current) return;
          locked.current = true;
          try {
            await onSave(value);
          } finally {
            locked.current = false;
          }
        })}
      >
        <fieldset className={styles.fields} disabled={busy} aria-label="Данные заметки">
          <Select {...form.register('kind')} label="Вид заметки">
            {Object.entries(noteLabels).map(([value, text]) => (
              <option key={value} value={value}>
                {text}
              </option>
            ))}
          </Select>
          <Textarea
            {...form.register('text')}
            label="Текст заметки"
            required
            error={form.formState.errors.text?.message}
          />
        </fieldset>
        {error && (
          <Alert tone="error" title="Не удалось сохранить заметку" role="alert">
            {error}
          </Alert>
        )}
        <Inline className={styles.actions} gap="tight">
          <Button type="submit" loading={busy}>
            {busy ? 'Сохраняем…' : 'Сохранить заметку'}
          </Button>
          <Button type="button" variant="secondary" disabled={busy} onClick={onClose}>
            Отмена
          </Button>
        </Inline>
      </form>
    </Modal>
  );
}
