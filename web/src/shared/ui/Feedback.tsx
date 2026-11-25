import { AlertTriangle, CheckCircle2, CircleAlert, Info, LoaderCircle } from 'lucide-react';
import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { classes } from '@/shared/lib/classes';
import type { StatusTone } from './Primitives';
import styles from './Feedback.module.css';

const statusIcons = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  error: CircleAlert,
};

type AlertProps = Omit<ComponentPropsWithoutRef<'div'>, 'title'> & {
  tone?: StatusTone;
  title: string;
};

export function Alert({ tone = 'info', title, className, children, ...props }: AlertProps) {
  const Icon = statusIcons[tone];

  return (
    <div {...props} className={classes(styles.alert, styles[tone], className)}>
      <Icon aria-hidden="true" />
      <div>
        <strong>{title}</strong>
        {children && <div className={styles.alertDescription}>{children}</div>}
      </div>
    </div>
  );
}

type StateProps = {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
};

export function EmptyState({ title, description, action, className }: StateProps) {
  return (
    <div className={classes(styles.empty, className)}>
      <strong>{title}</strong>
      {description && <div className={styles.emptyDescription}>{description}</div>}
      {action && <div>{action}</div>}
    </div>
  );
}

export function ErrorState({ title, description, action, className }: StateProps) {
  return (
    <div className={classes(styles.errorState, className)}>
      <Alert tone="error" role="alert" title={title}>
        {description}
      </Alert>
      {action && <div>{action}</div>}
    </div>
  );
}

export function Skeleton({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={classes(styles.skeleton, className)} aria-hidden="true">
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} />
      ))}
    </div>
  );
}

export function Spinner({
  label = 'Загрузка',
  size = 'normal',
}: {
  label?: string;
  size?: 'small' | 'normal';
}) {
  return (
    <span
      className={classes(styles.spinner, size === 'small' && styles.spinnerSmall)}
      role="status"
    >
      <LoaderCircle aria-hidden="true" />
      {label}
    </span>
  );
}
