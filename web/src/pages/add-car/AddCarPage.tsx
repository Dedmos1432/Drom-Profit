import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { useSession } from '@/entities/session/SessionBoundary';
import { vehicleApi } from '@/entities/vehicle/api';
import type { CarInput, Listing } from '@/entities/vehicle/api';
import { listingUrlSchema } from '@/entities/vehicle/validation';
import { VehiclePhoto } from '@/entities/vehicle/VehiclePhoto';
import { VehicleForm } from '@/features/add-vehicle/VehicleForm';
import { useSaveVehicle } from '@/features/add-vehicle/useSaveVehicle';
import { importErrorMessage, ListingLinkForm } from '@/features/import-vehicle/ListingLinkForm';
import { Alert, Badge, Breadcrumbs, Inline, PageHeader, Radio, Stack } from '@/shared/ui';
import styles from './AddCarPage.module.css';

export function AddCarPage() {
  const { api, config } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [search, setSearch] = useSearchParams();
  const mode = search.get('method') === 'manual' ? 'manual' : 'link';
  const [initialUrl] = useState(() => listingUrlSchema.safeParse(location.state?.listingUrl).data);
  const requested = useRef(false);
  const importBusy = useRef(false);
  const [preview, setPreview] = useState<{ listing: Listing; version: number }>();
  const save = useSaveVehicle();
  const { mutate, isPending, isError, error } = useMutation({
    mutationFn: (url: string) => vehicleApi.import(api, { url }),
    onSuccess(listing) {
      setPreview((previous) => ({ listing, version: (previous?.version ?? 0) + 1 }));
    },
    onSettled() {
      importBusy.current = false;
    },
  });

  useEffect(() => {
    if (!initialUrl || requested.current) return;
    requested.current = true;
    importBusy.current = true;
    navigate('/cars/new', { replace: true });
    mutate(initialUrl);
  }, [initialUrl, mutate, navigate]);

  function submitLink(url: string) {
    if (importBusy.current) return;
    importBusy.current = true;
    mutate(url);
  }

  function selectMode(next: 'manual' | 'link') {
    save.reset();
    setSearch(next === 'manual' ? { method: 'manual' } : {}, { replace: true });
  }

  async function saveCar(input: CarInput) {
    try {
      await save.mutateAsync(input);
      navigate('/');
    } catch {
      // The mutation exposes the error while the mounted form retains its fields.
    }
  }

  return (
    <Stack gap="roomy" className={styles.page}>
      <PageHeader
        title="Добавить автомобиль"
        breadcrumbs={
          <Breadcrumbs
            items={[
              { label: 'Моя выборка', link: <Link to="/">Моя выборка</Link> },
              { label: 'Добавить автомобиль' },
            ]}
          />
        }
      />
      <fieldset className={styles.method} disabled={save.isPending}>
        <legend>Способ добавления</legend>
        <Inline gap="roomy">
          <Radio
            name="add-method"
            label="По ссылке"
            checked={mode === 'link'}
            onChange={() => selectMode('link')}
          />
          <Radio
            name="add-method"
            label="Вручную"
            checked={mode === 'manual'}
            onChange={() => selectMode('manual')}
          />
        </Inline>
      </fieldset>
      {mode === 'manual' ? (
        <section aria-labelledby="manual-title">
          <Stack>
            <h2 id="manual-title">Ручное добавление</h2>
            <VehicleForm
              key="manual"
              onSave={saveCar}
              saving={save.isPending}
              failed={save.isError}
            />
          </Stack>
        </section>
      ) : (
        <>
          <section className={styles.importPanel} aria-label="Получение данных объявления">
            <Stack gap="tight">
              <p>
                Поддерживаются ссылки на объявления Дрома. Данные можно проверить перед сохранением.
              </p>
              {config.mode === 'mock' && (
                <p className={styles.hint}>
                  Для демопроверки подойдёт <code>https://auto.drom.ru/demo/1.html</code>.
                </p>
              )}
              <ListingLinkForm
                initialUrl={initialUrl}
                onSubmit={submitLink}
                busy={isPending || save.isPending}
              />
              {isError && (
                <Alert role="alert" tone="error" title="Не удалось получить данные">
                  {importErrorMessage(error)}
                </Alert>
              )}
              {!save.isPending && (
                <div>
                  <Link to="/cars/new?method=manual" onClick={() => save.reset()}>
                    Ввести данные вручную
                  </Link>
                </div>
              )}
            </Stack>
          </section>
          {preview && (
            <section aria-labelledby="preview-title">
              <Stack>
                <Inline gap="tight">
                  <h2 id="preview-title">Предпросмотр автомобиля</h2>
                  {preview.listing.source === 'demo' && <Badge tone="info">Демо-источник</Badge>}
                </Inline>
                <p className={styles.hint}>
                  {preview.listing.source === 'demo'
                    ? 'Показаны синтетические данные. Проверьте и при необходимости измените их перед сохранением.'
                    : 'Проверьте данные и при необходимости исправьте их перед сохранением.'}
                </p>
                {preview.listing.image_url && (
                  <VehiclePhoto value={preview.listing.image_url} className={styles.previewPhoto} />
                )}
                <VehicleForm
                  key={preview.version}
                  listing={preview.listing}
                  onSave={saveCar}
                  saving={save.isPending}
                  failed={save.isError}
                />
              </Stack>
            </section>
          )}
        </>
      )}
    </Stack>
  );
}
