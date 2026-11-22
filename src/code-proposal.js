// Read a single public source file at an immutable GitHub commit. This is a
// review-only snapshot; the APK does not get write access to the repository.
export function codeSourceUrl(path, commit) {
  if (typeof path !== 'string' || path.length > 180 || !/^[a-zA-Z0-9_./-]+\.(?:js|mjs|css|html|java|json|md|rules)$/.test(path) || path.split('/').some(p => !p || p === '.' || p === '..' || p.startsWith('.')) || !/^(?:src|test|scripts|android\/app\/src\/main|functions|docs)\//.test(path) || !/^[0-9a-f]{40}$/.test(commit || '')) throw new Error('Invalid source path or snapshot.');
  return `https://api.github.com/repos/Ishtiakru/Chatbox/contents/${path}?ref=${commit}`;
}

export async function loadCodeSource(path, commit, fetchImpl = fetch) {
  const url = codeSourceUrl(path, commit);
  const response = await fetchImpl(url, {headers:{Accept:'application/vnd.github.raw+json'}, redirect:'error', credentials:'omit'});
  if (!response.ok || !response.body?.getReader) throw new Error('Source unavailable.');
  const reader = response.body.getReader();
  const chunks = []; let length = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > 24000) throw new Error('File is too large for this experiment.');
      chunks.push(value);
    }
  } finally { await reader.cancel().catch(() => {}); }
  const bytes = new Uint8Array(length); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  const source = new TextDecoder('utf-8', {fatal:true}).decode(bytes);
  if (!source.trim() || source.includes('\0')) throw new Error('File is empty or not text.');
  return source;
}

export function copyCodeProposal(text, clipboard) {
  if (typeof text !== 'string' || !text.trim() || text.length > 50000 || typeof clipboard?.writeText !== 'function') return Promise.resolve(false);
  try { return Promise.resolve(clipboard.writeText(text)).then(() => true, () => false); }
  catch { return Promise.resolve(false); }
}
