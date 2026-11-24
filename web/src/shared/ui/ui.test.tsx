import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Button, Checkbox, ConfirmDialog, Input, Modal, Price } from './index';

describe('Button', () => {
  it('prevents clicks when disabled or loading and announces loading', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    const { rerender } = render(
      <Button disabled onClick={onClick}>
        Сохранить
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Сохранить' });
    expect(button).toBeDisabled();
    await user.click(button);

    rerender(
      <Button loading onClick={onClick}>
        Сохранить
      </Button>,
    );
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    await user.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('Fields', () => {
  it('associates Input errors and help with the labelled input', () => {
    render(
      <Input label="Цена" error="Введите целое число." helperText="Сумма в рублях." required />,
    );
    const input = screen.getByRole('textbox', { name: 'Цена' });
    expect(input).toBeInvalid();
    expect(input).toBeRequired();
    expect(input).toHaveAccessibleDescription('Сумма в рублях. Введите целое число.');
  });

  it('changes Checkbox state with the keyboard and calls onChange', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Checkbox label="Сравнить" onChange={onChange} />);
    const checkbox = screen.getByRole('checkbox', { name: 'Сравнить' });
    await user.tab();
    expect(checkbox).toHaveFocus();
    await user.keyboard(' ');
    expect(checkbox).toBeChecked();
    expect(onChange).toHaveBeenCalledOnce();
  });
});

describe('Price', () => {
  it.each([
    [1250000, '1\u00a0250\u00a0000\u00a0₽'],
    [0, '0\u00a0₽'],
    [null, 'Не указано'],
    [-20000, '-20\u00a0000\u00a0₽'],
  ])('formats %s as %s', (value, text) => {
    const { container } = render(<Price value={value} />);
    expect(container.textContent).toBe(text);
  });
});

function ModalExample() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Открыть</Button>
      <Modal open={open} onClose={() => setOpen(false)} title="Проверка" description="Подробности">
        <Button onClick={() => setOpen(false)}>Готово</Button>
      </Modal>
    </>
  );
}

describe('Modal', () => {
  it('names the dialog, moves focus inside, dismisses and restores the opener', async () => {
    const user = userEvent.setup();
    render(<ModalExample />);
    const opener = screen.getByRole('button', { name: 'Открыть' });
    await user.click(opener);

    const dialog = screen.getByRole('dialog', { name: 'Проверка' });
    expect(dialog).toHaveAccessibleDescription('Подробности');
    expect(within(dialog).getByRole('heading')).toHaveFocus();

    fireEvent(dialog, new Event('cancel', { cancelable: true }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(opener).toHaveFocus();

    await user.click(opener);
    await user.click(screen.getByRole('button', { name: 'Готово' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });

  it('starts confirmation on Cancel and prevents duplicate submission', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn(() => new Promise<void>(() => {}));
    render(
      <ConfirmDialog
        open
        title="Подтверждение"
        confirmLabel="Удалить"
        onClose={() => {}}
        onConfirm={onConfirm}
      />,
    );
    expect(screen.getByRole('button', { name: 'Отмена' })).toHaveFocus();
    const confirm = screen.getByRole('button', { name: 'Удалить' });
    await user.click(confirm);
    expect(confirm).toBeDisabled();
    await user.click(confirm);
    expect(onConfirm).toHaveBeenCalledOnce();
  });
});
