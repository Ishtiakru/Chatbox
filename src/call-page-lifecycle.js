// Local privacy gate for the default-off browser calling lab. This does not
// detect every OS/browser suspension and is not an authorization boundary.
export function canUseCallPage({enabled, pageActive, hidden, view, authenticated}) {
  return enabled === true && pageActive === true && hidden === false && view === 'live' && authenticated === true;
}
