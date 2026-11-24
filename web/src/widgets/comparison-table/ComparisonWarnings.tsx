import { Link } from 'react-router';
import type { Comparison } from '@/entities/comparison/api';
import { Alert, Stack } from '@/shared/ui';
import { columnName } from './columns';
import type { ComparisonColumn } from './columns';

const copy = {
  incomplete: {
    title: 'Часть данных требует уточнения',
    description:
      'Некоторые расходы или преимущества ещё не рассмотрены либо их стоимость неизвестна.',
  },
  double_count: {
    title: 'Обнаружен двойной учёт оснащения',
    description:
      'Разницы недоступны. Проверьте расходы и преимущества и выберите один способ учёта оснащения.',
  },
  negative_adjusted_cost: {
    title: 'Скорректированная стоимость ниже нуля',
    description:
      'Персональная ценность преимуществ превышает затраты на приобретение. Проверьте указанные суммы.',
  },
} satisfies Record<Comparison['warnings'][number]['code'], { title: string; description: string }>;

export function ComparisonWarnings({
  result,
  columns,
}: {
  result: Comparison;
  columns: readonly ComparisonColumn[];
}) {
  const codes = [...new Set(result.warnings.map(({ code }) => code))];
  if (!codes.length) return null;

  return (
    <Stack gap="tight">
      {codes.map((code) => {
        const keys = new Set(
          result.warnings
            .filter((warning) => warning.code === code)
            .flatMap(({ factor_key }) => (factor_key ? [factor_key] : [])),
        );
        const affected = columns.filter(({ car, item }) => {
          if (code === 'incomplete') return item.assessment_status === 'incomplete';
          if (code === 'negative_adjusted_cost') return item.adjusted_cost < 0;
          return (
            car.expenses.some(
              ({ kind, factor_key }) =>
                kind === 'missing_equipment' && factor_key && keys.has(factor_key),
            ) || car.benefits.some(({ factor_key }) => factor_key && keys.has(factor_key))
          );
        });
        const links = code === 'double_count' && !affected.length ? columns : affected;
        return (
          <Alert key={code} tone="warning" title={copy[code].title}>
            <p>{copy[code].description}</p>
            {links.length > 0 && (
              <ul>
                {links.map((column) => (
                  <li key={column.car.id}>
                    <Link
                      to={
                        '/cars/' +
                        encodeURIComponent(column.car.id) +
                        (code === 'negative_adjusted_cost' ? '#benefits' : '')
                      }
                    >
                      {columnName(column)}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Alert>
        );
      })}
    </Stack>
  );
}
