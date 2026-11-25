import type { Car } from './api';
import { safeWebUrl, vehicleName } from './presentation';
import type { VehicleImage } from './VehicleGallery';

export const showcasePhotos = {
  '101': [
    ['camry-xv50-silver.jpg', 'серебристый автомобиль спереди'],
    ['camry-xv50-white.jpg', 'белый автомобиль спереди'],
    ['camry-xv50-engine.jpg', 'моторный отсек'],
  ],
  '102': [
    ['accord-ix.jpg', 'вид сзади'],
    ['accord-ix-front.jpg', 'вид спереди'],
    ['accord-ix-side.jpg', 'вид сбоку'],
  ],
  '103': [
    ['mazda6-gj.jpg', 'красный автомобиль спереди'],
    ['mazda6-gj-rear.jpg', 'красный автомобиль сзади'],
    ['mazda6-gj-white.jpg', 'белый автомобиль спереди'],
  ],
  '104': [
    ['x-trail-t32.jpg', 'вид спереди'],
    ['x-trail-t32-rear.jpg', 'вид сзади'],
    ['x-trail-t32-interior.jpg', 'салон'],
  ],
  '105': [
    ['rav4-xa40.jpg', 'серебристый автомобиль спереди'],
    ['rav4-xa40-rear.jpg', 'белый автомобиль сзади'],
    ['rav4-xa40-interior.jpg', 'салон'],
  ],
} as const;

export function demoPhotoUrl(file: string, baseUrl: string) {
  const assetBase = new URL(import.meta.env.BASE_URL, globalThis.location?.origin ?? baseUrl);
  return new URL('demo/vehicles/' + file, assetBase).href;
}

export function vehicleGalleryImages(car: Car, mockBaseUrl?: string): VehicleImage[] {
  const main = safeWebUrl(car.image_url);
  if (!main) return [];

  const name = vehicleName(car);
  const id = car.listing_id;
  const photos = id && Object.hasOwn(showcasePhotos, id)
    ? showcasePhotos[id as keyof typeof showcasePhotos]
    : undefined;
  if (
    mockBaseUrl &&
    car.source === 'demo' &&
    photos &&
    main === demoPhotoUrl(photos[0][0], mockBaseUrl)
  ) {
    return photos.map(([file, view]) => ({
      url: demoPhotoUrl(file, mockBaseUrl),
      alt: `${name}, ${view}`,
    }));
  }

  return [{ url: main, alt: name }];
}
