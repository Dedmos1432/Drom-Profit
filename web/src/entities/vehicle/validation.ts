import { z } from 'zod';
import type { CarInput, CarPatch } from './api';

const brand = z.string().trim().min(1, 'Укажите марку.');
const model = z.string().trim().min(1, 'Укажите модель.');
const price = z
  .int({ error: 'Укажите цену целым числом.' })
  .positive('Цена должна быть больше нуля.');
const year = z
  .int({ error: 'Год должен быть целым числом.' })
  .min(1886, 'Укажите год не раньше 1886.');
const nonnegativeInteger = z
  .int({ error: 'Укажите целое число.' })
  .min(0, 'Укажите число не меньше нуля.');
const engineVolume = z
  .number({ error: 'Укажите объём двигателя числом.' })
  .min(0, 'Укажите число не меньше нуля.');
const uri = z.url({ error: 'Укажите полный адрес.' });

const demoAuthority = /^https:\/\/auto\.drom\.ru(\/[^?#]*)(.*)$/i;
const listingPath = /^(?:\/[A-Za-z0-9_.-]+)+\.html$/;
const uriParameter = "[A-Za-z0-9._~!$&'()*+,;=:@/?-]|%[0-9A-Fa-f]{2}";
const urlSuffix = new RegExp(String.raw`^(?:\?(?:${uriParameter})*)?(?:#(?:${uriParameter})*)?$`);

export function canonicalDemoListingUrl(url: string): string | null {
  if (url.length > 2048 || /[^\x21-\x7e]|\\/.test(url)) return null;
  const match = demoAuthority.exec(url);
  if (!match) return null;
  const [, path = '', suffix = ''] = match;
  if (!listingPath.test(path) || /\/\.{1,2}\//.test(path + '/') || !urlSuffix.test(suffix)) {
    return null;
  }
  return 'https://auto.drom.ru' + path;
}

export const listingUrlSchema = z
  .string()
  .min(1, 'Введите ссылку на объявление.')
  .refine((url) => canonicalDemoListingUrl(url) !== null, 'Укажите ссылку на объявление Дрома.');

export const carInputSchema = z.strictObject({
  source: z.enum(['manual', 'drom', 'demo']),
  listing_id: z.string().nullable().optional(),
  fetched_at: z.iso.datetime({ offset: true }).nullable().optional(),
  brand,
  model,
  price,
  year: year.nullable().optional(),
  mileage_km: nonnegativeInteger.nullable().optional(),
  trim: z.string().nullable().optional(),
  fuel_type: z.string().nullable().optional(),
  engine_volume_l: engineVolume.nullable().optional(),
  power_hp: nonnegativeInteger.nullable().optional(),
  transmission: z.string().nullable().optional(),
  drive_type: z.string().nullable().optional(),
  city: z.string().nullable().optional(),
  image_url: uri.nullable().optional(),
  listing_url: uri.nullable().optional(),
  market_valuation: nonnegativeInteger.nullable().optional(),
}) satisfies z.ZodType<CarInput>;

export const carPatchSchema = carInputSchema
  .omit({ source: true, listing_id: true, fetched_at: true })
  .extend({ expenses_reviewed: z.boolean(), benefits_reviewed: z.boolean() })
  .partial() satisfies z.ZodType<CarPatch>;

const optionalText = z
  .string()
  .trim()
  .transform((value) => value || null);

function parseNumber(value: string) {
  return /^-?(?:\d+(?:[.,]\d+)?|[.,]\d+)$/.test(value)
    ? Number(value.replace(',', '.'))
    : Number.NaN;
}

function optionalNumber(schema: z.ZodType<number, number>) {
  return z
    .string()
    .trim()
    .transform((value) => (value ? parseNumber(value) : null))
    .pipe(schema.nullable());
}

export const carFormSchema = z.object({
  brand,
  model,
  price: z.string().trim().transform(parseNumber).pipe(price),
  year: optionalNumber(year),
  mileage_km: optionalNumber(nonnegativeInteger),
  trim: optionalText,
  fuel_type: optionalText,
  engine_volume_l: optionalNumber(engineVolume),
  power_hp: optionalNumber(nonnegativeInteger),
  transmission: optionalText,
  drive_type: optionalText,
  city: optionalText,
  image_url: optionalText.pipe(uri.nullable()),
  listing_url: optionalText.pipe(uri.nullable()),
  market_valuation: optionalNumber(nonnegativeInteger),
});

export type CarFormValues = z.input<typeof carFormSchema>;
export type ValidCarForm = z.output<typeof carFormSchema>;
