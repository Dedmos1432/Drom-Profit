import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router';
import type { Comparison, ComparisonItem } from '@/entities/comparison/api';
import type { Car } from '@/entities/vehicle/api';
import {
  assessmentLabel,
  mileage,
  sourceLabel,
  vehicleName,
} from '@/entities/vehicle/presentation';
import { VehiclePhoto } from '@/entities/vehicle/VehiclePhoto';
import { Badge, Button, Price, Stack, Table } from '@/shared/ui';
import { columnName } from './columns';
import type { ComparisonColumn } from './columns';
import styles from './ComparisonTable.module.css';

const moneyRows = [
  { field: 'listing_price', label: 'Цена объявления' },
  { field: 'total_expenses', label: 'Учтённые вложения' },
  { field: 'acquisition_cost', label: 'Затраты на приобретение', major: true },
  { field: 'total_benefits', label: 'Ценность преимуществ' },
  { field: 'adjusted_cost', label: 'Скорректированная стоимость', major: true },
  { field: 'difference_from_min', label: 'Разница с минимумом' },
] as const satisfies readonly { field: keyof ComparisonItem; label: string; major?: boolean }[];

const number = new Intl.NumberFormat('ru-RU');
const withUnit = (value: number | null, unit: string) =>
  value === null ? null : number.format(value) + ' ' + unit;

const specs: { label: string; value: (car: Car) => ReactNode }[] = [
  { label: 'Марка и модель', value: (car) => car.brand + ' ' + car.model },
  { label: 'Год выпуска', value: (car) => car.year },
  { label: 'Пробег', value: mileage },
  { label: 'Комплектация', value: (car) => car.trim },
  { label: 'Тип двигателя', value: (car) => car.fuel_type },
  { label: 'Объём двигателя', value: (car) => withUnit(car.engine_volume_l, 'л') },
  { label: 'Мощность', value: (car) => withUnit(car.power_hp, 'л. с.') },
  { label: 'Коробка передач', value: (car) => car.transmission },
  { label: 'Привод', value: (car) => car.drive_type },
  { label: 'Город продажи', value: (car) => car.city },
];

const pathFor = ({ id }: Car) => '/cars/' + encodeURIComponent(id);

