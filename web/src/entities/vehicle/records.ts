import type { components } from '@/shared/api/schema';
import { factorMoney } from '@/shared/api/money';
import { checkedMoneyResponse, vehiclePath } from './api';
import type { VehicleClient } from './api';

type Schemas = components['schemas'];
export type Expense = Schemas['Expense'];
export type Benefit = Schemas['Benefit'];
export type Note = Schemas['Note'];
export type ExpenseInput = Schemas['ExpenseInput'];
export type BenefitInput = Schemas['BenefitInput'];
export type NoteInput = Schemas['NoteInput'];
export type ExpensePatch = Schemas['ExpensePatch'];
export type BenefitPatch = Schemas['BenefitPatch'];
export type NotePatch = Schemas['NotePatch'];

type Entries = { expenses: Expense; benefits: Benefit; notes: Note };
type Inputs = { expenses: ExpenseInput; benefits: BenefitInput; notes: NoteInput };
type Patches = { expenses: ExpensePatch; benefits: BenefitPatch; notes: NotePatch };
export type Collection = keyof Entries;

export type RecordChange<K extends Collection> =
  | { action: 'create'; body: Inputs[K] }
  | { action: 'edit'; itemId: string; body: Patches[K] }
  | { action: 'delete'; itemId: string };

export async function updateRecord<K extends Collection>(
  api: VehicleClient,
  id: string,
  collection: K,
  change: RecordChange<K>,
) {
  const path = `${vehiclePath(id)}/${collection}` as const;
  const money = collection !== 'notes';
  const itemPath =
    change.action === 'create' ? path : (`${path}/${encodeURIComponent(change.itemId)}` as const);
  if (change.action === 'delete') return api.request<void>(itemPath, { method: 'DELETE' });
  const response = await api.request<Entries[K]>(itemPath, {
    method: change.action === 'create' ? 'POST' : 'PATCH',
    body: change.body,
    money,
  });
  return money ? checkedMoneyResponse(factorMoney, response) : response;
}

export const expenseLabels = {
  repair: 'Ремонт',
  maintenance: 'Техническое обслуживание',
  missing_equipment: 'Отсутствующее оснащение',
  other: 'Другое',
} satisfies Record<Expense['kind'], string>;

export const noteLabels = {
  report: 'Сведения из отчёта',
  inspection: 'Результаты осмотра',
  seller: 'Информация от продавца',
  accepted_defect: 'Недостаток без необходимости устранения',
  other: 'Прочее',
} satisfies Record<Note['kind'], string>;
