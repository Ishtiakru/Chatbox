import { verifyNativeTestIdentity } from './native-test-identity.js';

// The opt-in is a test-only safeguard, not a permissions, identity or rules
// authorization boundary. Native device and privacy testing is still required.
export async function verifyNativeCallLab({isTestProject, enabled, nativeTestMode, webConfig, getIdentity}) {
  if (isTestProject !== true || enabled !== true || nativeTestMode !== true || typeof getIdentity !== 'function') {
    throw new Error('Android calling is not configured.');
  }
  const identity = await getIdentity();
  verifyNativeTestIdentity(webConfig, identity);
  if (identity.callLabEnabled !== true) throw new Error('Android calling is not configured.');
  return true;
}
