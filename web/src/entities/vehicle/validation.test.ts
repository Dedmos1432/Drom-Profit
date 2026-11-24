import { expect, it } from 'vitest';
import { carFormSchema, listingUrlSchema } from './validation';
import type { CarFormValues } from './validation';

const draft: CarFormValues = {
  brand: ' Toyota ',
  model: 'Camry',
  price: '1200000',
  year: '',
  mileage_km: '0',
  trim: '',
  fuel_type: '',
  engine_volume_l: '2,5',
  power_hp: '',
  transmission: '',
  drive_type: '',
  city: '',
  image_url: '',
  listing_url: '',
  market_valuation: '',
};

it('preserves an explicit zero and sends unknown values as null', () => {
  expect(carFormSchema.parse(draft)).toMatchObject({
    brand: 'Toyota',
    price: 1200000,
    year: null,
    mileage_km: 0,
    trim: null,
    engine_volume_l: 2.5,
    image_url: null,
    market_valuation: null,
  });
});

it.each([
  { brand: ' ' },
  { model: '' },
  { price: '' },
  { price: '0' },
  { price: '12.5' },
  { mileage_km: '-1' },
  { year: '1885' },
])('rejects invalid values %j', (changes) => {
  expect(carFormSchema.safeParse({ ...draft, ...changes }).success).toBe(false);
});

it('accepts a demo link and rejects invalid or unrelated links safely', () => {
  expect(listingUrlSchema.safeParse('https://auto.drom.ru/demo/1.html').success).toBe(true);
  expect(listingUrlSchema.safeParse('not a URL').success).toBe(false);
  expect(listingUrlSchema.safeParse('https://auto.drom.ru.example.test/1.html').success).toBe(
    false,
  );
});
