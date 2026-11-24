import { Link, useLocation } from 'react-router';
import { Alert, PageHeader, Stack } from '@/shared/ui';

export function ComparisonPage() {
  const { state } = useLocation();
  const count = Array.isArray(state?.carIds) ? new Set(state.carIds).size : 0;

  return (
    <Stack gap="roomy">
      <PageHeader title="Сравнение автомобилей" />
      <Alert tone="info" title={count >= 2 ? 'Автомобили выбраны' : 'Подготовка сравнения'}>
        {count >= 2
          ? 'Выбранные автомобили переданы на этот экран. '
          : 'Выберите хотя бы два автомобиля из своей выборки. '}
        Таблица сравнения появится на следующем этапе.
      </Alert>
      <div>
        <Link to="/">Вернуться в выборку</Link>
      </div>
    </Stack>
  );
}
