export function changedFields<T extends object>(before: Partial<T>, after: Partial<T>): Partial<T> {
  const changes: Partial<T> = {};
  for (const key of Object.keys(after) as (keyof T)[]) {
    if (!Object.is(before[key], after[key])) changes[key] = after[key];
  }
  return changes;
}
