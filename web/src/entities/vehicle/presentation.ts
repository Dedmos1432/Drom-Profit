import type { Car, Summary } from './api';

const number = new Intl.NumberFormat('ru-RU');

export const vehicleSourceLabel: Record<Car['source'], string> = {
  drom: 'Дром',
  demo: 'Демо-источник',
  manual: 'Ручной ввод',
};

export const assessmentLabel: Record<Summary['assessment_status'], string> = {
  complete: 'Все данные рассмотрены',
  incomplete: 'Предварительная оценка',
};

export function vehicleName(car: Car) {
  return [car.brand + ' ' + car.model, car.year].filter((value) => value !== null).join(', ');
}

export function mileage(car: Car) {
  return car.mileage_km === null ? null : number.format(car.mileage_km) + ' км';
}

export function engine(car: Car) {
  return (
    [
      car.fuel_type,
      car.engine_volume_l === null ? null : number.format(car.engine_volume_l) + ' л',
      car.power_hp === null ? null : number.format(car.power_hp) + ' л. с.',
    ]
      .filter(Boolean)
      .join(', ') || null
  );
}

export function safeWebUrl(url: string | null) {
  if (!url || !URL.canParse(url)) return null;
  const parsed = new URL(url);
  return ['https:', 'http:'].includes(parsed.protocol) && !parsed.username && !parsed.password
    ? parsed.href
    : null;
}

export function sourceLabel({ source, image_url }: Pick<Car, 'source' | 'image_url'>) {
  const image = image_url && safeWebUrl(image_url);
  if (source === 'demo' && image && new URL(image).pathname.includes('/demo/vehicles/')) {
    return 'Демо · фото модели';
  }
  return vehicleSourceLabel[source];
}

export function originalListing(car: Car) {
  const url = safeWebUrl(car.listing_url);
  if (car.source === 'demo' || !url) return null;
  const parsed = new URL(url);
  return parsed.hostname === 'auto.drom.ru' && parsed.pathname.startsWith('/demo/') ? null : url;
}
