import { useRef } from 'react';
import { Link } from 'react-router';
import { useComparison } from '@/entities/comparison/api';
import { useCars } from '@/entities/vehicle/api';
import { useComparisonSelection } from '@/features/select-for-comparison/ComparisonSelection';
import { ApiError } from '@/shared/api/client';
import {
  Alert,
  Badge,
  Breadcrumbs,
  Button,
  EmptyState,
  ErrorState,
  Inline,
  PageHeader,
  Spinner,
  Stack,
} from '@/shared/ui';
import { comparisonColumns } from '@/widgets/comparison-table/columns';
import { ComparisonTable } from '@/widgets/comparison-table/ComparisonTable';
import { ComparisonWarnings } from '@/widgets/comparison-table/ComparisonWarnings';

export default function ComparisonPage() {
  const selection = useComparisonSelection();
  const cars = useCars(true);
  const available = new Set(cars.data?.map(({ id }) => id));
  const ids = selection.ids.filter((id) => available.has(id));
  const missing = selection.ids.length - ids.length;
  const loadingCars = cars.isFetching;
  const carsReady = cars.isSuccess && !loadingCars && !cars.isPaused;
  const comparison = useComparison(ids, carsReady);
  const change = useRef<HTMLAnchorElement>(null);
  const missingCar = comparison.error instanceof ApiError && comparison.error.status === 404;

  async function retry() {
    if (!missingCar) {
      await comparison.refetch({ cancelRefetch: false });
      return;
    }
    const latest = await cars.refetch({ cancelRefetch: false });
    if (latest.isSuccess && ids.every((id) => latest.data.some((car) => car.id === id))) {
      await comparison.refetch({ cancelRefetch: false });
    }
  }

  function content() {
    if (loadingCars) return <Spinner label="Проверяем состав сравнения…" />;
    if (!carsReady)
      return (
        <ErrorState
          title="Не удалось загрузить выборку"
          description="Повторите запрос, чтобы проверить доступность выбранных автомобилей."
          action={
            <Button onClick={() => void cars.refetch({ cancelRefetch: false })}>
              Повторить загрузку
            </Button>
          }
        />
      );

    if (ids.length < 2)
      return (
        <EmptyState
          title={
            ids.length === 0
              ? 'Автомобили для сравнения не выбраны'
              : 'Для сравнения нужен ещё один автомобиль'
          }
          description="Выберите в своей выборке два или больше автомобилей."
          action={<Link to="/">Вернуться в выборку</Link>}
        />
      );

    if (comparison.isFetching) return <Spinner label="Обновляем результаты сравнения…" />;
    if (comparison.isError || comparison.isPaused || !comparison.data)
      return (
        <ErrorState
          title={
            missingCar
              ? 'Один или несколько автомобилей недоступны'
              : 'Не удалось загрузить сравнение'
          }
          description={
            missingCar
              ? 'Проверьте состав сравнения и повторите запрос.'
              : 'Повторите запрос, чтобы получить актуальные показатели.'
          }
          action={<Button onClick={() => void retry()}>Повторить запрос</Button>}
        />
      );

    const result = comparison.data;
    const columns = comparisonColumns(cars.data ?? [], result.items);
    return (
      <Stack>
        {result.provisional && (
          <Inline>
            <Badge tone="warning">Предварительное сравнение</Badge>
          </Inline>
        )}
        <ComparisonWarnings result={result} columns={columns} />
        <p>
          Затраты на приобретение включают цену и учтённые вложения. Для сравнения мы учитываем вашу
          оценку преимуществ. Разница показана относительно минимальной скорректированной стоимости.
        </p>
        <ComparisonTable
          columns={columns}
          result={result}
          onExclude={(id) => {
            change.current?.focus({ preventScroll: true });
            selection.select(id, false);
          }}
        />
      </Stack>
    );
  }

  return (
    <Stack gap="roomy">
      <Breadcrumbs
        items={[
          { label: 'Моя выборка', link: <Link to="/">Моя выборка</Link> },
          { label: 'Сравнение' },
        ]}
      />
      <PageHeader
        title="Сравнение автомобилей"
        description={carsReady ? <span role="status">В сравнении · {ids.length}</span> : undefined}
        actions={
          <Link ref={change} to="/">
            Изменить состав
          </Link>
        }
      />
      {carsReady && missing > 0 && (
        <Alert tone="warning" title="Часть выбранных автомобилей недоступна">
          <Stack gap="tight">
            <p>В сравнении участвуют автомобили, которые остались в вашей выборке.</p>
            <div>
              <Button variant="secondary" onClick={() => selection.retain(ids)}>
                Убрать недоступные из состава
              </Button>
            </div>
          </Stack>
        </Alert>
      )}
      {content()}
    </Stack>
  );
}
