import { useState } from 'react';
import type { Car } from '@/entities/vehicle/api';
import { noteLabels } from '@/entities/vehicle/records';
import type { Note, NoteInput } from '@/entities/vehicle/records';
import { NoteDialog } from '@/features/manage-vehicle-records/NoteDialog';
import {
  RecordActions,
  recordError,
  useRecordMutation,
} from '@/features/manage-vehicle-records/record-actions';
import { changedFields } from '@/shared/lib/changed-fields';
import { Button, ConfirmDialog, Stack } from '@/shared/ui';
import styles from './Workspace.module.css';

export function NotesSection({ car }: { car: Car }) {
  const [editing, setEditing] = useState<{ item: Note | null } | null>(null);
  const [removing, setRemoving] = useState<Note | null>(null);
  const change = useRecordMutation(car.id, 'notes');

  async function save(body: NoteInput) {
    try {
      if (editing?.item) {
        const patch = changedFields(editing.item, body);
        if (Object.keys(patch).length)
          await change.mutateAsync({ action: 'edit', itemId: editing.item.id, body: patch });
      } else {
        await change.mutateAsync({ action: 'create', body });
      }
      setEditing(null);
    } catch {
      // A failed request leaves the form open with the user's text.
    }
  }

  function open(item: Note | null) {
    change.reset();
    setEditing({ item });
  }

  return (
    <section id="notes" tabIndex={-1} aria-labelledby="notes-title" className={styles.section}>
      <Stack gap="tight">
        <div className={styles.sectionHeader}>
          <h2 id="notes-title">Заметки об автомобиле</h2>
          <Button variant="outline" onClick={() => open(null)}>
            Добавить заметку
          </Button>
        </div>
        {car.notes.length === 0 ? (
          <p className={styles.muted}>Заметок пока нет.</p>
        ) : (
          <ul className={styles.rows}>
            {car.notes.map((note) => (
              <li className={styles.row} key={note.id}>
                <div className={styles.rowBody}>
                  <strong>{noteLabels[note.kind]}</strong>
                  <p className={styles.entryText}>{note.text}</p>
                </div>
                <div className={styles.rowActions}>
                  <RecordActions onEdit={() => open(note)} onDelete={() => setRemoving(note)} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </Stack>
      {editing && (
        <NoteDialog
          item={editing.item}
          saving={change.isPending}
          error={change.isError ? recordError(change.error) : undefined}
          onClose={() => setEditing(null)}
          onSave={save}
        />
      )}
      {removing && (
        <ConfirmDialog
          open
          title="Удалить заметку?"
          description={noteLabels[removing.kind]}
          confirmLabel="Удалить"
          onClose={() => setRemoving(null)}
          onConfirm={async () => {
            await change.mutateAsync({ action: 'delete', itemId: removing.id });
          }}
        />
      )}
    </section>
  );
}
