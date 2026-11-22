// Run only from a trusted environment with project-owner authorization.
// This script creates the initial administrator once; it never overwrites an existing one.
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
const [projectId, uid] = process.argv.slice(2);
if (!projectId || !uid || process.argv.length !== 4) throw new Error('Usage: node bootstrap-admin.js PROJECT_ID FIREBASE_UID');
initializeApp({ projectId });
const target = await getAuth().getUser(uid);
if (target.disabled || !target.emailVerified) throw new Error('Account must be active with a verified email.');
const db = getFirestore();
await db.runTransaction(async tx => {
  const ref = db.doc('system/admin');
  if ((await tx.get(ref)).exists) throw new Error('An administrator already exists; use the audited transfer flow.');
  tx.create(ref, { uid, updatedAt: FieldValue.serverTimestamp() });
  tx.create(db.collection('adminAudit').doc(), { actorUid: uid, action: 'bootstrap', createdAt: FieldValue.serverTimestamp() });
});
console.log('Initial admin assigned in project:', projectId);
