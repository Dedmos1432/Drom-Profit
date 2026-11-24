import { createContext, useContext, useRef, useState } from 'react';
import { Outlet } from 'react-router';
import { useSession } from '@/entities/session/SessionBoundary';

function readIds(key: string) {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key) ?? '[]');
    if (Array.isArray(value)) {
      return [...new Set(value.filter((id): id is string => typeof id === 'string'))];
    }
  } catch {
    // A malformed local preference can be replaced by a new selection.
  }
  return [];
}

type Selection = {
  ids: readonly string[];
  select: (id: string, checked: boolean) => void;
  clear: () => void;
  retain: (ids: readonly string[]) => void;
};

const SelectionContext = createContext<Selection | null>(null);

function StoredSelection({ storageKey }: { storageKey: string }) {
  const [ids, setIds] = useState(() => readIds(storageKey));
  const latest = useRef(ids);

  function update(next: string[]) {
    latest.current = next;
    setIds(next);
    try {
      if (next.length) localStorage.setItem(storageKey, JSON.stringify(next));
      else localStorage.removeItem(storageKey);
    } catch {
      // The selection remains available while the application is open.
    }
  }

  function select(id: string, checked: boolean) {
    const next = new Set(latest.current);
    if (checked) next.add(id);
    else next.delete(id);
    update([...next]);
  }

  return (
    <SelectionContext.Provider
      value={{
        ids,
        select,
        clear: () => update([]),
        retain: (available) => update(latest.current.filter((id) => available.includes(id))),
      }}
    >
      <Outlet />
    </SelectionContext.Provider>
  );
}

export function ComparisonSelectionBoundary() {
  const { selectionKey } = useSession();
  return <StoredSelection key={selectionKey} storageKey={selectionKey} />;
}

export function useComparisonSelection() {
  const selection = useContext(SelectionContext);
  if (!selection) throw new Error('ComparisonSelectionBoundary is missing');
  return selection;
}
