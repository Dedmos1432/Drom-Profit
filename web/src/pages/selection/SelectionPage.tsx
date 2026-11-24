import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useSession } from '@/entities/session/SessionBoundary';
import { useCars } from '@/entities/vehicle/api';
import { ListingLinkForm } from '@/features/import-vehicle/ListingLinkForm';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Inline,
  PageHeader,
  Spinner,
  Stack,
} from '@/shared/ui';
import { VehicleCard } from '@/widgets/vehicle-card/VehicleCard';
import styles from './SelectionPage.module.css';

const plural = new Intl.PluralRules('ru');

function savedCount(count: number) {
  const unit =
    { one: 'автомобиль', few: 'автомобиля', many: 'автомобилей' }[
      plural.select(count) as 'one' | 'few' | 'many'
    ] ?? 'автомобилей';
  return count + ' ' + unit;
}

export function SelectionPage() {
  const navigate = useNavigate();
  const { config } = useSession();
  const cars = useCars();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const selectedIds = cars.data?.filter(({ id }) => selected.has(id)).map(({ id }) => id) ?? [];

  function changeSelection(id: string, checked: boolean) {
    setSelected((previous) => {
      const next = new Set(previous);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  return (
    <Stack gap="roomy">
      <PageHeader
        title="Моя выборка"
        description="Соберите автомобили, которые рассматриваете, чтобы оценить вложения и сравнить варианты."
        actions={<Button onClick={() => navigate('/cars/new')}>Добавить автомобиль</Button>}
      />
      <section aria-label="Быстрое добавление" className={styles.quick}>
        <Stack gap="tight">
          <ListingLinkForm
            quick
            onSubmit={(listingUrl) => navigate('/cars/new', { state: { listingUrl } })}
          />
          <div>
            <Link to="/cars/new?method=manual">Добавить вручную</Link>
          </div>
        </Stack>
      </section>
      <section aria-labelledby="saved-cars" className={styles.list}>
        <div className={styles.listHeader}>
          <Inline gap="tight">
            <h2 id="saved-cars">Сохранённые автомобили</h2>
            {cars.data && <span className={styles.count}>{savedCount(cars.data.length)}</span>}
            {config.scenario === 'seeded' && <Badge tone="info">Демонстрационная выборка</Badge>}
          </Inline>
          {cars.data && cars.data.length > 0 && (
            <Inline gap="tight">
              <span className={styles.count} role="status">
                Выбрано {selectedIds.length}
              </span>
              <Button
                variant="outline"
                disabled={selectedIds.length < 2}
                onClick={() => navigate('/comparison', { state: { carIds: selectedIds } })}
              >
                Подготовить сравнение
              </Button>
            </Inline>
          )}
        </div>
        {cars.isPending && <Spinner label="Загружаем выборку…" />}
        {cars.isError && !cars.data && (
          <ErrorState
            title="Не удалось загрузить выборку"
            description="Повторите запрос, чтобы увидеть сохранённые автомобили."
            action={
              <Button onClick={() => void cars.refetch()} loading={cars.isFetching}>
                Повторить
              </Button>
            }
          />
        )}
        {cars.data?.length === 0 && (
          <div className={styles.empty}>
            <EmptyState
              title="Пока нет автомобилей"
              description="Вставьте ссылку на объявление выше или введите данные первого автомобиля вручную."
            />
          </div>
        )}
        {cars.data && cars.data.length > 0 && (
          <>
            <p className={styles.count}>
              Выберите хотя бы два автомобиля. Таблица сравнения появится на следующем этапе.
            </p>
            <ul className={styles.items}>
              {cars.data.map((car) => (
                <li key={car.id}>
                  <VehicleCard
                    car={car}
                    selected={selected.has(car.id)}
                    onSelection={changeSelection}
                    onRemoved={(id) => changeSelection(id, false)}
                  />
                </li>
              ))}
            </ul>
          </>
        )}
      </section>
    </Stack>
  );
}
