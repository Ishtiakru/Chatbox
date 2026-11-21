import { validateProposal, isAdministrator } from './logic.js';

export class OperationError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function verifiedAdmin(snap, uid) {
  if (!isAdministrator(snap.data(), uid)) throw new OperationError('permission-denied', 'Administrator access required.');
}

// Each write and audit entry happens in the same transaction as the authoritative role check.
export async function updateWelcome(db, uid, rawProposal, confirmed, timestamp) {
  let proposal;
  try { proposal = validateProposal(rawProposal); } catch { throw new OperationError('invalid-argument', 'Invalid proposal.'); }
  if (proposal.action !== 'set_welcome' || confirmed !== true) throw new OperationError('failed-precondition', 'A supported proposal and confirmation are required.');
  await db.runTransaction(async tx => {
    verifiedAdmin(await tx.get(db.doc('system/admin')), uid);
    tx.set(db.doc('settings/public'), { welcome: proposal.value, updatedAt: timestamp() }, { merge: true });
    tx.create(db.collection('adminAudit').doc(), { actorUid: uid, action: 'set_welcome', createdAt: timestamp() });
  });
}

export async function changeAdministrator(db, uid, target, confirmed, timestamp) {
  if (!target || typeof target.uid !== 'string' || !target.uid || target.uid === uid || confirmed !== true) throw new OperationError('failed-precondition', 'Confirm a different administrator.');
  if (target.disabled || !target.emailVerified) throw new OperationError('failed-precondition', 'Target needs an active account with a verified email.');
  await db.runTransaction(async tx => {
    verifiedAdmin(await tx.get(db.doc('system/admin')), uid);
    tx.set(db.doc('system/admin'), { uid: target.uid, updatedAt: timestamp() });
    tx.create(db.collection('adminAudit').doc(), { actorUid: uid, targetUid: target.uid, action: 'transfer_admin', createdAt: timestamp() });
  });
}
