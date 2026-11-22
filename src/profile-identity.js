// Review-only identity prototype. A six-digit code is a contact identifier,
// never a password or proof of identity. Treat it as enumerable and revocable
// only after a separately designed account lifecycle and abuse controls exist.
const CODE = /^[0-9]{6}$/;
export function validContactCode(value) { return typeof value === 'string' && value.length === 6 && CODE.test(value); }
export function contactQrPayload(code) {
  if (!validContactCode(code)) throw new Error('Invalid contact code.');
  return `chatbox:contact:v1:${code}`;
}
export function parseContactQr(value) {
  if (typeof value !== 'string' || value.length !== 25) return null;
  const match = /^chatbox:contact:v1:([0-9]{6})$/.exec(value);
  return match ? match[1] : null;
}
export function randomContactCode(cryptoImpl = globalThis.crypto) {
  if (typeof cryptoImpl?.getRandomValues !== 'function') throw new Error('Secure randomness is unavailable.');
  const buf = new Uint32Array(1);
  // Rejection sampling avoids bias from reducing a 32-bit integer modulo 1M.
  do { cryptoImpl.getRandomValues(buf); } while (buf[0] >= 4294000000);
  return String(buf[0] % 1000000).padStart(6, '0');
}

// The Firestore transaction and matching security rules must be used together.
// The caller provides fresh verified identity and a session guard. The rules,
// not this check or the random generator, enforce the atomic cross-reference.
export async function claimContactCode({uid, verified, guard, transact, profileRef, codeRef, newCode = randomContactCode, attempts = 8}) {
  if (typeof uid !== 'string' || !uid || uid.length > 128 || uid.includes('/') || verified !== true || typeof guard?.run !== 'function' || typeof transact !== 'function' || typeof profileRef !== 'function' || typeof codeRef !== 'function' || !Number.isInteger(attempts) || attempts < 1 || attempts > 20) throw new Error('Invalid identity request.');
  for (let i=0;i<attempts;i++) {
    const candidate = newCode();
    if (!validContactCode(candidate)) throw new Error('Invalid generated contact code.');
    const result = await guard.run(() => transact(async tx => {
      const profile = await guard.run(() => tx.get(profileRef(uid)));
      if (profile.exists()) {
        const value = profile.data();
        if (value?.uid !== uid || !validContactCode(value?.code)) throw new Error('Invalid stored identity.');
        return value.code;
      }
      const existing = await guard.run(() => tx.get(codeRef(candidate)));
      if (existing.exists()) return null;
      return guard.run(() => {
        tx.set(profileRef(uid), {uid,code:candidate});
        tx.set(codeRef(candidate), {uid,code:candidate});
        return candidate;
      });
    }));
    if (result) return result;
  }
  throw new Error('Could not reserve an available contact code.');
}
