import { ImageOff } from 'lucide-react';
import { cloneElement, useEffect, useId, useRef, useState } from 'react';
import type { AriaAttributes, ComponentPropsWithRef, ReactElement, ReactNode } from 'react';
import { classes } from '@/shared/lib/classes';
import styles from './Data.module.css';

const rubles = new Intl.NumberFormat('ru-RU', {
  style: 'currency',
  currency: 'RUB',
  maximumFractionDigits: 0,
});

type PriceProps = Omit<ComponentPropsWithRef<'span'>, 'children'> & {
  value: number | null;
  size?: 'compact' | 'regular' | 'display';
  unknownLabel?: string;
};

export function Price({
  value,
  size = 'regular',
  unknownLabel = 'Не указано',
  className,
  ...props
}: PriceProps) {
  const known = value !== null && Number.isSafeInteger(value);
  return (
    <span
      {...props}
      className={classes(styles.price, styles[size], !known && styles.unknown, className)}
    >
      {known ? rubles.format(value) : unknownLabel}
    </span>
  );
}

type Specification = { label: string; value: ReactNode };

export function SpecificationList({
  items,
  className,
  ...props
}: ComponentPropsWithRef<'dl'> & { items: readonly Specification[] }) {
  return (
    <dl {...props} className={classes(styles.specifications, className)}>
      {items.map(({ label, value }, index) => (
        <div className={styles.specification} key={index}>
          <dt>{label}</dt>
          <dd>{value ?? 'Нет данных'}</dd>
        </div>
      ))}
    </dl>
  );
}

type TableProps = ComponentPropsWithRef<'table'> & { caption: string; wide?: boolean };

export function Table({ caption, children, wide = false, className, ...props }: TableProps) {
  const captionId = useId();
  const scrollRef = useRef<HTMLDivElement>(null);
  const [overflowing, setOverflowing] = useState(false);

  useEffect(() => {
    const scroller = scrollRef.current;
    const table = scroller?.querySelector('table');
    if (!scroller || !table) return;

    function measure() {
      if (scroller) setOverflowing(scroller.scrollWidth > scroller.clientWidth);
    }
    measure();

    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(scroller);
    observer.observe(table);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={scrollRef}
      className={styles.tableScroll}
      role={overflowing ? 'region' : undefined}
      aria-labelledby={overflowing ? captionId : undefined}
      tabIndex={overflowing ? 0 : undefined}
    >
      <table {...props} className={classes(styles.table, wide && styles.wide, className)}>
        <caption id={captionId}>{caption}</caption>
        {children}
      </table>
    </div>
  );
}

type TooltipProps = {
  text: string;
  children: ReactElement<AriaAttributes>;
  align?: 'start' | 'end';
};

export function Tooltip({ text, children, align = 'start' }: TooltipProps) {
  const id = useId();
  const [focused, setFocused] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const visible = (focused || hovered) && !dismissed;
  const description = [children.props['aria-describedby'], id].filter(Boolean).join(' ');

  useEffect(() => {
    if (!visible) return;
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setDismissed(true);
    }
    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [visible]);

  return (
    <span
      className={styles.tooltipAnchor}
      onFocus={() => {
        setFocused(true);
        setDismissed(false);
      }}
      onBlur={() => setFocused(false)}
      onPointerEnter={(event) => {
        if (event.pointerType === 'touch') return;
        setHovered(true);
        setDismissed(false);
      }}
      onPointerLeave={() => setHovered(false)}
    >
      {cloneElement(children, { 'aria-describedby': description })}
      <span
        className={classes(styles.tooltip, align === 'end' && styles.tooltipEnd)}
        id={id}
        role="tooltip"
        hidden={!visible}
      >
        {text}
      </span>
    </span>
  );
}

export function ImagePlaceholder({
  label = 'Нет фотографии',
  className,
  ...props
}: ComponentPropsWithRef<'div'> & { label?: string }) {
  return (
    <div {...props} className={classes(styles.placeholder, className)}>
      <ImageOff aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
