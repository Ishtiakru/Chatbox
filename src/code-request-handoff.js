// Manual handoff only: this never sends a request or changes source code.
// Clipboard contents may be visible to other applications.
export function copyCodeRequest(value, clipboard) {
  if (typeof value !== 'string') return Promise.resolve(false);
  const request = value.trim();
  if (!request || request.length > 1000 || typeof clipboard?.writeText !== 'function') return Promise.resolve(false);
  const text = `Please make the following change to the Chatbox app. Inspect the repository, implement and test it, and explain any limitations. Do not deploy without review.\n\n${request}`;
  try {
    return Promise.resolve(clipboard.writeText(text)).then(() => true, () => false);
  } catch {
    return Promise.resolve(false);
  }
}
