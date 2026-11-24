import { Link as RouterLink, useParams } from 'react-router';
import { useCar } from '@/entities/vehicle/api';
import {
  engine,
  mileage,
  originalListing,
  vehicleName,
  vehicleSourceLabel,
} from '@/entities/vehicle/presentation';
import { VehiclePhoto } from '@/entities/vehicle/VehiclePhoto';
import { ApiError } from '@/shared/api/client';
import {
  Badge,
  Breadcrumbs,
  Button,
  EmptyState,
  ErrorState,
  Link,
  PageHeader,
  Price,
  SpecificationList,
  Spinner,
  Stack,
} from '@/shared/ui';
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

  const car = query.data;
  const name = vehicleName(car);
  const original = originalListing(car);

  return (
    <Stack gap="roomy">
      <Breadcrumbs
        items={[
          { label: 'Моя выборка', link: <RouterLink to="/">Моя выборка</RouterLink> },
          { label: name },
        ]}
      />
      <div className={styles.details}>
        <VehiclePhoto value={car.image_url} />
        <Stack className={styles.description} gap="roomy">
          <Stack gap="tight">
            <PageHeader title={name} />
            <Badge tone={car.source === 'demo' ? 'info' : 'neutral'}>
              {vehicleSourceLabel[car.source]}
            </Badge>
            <div>
              <p className={styles.muted}>Цена объявления</p>
              <Price size="display" value={car.price} />
            </div>
            {car.market_valuation !== null && (
              <p>
                Рыночная оценка Дрома <Price size="compact" value={car.market_valuation} />
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
                { label: 'Источник', value: vehicleSourceLabel[car.source] },
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
          <div>{back}</div>
        </Stack>
      </div>
    </Stack>
  );
}
