import { LoaderCircle } from 'lucide-react';
import type { ComponentPropsWithRef, ReactNode } from 'react';
import { classes } from '@/shared/lib/classes';
import styles from './Button.module.css';

export type ButtonVariant = 'primary' | 'secondary' | 'contrast' | 'outline' | 'ghost' | 'danger';
export type ButtonSize = 'standard' | 'large';

export type ButtonProps = ComponentPropsWithRef<'button'> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
  icon?: ReactNode;
};

export function Button({
  variant = 'primary',
  size = 'standard',
  loading = false,
  fullWidth = false,
  icon,
  children,
  className,
  disabled,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      className={classes(
        styles.button,
        styles[variant],
        styles[size],
        fullWidth && styles.full,
        className,
      )}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || props['aria-busy']}
    >
      {loading || icon ? (
        <span className={styles.icon} aria-hidden="true">
          {loading ? <LoaderCircle className={styles.loading} /> : icon}
        </span>
      ) : null}
      {children}
    </button>
  );
}

type IconButtonProps = Omit<ButtonProps, 'children' | 'icon' | 'fullWidth'> & {
  label: string;
  icon: ReactNode;
};

export function IconButton({
  label,
  icon,
  variant = 'ghost',
  className,
  ...props
}: IconButtonProps) {
  return (
    <Button
      {...props}
      className={classes(styles.iconButton, className)}
      variant={variant}
      icon={icon}
      aria-label={label}
    />
  );
}
