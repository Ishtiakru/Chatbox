// STUN helps peers discover network addresses; it is not a relay and does not
// guarantee connectivity. It exposes the caller's IP address to the service
// and may expose network addresses to the peer. Test lab only, opt-in only.
// Never bundle TURN passwords or long-lived credentials in a web/Android build.
export function experimentalIceServers(stunEnabled) {
  return stunEnabled === true ? [{urls:'stun:stun.l.google.com:19302'}] : [];
}
