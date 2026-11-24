import { useState } from 'react';
import { classes } from '@/shared/lib/classes';
import { ImagePlaceholder } from '@/shared/ui';
import { safeWebUrl } from './presentation';
import styles from './VehiclePhoto.module.css';

export function VehiclePhoto({ value, className }: { value: string | null; className?: string }) {
  const url = safeWebUrl(value);
  const [failed, setFailed] = useState<string | null>(null);

  return url && failed !== url ? (
    <img
      className={classes(styles.photo, className)}
      alt=""
      src={url}
      loading="lazy"
      onError={() => setFailed(url)}
    />
  ) : (
    <ImagePlaceholder data-photo-placeholder="" className={classes(styles.photo, className)} />
  );
}
