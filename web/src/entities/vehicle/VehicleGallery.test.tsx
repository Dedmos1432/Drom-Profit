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

  screen.getByRole('button', { name: front.alt }).focus();
  await user.tab();
  await user.keyboard('{Enter}');
  expect(screen.getByRole('img', { name: rear.alt })).toHaveAttribute('src', rear.url);
  expect(screen.getByRole('button', { name: rear.alt })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('button', { name: front.alt })).toHaveAttribute('aria-pressed', 'false');
});

it('navigates a long gallery and selects the first photo when the old selection disappears', async () => {
  const user = userEvent.setup();
  const images = Array.from({ length: 28 }, (_, index) => ({
    url: `https://images.test/${index}.jpg`,
    alt: `Фото ${index + 1}`,
  }));
  const { rerender } = render(<VehicleGallery images={images} />);
  await user.click(screen.getByRole('button', { name: 'Предыдущее фото' }));
  expect(screen.getByRole('img', { name: 'Фото 28' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Следующее фото' }));
  const firstThumbnail = screen.getByRole('button', { name: 'Фото 1' });
  expect(firstThumbnail).toHaveAttribute('aria-pressed', 'true');
  firstThumbnail.focus();
  await user.keyboard('{ArrowRight}');
  expect(screen.getByRole('img', { name: 'Фото 2' })).toBeInTheDocument();

  rerender(<VehicleGallery images={[rear]} />);
  expect(screen.getByRole('img', { name: rear.alt })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Следующее фото' })).not.toBeInTheDocument();
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
