import { z } from 'zod';
import type { Car, Listing } from './api';
import type { VehicleImage } from './VehicleGallery';

export const MAX_PHOTOS = 64;
const MAX_PHOTO_URL_LENGTH = 2048;
const demoPhoto = /^\/demo\/vehicles\/[a-z0-9-]+\.jpg$/;
const remotePhoto = /^https:\/\/([^/?#]+)(.*)$/;
const publicHostname = /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,62}$/i;
const localHost = /\.(localhost|local|localdomain|internal|lan|home\.arpa)$/i;
const uriAtom = "[A-Za-z0-9._~!$&'()*+,;=:@/-]|%[0-9A-Fa-f]{2}";
const uriTail = new RegExp(`^(?:${uriAtom})*(?:\\?(?:${uriAtom}|\\?)*)?(?:#(?:${uriAtom}|\\?)*)?$`);

export function isDemoPhoto(value: string | null) {
  return value !== null && demoPhoto.test(value);
}

function validPhotoReference(value: string) {
  if (value.length > MAX_PHOTO_URL_LENGTH || /[^!-~]/.test(value)) return false;
  if (isDemoPhoto(value)) return true;
  const match = remotePhoto.exec(value);
  return (
    !!match &&
    publicHostname.test(match[1]!) &&
    !localHost.test(match[1]!) &&
    uriTail.test(match[2]!) &&
    URL.canParse(value)
  );
}

export const photoReferenceSchema = z.string().refine(validPhotoReference, {
  error: 'Укажите HTTPS-адрес фотографии.',
});
export const photoReferencesSchema = z.array(photoReferenceSchema).max(MAX_PHOTOS);

type PhotoWrite = { image_url?: string | null; image_urls?: string[] };

export function consistentPhotoFields({ image_url, image_urls }: PhotoWrite) {
  return (
    image_urls === undefined || image_url === undefined || (image_urls[0] ?? null) === image_url
  );
}

export const photoWriteSchema = z
  .object({
    image_url: photoReferenceSchema.nullable().optional(),
    image_urls: photoReferencesSchema.optional(),
  })
  .refine(consistentPhotoFields, 'Основная фотография должна быть первой в галерее.');

export function photosAfterWrite({ image_url, image_urls }: PhotoWrite, current: string[] = []) {
  if (image_urls !== undefined) return [...new Set(image_urls)];
  if (image_url === undefined) return current;
  return image_url === null ? [] : [...new Set([image_url, ...current])];
}

export function readPhotoReference(value: unknown): string | null {
  const parsed = photoReferenceSchema.safeParse(value);
  if (parsed.success) return parsed.data;
  // Старый Mock сохранял адрес локального JPG с абсолютным origin.
  if (
    typeof value === 'string' &&
    typeof location !== 'undefined' &&
    value.startsWith(location.origin)
  ) {
    const path = value.slice(location.origin.length);
    if (isDemoPhoto(path)) return path;
  }
  return null;
}

export function readGallery(source: { image_url: unknown; image_urls?: unknown }) {
  const main = readPhotoReference(source.image_url);
  const references =
    Array.isArray(source.image_urls) && source.image_urls.length <= MAX_PHOTOS
      ? source.image_urls
      : [];
  const photos = [...new Set(references.map(readPhotoReference).filter((url) => url !== null))];
  const image_urls = main === null || photos[0] === main ? photos : [main];
  return { image_url: image_urls[0] ?? null, image_urls };
}

export function carWithoutGallery(car: Car & { image_urls?: unknown }): Car {
  const result = { ...car, image_url: readPhotoReference(car.image_url) };
  delete result.image_urls;
  return result;
}

export function imageSrc(value: string | null) {
  const reference = readPhotoReference(value);
  return reference?.startsWith('/') ? import.meta.env.BASE_URL + reference.slice(1) : reference;
}

export function vehicleGalleryImages(
  car: Pick<Car | Listing, 'brand' | 'model' | 'image_url'> & {
    image_urls?: string[];
  },
): VehicleImage[] {
  const name = [car.brand, car.model].filter(Boolean).join(' ') || 'Автомобиль';
  const { image_urls } = readGallery(car);
  return image_urls.map((url, index) => ({ url, alt: `${name}, фото ${index + 1}` }));
}
