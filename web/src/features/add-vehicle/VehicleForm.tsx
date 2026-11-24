import { zodResolver } from '@hookform/resolvers/zod';
import type { HTMLAttributes } from 'react';
import { useRef } from 'react';
import { useForm } from 'react-hook-form';
import type { CarInput, Listing } from '@/entities/vehicle/api';
import { carFormSchema } from '@/entities/vehicle/validation';
import type { CarFormValues, ValidCarForm } from '@/entities/vehicle/validation';
import { Alert, Button, Input, Stack } from '@/shared/ui';
import styles from './VehicleForm.module.css';

type Field = {
  name: keyof CarFormValues;
  label: string;
  inputMode?: HTMLAttributes<HTMLInputElement>['inputMode'];
  type?: 'url';
  required?: boolean;
  helperText?: string;
};

const basics: Field[] = [
  { name: 'brand', label: 'Марка', required: true },
  { name: 'model', label: 'Модель', required: true },
  { name: 'price', label: 'Цена, ₽', inputMode: 'numeric', required: true },
  { name: 'year', label: 'Год выпуска', inputMode: 'numeric' },
  { name: 'mileage_km', label: 'Пробег, км', inputMode: 'numeric' },
  { name: 'city', label: 'Город продажи' },
];

const specifications: Field[] = [
  { name: 'trim', label: 'Комплектация' },
  { name: 'fuel_type', label: 'Тип двигателя' },
  { name: 'engine_volume_l', label: 'Объём двигателя, л', inputMode: 'decimal' },
  { name: 'power_hp', label: 'Мощность, л. с.', inputMode: 'numeric' },
  { name: 'transmission', label: 'Коробка передач' },
  { name: 'drive_type', label: 'Привод' },
];

const source: Field[] = [
  { name: 'image_url', label: 'Адрес фотографии', type: 'url' },
  { name: 'listing_url', label: 'Оригинальное объявление', type: 'url' },
  {
    name: 'market_valuation',
    label: 'Рыночная оценка Дрома, ₽',
    inputMode: 'numeric',
    helperText: 'Заполните, если оценка указана в объявлении.',
  },
];

const allFields = [...basics, ...specifications, ...source];

function initialValues(listing?: Listing): CarFormValues {
  return Object.fromEntries(
    allFields.map(({ name }) => [name, String(listing?.[name] ?? '')]),
  ) as CarFormValues;
}

export function VehicleForm({
  listing,
  onSave,
  saving,
  failed,
}: {
  listing?: Listing;
  onSave: (input: CarInput) => Promise<void>;
  saving?: boolean;
  failed?: boolean;
}) {
  const locked = useRef(false);
  const {
    handleSubmit,
    register,
    formState: { errors },
  } = useForm<CarFormValues, unknown, ValidCarForm>({
    resolver: zodResolver(carFormSchema),
    defaultValues: initialValues(listing),
  });

  function group(title: string, fields: Field[]) {
    return (
      <fieldset className={styles.group} disabled={saving}>
        <legend>{title}</legend>
        <div className={styles.fields}>
          {fields.map(({ name, ...field }) => (
            <Input key={name} {...register(name)} {...field} error={errors[name]?.message} />
          ))}
        </div>
      </fieldset>
    );
  }

  return (
    <form
      noValidate
      onSubmit={handleSubmit(async (values) => {
        if (locked.current) return;
        locked.current = true;
        try {
          await onSave({
            ...values,
            source: listing?.source ?? 'manual',
            listing_id: listing?.listing_id ?? null,
            fetched_at: listing?.fetched_at ?? null,
          });
        } finally {
          locked.current = false;
        }
      })}
    >
      <Stack gap="roomy">
        <p className={styles.hint}>Обязательные поля отмечены звёздочкой.</p>
        {group('Основные данные', basics)}
        {group('Характеристики', specifications)}
        {group('Объявление и фото', source)}
        {failed && (
          <Alert tone="error" title="Не удалось сохранить автомобиль" role="alert">
            Введённые данные сохранены в форме. Повторите попытку.
          </Alert>
        )}
        <div>
          <Button type="submit" loading={saving}>
            {saving ? 'Сохраняем…' : 'Сохранить автомобиль'}
          </Button>
        </div>
      </Stack>
    </form>
  );
}