export function ComparisonTable({
  columns,
  result,
  onExclude,
}: {
  columns: readonly ComparisonColumn[];
  result: Comparison;
  onExclude: (id: string) => void;
}) {
  const doubleCount = result.warnings.some(({ code }) => code === 'double_count');
  const showMinimum = !result.warnings.some(
    ({ code }) => code === 'double_count' || code === 'negative_adjusted_cost',
  );
  const showMarket = columns.some(({ car }) => car.market_valuation !== null);
  const showUnknown = columns.some(
    ({ item }) => item.unknown_expenses > 0 || item.unknown_benefits > 0,
  );
  const width = { '--comparison-columns': columns.length } as CSSProperties;

  function group(label: string) {
    return (
      <tr className={styles.group}>
        <th colSpan={columns.length + 1} scope="rowgroup">
          <span>{label}</span>
        </th>
      </tr>
    );
  }

  return (
    <Table
      caption="Показатели по автомобилям"
      className={styles.table}
      scrollClassName={styles.scroller}
      scrollHint="В таблице можно прокручивать строки и столбцы."
      style={width}
    >
      <colgroup>
        <col className={styles.labelColumn} />
        {columns.map(({ car }) => (
          <col key={car.id} />
        ))}
      </colgroup>
      <thead>
        <tr>
          <th scope="col" className={styles.pin}>
            Показатель
          </th>
          {columns.map((column) => {
            const { car, item } = column;
            return (
              <th key={car.id} scope="col" aria-label={columnName(column)}>
                <Link to={pathFor(car)} className={styles.carName} aria-label={columnName(column)}>
                  {vehicleName(car)}
                </Link>
                <div className={styles.identifiers}>
                  <Price size="compact" value={item.listing_price} wrapGroups />
                  {mileage(car) && <span className={styles.muted}>{mileage(car)}</span>}
                </div>
              </th>
            );
          })}
        </tr>
      </thead>
      <tbody>
        <tr>
          <th scope="row" className={styles.pin}>
            Автомобили
          </th>
          {columns.map((column) => {
            const { car } = column;
            return (
              <td key={car.id}>
                <div className={styles.vehicle}>
                  <VehiclePhoto value={car.image_url} className={styles.photo} />
                  <Badge tone={car.source === 'demo' ? 'info' : 'neutral'}>
                    {sourceLabel(car)}
                  </Badge>
                  <Button
                    variant="ghost"
                    className={styles.exclude}
                    onClick={() => onExclude(car.id)}
                    aria-label={'Исключить из сравнения ' + columnName(column)}
                  >
                    Исключить
                  </Button>
                </div>
              </td>
            );
          })}
        </tr>
      </tbody>
      <tbody>
        {group('Экономические показатели')}
        {moneyRows.map((row) => (
          <tr key={row.field} className={'major' in row ? styles.major : undefined}>
            <th scope="row" className={styles.pin}>
              {row.label}
              {row.field === 'difference_from_min' &&
                (doubleCount ? (
                  <span className={styles.metricNote}>Двойной учёт</span>
                ) : (
                  result.provisional && <span className={styles.metricNote}>Предварительно</span>
                ))}
            </th>
            {columns.map(({ car, item }) => {
              const difference = row.field === 'difference_from_min';
              const minimum =
                row.field === 'adjusted_cost' && showMinimum && item.difference_from_min === 0;
              return (
                <td key={car.id} data-numeric="" className={minimum ? styles.minimum : undefined}>
                  <Price
                    size={'major' in row ? 'display' : difference ? 'regular' : 'compact'}
                    className={'major' in row ? styles.majorAmount : undefined}
                    wrapGroups
                    value={item[row.field]}
                    unknownLabel={difference ? 'Недоступна' : 'Не указано'}
                  />
                  {minimum && (
                    <div className={styles.minimumLabel}>
                      <Badge tone={result.provisional ? 'warning' : 'info'}>
                        {result.provisional ? 'Предварительный минимум' : 'Минимум'}
                      </Badge>
                    </div>
                  )}
                </td>
              );
            })}
          </tr>
        ))}
        <tr>
          <th scope="row" className={styles.pin}>
            Готовность оценки
          </th>
          {columns.map(({ car, item }) => (
            <td key={car.id}>
              <Badge tone={item.assessment_status === 'complete' ? 'success' : 'warning'}>
                {assessmentLabel[item.assessment_status]}
              </Badge>
            </td>
          ))}
        </tr>
        {showUnknown && (
          <tr>
            <th scope="row" className={styles.pin}>
              Неизвестные суммы
            </th>
            {columns.map(({ car, item }) => (
              <td key={car.id}>
                {item.unknown_expenses > 0 && <div>Расходы · {item.unknown_expenses}</div>}
                {item.unknown_benefits > 0 && <div>Преимущества · {item.unknown_benefits}</div>}
                {item.unknown_expenses === 0 && item.unknown_benefits === 0 && 'Нет'}
              </td>
            ))}
          </tr>
        )}
        <tr>
          <th scope="row" className={styles.pin}>
            Записи по автомобилю
          </th>
          {columns.map(({ car }) => (
            <td key={car.id}>
              <Stack gap="tight">
                <Link to={pathFor(car) + '#expenses'}>Расходы</Link>
                <Link to={pathFor(car) + '#benefits'}>Преимущества</Link>
                <Link to={pathFor(car) + '#notes'}>Заметки</Link>
              </Stack>
            </td>
          ))}
        </tr>
      </tbody>
      <tbody>
        {group('Характеристики')}
        {specs.map(({ label, value }) => (
          <tr key={label}>
            <th scope="row" className={styles.pin}>
              {label}
            </th>
            {columns.map(({ car }) => (
              <td key={car.id}>{value(car) ?? 'Не указано'}</td>
            ))}
          </tr>
        ))}
        {showMarket && (
          <tr>
            <th scope="row" className={styles.pin}>
              Рыночная оценка Дрома
            </th>
            {columns.map(({ car }) => (
              <td key={car.id} data-numeric="">
                <Price size="compact" value={car.market_valuation} wrapGroups />
              </td>
            ))}
          </tr>
        )}
      </tbody>
    </Table>
  );
}
