export function classes(...names: (string | false | undefined)[]) {
  return names.filter(Boolean).join(' ');
}
