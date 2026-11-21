// Experimental incoming-call visibility. This is a UI convenience, not an
// authorization boundary; Firestore rules must still validate participants.
export const SIGNAL_LIFETIME_MS = 5 * 60 * 1000;
const MAX_FUTURE_SKEW_MS = 60 * 1000;

/**
 * Re-evaluate a Firestore snapshot at the next visibility boundary. Without a
 * timer, an unchanged ringing document would remain on screen indefinitely.
 * The scheduler and clock can be injected to test expiry without waiting.
 */
export function createIncomingCallFilter({ uid, onCalls, now = Date.now, setTimer = setTimeout, clearTimer = clearTimeout }) {
  if (typeof uid !== 'string' || !uid || typeof onCalls !== 'function') throw new Error('Invalid incoming call filter.');
  let items = [], timer = null, stopped = false;
  function refresh() {
    if (stopped) return;
    if (timer !== null) { clearTimer(timer); timer = null; }
    const time = now();
    let nextBoundary = Infinity;
    const visible = [];
    for (const item of items) {
      if (item.metadata.hasPendingWrites) continue;
      const call = item.data();
      if (call.receiverId !== uid || call.status !== 'ringing') continue;
      const created = call.createdAt?.toMillis?.();
      if (!Number.isFinite(created)) continue;
      const start = created - MAX_FUTURE_SKEW_MS;
      const end = created + SIGNAL_LIFETIME_MS;
      if (time < start) nextBoundary = Math.min(nextBoundary, start);
      else if (time < end) {
        visible.push({ ...call, id: item.id });
        nextBoundary = Math.min(nextBoundary, end);
      }
    }
    // Schedule before invoking callbacks so a synchronous stop() cancels it.
    if (Number.isFinite(nextBoundary)) timer = setTimer(refresh, Math.max(1, Math.min(nextBoundary - time, 2147483647)));
    onCalls(visible);
  }
  return {
    update(docs) { if (!stopped) { items = Array.from(docs); refresh(); } },
    stop() { if (!stopped) { stopped = true; items = []; if (timer !== null) clearTimer(timer); timer = null; } },
  };
}
