import fixtures from '../../../shared/fixtures/demo-cars.json';
import listings from '../../../shared/fixtures/import-listings.json';
import type { Car, CarDetail, CarInput } from '@/entities/vehicle/api';
import { photosAfterWrite, readGallery } from '@/entities/vehicle/photos';
import type { AppConfig } from '@/shared/config/env';
import { showcaseCars } from './showcase';

function seededCars(): CarDetail[] {
  return (fixtures as Car[]).map((car) => {
    const listing = listings.find(({ listing_id }) => listing_id === car.listing_id);
    return { ...car, ...readGallery(listing ?? car) };
  });
}

export function createMockStore(
  storage: Pick<Storage, 'getItem' | 'setItem'>,
  config: AppConfig,
  locks?: LockManager,
) {
  const scope = encodeURIComponent(config.baseUrl.replace(/\/+$/, ''));
  const keyFor = (token: string) =>
    ['drom-profit.mock-db.v1', config.scenario, scope, token].join('.');

  function list(token: string): CarDetail[] | null {
    const data = storage.getItem(keyFor(token));
    if (!data) return null;
    return (JSON.parse(data) as CarDetail[]).map((car) => ({ ...car, ...readGallery(car) }));
  }

  function save(token: string, cars: CarDetail[]) {
    storage.setItem(keyFor(token), JSON.stringify(cars));
  }

  function transact<T>(token: string, mutate: (cars: CarDetail[]) => T) {
    const update = () => {
      const cars = list(token);
      if (!cars) return null;
      const result = mutate(cars);
      save(token, cars);
      return result;
    };
    return locks ? locks.request(keyFor(token), update) : Promise.resolve().then(update);
  }

  return {
    createSession() {
      const token = 'drom-demo-' + crypto.randomUUID();
      const cars =
        config.scenario === 'showcase'
          ? showcaseCars()
          : config.scenario === 'seeded'
            ? seededCars()
            : [];
      save(token, cars);
      return token;
    },
    list,
    mutateCar<T>(token: string, id: string, change: (car: CarDetail) => T) {
      return transact(token, (cars) => {
        const car = cars.find((candidate) => candidate.id === id);
        return car ? change(car) : undefined;
      });
    },
    add(token: string, input: CarInput) {
      return transact(token, (cars) => {
        const image_urls = photosAfterWrite(input);
        const car: CarDetail = {
          id: crypto.randomUUID(),
          source: input.source,
          brand: input.brand,
          model: input.model,
          price: input.price,
          listing_id: input.listing_id ?? null,
          fetched_at: input.fetched_at ?? null,
          year: input.year ?? null,
          mileage_km: input.mileage_km ?? null,
          trim: input.trim ?? null,
          fuel_type: input.fuel_type ?? null,
          engine_volume_l: input.engine_volume_l ?? null,
          power_hp: input.power_hp ?? null,
          transmission: input.transmission ?? null,
          drive_type: input.drive_type ?? null,
          city: input.city ?? null,
          image_url: image_urls[0] ?? null,
          image_urls,
          listing_url: input.listing_url ?? null,
          market_valuation: input.market_valuation ?? null,
          expenses_reviewed: false,
          benefits_reviewed: false,
          expenses: [],
          benefits: [],
          notes: [],
        };
        cars.unshift(car);
        return car;
      });
    },
    remove(token: string, id: string) {
      return transact(token, (cars) => {
        const index = cars.findIndex((car) => car.id === id);
        if (index < 0) return false;
        cars.splice(index, 1);
        return true;
      });
    },
  };
}
