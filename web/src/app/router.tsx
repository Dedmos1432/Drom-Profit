import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { AppLayout } from './layouts/AppLayout';
import {
  AddCarPage,
  CarDetailsPage,
  ComparisonPage,
  NotFoundPage,
  SelectionPage,
} from '@/pages/product/ProductPages';
import { Spinner } from '@/shared/ui';

const UiKitPage = import.meta.env.DEV ? lazy(() => import('@/pages/ui-kit/UiKitPage')) : null;

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<SelectionPage />} />
        <Route path="cars/new" element={<AddCarPage />} />
        <Route path="cars/:carId" element={<CarDetailsPage />} />
        <Route path="comparison" element={<ComparisonPage />} />
        {UiKitPage && (
          <Route
            path="ui-kit"
            element={
              <Suspense fallback={<Spinner />}>
                <UiKitPage />
              </Suspense>
            }
          />
        )}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
