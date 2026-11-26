import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { useSession } from '@/entities/session/SessionBoundary';
import { summarySchema } from '@/entities/vehicle/api';
import { ApiError } from '@/shared/api/client';
import { comparisonDifference } from '@/shared/api/money';
import type { paths } from '@/shared/api/schema';
import { comparisonKeys } from './keys';

type Operation = paths['/api/comparison']['post'];
export type ComparisonRequest = Operation['requestBody']['content']['application/json'];
export type Comparison = Operation['responses'][200]['content']['application/json'];
export type ComparisonItem = Comparison['items'][number];
export type ComparisonWarning = Comparison['warnings'][number];

export const comparisonSchema = z.object({
  items: z.array(summarySchema.extend({ difference_from_min: comparisonDifference.nullable() })),
  warnings: z.array(
    z.object({
      code: z.enum(['incomplete', 'double_count', 'negative_adjusted_cost']),
      message: z.string(),
      factor_key: z.string().nullable().optional(),
    }),
  ),
  provisional: z.boolean(),
}) satisfies z.ZodType<Comparison>;

export function useComparison(ids: string[], enabled: boolean) {
  const { api } = useSession();
  return useQuery({
    queryKey: comparisonKeys.result(ids),
    enabled: enabled && ids.length >= 2,
    async queryFn({ signal }) {
      const body: ComparisonRequest = { car_ids: ids };
      const payload = await api.request<unknown>('/api/comparison', {
        method: 'POST',
        body,
        signal,
        money: true,
      });
      const parsed = comparisonSchema.safeParse(payload);
      if (!parsed.success) {
        throw new ApiError(
          'invalid_response',
          'invalid_comparison',
          'Сервер вернул некорректное сравнение.',
        );
      }

      const result = parsed.data;
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
