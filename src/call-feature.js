// Browser-only experimental adapter. Production rules, notifications,
// retention, quotas, relay infrastructure and device testing are still needed.
import { Capacitor } from '@capacitor/core';
import { getFirestore } from 'firebase/firestore';
import { app } from './firebase.js';
import { createCallController } from './call-controller.js';
import { createFirestoreCall, watchIncomingCalls } from './firestore-call-transport.js';
import { createForegroundMediaDevices } from './foreground-media.js';

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
      // No TURN relay is configured. Connectivity outside local/direct paths is
      // not guaranteed, and candidate addresses can be exposed to the peer.
      iceServers:[],
    }),
  });
}
