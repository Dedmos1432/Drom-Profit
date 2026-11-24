import type { Car, useCarSummary } from '@/entities/vehicle/api';
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
  const { current, updating, retry } = summary;
  const complete = current?.assessment_status === 'complete';
  const reasons = [
    reviewAvailable && !car.expenses_reviewed ? 'Расходы не подтверждены.' : null,
    reviewAvailable && !car.benefits_reviewed ? 'Преимущества не подтверждены.' : null,
    current?.unknown_expenses || current?.unknown_benefits ? 'Есть неизвестные суммы.' : null,
  ].filter(Boolean);

  return (
    <aside
      id="financial-summary"
      tabIndex={-1}
      aria-labelledby="financial-summary-title"
      className={styles.summary}
    >
      <Stack gap="tight">
        <h2 id="financial-summary-title">Экономическое саммари</h2>
        <div className={styles.status}>
          {updating ? (
            <Spinner size="small" label="Обновляем саммари…" />
          ) : current ? (
            <Badge tone={complete ? 'success' : 'warning'}>
              {complete ? 'Оценка полная' : 'Оценка предварительная'}
            </Badge>
          ) : null}
        </div>
        {!updating && !current && (
          <ErrorState
            title="Не удалось загрузить саммари"
            description="Повторите запрос, чтобы увидеть актуальные суммы."
            action={
              <Button variant="secondary" onClick={() => void retry()}>
                Повторить загрузку
              </Button>
            }
          />
        )}
        <dl className={styles.numbers} aria-busy={updating}>
          <div className={styles.line}>
            <dt>Цена объявления</dt>
            <dd>
              <Price size="compact" value={current?.listing_price ?? null} unknownLabel="—" />
            </dd>
          </div>
          <div className={styles.line}>
            <dt>Известные вложения</dt>
            <dd>
              <Price size="compact" value={current?.total_expenses ?? null} unknownLabel="—" />
            </dd>
          </div>
          <div className={styles.major}>
            <dt>Предполагаемые затраты на приобретение</dt>
            <dd>
              <Price size="display" value={current?.acquisition_cost ?? null} unknownLabel="—" />
            </dd>
          </div>
          <div className={styles.line}>
            <dt>Известная персональная ценность преимуществ</dt>
            <dd>
              <Price size="compact" value={current?.total_benefits ?? null} unknownLabel="—" />
            </dd>
          </div>
          <div className={styles.major}>
            <dt>Скорректированная стоимость</dt>
            <dd>
              <Price size="display" value={current?.adjusted_cost ?? null} unknownLabel="—" />
            </dd>
            <dd className={styles.hint}>Условный показатель для сравнения автомобилей.</dd>
          </div>
          {current && (
            <div className={styles.line}>
              <dt>Неизвестные суммы</dt>
              <dd className={styles.counts}>
                Расходы · {current.unknown_expenses}
                <br />
                Преимущества · {current.unknown_benefits}
              </dd>
            </div>
          )}
        </dl>
        {current && !complete && (
          <Alert tone="warning" title="Результат предварительный">
            Учтены только указанные суммы. {reasons.join(' ')}
          </Alert>
        )}
        {current && current.adjusted_cost < 0 && (
          <Alert tone="warning" title="Скорректированная стоимость ниже нуля">
            Проверьте персональную ценность указанных преимуществ.
          </Alert>
        )}
      </Stack>
    </aside>
  );
}
