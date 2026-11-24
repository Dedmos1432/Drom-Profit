import { http, HttpResponse } from 'msw';
import type { z } from 'zod';
import type { Car } from '@/entities/vehicle/api';
import type {
  Benefit,
  BenefitInput,
  Expense,
  ExpenseInput,
  Note,
  NoteInput,
} from '@/entities/vehicle/records';
import {
  benefitInputSchema,
  expenseInputSchema,
  noteInputSchema,
} from '@/entities/vehicle/record-validation';
import { carPatchSchema } from '@/entities/vehicle/validation';
import { changedFields } from '@/shared/lib/changed-fields';
import type { components } from '@/shared/api/schema';

type MutateCar = (request: Request, id: string, apply: (car: Car) => Response) => Promise<Response>;
type Review = 'expenses_reviewed' | 'benefits_reviewed';

function error(status: number, code: string, message: string) {
  return HttpResponse.json<components['schemas']['Error']>({ code, message }, { status });
}

const invalid = () => error(422, 'validation_error', 'Проверьте введённые данные.');
const missing = () => error(404, 'item_not_found', 'Запись не найдена.');
const duplicate = () =>
  error(400, 'duplicate_factor', 'Запись с таким ключом и категорией уже есть.');
const doubleCount = () =>
  error(
    400,
    'double_count',
    'Это оснащение уже учтено в другой группе. Выберите один способ учёта.',
  );
const noContent = () => new Response(null, { status: 204 });

type RecordOptions<Item extends { id: string }, Input> = {
  url: string;
  input: z.ZodType<Input>;
  patch: z.ZodType<Partial<Omit<Item, 'id'>>>;
  entries: (car: Car) => Item[];
  create: (input: Input) => Item;
  review?: Review;
  conflict?: (car: Car, candidate: Item) => Response | undefined;
};

function recordHandlers<Item extends { id: string }, Input>(
  mutateCar: MutateCar,
  options: RecordOptions<Item, Input>,
) {
  return [
    http.post(options.url, async ({ request, params }) => {
      const body = await request.json().catch(() => null);
      return mutateCar(request, String(params.carId), (car) => {
        const parsed = options.input.safeParse(body);
        if (!parsed.success) return invalid();
        const item = options.create(parsed.data);
        const conflict = options.conflict?.(car, item);
        if (conflict) return conflict;
        options.entries(car).push(item);
        if (options.review) car[options.review] = false;
        return HttpResponse.json(item, { status: 201 });
      });
    }),
    http.patch(options.url + '/:itemId', async ({ request, params }) => {
      const body = await request.json().catch(() => null);
      return mutateCar(request, String(params.carId), (car) => {
        const entries = options.entries(car);
        const index = entries.findIndex(({ id }) => id === params.itemId);
        const item = entries[index];
        if (!item) return missing();
        const parsed = options.patch.safeParse(body);
        if (!parsed.success) return invalid();
        const patch = changedFields<Omit<Item, 'id'>>(item, parsed.data);
        if (!Object.keys(patch).length) return HttpResponse.json(item);
        const next = { ...item, ...patch };
        const conflict = options.conflict?.(car, next);
        if (conflict) return conflict;
        entries[index] = next;
        if (options.review) car[options.review] = false;
        return HttpResponse.json(next);
      });
    }),
    http.delete(options.url + '/:itemId', ({ request, params }) =>
      mutateCar(request, String(params.carId), (car) => {
        const entries = options.entries(car);
        const index = entries.findIndex(({ id }) => id === params.itemId);
        if (index < 0) return missing();
        entries.splice(index, 1);
        if (options.review) car[options.review] = false;
        return noContent();
      }),
    ),
  ];
}

export function vehicleWriteHandlers(url: (path: string) => string, mutateCar: MutateCar) {
  return [
    http.patch(url('/api/cars/:carId'), async ({ request, params }) => {
      const body = await request.json().catch(() => null);
      return mutateCar(request, String(params.carId), (car) => {
        const parsed = carPatchSchema.safeParse(body);
        if (!parsed.success) return invalid();
        const { expenses_reviewed, benefits_reviewed, ...fields } = parsed.data;
        const changes = changedFields(car, fields);
        if (Object.keys(changes).length) {
          Object.assign(car, changes);
          car.expenses_reviewed = false;
          car.benefits_reviewed = false;
        }
        if (expenses_reviewed !== undefined) car.expenses_reviewed = expenses_reviewed;
        if (benefits_reviewed !== undefined) car.benefits_reviewed = benefits_reviewed;
        return HttpResponse.json(car);
      });
    }),
    ...recordHandlers<Expense, ExpenseInput>(mutateCar, {
      url: url('/api/cars/:carId/expenses'),
      input: expenseInputSchema,
      patch: expenseInputSchema.partial(),
      entries: (car) => car.expenses,
      create: (input) => ({
        ...input,
        id: crypto.randomUUID(),
        factor_key: input.factor_key ?? null,
        comment: input.comment ?? null,
      }),
      review: 'expenses_reviewed',
      conflict(car, candidate) {
        const key = candidate.factor_key;
        if (!key) return;
        if (
          car.expenses.some(
            (item) =>
              item.id !== candidate.id && item.kind === candidate.kind && item.factor_key === key,
          )
        )
          return duplicate();
        if (
          candidate.kind === 'missing_equipment' &&
          car.benefits.some((item) => item.factor_key === key)
        )
          return doubleCount();
      },
    }),
    ...recordHandlers<Benefit, BenefitInput>(mutateCar, {
      url: url('/api/cars/:carId/benefits'),
      input: benefitInputSchema,
      patch: benefitInputSchema.partial(),
      entries: (car) => car.benefits,
      create: (input) => ({
        ...input,
        id: crypto.randomUUID(),
        factor_key: input.factor_key ?? null,
        comment: input.comment ?? null,
      }),
      review: 'benefits_reviewed',
      conflict(car, candidate) {
        const key = candidate.factor_key;
        if (!key) return;
        if (car.benefits.some((item) => item.id !== candidate.id && item.factor_key === key))
          return duplicate();
        if (
          car.expenses.some((item) => item.kind === 'missing_equipment' && item.factor_key === key)
        )
          return doubleCount();
      },
    }),
    ...recordHandlers<Note, NoteInput>(mutateCar, {
      url: url('/api/cars/:carId/notes'),
      input: noteInputSchema,
      patch: noteInputSchema.partial(),
      entries: (car) => car.notes,
      create: (input) => ({ ...input, id: crypto.randomUUID() }),
    }),
  ];
}
