import type { Car, Listing } from '@/entities/vehicle/api';
import type { Benefit, Expense } from '@/entities/vehicle/records';
import { demoPhotoUrl, showcasePhotos } from '@/entities/vehicle/showcase-photos';

type ShowcaseVehicle = {
  listingId: keyof typeof showcasePhotos;
  specs: Pick<
    Car,
    | 'brand'
    | 'model'
    | 'year'
    | 'price'
    | 'mileage_km'
    | 'fuel_type'
    | 'engine_volume_l'
    | 'power_hp'
    | 'transmission'
    | 'drive_type'
    | 'trim'
    | 'city'
  >;
  assessment: Pick<
    Car,
    'expenses' | 'benefits' | 'notes' | 'expenses_reviewed' | 'benefits_reviewed'
  >;
};

function expense(id: string, kind: Expense['kind'], title: string, amount: number | null): Expense {
  return { id, kind, title, amount, comment: null, factor_key: null };
}

function benefit(id: string, title: string, amount: number): Benefit {
  return { id, title, amount, comment: null, factor_key: null };
}

const showcase: readonly ShowcaseVehicle[] = [
  {
    listingId: '101',
    specs: {
      brand: 'Toyota',
      model: 'Camry',
      year: 2015,
      price: 1_200_000,
      mileage_km: 145_000,
      fuel_type: 'Бензин',
      engine_volume_l: 2.5,
      power_hp: 181,
      transmission: 'Автомат',
      drive_type: 'Передний',
      trim: 'Элеганс',
      city: 'Владивосток',
    },
    assessment: {
      expenses: [
        expense('brake-pads', 'repair', 'Замена передних колодок', 18_000),
        expense('service', 'maintenance', 'Замена масла и фильтров', 15_000),
      ],
      benefits: [benefit('tires', 'Комплект зимних шин', 25_000)],
      notes: [
        {
          id: 'road-test',
          kind: 'inspection',
          text: 'Проверить работу кондиционера на пробной поездке.',
        },
      ],
      expenses_reviewed: true,
      benefits_reviewed: true,
    },
  },
  {
    listingId: '102',
    specs: {
      brand: 'Honda',
      model: 'Accord',
      year: 2016,
      price: 1_350_000,
      mileage_km: 130_000,
      fuel_type: 'Бензин',
      engine_volume_l: 3.5,
      power_hp: 278,
      transmission: 'Автомат',
      drive_type: 'Передний',
      trim: 'Touring',
      city: 'Хабаровск',
    },
    assessment: {
      expenses: [
        expense('suspension', 'repair', 'Устранение стука в подвеске', null),
        expense('transmission-service', 'maintenance', 'Замена масла в коробке передач', 17_000),
      ],
      benefits: [benefit('camera', 'Камера заднего вида', 12_000)],
      notes: [
        {
          id: 'quote',
          kind: 'inspection',
          text: 'Получить смету ремонта подвески после диагностики.',
        },
      ],
      expenses_reviewed: true,
      benefits_reviewed: true,
    },
  },
  {
    listingId: '103',
    specs: {
      brand: 'Mazda',
      model: '6',
      year: 2017,
      price: 1_450_000,
      mileage_km: 112_000,
      fuel_type: 'Бензин',
      engine_volume_l: 2,
      power_hp: 150,
      transmission: 'Автомат',
      drive_type: 'Передний',
      trim: 'Active',
      city: 'Красноярск',
    },
    assessment: {
      expenses: [expense('alignment', 'maintenance', 'Проверка и регулировка сход-развала', 5_000)],
      benefits: [benefit('parking', 'Передние и задние парктроники', 15_000)],
      notes: [
        {
          id: 'scratch',
          kind: 'accepted_defect',
          text: 'Небольшая царапина на заднем бампере, ремонт не планируется.',
        },
      ],
      expenses_reviewed: true,
      benefits_reviewed: true,
    },
  },
  {
    listingId: '104',
    specs: {
      brand: 'Nissan',
      model: 'X-Trail',
      year: 2015,
      price: 1_550_000,
      mileage_km: 160_000,
      fuel_type: 'Бензин',
      engine_volume_l: 2,
      power_hp: 144,
      transmission: 'Вариатор',
      drive_type: 'Полный',
      trim: 'SE',
      city: 'Иркутск',
    },
    assessment: {
      expenses: [
        expense('battery', 'maintenance', 'Новый аккумулятор', 12_000),
        expense('undertray', 'missing_equipment', 'Защита двигателя', 9_000),
      ],
      benefits: [benefit('remote-start', 'Дистанционный запуск двигателя', 10_000)],
      notes: [
        { id: 'keys', kind: 'seller', text: 'Продавец обещал привезти второй ключ к осмотру.' },
      ],
      expenses_reviewed: true,
      benefits_reviewed: false,
    },
  },
  {
    listingId: '105',
    specs: {
      brand: 'Toyota',
      model: 'RAV4',
      year: 2016,
      price: 1_850_000,
      mileage_km: 98_000,
      fuel_type: 'Бензин',
      engine_volume_l: 2,
      power_hp: 146,
      transmission: 'Вариатор',
      drive_type: 'Полный',
      trim: 'Комфорт',
      city: 'Новосибирск',
    },
    assessment: {
      expenses: [
        expense('coolant', 'maintenance', 'Плановая замена охлаждающей жидкости', 13_000),
        expense('registration', 'other', 'Постановка на учёт и комплект номеров', 4_000),
      ],
      benefits: [benefit('extra-wheels', 'Дополнительный комплект дисков', 28_000)],
      notes: [
        {
          id: 'maintenance-record',
          kind: 'report',
          text: 'Записаны даты последних плановых обслуживаний.',
        },
      ],
      expenses_reviewed: true,
      benefits_reviewed: true,
    },
  },
];

const listingUrl = (id: string) => 'https://auto.drom.ru/demo/' + id + '.html';

function listingData({ listingId, specs }: ShowcaseVehicle, baseUrl: string) {
  return {
    ...specs,
    source: 'demo',
    listing_id: listingId,
    listing_url: listingUrl(listingId),
    image_url: demoPhotoUrl(showcasePhotos[listingId][0][0], baseUrl),
    market_valuation: null,
    fetched_at: null,
  } satisfies Listing;
}

export function findShowcaseListing(url: string, baseUrl: string): Listing | undefined {
  const vehicle = showcase.find(({ listingId }) => listingUrl(listingId) === url);
  return vehicle && listingData(vehicle, baseUrl);
}

export function showcaseCars(baseUrl: string): Car[] {
  return showcase.map((vehicle) => ({
    ...listingData(vehicle, baseUrl),
    ...vehicle.assessment,
    id: 'showcase-' + vehicle.listingId,
  }));
}
