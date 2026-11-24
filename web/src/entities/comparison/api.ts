import { useQuery } from '@tanstack/react-query';
import { useSession } from '@/entities/session/SessionBoundary';
import { ApiError } from '@/shared/api/client';
import type { paths } from '@/shared/api/schema';
import { comparisonKeys } from './keys';

type Operation = paths['/api/comparison']['post'];
export type ComparisonRequest = Operation['requestBody']['content']['application/json'];
export type Comparison = Operation['responses'][200]['content']['application/json'];
export type ComparisonItem = Comparison['items'][number];
export type ComparisonWarning = Comparison['warnings'][number];

export function useComparison(ids: string[], enabled: boolean) {
  const { api } = useSession();
  return useQuery({
    queryKey: comparisonKeys.result(ids),
    enabled: enabled && ids.length >= 2,
    async queryFn({ signal }) {
      const body: ComparisonRequest = { car_ids: ids };
      const result = await api.request<Comparison>('/api/comparison', {
        method: 'POST',
        body,
        signal,
      });
      const returned = new Set(result.items.map((item) => item.car_id));
      if (result.items.length !== ids.length || ids.some((id) => !returned.has(id))) {
        throw new ApiError(
          'invalid_response',
          'invalid_comparison',
          'Ответ не соответствует составу сравнения.',
        );
      }
      return result;
    },
  });
}
