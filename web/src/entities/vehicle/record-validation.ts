import { z } from 'zod';
import { moneyAmount, rublesText } from '@/shared/api/money';
import type { BenefitInput, ExpenseInput, NoteInput } from './records';

const title = z.string().trim().min(1, 'Укажите название.');
const amount = moneyAmount;
const nullableText = z.string().nullable().optional();
const factorKey = z
  .string()
  .trim()
  .transform((key) => key || null)
  .nullable()
  .optional();
const text = z.string().trim().min(1, 'Введите текст заметки.');
const expenseKind = z.enum(['repair', 'maintenance', 'missing_equipment', 'other']);
const noteKind = z.enum(['report', 'inspection', 'seller', 'accepted_defect', 'other']);

const monetaryFields = {
  title,
  amount: amount.nullable(),
  comment: nullableText,
  factor_key: factorKey,
};
export const expenseInputSchema = z.strictObject({
  ...monetaryFields,
  kind: expenseKind,
}) satisfies z.ZodType<ExpenseInput>;
export const benefitInputSchema = z.strictObject(monetaryFields) satisfies z.ZodType<BenefitInput>;
export const noteInputSchema = z.strictObject({
  kind: noteKind,
  text,
}) satisfies z.ZodType<NoteInput>;

const amountText = rublesText.pipe(amount);
const optionalText = z
  .string()
  .trim()
  .transform((value) => value || null);

export const factorFormSchema = z
  .object({
    title,
    kind: expenseKind,
    amount: z.string(),
    unknown: z.boolean(),
    comment: optionalText,
    factor_key: optionalText,
  })
  .transform(({ amount, unknown, ...fields }, ctx) => {
    if (unknown) return { ...fields, amount: null };
    const parsed = amountText.safeParse(amount);
    if (!parsed.success) {
      ctx.addIssue({
        code: 'custom',
        path: ['amount'],
        message: parsed.error.issues[0]?.message ?? 'Проверьте сумму.',
      });
      return z.NEVER;
    }
    return { ...fields, amount: parsed.data };
  });
