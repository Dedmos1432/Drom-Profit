import type { ComponentPropsWithRef } from 'react';
import { classes } from '@/shared/lib/classes';
import styles from './Primitives.module.css';

export function Link({ className, ...props }: ComponentPropsWithRef<'a'>) {
  return <a className={classes(styles.link, className)} {...props} />;
}

export type StatusTone = 'info' | 'success' | 'warning' | 'error';

type BadgeProps = ComponentPropsWithRef<'span'> & { tone?: StatusTone | 'neutral' };

export function Badge({ tone = 'neutral', className, ...props }: BadgeProps) {
  return <span {...props} className={classes(styles.badge, styles[tone], className)} />;
}

export function Divider({ className, ...props }: ComponentPropsWithRef<'hr'>) {
  return <hr {...props} className={classes(styles.divider, className)} />;
}
