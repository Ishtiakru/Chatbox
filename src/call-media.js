// These controls affect the tracks supplied to the current peer connection.
// Disabling a track suppresses its outgoing content; it is not the same as
// stopping capture or revoking device permission.
const tracksOf = (stream, kind) => {
  if (!['audio', 'video'].includes(kind) || typeof stream?.getTracks !== 'function') return [];
  return stream.getTracks().filter(track => track.kind === kind && track.readyState === 'live');
};
export function readLocalMedia(stream) {
  const read = kind => {
    const tracks = tracksOf(stream, kind);
    return { available: tracks.length > 0, enabled: tracks.some(track => track.enabled) };
  };
  return {audio:read('audio'), video:read('video')};
}
export function setLocalMediaEnabled(stream, kind, enabled) {
  if (!['audio','video'].includes(kind) || typeof enabled !== 'boolean') throw new Error('Invalid media control.');
  const tracks = tracksOf(stream, kind);
  if (!tracks.length) throw new Error('Media track unavailable.');
  let failed = false;
  for (const track of tracks) {
    try { track.enabled = enabled; if (track.enabled !== enabled) failed = true; }
    catch { failed = true; }
  }
  // If enabling only partially succeeds, try to fail closed. A setter can
  // still fail; the UI must then show the actual observed track state.
  if (failed && enabled) for (const track of tracks) { try { track.enabled = false; } catch {} }
  if (failed) throw new Error('Unable to change media track.');
}
