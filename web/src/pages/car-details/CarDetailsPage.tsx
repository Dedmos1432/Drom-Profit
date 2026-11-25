import { Link as RouterLink, useLocation, useParams } from 'react-router';
import { useEffect, useState } from 'react';
import { useSession } from '@/entities/session/SessionBoundary';
import { useCar, useCarSummary } from '@/entities/vehicle/api';
import type { Car } from '@/entities/vehicle/api';
import {
  engine,
  mileage,
  originalListing,
  sourceLabel,
  vehicleName,
} from '@/entities/vehicle/presentation';
import { vehicleGalleryImages } from '@/entities/vehicle/showcase-photos';
import { VehicleGallery } from '@/entities/vehicle/VehicleGallery';
import { EditVehicleDialog } from '@/features/edit-vehicle/EditVehicleDialog';
import { ApiError } from '@/shared/api/client';
import {
  Badge,
  Breadcrumbs,
  Button,
  EmptyState,
  ErrorState,
  Inline,
  Link,
  PageHeader,
  Price,
  SpecificationList,
  Spinner,
  Stack,
} from '@/shared/ui';
import { FinancialSummary } from '@/widgets/vehicle-workspace/FinancialSummary';
import { MoneyFactorsSection } from '@/widgets/vehicle-workspace/MoneyFactorsSection';
import { NotesSection } from '@/widgets/vehicle-workspace/NotesSection';
import styles from './CarDetailsPage.module.css';

export function CarDetailsPage() {
  const { carId = '' } = useParams();
  const query = useCar(carId);
  const back = <RouterLink to="/">Вернуться в выборку</RouterLink>;

  if (query.isPending) return <Spinner label="Загружаем автомобиль…" />;

  if (query.error instanceof ApiError && query.error.status === 404)
    return (
      <Stack gap="roomy">
        <PageHeader title="Автомобиль не найден" />
        <EmptyState
          title="Карточка недоступна"
          description="Возможно, автомобиль уже удалён из вашей выборки."
          action={back}
        />
      </Stack>
    );

  if (!query.data)
    return (
      <Stack gap="roomy">
        <PageHeader title="Автомобиль" />
        <ErrorState
          title="Не удалось загрузить автомобиль"
          description="Повторите попытку или вернитесь в выборку."
          action={
            <Button onClick={() => void query.refetch()} loading={query.isFetching}>
              Повторить
            </Button>
          }
        />
        <div>{back}</div>
      </Stack>
    );

  return (
    <VehicleDetails
      key={query.data.id}
      car={query.data}
      reviewAvailable={!query.isFetching && !query.isError}
      failed={query.isError}
      retry={() => void query.refetch()}
    />
  );
}

function VehicleDetails({
  car,
  reviewAvailable,
  failed,
  retry,
}: {
  car: Car;
  reviewAvailable: boolean;
  failed: boolean;
  retry: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const { config } = useSession();
  const summary = useCarSummary(car.id);
  const { hash } = useLocation();
  const name = vehicleName(car);
  const original = originalListing(car);
  const images = vehicleGalleryImages(car, config.mode === 'mock' ? config.baseUrl : undefined);

  useEffect(() => {
    const target = hash.slice(1);
    if (!['expenses', 'benefits', 'notes'].includes(target)) return;
    const frame = requestAnimationFrame(() => document.getElementById(target)?.focus());
    return () => cancelAnimationFrame(frame);
  }, [car.id, hash]);

  return (
    <Stack gap="roomy">
      <Breadcrumbs
        items={[
          { label: 'Моя выборка', link: <RouterLink to="/">Моя выборка</RouterLink> },
          { label: name },
        ]}
      />
      {failed && (
        <ErrorState
          title="Не удалось обновить карточку"
          description="Показаны последние загруженные характеристики."
          action={
            <Button variant="secondary" onClick={retry}>
              Повторить загрузку карточки
            </Button>
          }
        />
      )}
      <div className={styles.details}>
        <VehicleGallery images={images} modelPhotos={images.length > 1} />
        <Stack className={styles.description} gap="roomy">
          <Stack gap="tight">
            <PageHeader title={name} />
            <Badge tone={car.source === 'demo' ? 'info' : 'neutral'}>{sourceLabel(car)}</Badge>
            <div>
              <p className={styles.muted}>Цена объявления</p>
              <Price size="display" value={car.price} wrapGroups />
            </div>
            {car.market_valuation !== null && (
              <p>
                Рыночная оценка Дрома{' '}
                <Price size="compact" value={car.market_valuation} wrapGroups />
              </p>
            )}
          </Stack>
          <section aria-labelledby="specs-heading" className={styles.facts}>
            <h2 id="specs-heading">Характеристики</h2>
            <SpecificationList
              items={[
                { label: 'Год выпуска', value: car.year },
                { label: 'Пробег', value: mileage(car) },
                { label: 'Двигатель', value: engine(car) },
                { label: 'Коробка передач', value: car.transmission },
                { label: 'Привод', value: car.drive_type },
                { label: 'Город продажи', value: car.city },
                { label: 'Комплектация', value: car.trim },
              ]}
            />
          </section>
          {original && (
            <div>
              <Link href={original} target="_blank" rel="noopener noreferrer">
                Оригинальное объявление
              </Link>
            </div>
          )}
          <Inline gap="tight">
            <Button variant="outline" onClick={() => setEditing(true)}>
              Редактировать характеристики
            </Button>
            <Link href="#financial-summary">К экономической оценке</Link>
          </Inline>
        </Stack>
      </div>
      <div className={styles.workspace}>
        <Stack gap="roomy">
          <MoneyFactorsSection
            car={car}
            group="expenses"
            summary={summary.current}
            reviewAvailable={reviewAvailable}
          />
          <MoneyFactorsSection
            car={car}
            group="benefits"
            summary={summary.current}
            reviewAvailable={reviewAvailable}
          />
          <NotesSection car={car} />
        </Stack>
        <FinancialSummary car={car} summary={summary} reviewAvailable={reviewAvailable} />
      </div>
      <div>
        <RouterLink to="/">Вернуться в выборку</RouterLink>
      </div>
      {editing && <EditVehicleDialog car={car} onClose={() => setEditing(false)} />}
    </Stack>
  );
}
