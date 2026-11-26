import { ApiError } from '@/shared/api/client';
import { readConfig } from '@/shared/config/env';
import { prepareShowcase } from './prepare-showcase';

const button = document.querySelector<HTMLButtonElement>('#prepare');
const status = document.querySelector<HTMLElement>('#status');
if (!button || !status) throw new Error('Missing showcase controls');

button.addEventListener('click', async () => {
  button.disabled = true;
  status.textContent = 'Подготавливаем демонстрационную сессию…';
  try {
    if (!import.meta.env.DEV || !navigator.locks || navigator.serviceWorker.controller) {
      throw new Error('Откройте локальный Web в отдельном профиле Chrome без Mock Service Worker.');
    }
    const result = await prepareShowcase(readConfig(), localStorage, navigator.locks);
    status.textContent =
      result === 'created'
        ? 'Пять карточек готовы. Summary и Comparison проверены.'
        : 'Подготовка уже выполнена в этом профиле. Можно открыть текущую выборку.';
  } catch (error) {
    status.textContent =
      error instanceof ApiError
        ? 'Запрос к Backend не удался (' +
          (error.status ?? error.kind) +
          '). Проверьте настройки стенда.'
        : error instanceof Error
          ? error.message
          : 'Не удалось подготовить демонстрационную сессию.';
  } finally {
    button.disabled = false;
  }
});

button.disabled = false;
