import type { Car, useCarSummary } from '@/entities/vehicle/api';
import { assessmentLabel } from '@/entities/vehicle/presentation';
import { Alert, Badge, Button, ErrorState, Price, Spinner, Stack } from '@/shared/ui';
import styles from './FinancialSummary.module.css';

type SummaryView = ReturnType<typeof useCarSummary>;

export function FinancialSummary({
  car,
  summary,
  reviewAvailable,
}: {
  car: Car;
  summary: SummaryView;
  reviewAvailable: boolean;
}) {
  const { current, updating, issue, paused, retry } = summary;
  const complete = current?.assessment_status === 'complete';
  const reasons = [
    reviewAvailable && !car.expenses_reviewed ? 'Отметьте, что рассмотрели расходы.' : null,
    reviewAvailable && !car.benefits_reviewed ? 'Отметьте, что рассмотрели преимущества.' : null,
  ].filter(Boolean);

  return (
    <aside
      id="financial-summary"
      tabIndex={-1}
      aria-labelledby="financial-summary-title"
      className={styles.summary}
    >
      <Stack gap="tight">
        <h2 id="financial-summary-title">Экономическая оценка</h2>
        <div className={styles.status}>
          {updating ? (
            <Spinner size="small" label="Обновляем оценку…" />
          ) : current ? (
            <Badge tone={complete ? 'success' : 'warning'}>
              {assessmentLabel[current.assessment_status]}
            </Badge>
          ) : null}
        </div>
        {!updating && issue && (
          <ErrorState
            title={paused ? 'Загрузка оценки приостановлена' : 'Не удалось загрузить оценку'}
            description={issue}
            action={
              !paused && (
                <Button variant="secondary" onClick={() => void retry({ cancelRefetch: false })}>
                  Повторить загрузку
                </Button>
              )
            }
          />
        )}
        {current && !complete && (
          <p className={styles.hint}>Учтены только известные суммы. {reasons.join(' ')}</p>
        )}
        <dl className={styles.numbers} aria-busy={updating}>
          <div className={styles.line}>
            <dt>Цена объявления</dt>
            <dd>
              <Price
                size="compact"
                value={current?.listing_price ?? null}
                unknownLabel="—"
                wrapGroups
              />
            </dd>
          </div>
          <div className={styles.line}>
            <dt>Учтённые вложения</dt>
            <dd>
              <Price
                size="compact"
                value={current?.total_expenses ?? null}
                unknownLabel="—"
                wrapGroups
              />
            </dd>
          </div>
          <div className={styles.major}>
            <dt>Предполагаемые затраты на приобретение</dt>
            <dd>
              <Price
                size="display"
                value={current?.acquisition_cost ?? null}
                unknownLabel="—"
                wrapGroups
              />
            </dd>
          </div>
          <div className={styles.line}>
            <dt>Ценность преимуществ</dt>
            <dd>
              <Price
                size="compact"
                value={current?.total_benefits ?? null}
                unknownLabel="—"
                wrapGroups
              />
            </dd>
          </div>
          <div className={styles.major}>
            <dt>Скорректированная стоимость</dt>
            <dd>
              <Price
                size="display"
                value={current?.adjusted_cost ?? null}
                unknownLabel="—"
                wrapGroups
              />
            </dd>
            <dd className={styles.hint}>Учитывает вашу оценку преимуществ при сравнении.</dd>
          </div>
          {current && (current.unknown_expenses > 0 || current.unknown_benefits > 0) && (
            <div className={styles.line}>
              <dt>Неизвестные суммы</dt>
              <dd className={styles.counts}>
                {current.unknown_expenses > 0 && <div>Расходы · {current.unknown_expenses}</div>}
                {current.unknown_benefits > 0 && (
                  <div>Преимущества · {current.unknown_benefits}</div>
                )}
              </dd>
            </div>
          )}
        </dl>
        {current && current.adjusted_cost < 0 && (
          <Alert tone="warning" title="Скорректированная стоимость ниже нуля">
            Проверьте персональную ценность указанных преимуществ.
          </Alert>
        )}
      </Stack>
    </aside>
  );
}
