import { X } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import type { ReactNode, RefObject } from 'react';
import { Button, IconButton } from './Button';
import type { ButtonVariant } from './Button';
import { Alert } from './Feedback';
import { Inline } from './Layout';
import styles from './Modal.module.css';

type ModalProps = {
  title: string;
  description?: ReactNode;
  open: boolean;
  onClose: () => void;
  children?: ReactNode;
  initialFocus?: RefObject<HTMLElement | null>;
  closeDisabled?: boolean;
};

export function Modal({
  open,
  title,
  description,
  onClose,
  initialFocus,
  closeDisabled = false,
  children,
}: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!open || !dialog) return;

    const opener = dialog.ownerDocument.activeElement;
    dialog.showModal();
    (initialFocus?.current ?? titleRef.current)?.focus();

    return () => {
      if (dialog.open) dialog.close();
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus();
    };
  }, [open, initialFocus]);

  if (!open) return null;

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault();
        if (!closeDisabled) onClose();
      }}
    >
      <div className={styles.content}>
        <div className={styles.heading}>
          <h2 id={titleId} ref={titleRef} tabIndex={-1}>
            {title}
          </h2>
          <IconButton
            icon={<X />}
            label="Закрыть диалог"
            disabled={closeDisabled}
            onClick={onClose}
          />
        </div>
        {description && <div id={descriptionId}>{description}</div>}
        {children}
      </div>
    </dialog>
  );
}

type ConfirmDialogProps = Pick<ModalProps, 'title' | 'description' | 'open' | 'onClose'> & {
  confirmLabel: string;
  confirmVariant?: Extract<ButtonVariant, 'primary' | 'danger'>;
  onConfirm: () => void | Promise<void>;
};

export function ConfirmDialog({
  confirmLabel,
  confirmVariant = 'danger',
  open,
  onClose,
  onConfirm,
  title,
  description,
}: ConfirmDialogProps) {
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);
  const inFlight = useRef(false);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const pendingStatusRef = useRef<HTMLSpanElement>(null);

  function dismiss() {
    if (inFlight.current) return;
    setFailed(false);
    onClose();
  }

  async function confirm() {
    if (inFlight.current) return;
    inFlight.current = true;
    setFailed(false);
    setPending(true);

    try {
      await onConfirm();
      onClose();
    } catch {
      setFailed(true);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  useEffect(() => {
    if (pending) pendingStatusRef.current?.focus();
  }, [pending]);

  return (
    <Modal
      open={open}
      title={title}
      description={description}
      onClose={dismiss}
      initialFocus={cancelRef}
      closeDisabled={pending}
    >
      {pending && (
        <span ref={pendingStatusRef} role="status" tabIndex={-1}>
          Выполняем действие…
        </span>
      )}
      {failed && (
        <Alert tone="error" role="alert" title="Действие не выполнено">
          Попробуйте ещё раз.
        </Alert>
      )}
      <Inline gap="tight">
        <Button ref={cancelRef} variant="secondary" disabled={pending} onClick={dismiss}>
          Отмена
        </Button>
        <Button variant={confirmVariant} loading={pending} onClick={() => void confirm()}>
          {confirmLabel}
        </Button>
      </Inline>
    </Modal>
  );
}
