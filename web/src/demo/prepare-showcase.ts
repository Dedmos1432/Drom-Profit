import listings from '../../../shared/fixtures/demo-showcase.json';
import assessments from '../../../shared/fixtures/demo-showcase-assessments.json';
import { comparisonSchema } from '@/entities/comparison/api';
import { createSessionStore, sessionStorageKey } from '@/entities/session/session-store';
import { vehicleApi } from '@/entities/vehicle/api';
import type { CarInput, VehicleClient } from '@/entities/vehicle/api';
import { updateRecord } from '@/entities/vehicle/records';
import type { BenefitInput, ExpenseInput, NoteInput } from '@/entities/vehicle/records';
import { createApiClient } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';
import type { AppConfig } from '@/shared/config/env';

type Assessment = {
  expenses: ExpenseInput[];
  benefits: BenefitInput[];
  notes: NoteInput[];
  expenses_reviewed: boolean;
  benefits_reviewed: boolean;
};

const scenarios = assessments as Record<string, Assessment>;

async function populate(api: VehicleClient, previews: CarInput[], signal: AbortSignal) {
  const summaries = [];
  for (const preview of previews) {
    const scenario = scenarios[preview.listing_id ?? ''];
    if (!scenario) throw new Error('Для объявления отсутствует сценарий оценки.');
    const car = await vehicleApi.create(api, preview);
    const { expenses, benefits, notes, ...reviewed } = scenario;
    for (const body of expenses)
      await updateRecord(api, car.id, 'expenses', { action: 'create', body });
    for (const body of benefits)
      await updateRecord(api, car.id, 'benefits', { action: 'create', body });
    for (const body of notes) await updateRecord(api, car.id, 'notes', { action: 'create', body });
    if (reviewed.expenses_reviewed || reviewed.benefits_reviewed) {
      await vehicleApi.patch(api, car.id, reviewed);
    }
    summaries.push(await vehicleApi.summary(api, car.id, signal));
  }
  return summaries;
}

export async function prepareShowcase(config: AppConfig, storage: Storage, locks: LockManager) {
  const base = new URL(config.baseUrl);
  if (
    config.mode !== 'real' ||
    base.protocol !== 'http:' ||
    !['127.0.0.1', 'localhost'].includes(base.hostname)
  ) {
    throw new Error('Запустите Web в режиме real с локальным API.');
  }

  const key = sessionStorageKey(config);
  const marker = key + '.demo-showcase';
  return locks.request(key, async () => {
    if (storage.getItem(marker) === 'ready') return 'existing';
    if (storage.getItem(marker)) {
      throw new Error(
        'Подготовка ранее прервалась. Откройте текущую выборку или используйте новый профиль Chrome.',
      );
    }
    if (storage.getItem(key)) {
      throw new Error('В этом профиле уже есть сессия. Используйте новый профиль Chrome.');
    }

    const publicApi = createApiClient({ baseUrl: config.baseUrl });
    const session = await publicApi.request<components['schemas']['Session']>('/api/session', {
      method: 'POST',
    });
    if (
      session.token_type !== 'bearer' ||
      typeof session.token !== 'string' ||
      !session.token.trim()
    ) {
      throw new Error('Сервер вернул некорректную сессию.');
    }
    const api = createApiClient({ baseUrl: config.baseUrl, getToken: () => session.token });
    // Fetch all previews before the first write to this new selection.
    const previews = await Promise.all(
      listings.map(async (listing) => {
        const preview = await vehicleApi.import(api, { url: listing.listing_url });
        const { brand, model, price } = preview;
        if (
          preview.source !== 'demo' ||
          preview.listing_id !== listing.listing_id ||
          !brand ||
          !model ||
          price === null ||
          preview.image_urls.length !== listing.image_urls.length ||
          preview.image_urls.some((photo, index) => photo !== listing.image_urls[index])
        ) {
          throw new Error('Демонстрационный предпросмотр неполный. Проверьте настройки Backend.');
        }
        return { ...preview, brand, model, price };
      }),
    );

    storage.setItem(marker, 'preparing');
    await createSessionStore(storage, key, async () => session.token).load();
    const signal = new AbortController().signal;
    const summaries = await populate(api, previews, signal);
    const payload = await api.request<unknown>('/api/comparison', {
      method: 'POST',
      body: { car_ids: summaries.map(({ car_id }) => car_id) },
      money: true,
    });
    const parsed = comparisonSchema.safeParse(payload);
    if (!parsed.success) throw new Error('Сервер вернул некорректное сравнение витрины.');
    const comparison = parsed.data;
    if (
      !comparison.provisional ||
      comparison.items.length !== summaries.length ||
      summaries.some((summary, index) => {
        const item = comparison.items[index];
        return (
          item?.car_id !== summary.car_id ||
          item.adjusted_cost !== summary.adjusted_cost ||
          item.assessment_status !== summary.assessment_status
        );
      })
    ) {
      throw new Error('Summary и Comparison не прошли проверку витрины.');
    }
    storage.setItem(marker, 'ready');
    return 'created';
  });
}
