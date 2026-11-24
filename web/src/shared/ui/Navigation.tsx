import { ChevronRight } from 'lucide-react';
import { useId, useState } from 'react';
import type { KeyboardEvent, ReactNode } from 'react';
import { classes } from '@/shared/lib/classes';
import styles from './Navigation.module.css';

type Crumb = { label: string; link?: ReactNode };

export function Breadcrumbs({ items }: { items: readonly Crumb[] }) {
  return (
    <nav aria-label="Хлебные крошки">
      <ol className={styles.crumbs}>
        {items.map(({ label, link }, index) => (
          <li className={styles.crumb} key={index}>
            {index > 0 && <ChevronRight aria-hidden="true" />}
            {index === items.length - 1 ? (
              <span aria-current="page">{label}</span>
            ) : (
              (link ?? label)
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}

type Tab = { id: string; label: string; content: ReactNode; disabled?: boolean };

type TabsProps = {
  items: readonly Tab[];
  label: string;
  defaultValue?: string;
  className?: string;
};

export function Tabs({ items, label, defaultValue, className }: TabsProps) {
  const baseId = useId();
  const [value, setValue] = useState(defaultValue);
  const enabled = items.filter((item) => !item.disabled);
  const selected = enabled.find((item) => item.id === value) ?? enabled[0];
  const idFor = (kind: 'tab' | 'panel', id: string) => baseId + '-' + kind + '-' + id;

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, tab: Tab) {
    const key = event.key;
    if (key !== 'ArrowLeft' && key !== 'ArrowRight' && key !== 'Home' && key !== 'End') return;
    const index = enabled.indexOf(tab);
    const target = {
      ArrowLeft: enabled.at(index - 1),
      ArrowRight: enabled[(index + 1) % enabled.length],
      Home: enabled[0],
      End: enabled.at(-1),
    }[key];
    if (!target) return;

    event.preventDefault();
    setValue(target.id);
    event.currentTarget.ownerDocument.getElementById(idFor('tab', target.id))?.focus();
  }

  return (
    <div className={classes(styles.tabs, className)}>
      <div className={styles.tabList} role="tablist" aria-label={label}>
        {items.map((item) => (
          <button
            aria-controls={idFor('panel', item.id)}
            aria-selected={item.id === selected?.id}
            className={styles.tab}
            disabled={item.disabled}
            id={idFor('tab', item.id)}
            key={item.id}
            onClick={() => setValue(item.id)}
            onKeyDown={(event) => onKeyDown(event, item)}
            role="tab"
            tabIndex={item.id === selected?.id ? 0 : -1}
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>
      {items.map((item) => (
        <div
          aria-labelledby={idFor('tab', item.id)}
          className={styles.tabPanel}
          hidden={item.id !== selected?.id}
          id={idFor('panel', item.id)}
          key={item.id}
          role="tabpanel"
          tabIndex={0}
        >
          {item.content}
        </div>
      ))}
    </div>
  );
}
