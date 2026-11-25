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
  reviewAvailable,
}: {
  car: Car;
  group: 'expenses' | 'benefits';
  summary?: Summary;
  reviewAvailable: boolean;
}) {
  const expense = group === 'expenses';
  const items = car[group];
  const label = expense ? 'Необходимые вложения' : 'Дополнительные преимущества';
  const addLabel = expense ? 'Добавить расход' : 'Добавить преимущество';
  const reviewKey = expense ? 'expenses_reviewed' : 'benefits_reviewed';
  const reviewed = car[reviewKey];
  const reviewLabel = expense ? 'Все расходы учтены' : 'Все преимущества учтены';
  const knownTotal = expense ? summary?.total_expenses : summary?.total_benefits;
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
    <section id={group} tabIndex={-1} aria-labelledby={group + '-title'} className={styles.section}>
      <Stack gap="tight">
        <div className={styles.sectionHeader}>
          <h2 id={group + '-title'}>{label}</h2>
          <Button variant="outline" onClick={() => open(null)}>
            {addLabel}
          </Button>
        </div>
        <p className={styles.muted}>
          {expense
            ? 'Запишите, во сколько обойдутся ремонт, обслуживание и недостающее оснащение.'
            : 'Укажите, сколько дополнительное оснащение стоит лично для вас. Учитывайте его один раз — как вложение или преимущество.'}
        </p>
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
                </div>
                <Price
                  size="compact"
                  className={styles.amount}
                  value={item.amount}
                  wrapGroups
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
            {knownTotal !== undefined && (
              <div className={styles.reviewLine}>
                <span>{expense ? 'Учтённые вложения' : 'Учтённая ценность преимуществ'}</span>
                <Price value={knownTotal} wrapGroups />
              </div>
            )}
            {reviewAvailable && !reviewed && (
              <p className={styles.muted}>
                {items.length
                  ? 'Отметьте, что рассмотрели весь список. Неизвестные суммы можно уточнить позже.'
                  : 'Если добавлять нечего, отметьте это для оценки.'}
              </p>
            )}
            <div className={styles.reviewLine}>
              {reviewAvailable ? (
                reviewed && (
                  <Badge tone="success">
                    {expense ? 'Расходы рассмотрены' : 'Преимущества рассмотрены'}
                  </Badge>
                )
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
                    ? 'Пересмотреть расходы'
                    : 'Пересмотреть преимущества'
                  : items.length > 0
                    ? reviewLabel
                    : expense
                      ? 'Расходов нет'
                      : 'Преимуществ нет'}
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
