// Best-effort privacy guard around a permission prompt. If the page or session
// becomes unavailable while the browser waits for permission, stop every late
// track before it can be attached to a peer connection. This cannot force the
// browser to dispatch lifecycle events or revoke a permission.
export function createForegroundMediaDevices(mediaDevices, isAvailable) {
  if (typeof mediaDevices?.getUserMedia !== 'function' || typeof isAvailable !== 'function') throw new Error('Media dependencies are required.');
  function available() { try { return isAvailable() === true; } catch { return false; } }
  return {
    async getUserMedia(constraints) {
      if (!available()) throw new Error('Calling is not available on this page.');
      const stream = await mediaDevices.getUserMedia(constraints);
      if (!available()) {
        for (const track of stream.getTracks()) { try { track.stop(); } catch { /* Release the other tracks too. */ } }
        throw new Error('Calling is not available on this page.');
      }
      return stream;
    },
  };
}
