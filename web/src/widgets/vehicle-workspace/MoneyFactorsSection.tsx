import { useState } from 'react';
import type { Car, Summary } from '@/entities/vehicle/api';
import { useCarPatch } from '@/entities/vehicle/mutations';
import { expenseLabels } from '@/entities/vehicle/records';
import type { Benefit, Expense } from '@/entities/vehicle/records';
import { FactorDialog } from '@/features/manage-vehicle-records/FactorDialog';
import type { FactorValue } from '@/features/manage-vehicle-records/FactorDialog';
import {
  RecordActions,
  recordError,
  useRecordMutation,
} from '@/features/manage-vehicle-records/record-actions';
import { changedFields } from '@/shared/lib/changed-fields';
import { Alert, Badge, Button, ConfirmDialog, Price, Stack } from '@/shared/ui';
import styles from './Workspace.module.css';

type Factor = Expense | Benefit;

export function MoneyFactorsSection({
  car,
  group,
  summary,
  summaryUpdating,
  reviewAvailable,
}: {
  car: Car;
  group: 'expenses' | 'benefits';
  summary?: Summary;
  summaryUpdating: boolean;
  reviewAvailable: boolean;
}) {
  const expense = group === 'expenses';
  const items = car[group];
  const label = expense ? 'Необходимые вложения' : 'Дополнительные преимущества';
  const addLabel = expense ? 'Добавить расход' : 'Добавить преимущество';
  const reviewKey = expense ? 'expenses_reviewed' : 'benefits_reviewed';
  const reviewed = car[reviewKey];
  const reviewLabel = expense ? 'Подтвердить расходы' : 'Подтвердить преимущества';
  const knownTotal = expense ? summary?.total_expenses : summary?.total_benefits;
  const unknown = expense ? summary?.unknown_expenses : summary?.unknown_benefits;
  const [editing, setEditing] = useState<{ item: Factor | null } | null>(null);
  const [removing, setRemoving] = useState<Factor | null>(null);
  const change = useRecordMutation(car.id, group);
  const review = useCarPatch(car.id);

  async function save({ kind, ...fields }: FactorValue) {
    const body = expense ? { ...fields, kind } : fields;
    try {
      if (editing?.item) {
        const patch = changedFields(editing.item, body);
        if (Object.keys(patch).length)
          await change.mutateAsync({ action: 'edit', itemId: editing.item.id, body: patch });
      } else {
        await change.mutateAsync({ action: 'create', body });
      }
      setEditing(null);
    } catch {
      // The open dialog keeps the entered values and shows the mutation error.
    }
  }

  function open(item: Factor | null) {
    change.reset();
    setEditing({ item });
  }

  return (
    <section id={group} aria-labelledby={group + '-title'} className={styles.section}>
      <Stack gap="tight">
        <div className={styles.sectionHeader}>
          <h2 id={group + '-title'}>{label}</h2>
          <Button variant="outline" onClick={() => open(null)}>
            {addLabel}
          </Button>
        </div>
        {!expense && (
          <p className={styles.muted}>
            Укажите, сколько оснащение значит лично для вас. Рыночная стоимость и сумма к оплате от
            этого не меняются. Учитывайте одно оснащение одним способом — как вложение или как
            преимущество.
          </p>
        )}
        {items.length === 0 ? (
          <p className={styles.muted}>
            {reviewAvailable && reviewed
              ? expense
                ? 'Необходимых расходов нет.'
                : 'Дополнительных преимуществ нет.'
              : expense
                ? 'Расходы пока не добавлены.'
                : 'Преимущества пока не добавлены.'}
          </p>
        ) : (
          <ul className={styles.rows}>
            {items.map((item) => (
              <li key={item.id} className={styles.row} data-monetary="">
                <div className={styles.rowBody}>
                  <strong>{item.title}</strong>
                  {'kind' in item && <p className={styles.muted}>{expenseLabels[item.kind]}</p>}
                  {item.comment && <p className={styles.entryText}>{item.comment}</p>}
                  {item.factor_key && (
                    <p className={styles.muted}>Ключ оснащения · {item.factor_key}</p>
                  )}
                </div>
                <Price
                  size="compact"
                  className={styles.amount}
                  value={item.amount}
                  unknownLabel={expense ? 'Стоимость неизвестна' : 'Ценность неизвестна'}
                />
                <div className={styles.rowActions}>
                  <RecordActions onEdit={() => open(item)} onDelete={() => setRemoving(item)} />
                </div>
              </li>
            ))}
          </ul>
        )}
        <div className={styles.review}>
          <Stack gap="tight">
            {knownTotal === undefined ? (
              <p className={styles.muted}>
                {summaryUpdating ? 'Обновляем итоги…' : 'Итог недоступен.'}
              </p>
            ) : (
              <div className={styles.reviewLine}>
                <span>{expense ? 'Сумма известных расходов' : 'Сумма известных преимуществ'}</span>
                <Price value={knownTotal} />
                <span className={styles.muted}>Неизвестных сумм · {unknown}</span>
              </div>
            )}
            <div className={styles.reviewLine}>
              {reviewAvailable ? (
                <Badge tone={reviewed ? 'success' : 'warning'}>
                  {expense
                    ? reviewed
                      ? 'Расходы подтверждены'
                      : 'Расходы не подтверждены'
                    : reviewed
                      ? 'Преимущества подтверждены'
                      : 'Преимущества не подтверждены'}
                </Badge>
              ) : (
                <p className={styles.muted}>Подтверждение уточняется…</p>
              )}
              <Button
                variant="secondary"
                loading={review.isPending}
                disabled={!reviewAvailable}
                onClick={() => review.mutate({ [reviewKey]: !reviewed })}
              >
                {reviewed
                  ? expense
                    ? 'Снять подтверждение расходов'
                    : 'Снять подтверждение преимуществ'
                  : items.length > 0
                    ? reviewLabel
                    : expense
                      ? 'Подтвердить отсутствие расходов'
                      : 'Подтвердить отсутствие преимуществ'}
              </Button>
            </div>
            {review.isError && (
              <Alert tone="error" title="Не удалось изменить подтверждение" role="alert">
                Повторите попытку.
              </Alert>
            )}
          </Stack>
        </div>
      </Stack>
      {editing && (
        <FactorDialog
          group={group}
          item={editing.item}
          saving={change.isPending}
          error={change.isError ? recordError(change.error) : undefined}
          onClose={() => setEditing(null)}
          onSave={save}
        />
      )}
      {removing && (
        <ConfirmDialog
          open
          title={expense ? 'Удалить расход?' : 'Удалить преимущество?'}
          description={removing.title}
          confirmLabel="Удалить"
          onClose={() => setRemoving(null)}
          onConfirm={async () => {
            await change.mutateAsync({ action: 'delete', itemId: removing.id });
          }}
        />
      )}
    </section>
  );
}
