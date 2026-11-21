// A slow Firestore write can be pending indefinitely while offline. This timer
// only shows a notice: it does not cancel, fail or retry the underlying write.
export function createSlowOperationNotice(onChange, {delay = 8000, schedule = setTimeout, cancel = clearTimeout} = {}) {
  if (typeof onChange !== 'function' || typeof schedule !== 'function' || typeof cancel !== 'function' || !Number.isFinite(delay) || delay < 0) throw new Error('Invalid notice dependencies.');
  let ticket = 0, timer = null, shown = false;
  function clear() {
    ticket++;
    if (timer !== null) cancel(timer);
    timer = null;
    shown = false;
  }
  function begin() {
    clear();
    const current = ticket;
    timer = schedule(() => {
      if (ticket !== current) return;
      timer = null;
      shown = true;
      onChange(true);
    }, delay);
    return () => {
      if (ticket !== current) return;
      const wasShown = shown;
      clear();
      if (wasShown) onChange(false);
    };
  }
  return {begin, reset:clear};
}
