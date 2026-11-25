import { z } from 'zod';

export const MONEY_LIMIT = 1_000_000_000_000;
export const COST_MAX = 2 * MONEY_LIMIT;
export const ADJUSTED_COST_MIN = 1 - MONEY_LIMIT;
export const DIFFERENCE_MAX = 3 * MONEY_LIMIT - 1;
export const MONEY_LIMIT_LABEL = new Intl.NumberFormat('ru-RU').format(MONEY_LIMIT) + ' ₽';
export const MONEY_INPUT_FIELDS = new Set(['price', 'market_valuation', 'amount']);

const moneyFields = new Set([
  ...MONEY_INPUT_FIELDS,
  'listing_price',
  'total_expenses',
  'total_benefits',
  'acquisition_cost',
  'adjusted_cost',
  'difference_from_min',
]);
const integerLiteral = /^-?(?:0|[1-9][0-9]*)$/;
const integer = z.int({ error: 'Укажите целое число рублей.' });
export const positiveMoney = integer
  .min(1, 'Сумма должна быть больше нуля.')
  .max(MONEY_LIMIT, 'Сумма не должна превышать ' + MONEY_LIMIT_LABEL + '.');
export const moneyAmount = integer
  .min(0, 'Сумма должна быть не меньше нуля.')
  .max(MONEY_LIMIT, 'Сумма не должна превышать ' + MONEY_LIMIT_LABEL + '.');
export const acquisitionCost = integer.min(1).max(COST_MAX);
export const adjustedCost = integer.min(ADJUSTED_COST_MIN).max(COST_MAX);
export const comparisonDifference = integer.min(0).max(DIFFERENCE_MAX);

function integerForValidation(literal: string) {
  const value = Number(literal);
  if (Number.isSafeInteger(value)) return value === 0 ? 0 : value;
  // Preserve the range error for a well-formed huge integer. This value
  // exceeds every monetary maximum and cannot pass any monetary schema.
  return literal.startsWith('-') ? -Number.MAX_SAFE_INTEGER : Number.MAX_SAFE_INTEGER;
}

export const rublesText = z
  .string()
  .trim()
  .regex(/^(?:[0-9]+|[0-9]{1,3}(?:[ \u00a0\u202f][0-9]{3})+)$/, 'Укажите целое число рублей.')
  .transform((text) => integerForValidation(text.replace(/[ \u00a0\u202f]/g, '')));

export async function parseMoneyJson(text: string, fields: ReadonlySet<string> = moneyFields) {
  const { LosslessNumber, parse } = await import('lossless-json');
  return parse(
    text,
    (key, value) => {
      if (!(value instanceof LosslessNumber)) return value;
      if (!fields.has(key)) return Number(value.value);
      // Leave noninteger tokens as nonnumbers so the schema reports a type
      // error even when the original token would round to an integer.
      return integerLiteral.test(value.value) ? integerForValidation(value.value) : value;
    },
    { onDuplicateKey: ({ newValue }) => newValue },
  );
}

export const listingMoney = z.looseObject({
  price: positiveMoney.nullable(),
  market_valuation: positiveMoney.nullable(),
});
export const factorMoney = z.looseObject({ amount: moneyAmount.nullable() });
export const carMoney = listingMoney.extend({
  price: positiveMoney,
  expenses: z.array(factorMoney),
  benefits: z.array(factorMoney),
});
