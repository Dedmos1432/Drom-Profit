import type { QueryFilters } from '@tanstack/react-query';
import { sessionDataKey } from '@/entities/session/session-store';

const all = [...sessionDataKey, 'comparison'] as const;

export const comparisonKeys = {
  result: (ids: readonly string[]) => [...all, ids] as const,
};

export function comparisonsWithVehicle(id: string): QueryFilters {
  return {
    queryKey: all,
    predicate: ({ queryKey }) => {
      const ids = queryKey.at(-1);
      return Array.isArray(ids) && ids.includes(id);
    },
  };
}
