// Browser-only experimental adapter. Production rules, notifications,
// retention, quotas, relay infrastructure and device testing are still needed.
import { Capacitor } from '@capacitor/core';
import { getFirestore } from 'firebase/firestore';
import { app } from './firebase.js';
import { createCallController } from './call-controller.js';
import { createFirestoreCall, watchIncomingCalls } from './firestore-call-transport.js';
import { createForegroundMediaDevices } from './foreground-media.js';
import { EXPERIMENTAL_STUN_ENABLED } from './feature-flags.js';
import { experimentalIceServers } from './call-ice-config.js';

export function createBrowserCallController(onChange, isAvailable) {
  if (Capacitor.isNativePlatform()) throw new Error('Calling is not configured on Android.');
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
