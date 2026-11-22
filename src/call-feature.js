// Experimental browser/isolated Android adapter. Production rules, notifications,
// retention, quotas, relay infrastructure and device testing are still needed.
import { Capacitor, registerPlugin } from '@capacitor/core';
import { getFirestore } from 'firebase/firestore';
import { app, isTestProject } from './firebase.js';
import { verifyNativeCallLab } from './native-call-gate.js';
import { createCallController } from './call-controller.js';
import { createFirestoreCall, watchIncomingCalls } from './firestore-call-transport.js';
import { createForegroundMediaDevices } from './foreground-media.js';
import { EXPERIMENTAL_STUN_ENABLED } from './feature-flags.js';
import { experimentalIceServers } from './call-ice-config.js';

export async function createBrowserCallController(onChange, isAvailable) {
  if (Capacitor.isNativePlatform()) {
    const nativeIdentity = registerPlugin('NativeFirebaseIdentity');
    await verifyNativeCallLab({isTestProject, enabled:import.meta.env.VITE_ENABLE_EXPERIMENTAL_ANDROID_CALLS === 'true',
      nativeTestMode:import.meta.env.VITE_CHATBOX_NATIVE_TEST_MODE === 'true', webConfig:app.options,
      getIdentity:() => nativeIdentity.getIdentity()});
    if (isAvailable() !== true) throw new Error('Calling is not available on this page.');
  }
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || !window.RTCPeerConnection) throw new Error('Calling is unavailable in this browser.');
  const db = getFirestore(app);
  const mediaDevices = createForegroundMediaDevices(navigator.mediaDevices, isAvailable);
  return createCallController({
    onChange,
    watchIncoming: (context, next, fail) => watchIncomingCalls(db, context.id, context.uid, next, fail),
    createSession: options => createFirestoreCall({
      ...options, db, conversationId:options.id,
      mediaDevices, peerFactory:config=>new RTCPeerConnection(config),
      // STUN is optional and only aids address discovery. There is no TURN
      // relay, so connectivity is not guaranteed and IPs may reach the peer.
      iceServers:experimentalIceServers(EXPERIMENTAL_STUN_ENABLED),
    }),
  });
}
