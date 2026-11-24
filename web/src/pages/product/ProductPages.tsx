import { Link as RouterLink, useParams } from 'react-router';
import { Breadcrumbs, EmptyState, PageHeader, Panel, Stack } from '@/shared/ui';

function SelectionBreadcrumbs({ current }: { current: string }) {
  return (
    <Breadcrumbs
      items={[
        { label: 'Моя выборка', link: <RouterLink to="/">Моя выборка</RouterLink> },
        { label: current },
      ]}
    />
  );
}

export function SelectionPage() {
  return (
    <Stack gap="roomy">
      <PageHeader title="Моя выборка" />
      <Panel>
        <EmptyState
          title="Автомобили для сравнения"
          description="Здесь будут сохранённые автомобили."
          action={<RouterLink to="/cars/new">Перейти к добавлению</RouterLink>}
        />
      </Panel>
    </Stack>
  );
}

export function AddCarPage() {
  return (
    <Stack gap="roomy">
      <PageHeader
        title="Добавление автомобиля"
        breadcrumbs={<SelectionBreadcrumbs current="Добавление автомобиля" />}
      />
      <Panel>
        <p>На этом экране будет предпросмотр объявления по ссылке и форма ручного ввода.</p>
      </Panel>
    </Stack>
  );
}

export function CarDetailsPage() {
  const { carId } = useParams();

  return (
    <Stack gap="roomy">
      <PageHeader title="Автомобиль" breadcrumbs={<SelectionBreadcrumbs current="Автомобиль" />} />
      <Panel>
        <Stack gap="tight">
          <p>Идентификатор автомобиля — {carId}</p>
          <p>Здесь будут характеристики, расходы, преимущества, заметки и саммари.</p>
        </Stack>
      </Panel>
    </Stack>
  );
}

export function ComparisonPage() {
  return (
    <Stack gap="roomy">
      <PageHeader
        title="Сравнение автомобилей"
        breadcrumbs={<SelectionBreadcrumbs current="Сравнение" />}
      />
      <Panel>
        <EmptyState
          title="Сравнение выбранных автомобилей"
          description="В сравнении можно сопоставить два или более автомобиля из своей выборки."
          action={<RouterLink to="/">Открыть выборку</RouterLink>}
        />
      </Panel>
    </Stack>
  );
}

export function NotFoundPage() {
  return (
    <Stack gap="roomy">
      <PageHeader title="Страница не найдена" />
      <RouterLink to="/">Вернуться в выборку</RouterLink>
    </Stack>
  );
}
