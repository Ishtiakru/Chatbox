// Local privacy gate for the default-off browser calling lab. This does not
// detect every OS/browser suspension and is not an authorization boundary.
export function canUseCallPage({enabled, pageActive, hidden, view, authenticated, nativeActive = true}) {
  return enabled === true && pageActive === true && nativeActive === true && hidden === false && view === 'live' && authenticated === true;
}

// Complements visibility events in the isolated Android lab. Native delivery
// can be delayed or lost; this is not a guarantee that capture stops on pause.
export function createNativeCallLifecycle({onStop, onStart}) {
  if (typeof onStop !== 'function' || typeof onStart !== 'function') throw new Error('Lifecycle handlers are required.');
  let active = true;
  return {
    isActive: () => active,
    stop() { active = false; onStop(); },
    start() { active = true; onStart(); },
  };
}
