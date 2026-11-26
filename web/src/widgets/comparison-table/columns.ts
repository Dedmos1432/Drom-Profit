import type { ComparisonItem } from '@/entities/comparison/api';
import type { Car } from '@/entities/vehicle/api';
import { mileage, vehicleName } from '@/entities/vehicle/presentation';

const rubles = new Intl.NumberFormat('ru-RU', {
  style: 'currency',
  currency: 'RUB',
  maximumFractionDigits: 0,
});

export function comparisonColumns(cars: readonly Car[], items: readonly ComparisonItem[]) {
  const carsById = new Map(cars.map((car) => [car.id, car]));
  return items.flatMap((item) => {
    const car = carsById.get(item.car_id);
    return car ? [{ car, item }] : [];
  });
}

export type ComparisonColumn = ReturnType<typeof comparisonColumns>[number];

export function columnName({ car, item }: ComparisonColumn) {
  return [vehicleName(car), rubles.format(item.listing_price), mileage(car)]
    .filter(Boolean)
    .join(' · ');
}
