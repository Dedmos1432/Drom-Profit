import { Link } from 'react-router';
import { PageHeader, Stack } from '@/shared/ui';

export function NotFoundPage() {
  return (
    <Stack gap="roomy">
      <PageHeader title="Страница не найдена" />
      <div>
        <Link to="/">Вернуться в выборку</Link>
      </div>
    </Stack>
  );
}
