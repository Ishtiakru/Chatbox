// Experimental message pagination. Authorization must be enforced by Firestore
// rules; this client-side cache is scoped to one selected conversation in memory.
const PAGE_SIZE = 100;
const encoder = new TextEncoder();
// Firestore orders document names by UTF-8 bytes, not JS UTF-16 code units.
function compareIds(a, b) {
  const x = encoder.encode(a), y = encoder.encode(b);
  for (let i = 0; i < Math.min(x.length, y.length); i++) {
    if (x[i] !== y[i]) return x[i] - y[i];
  }
  return x.length - y.length;
}
function compareMessages(a, b) {
  const x = a.sentAt, y = b.sentAt;
  // Server-pending messages are shown last until their timestamp resolves.
  if (!x && !y) return compareIds(a.id, b.id);
  if (!x) return 1;
  if (!y) return -1;
  return (x.seconds - y.seconds) || (x.nanoseconds - y.nanoseconds) || compareIds(a.id, b.id);
}

export function createMessageFeed({ listenRecent, fetchOlder, onChange, onError, onFatalError = onError, pageSize = PAGE_SIZE }) {
  if (typeof listenRecent !== 'function' || typeof fetchOlder !== 'function' || typeof onChange !== 'function' || typeof onError !== 'function' || typeof onFatalError !== 'function' || !Number.isInteger(pageSize) || pageSize < 1) throw new Error('Message feed dependencies are required.');
  const messages = new Map();
  const pendingIds = new Set();
  let cursor = null, hasMore = true, loading = false, stopped = false;
  let serverSeen = false, previousServerIds = new Set(), epoch = 0, fromCache = true, historyReset = false;
  function emit() {
    if (!stopped) onChange({ messages: [...messages.values()].sort(compareMessages), hasMore: serverSeen && hasMore && !!cursor, loading, fromCache, historyReset });
  }
  function merge(docs) {
    for (const doc of docs) messages.set(doc.id, { ...doc.data(), id: doc.id, pendingWrites:doc.metadata.hasPendingWrites });
  }
  function validCursor(doc) {
    return !doc.metadata.hasPendingWrites && !!doc.data().sentAt;
  }
  const unsubscribe = listenRecent(snapshot => {
    if (stopped) return;
    const docs = snapshot.docs;
    fromCache = snapshot.metadata.fromCache;
    const visibleIds = new Set(docs.map(doc => doc.id));
    for (const id of pendingIds) if (!visibleIds.has(id)) messages.delete(id);
    pendingIds.clear();
    for (const doc of docs) if (doc.metadata.hasPendingWrites) pendingIds.add(doc.id);

    if (fromCache) {
      // Cache can be incomplete or stale. Show its initial contents as
      // provisional, but never use them as a pagination anchor. After a server
      // window, only add local pending writes from cache, not disconnected
      // historical fragments that might hide a gap.
      merge(serverSeen ? docs.filter(doc => doc.metadata.hasPendingWrites) : docs);
      emit();
      return;
    }
    const confirmed = docs.filter(validCursor);
    const currentIds = new Set(confirmed.map(doc => doc.id));
    const disconnectedWindows = previousServerIds.size > 0 && currentIds.size > 0 && ![...currentIds].some(id => previousServerIds.has(id));
    if (!serverSeen || disconnectedWindows) {
      // An initial cache or an entirely displaced 100-message window may have
      // missed arbitrarily many messages. Restart at the newest server window
      // so earlier messages can be paged without silently skipping the gap.
      messages.clear(); cursor = null; hasMore = true; epoch++; loading = false;
      if (disconnectedWindows) historyReset = true;
    }
    // If an initially empty confirmed query jumps directly to a full window,
    // there may already be older messages even though the empty query ended history.
    if (serverSeen && previousServerIds.size === 0 && currentIds.size && docs.length >= pageSize) hasMore = true;
    serverSeen = true;
    // An all-pending snapshot does not establish a new committed boundary.
    if (currentIds.size) previousServerIds = currentIds;
    merge(docs);
    const first = confirmed[0];
    if (first && (!cursor || compareMessages({ ...first.data(), id:first.id }, { ...cursor.data(), id:cursor.id }) < 0)) cursor = first;
    if (docs.length < pageSize && !snapshot.metadata.hasPendingWrites) hasMore = false;
    emit();
  }, error => { if (!stopped) onFatalError(error); });
  async function loadOlder() {
    if (stopped || loading || !serverSeen || !hasMore || !cursor) return;
    const requestEpoch = epoch;
    loading = true; emit();
    try {
      const page = await fetchOlder(cursor);
      if (stopped || requestEpoch !== epoch) return;
      merge(page.docs);
      historyReset = false;
      const first = page.docs.find(validCursor);
      if (first && compareMessages({ ...first.data(), id:first.id }, { ...cursor.data(), id:cursor.id }) < 0) cursor = first;
      if (page.docs.length < pageSize) hasMore = false;
    } catch (error) {
      if (!stopped && requestEpoch === epoch) onError(error);
    } finally {
      if (!stopped && requestEpoch === epoch) { loading = false; emit(); }
    }
  }
  function stop() {
    if (stopped) return;
    stopped = true; messages.clear(); pendingIds.clear(); cursor = null; unsubscribe();
  }
  return { loadOlder, stop };
}
