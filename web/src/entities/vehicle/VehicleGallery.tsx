import { useState } from 'react';
import { safeWebUrl } from './presentation';
import { VehiclePhoto } from './VehiclePhoto';
import styles from './VehicleGallery.module.css';

export type VehicleImage = { url: string; alt: string };

export function VehicleGallery({
  images,
  modelPhotos = false,
}: {
  images: readonly VehicleImage[];
  modelPhotos?: boolean;
}) {
  const [selected, setSelected] = useState<string>();
  const [unavailable, setUnavailable] = useState<ReadonlySet<string>>(() => new Set());
  const available = new Map<string, string>();
  for (const image of images) {
    const url = safeWebUrl(image.url);
    if (url && !unavailable.has(url)) available.set(url, image.alt);
  }
  const current = selected && available.has(selected) ? selected : available.keys().next().value;

  function hideUnavailable(url: string) {
    setUnavailable((previous) => new Set(previous).add(url));
  }

  return (
    <div className={styles.gallery}>
      <VehiclePhoto
        value={current ?? null}
        alt={current ? available.get(current) : undefined}
        className={styles.main}
        onUnavailable={hideUnavailable}
      />
      {available.size > 1 && (
        <>
          <div className={styles.thumbnails} role="group" aria-label="Фотографии автомобиля">
            {[...available].map(([url, alt]) => (
              <button
                key={url}
                type="button"
                className={styles.thumbnail}
                aria-label={alt}
                aria-pressed={url === current}
                onClick={() => setSelected(url)}
              >
                <img src={url} alt="" loading="lazy" onError={() => hideUnavailable(url)} />
              </button>
            ))}
          </div>
          {modelPhotos && <p className={styles.caption}>Иллюстративные фотографии модели</p>}
        </>
      )}
    </div>
  );
}
