import { zodResolver } from '@hookform/resolvers/zod';
import { useRef } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import type { z } from 'zod';
import { factorFormSchema } from '@/entities/vehicle/record-validation';
import { expenseLabels } from '@/entities/vehicle/records';
import type { Benefit, Expense } from '@/entities/vehicle/records';
import {
  Alert,
  Button,
  Checkbox,
  Inline,
  Input,
  Modal,
  Select,
  Stack,
  Textarea,
} from '@/shared/ui';
import styles from './RecordForms.module.css';

type Values = z.input<typeof factorFormSchema>;
export type FactorValue = z.output<typeof factorFormSchema>;

export function FactorDialog({
  group,
  item,
  saving,
  error,
  onClose,
  onSave,
}: {
  group: 'expenses' | 'benefits';
  item: Expense | Benefit | null;
  saving: boolean;
  error?: string;
  onClose: () => void;
  onSave: (value: FactorValue) => Promise<void>;
}) {
  const expense = group === 'expenses';
  const form = useForm<Values, unknown, FactorValue>({
    resolver: zodResolver(factorFormSchema),
    defaultValues: {
      kind: item && 'kind' in item ? item.kind : 'repair',
      title: item?.title ?? '',
      amount: String(item?.amount ?? ''),
      unknown: item?.amount === null,
      factor_key: item?.factor_key ?? '',
      comment: item?.comment ?? '',
    },
  });
  const locked = useRef(false);
  const unknown = useWatch({ control: form.control, name: 'unknown' });
  const busy = saving || form.formState.isSubmitting;
  const title = item
    ? expense
      ? 'Редактировать расход'
      : 'Редактировать преимущество'
    : expense
      ? 'Добавить расход'
      : 'Добавить преимущество';

  return (
    <Modal open title={title} onClose={onClose} closeDisabled={busy}>
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
        <fieldset className={styles.fields} disabled={busy} aria-label="Данные записи">
          <Input
            {...form.register('title')}
            label="Название"
            required
            error={form.formState.errors.title?.message}
          />
          {expense && (
            <Select {...form.register('kind')} label="Категория расхода">
              {Object.entries(expenseLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          )}
          <Stack gap="tight">
            {!unknown && (
              <Input
                {...form.register('amount')}
                label={expense ? 'Стоимость, ₽' : 'Персональная ценность, ₽'}
                inputMode="numeric"
                required
                error={form.formState.errors.amount?.message}
              />
            )}
            <Checkbox
              {...form.register('unknown')}
              label={expense ? 'Стоимость пока неизвестна' : 'Ценность пока неизвестна'}
            />
          </Stack>
          <Input
            {...form.register('factor_key')}
            label="Ключ оснащения (необязательно)"
            helperText="Для одинакового оснащения используйте один ключ, например winter_tires."
          />
          <Textarea {...form.register('comment')} label="Комментарий (необязательно)" rows={2} />
        </fieldset>
        {error && (
          <Alert tone="error" title="Не удалось сохранить запись" role="alert">
            {error}
          </Alert>
        )}
        <Inline gap="tight" className={styles.actions}>
          <Button type="submit" loading={busy}>
            {busy ? 'Сохраняем…' : 'Сохранить запись'}
          </Button>
          <Button type="button" variant="secondary" disabled={busy} onClick={onClose}>
            Отмена
          </Button>
        </Inline>
      </form>
    </Modal>
  );
}
