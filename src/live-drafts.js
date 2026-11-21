// Volatile only: never put private drafts into localStorage, IndexedDB or logs.
// Caller must clear on sign-out, account change, or terminal subscription error.
export function createLiveDrafts() {
  const drafts = new Map();
  const validId = id => typeof id === 'string' && id.length > 0 && id.length <= 128 && !id.includes('/');
  const empty = () => ({text:'', error:''});
  function read(id) { return validId(id) ? {...(drafts.get(id) || empty())} : empty(); }
  function update(id, patch) {
    if (!validId(id)) throw new Error('Invalid conversation ID.');
    const value = {...read(id), ...patch};
    if (!value.text && !value.error) drafts.delete(id);
    else drafts.set(id, value);
  }
  function setText(id, text) {
    if (typeof text !== 'string' || text.length > 2000) throw new Error('Invalid draft.');
    update(id, {text});
  }
  function setError(id, error) {
    if (typeof error !== 'string') throw new Error('Invalid error.');
    update(id, {error});
  }
  // Do not clear a newer draft when an earlier asynchronous send completes.
  function acknowledge(id, sentText) {
    if (!validId(id) || typeof sentText !== 'string') return false;
    const current = read(id);
    if (current.text !== sentText) return false;
    drafts.delete(id);
    return true;
  }
  return {read,setText,setError,acknowledge,remove:id=>drafts.delete(id),clear:()=>drafts.clear()};
}
