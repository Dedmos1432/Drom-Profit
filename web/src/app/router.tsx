import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { AppLayout } from './layouts/AppLayout';
import { SessionBoundary } from '@/entities/session/SessionBoundary';
import { AddCarPage } from '@/pages/add-car/AddCarPage';
import { CarDetailsPage } from '@/pages/car-details/CarDetailsPage';
import { ComparisonPage } from '@/pages/comparison/ComparisonPage';
import { NotFoundPage } from '@/pages/not-found/NotFoundPage';
import { SelectionPage } from '@/pages/selection/SelectionPage';
import type { AppConfig } from '@/shared/config/env';
import { Spinner } from '@/shared/ui';

const UiKitPage = import.meta.env.DEV ? lazy(() => import('@/pages/ui-kit/UiKitPage')) : null;

export function AppRoutes({ config }: { config: AppConfig }) {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route element={<SessionBoundary config={config} />}>
          <Route index element={<SelectionPage />} />
          <Route path="cars/new" element={<AddCarPage />} />
          <Route path="cars/:carId" element={<CarDetailsPage />} />
          <Route path="comparison" element={<ComparisonPage />} />
        </Route>
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
