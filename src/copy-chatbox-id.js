// Only called from an explicit user gesture. Do not read the clipboard or
// automatically share IDs. Clipboard contents may be visible to other apps.
export function copyChatboxId(uid, clipboard) {
  if (typeof uid !== 'string' || !uid || uid.length > 128 || typeof clipboard?.writeText !== 'function') return Promise.resolve(false);
  try {
    return Promise.resolve(clipboard.writeText(uid)).then(() => true, () => false);
  } catch {
    return Promise.resolve(false);
  }
}
