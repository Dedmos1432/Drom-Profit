import { z } from 'zod';
import type { CarInput } from './api';

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

export const listingUrlSchema = z
  .string()
  .trim()
  .min(1, 'Введите ссылку на объявление.')
  .pipe(uri)
  .refine((url) => {
    if (!URL.canParse(url)) return false;
    const { protocol, hostname, username, password } = new URL(url);
    return (
      (protocol === 'https:' || protocol === 'http:') &&
      (hostname === 'drom.ru' || hostname.endsWith('.drom.ru')) &&
      !username &&
      !password
    );
  }, 'Укажите ссылку на объявление Дрома.');

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
