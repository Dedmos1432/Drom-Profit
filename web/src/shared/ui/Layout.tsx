import { useId } from 'react';
import type { ComponentPropsWithRef, ReactNode } from 'react';
import { classes } from '@/shared/lib/classes';
import styles from './Layout.module.css';

type DivProps = ComponentPropsWithRef<'div'>;
type Gap = 'tight' | 'normal' | 'roomy';
type FlowProps = DivProps & { gap?: Gap };

export function Container({
  narrow = false,
  className,
  ...props
}: DivProps & { narrow?: boolean }) {
  return (
    <div {...props} className={classes(styles.container, narrow && styles.narrow, className)} />
  );
}

export function Stack({ gap = 'normal', className, ...props }: FlowProps) {
  return <div {...props} className={classes(styles.stack, styles[gap], className)} />;
}

export function Inline({ gap = 'normal', className, ...props }: FlowProps) {
  return <div {...props} className={classes(styles.inline, styles[gap], className)} />;
}

export function Panel({ className, large = false, ...props }: DivProps & { large?: boolean }) {
  return (
    <div {...props} className={classes(styles.panel, large && styles.largePanel, className)} />
  );
}

type SectionProps = Omit<ComponentPropsWithRef<'section'>, 'title'> & {
  title: string;
  actions?: ReactNode;
  description?: ReactNode;
};

export function Section({
  title,
  actions,
  description,
  children,
  className,
  ...props
}: SectionProps) {
  const id = useId();

  return (
    <section
      {...props}
      aria-labelledby={props['aria-labelledby'] ?? id}
      className={classes(styles.section, className)}
    >
      <div className={styles.heading}>
        <div>
          <h2 id={id}>{title}</h2>
          {description && <div className={styles.description}>{description}</div>}
        </div>
        {actions && <div className={styles.actions}>{actions}</div>}
      </div>
      {children}
    </section>
  );
}

type PageHeaderProps = Omit<ComponentPropsWithRef<'header'>, 'title'> & {
  title: string;
  description?: ReactNode;
  breadcrumbs?: ReactNode;
  actions?: ReactNode;
};

export function PageHeader({
  title,
  description,
  breadcrumbs,
  actions,
  className,
  ...props
}: PageHeaderProps) {
  return (
    <header {...props} className={classes(styles.pageHeader, className)}>
      {breadcrumbs}
      <div className={styles.heading}>
        <div>
          <h1>{title}</h1>
          {description && <div className={styles.description}>{description}</div>}
        </div>
        {actions && <div className={styles.actions}>{actions}</div>}
      </div>
    </header>
  );
}
