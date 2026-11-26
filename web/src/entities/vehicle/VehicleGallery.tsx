import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useState } from 'react';
import { IconButton } from '@/shared/ui';
import { classes } from '@/shared/lib/classes';
import { imageSrc, readPhotoReference } from './photos';
import { VehiclePhoto } from './VehiclePhoto';
import styles from './VehicleGallery.module.css';

export type VehicleImage = { url: string; alt: string };

export function VehicleGallery({
  images,
  modelPhotos = false,
  className,
}: {
  images: readonly VehicleImage[];
  modelPhotos?: boolean;
  className?: string;
}) {
  const [selected, setSelected] = useState<string>();
  const [unavailable, setUnavailable] = useState<ReadonlySet<string>>(() => new Set());
  const available = new Map<string, string>();
  for (const image of images) {
    const url = readPhotoReference(image.url);
    if (url && !unavailable.has(url)) available.set(url, image.alt);
  }
  const urls = [...available.keys()];
  const current = selected && available.has(selected) ? selected : urls[0];

  function move(step: number) {
    if (current === undefined || urls.length < 2) return;
    setSelected(urls[(urls.indexOf(current) + step + urls.length) % urls.length]);
  }

  function hideUnavailable(url: string) {
    setUnavailable((previous) => new Set(previous).add(url));
  }

  return (
    <div className={classes(styles.gallery, className)}>
      <div className={styles.frame}>
        <VehiclePhoto
          value={current ?? null}
          alt={current ? available.get(current) : undefined}
          className={styles.main}
          onUnavailable={hideUnavailable}
        />
        {urls.length > 1 && (
          <div className={styles.navigation}>
            <IconButton
              label="Предыдущее фото"
              icon={<ChevronLeft />}
              variant="secondary"
              onClick={() => move(-1)}
            />
            <IconButton
              label="Следующее фото"
              icon={<ChevronRight />}
              variant="secondary"
              onClick={() => move(1)}
            />
          </div>
        )}
      </div>
      {available.size > 1 && (
        <>
          <div
            className={styles.thumbnails}
            role="group"
            aria-label="Фотографии автомобиля"
            onKeyDown={(event) => {
              if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
              event.preventDefault();
              move(event.key === 'ArrowLeft' ? -1 : 1);
            }}
          >
            {[...available].map(([url, alt]) => (
              <button
                key={url}
                type="button"
                className={styles.thumbnail}
                aria-label={alt}
                aria-pressed={url === current}
                onClick={() => setSelected(url)}
              >
                <img
                  src={imageSrc(url) ?? undefined}
                  alt=""
                  loading="lazy"
                  onError={() => hideUnavailable(url)}
                />
              </button>
            ))}
          </div>
          {modelPhotos && <p className={styles.caption}>Иллюстративные фотографии модели</p>}
        </>
      )}
    </div>
  );
}
