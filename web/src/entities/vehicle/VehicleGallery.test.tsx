import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { VehicleGallery } from './VehicleGallery';

const front = { url: 'https://images.test/front.jpg', alt: 'Toyota Camry спереди' };
const rear = { url: 'https://images.test/rear.jpg', alt: 'Toyota Camry сзади' };

it('switches the main photo from the keyboard and reflects which thumbnail is selected', async () => {
  const user = userEvent.setup();
  render(<VehicleGallery images={[front, rear]} />);

  expect(screen.getByRole('img', { name: front.alt })).toHaveAttribute('src', front.url);
  expect(screen.getByRole('button', { name: front.alt })).toHaveAttribute('aria-pressed', 'true');

  await user.tab();
  await user.tab();
  await user.keyboard('{Enter}');
  expect(screen.getByRole('img', { name: rear.alt })).toHaveAttribute('src', rear.url);
  expect(screen.getByRole('button', { name: rear.alt })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('button', { name: front.alt })).toHaveAttribute('aria-pressed', 'false');
});

it('uses another photo when one fails, and a placeholder when none are available', () => {
  const { rerender } = render(<VehicleGallery images={[]} />);
  expect(screen.getByText('Нет фотографии')).toBeInTheDocument();

  rerender(<VehicleGallery images={[front, rear]} />);
  fireEvent.error(screen.getByRole('img', { name: front.alt }));
  expect(screen.getByRole('img', { name: rear.alt })).toBeInTheDocument();
  expect(screen.queryByRole('group', { name: 'Фотографии автомобиля' })).not.toBeInTheDocument();

  fireEvent.error(screen.getByRole('img', { name: rear.alt }));
  expect(screen.getByText('Нет фотографии')).toBeInTheDocument();
});
