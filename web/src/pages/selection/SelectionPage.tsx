import { useNavigate } from 'react-router';
import { useSession } from '@/entities/session/SessionBoundary';
import { useCars } from '@/entities/vehicle/api';
import { ListingLinkForm } from '@/features/import-vehicle/ListingLinkForm';
import { useComparisonSelection } from '@/features/select-for-comparison/ComparisonSelection';
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
  const selection = useComparisonSelection();
  const selected = new Set(selection.ids);
  const availableIds = cars.data?.map(({ id }) => id) ?? [];
  const selectedCount = availableIds.filter((id) => selected.has(id)).length;

  return (
    <Stack gap="roomy">
      <PageHeader
        title="Моя выборка"
        description="Добавьте автомобили, оцените расходы и преимущества, затем сравните варианты."
      />
      <section aria-label="Быстрое добавление" className={styles.quick}>
        <ListingLinkForm
          quick
          onSubmit={(listingUrl) => navigate('/cars/new', { state: { listingUrl } })}
          secondaryAction={
            <Button
              type="button"
              variant="secondary"
              onClick={() => navigate('/cars/new?method=manual')}
            >
              Добавить вручную
            </Button>
          }
        />
      </section>
      <section aria-labelledby="saved-cars" className={styles.list}>
        <div className={styles.listHeader}>
          <Inline gap="tight">
            <h2 id="saved-cars">Сохранённые автомобили</h2>
            {cars.data && <span className={styles.count}>{savedCount(cars.data.length)}</span>}
            {(config.scenario === 'seeded' || config.scenario === 'showcase') && (
              <Badge tone="info">Демонстрационная выборка</Badge>
            )}
          </Inline>
          {cars.data && cars.data.length > 0 && (
            <Inline gap="tight">
              <Button
                variant="ghost"
                disabled={selectedCount === cars.data.length}
                onClick={() => selection.selectAll(availableIds)}
              >
                Выбрать все
              </Button>
              <span className={styles.count} role="status">
                Выбрано {selectedCount} из {cars.data.length}
              </span>
              <Button
                variant="outline"
                disabled={selectedCount < 2}
                onClick={() => navigate('/comparison')}
              >
                Сравнить автомобили
              </Button>
              {selection.ids.length > 0 && (
                <Button variant="ghost" onClick={selection.clear}>
                  Очистить выбор
                </Button>
              )}
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
            {selectedCount < 2 && (
              <p className={styles.count}>Выберите хотя бы два автомобиля для сравнения.</p>
            )}
            <ul className={styles.items}>
              {cars.data.map((car) => (
                <li key={car.id}>
                  <VehicleCard
                    car={car}
                    selected={selected.has(car.id)}
                    onSelection={selection.select}
                    onRemoved={(id) => selection.select(id, false)}
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
