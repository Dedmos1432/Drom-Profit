import { expect, it } from 'vitest';
import { canonicalDemoListingUrl, carFormSchema, listingUrlSchema } from './validation';
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

const demoUrl = 'https://auto.drom.ru/demo/1.html';

it.each([
  demoUrl,
  demoUrl + '?ref=demo%2Fitem',
  demoUrl + '#photo',
  'https://AUTO.DROM.RU/demo/1.html',
  demoUrl.replace('/1.html', '/999.html'),
])('accepts a supported listing URL %s without rewriting the API request', (url) => {
  expect(listingUrlSchema.parse(url)).toBe(url);
  expect(canonicalDemoListingUrl(url)).toBe(
    url.includes('999.html') ? 'https://auto.drom.ru/demo/999.html' : demoUrl,
  );
});

it.each([
  'http://auto.drom.ru/demo/1.html',
  'https://auto.drom.ru:443/demo/1.html',
  'https://drom.ru/demo/1.html',
  'https://auto.drom.ru/',
  'https://auto.drom.ru/demo/',
  'https://auto.drom.ru/demo/1',
  'https://other.example/demo/1.html',
  'https://user@auto.drom.ru/demo/1.html',
  'https://auto.drom.ru/demo/./1.html',
  demoUrl + '?ref=%xx',
])('rejects unsupported listing URL %s before URL normalization', (url) => {
  expect(listingUrlSchema.safeParse(url).success).toBe(false);
});

it('accepts the unreserved tilde in a listing path on the allowed host', () => {
  const url = 'https://auto.drom.ru/city/brand/model~name/12345678.html';
  expect(listingUrlSchema.parse(url + '?ref=test#photo')).toBe(url + '?ref=test#photo');
  expect(canonicalDemoListingUrl(url + '?ref=test#photo')).toBe(url);
});

it('accepts a demo link and rejects invalid or unrelated links safely', () => {
  expect(listingUrlSchema.safeParse('https://auto.drom.ru/demo/1.html').success).toBe(true);
  expect(listingUrlSchema.safeParse('not a URL').success).toBe(false);
  expect(listingUrlSchema.safeParse('https://auto.drom.ru.example.test/1.html').success).toBe(
    false,
  );
});
