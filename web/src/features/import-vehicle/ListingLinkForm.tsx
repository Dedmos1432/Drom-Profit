import { zodResolver } from '@hookform/resolvers/zod';
import type { ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { listingUrlSchema } from '@/entities/vehicle/validation';
import { ApiError } from '@/shared/api/client';
import { classes } from '@/shared/lib/classes';
import { Button, Input } from '@/shared/ui';
import styles from './ListingLinkForm.module.css';

const schema = z.object({ url: listingUrlSchema });

const importErrors: Record<string, string> = {
  invalid_url: 'Укажите ссылку на объявление Дрома.',
  network_error: 'Не удалось связаться с сервером. Повторите попытку или введите данные вручную.',
  source_forbidden: 'У источника нет доступных данных для импорта. Попробуйте ручной ввод.',
  listing_unavailable: 'Объявление недоступно. Проверьте ссылку или добавьте автомобиль вручную.',
  parse_error: 'Не удалось прочитать данные объявления. Их можно ввести вручную.',
  source_unavailable: 'Источник временно недоступен. Повторите попытку или введите данные вручную.',
};

export function importErrorMessage(error: unknown) {
  return (
    (error instanceof ApiError && importErrors[error.code]) ||
    'Не удалось получить данные. Повторите попытку или добавьте автомобиль вручную.'
  );
}

type Props = {
  onSubmit: (url: string) => void;
  initialUrl?: string;
  busy?: boolean;
  quick?: boolean;
  secondaryAction?: ReactNode;
};

export function ListingLinkForm({
  onSubmit,
  initialUrl = '',
  busy,
  quick,
  secondaryAction,
}: Props) {
  const {
    handleSubmit,
    register,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { url: initialUrl },
  });

  return (
    <form
      noValidate
      className={classes(styles.form, secondaryAction ? styles.withSecondary : undefined)}
      onSubmit={handleSubmit(({ url }) => onSubmit(url))}
    >
      <Input
        {...register('url')}
        type="url"
        label="Ссылка на объявление"
        placeholder="https://auto.drom.ru/…"
        autoComplete="off"
        readOnly={busy}
        error={errors.url?.message}
      />
      <Button type="submit" variant={quick ? 'contrast' : 'primary'} loading={busy}>
        {quick ? 'Добавить по ссылке' : busy ? 'Получаем данные…' : 'Получить данные'}
      </Button>
      {secondaryAction}
    </form>
  );
}
