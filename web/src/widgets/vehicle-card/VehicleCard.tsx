import { Link as RouterLink, useNavigate } from 'react-router';
import { useCarSummary } from '@/entities/vehicle/api';
import type { Car } from '@/entities/vehicle/api';
import {
  assessmentLabel,
  engine,
  mileage,
  originalListing,
  sourceLabel,
  vehicleName,
} from '@/entities/vehicle/presentation';
import { VehiclePhoto } from '@/entities/vehicle/VehiclePhoto';
import { DeleteVehicleButton } from '@/features/delete-vehicle/DeleteVehicleButton';
import { Badge, Button, Checkbox, Inline, Link, Price } from '@/shared/ui';
import styles from './VehicleCard.module.css';

type Props = {
  car: Car;
  selected: boolean;
  onSelection: (id: string, checked: boolean) => void;
  onRemoved: (id: string) => void;
};

export function VehicleCard({ car, selected, onSelection, onRemoved }: Props) {
  const navigate = useNavigate();
  const summary = useCarSummary(car.id);
  const name = vehicleName(car);
  const facts = [engine(car), car.transmission, car.drive_type, mileage(car)]
    .filter(Boolean)
    .join(' · ');
  const original = originalListing(car);
  const complete = summary.current?.assessment_status === 'complete';
  const detailPath = '/cars/' + encodeURIComponent(car.id);
  const action = summary.current
    ? complete
      ? 'Уточнить данные'
      : 'Заполнить данные'
    : 'Открыть карточку';

  return (
    <article className={styles.card} aria-label={name}>
      <VehiclePhoto value={car.image_url} className={styles.photo} />
      <div className={styles.description}>
        <h3>
          <RouterLink to={detailPath}>{name}</RouterLink>
        </h3>
        {car.trim && <p>{car.trim}</p>}
        {facts && <p>{facts}</p>}
        {car.city && <p className={styles.muted}>{car.city}</p>}
        <Badge tone={car.source === 'demo' ? 'info' : 'neutral'}>{sourceLabel(car)}</Badge>
      </div>
      <div className={styles.money}>
        <div>
          <p className={styles.muted}>Цена объявления</p>
          <Price size="display" value={car.price} wrapGroups />
        </div>
        {car.market_valuation !== null && (
          <p className={styles.metric}>
            Рыночная оценка Дрома <Price size="compact" value={car.market_valuation} wrapGroups />
          </p>
        )}
        {summary.current ? (
          <>
            <Badge tone={complete ? 'success' : 'warning'}>
              {assessmentLabel[summary.current.assessment_status]}
            </Badge>
            <dl className={styles.metrics}>
              <div className={styles.calculated}>
                <dt>Затраты на приобретение</dt>
                <dd>
                  <Price size="compact" value={summary.current.acquisition_cost} wrapGroups />
                </dd>
              </div>
              <div className={styles.calculated}>
                <dt>Скорректированная стоимость</dt>
                <dd>
                  <Price size="compact" value={summary.current.adjusted_cost} wrapGroups />
                </dd>
              </div>
            </dl>
          </>
        ) : summary.updating ? (
          <p className={styles.muted} role="status">
            Обновляем оценку…
          </p>
        ) : summary.issue ? (
          <div className={styles.issue} role="status">
            <strong>
              {summary.paused ? 'Загрузка оценки приостановлена' : 'Оценка недоступна'}
            </strong>
            <p className={styles.muted}>{summary.issue}</p>
            {!summary.paused && (
              <Button variant="ghost" onClick={() => void summary.retry({ cancelRefetch: false })}>
                Повторить
              </Button>
            )}
          </div>
        ) : null}
      </div>
      <div className={styles.cta}>
        <Button
          variant={summary.current && !complete ? 'primary' : 'secondary'}
          onClick={() => navigate(detailPath)}
        >
          {action}
        </Button>
        <p className={styles.muted}>Расходы, преимущества и заметки</p>
      </div>
      <div className={styles.actions}>
        <Checkbox
          label="К сравнению"
          aria-label={'Выбрать для сравнения ' + name}
          checked={selected}
          onChange={(event) => onSelection(car.id, event.target.checked)}
        />
        <Inline gap="tight">
          {original && (
            <Link href={original} target="_blank" rel="noopener noreferrer">
              Оригинальное объявление
            </Link>
          )}
          <DeleteVehicleButton car={car} onRemoved={onRemoved} />
        </Inline>
      </div>
    </article>
  );
}
