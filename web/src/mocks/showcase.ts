import showcaseListings from '../../../shared/fixtures/demo-showcase.json';
import type { Car, CarDetail, Listing } from '@/entities/vehicle/api';
import type { Benefit, Expense } from '@/entities/vehicle/records';

type ShowcaseVehicle = {
  listingId: string;
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

const listings = showcaseListings.map((listing) => ({
  ...listing,
  source: 'demo',
})) satisfies Listing[];

export function findShowcaseListing(url: string): Listing | undefined {
  return listings.find(({ listing_url }) => listing_url === url);
}

export function showcaseCars(): CarDetail[] {
  return listings.map((listing) => {
    const vehicle = showcase.find(({ listingId }) => listingId === listing.listing_id);
    if (!vehicle) throw new Error('Missing Mock Showcase assessment');
    return { ...listing, ...vehicle.assessment, id: 'showcase-' + vehicle.listingId };
  });
}
