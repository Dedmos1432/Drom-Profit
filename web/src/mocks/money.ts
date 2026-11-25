import { HttpResponse } from 'msw';
import type { z } from 'zod';
import { MONEY_INPUT_FIELDS, MONEY_LIMIT_LABEL, parseMoneyJson } from '@/shared/api/money';

export async function readMoneyInput(request: Request) {
  try {
    return await parseMoneyJson(await request.text(), MONEY_INPUT_FIELDS);
  } catch {
    return null;
  }
}

export function amountOutOfRange(
  message = 'Допустимый диапазон суммы зависит от поля. Максимум — ' + MONEY_LIMIT_LABEL + '.',
) {
  return HttpResponse.json({ code: 'amount_out_of_range', message }, { status: 422 });
}

export function moneyValidationError(error: z.ZodError, fallback: string) {
  if (
    error.issues.every(
      ({ code, path }) =>
        (code === 'too_big' || code === 'too_small') &&
        path.length === 1 &&
        typeof path[0] === 'string' &&
        MONEY_INPUT_FIELDS.has(path[0]),
    )
  )
    return amountOutOfRange();
  return HttpResponse.json({ code: 'validation_error', message: fallback }, { status: 422 });
}
