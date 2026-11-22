import { doc, setDoc, getDocFromServer, getDocsFromServer, collection, query, limit, updateDoc, runTransaction, serverTimestamp, onSnapshot } from 'firebase/firestore';
import { validSparkTarget, parseSparkCommand } from './spark-admin-commands.js';
const defaultOps = {doc, setDoc, getDocFromServer, getDocsFromServer, collection, query, limit, updateDoc, runTransaction, serverTimestamp, onSnapshot};
export function createSparkAdminService(db, ops = defaultOps) {
  const { doc, setDoc, getDocFromServer, getDocsFromServer, collection, query, limit, updateDoc, runTransaction, serverTimestamp, onSnapshot } = ops;
  const admin = () => doc(db, 'sparkAdmin', 'current');
  const delegation = email => doc(db, 'sparkDelegations', email);
  const welcome = () => doc(db, 'settings', 'sparkPublic');

  // Status is a UI convenience. Firestore rules are authoritative for each write.
  // The optional session guard stops later stages after a client auth change;
  // it cannot revoke a Firestore request already in flight.
  const unguarded = {run: operation => operation()};
  async function getSparkAdminStatus(user, guard = unguarded) {
    if (!user?.uid || !user.email) throw new Error('Sign in with Google.');
    const email = user.email.toLowerCase();
    try {
      const snap = await guard.run(() => getDocFromServer(admin()));
      if (snap.exists() && snap.data().uid === user.uid) return { status:'authorized', role:'owner' };
    } catch (error) {
      if (error?.code !== 'permission-denied') throw error;
    }
    if (email === 'ishtiakahmedru@gmail.com') {
      try {
        await guard.run(() => setDoc(admin(), { uid:user.uid, email, pendingEmail:'', createdAt:serverTimestamp(), updatedAt:serverTimestamp() }));
      } catch (error) {
        // An existing owner cannot be overwritten.
        if (error?.code !== 'permission-denied') throw error;
      }
      try {
        const snap = await guard.run(() => getDocFromServer(admin()));
        if (snap.exists() && snap.data().uid === user.uid) return { status:'authorized', role:'owner' };
      } catch (error) { if (error?.code !== 'permission-denied') throw error; }
    }
    if (validSparkTarget(email)) {
      const snap = await guard.run(() => getDocFromServer(delegation(email)));
      if (snap.exists()) {
        const data = snap.data();
        if (data.status === 'invited') return { status:'invited', role:'delegate' };
        if (data.status === 'active' && data.uid === user.uid) return { status:'authorized', role:'delegate' };
      }
    }
    return { status:'forbidden', role:'' };
  }
  async function applySparkWelcome(user, command, confirmed, preview) {
    const proposal = parseSparkCommand(command);
    if (!user?.uid || proposal?.action !== 'set_welcome' || confirmed !== true || preview?.action !== proposal.action || preview?.value !== proposal.value) {
      throw new Error('A matching preview and confirmation are required.');
    }
    await setDoc(welcome(), { welcome:proposal.value, updatedBy:user.uid, updatedAt:serverTimestamp() });
  }
  // Each appearance setting is a separate, strictly bounded public document.
  // Rules enforce authorization; client-side checks are not a security boundary.
  async function applySparkAppearance(user, command, confirmed, preview) {
    const proposal = parseSparkCommand(command);
    const paths = {set_theme:'sparkTheme', set_menu:'sparkMenu'};
    if (!user?.uid || !proposal || !paths[proposal.action] || confirmed !== true || preview?.action !== proposal.action || preview?.value !== proposal.value) {
      throw new Error('A matching preview and confirmation are required.');
    }
    await setDoc(doc(db, 'settings', paths[proposal.action]), {value:proposal.value, updatedBy:user.uid, updatedAt:serverTimestamp()});
  }
  function watchSparkAppearance(next, fail) {
    const values = {theme:'default', menu:'bottom'};
    const watch = (name, key, allowed) => onSnapshot(doc(db, 'settings', name), {includeMetadataChanges:true}, snap => {
      // Local optimistic writes are not confirmation. Metadata changes are
      // required to receive the later server acknowledgement.
      if (snap.metadata?.hasPendingWrites || snap.metadata?.fromCache) return;
      const value = snap.data()?.value;
      values[key] = allowed.includes(value) ? value : allowed[0];
      next({...values});
    }, fail);
    const stopTheme = watch('sparkTheme', 'theme', ['default','lavender']);
    const stopMenu = watch('sparkMenu', 'menu', ['bottom','top']);
    return () => { stopTheme(); stopMenu(); };
  }
  // A server read enforces current Firestore authorization; never use cached
  // data for this private record. No client operation can provision it.
  async function getSparkSourceHandover() {
    const snap = await getDocFromServer(doc(db, 'sparkPrivate', 'sourceHandover'));
    if (!snap.exists()) throw new Error('Source handover is not configured.');
    return snap.data();
  }
  async function listSparkDelegations() {
    const snap = await getDocsFromServer(query(collection(db,'sparkDelegations'),limit(100)));
    return snap.docs.map(item => ({email:item.id,status:item.data().status}));
  }
  async function inviteSparkDelegate(user, email, confirmed, guard = unguarded) {
    if (!user?.uid || confirmed !== true || !validSparkTarget(email)) throw new Error('Confirm a valid Gmail address.');
    const ref=delegation(email);
    await guard.run(() => runTransaction(db, async tx => {
      const existing=await guard.run(() => tx.get(ref));
      guard.run(() => {
        if (!existing.exists()) tx.set(ref,{email,uid:'',invitedBy:user.uid,status:'invited',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
        else if (existing.data().status === 'revoked') tx.update(ref,{uid:'',invitedBy:user.uid,status:'invited',updatedAt:serverTimestamp()});
        else throw new Error('The invitation or delegation already exists.');
      });
    }));
  }
  async function revokeSparkDelegate(email, confirmed) {
    if (confirmed !== true || !validSparkTarget(email)) throw new Error('Confirmation is required.');
    await updateDoc(delegation(email),{status:'revoked',updatedAt:serverTimestamp()});
  }
  async function acceptSparkDelegation(user, confirmed) {
    if (!user?.uid || !validSparkTarget(user.email?.toLowerCase()) || confirmed !== true) throw new Error('Confirmation is required.');
    await updateDoc(delegation(user.email.toLowerCase()),{uid:user.uid,status:'active',updatedAt:serverTimestamp()});
  }
  function watchSparkWelcome(next, fail) {
    // Do not display optimistic or stale cache data as a confirmed public change.
    // Metadata events deliver the subsequent server acknowledgement even when
    // the document content itself has not changed.
    return onSnapshot(welcome(), {includeMetadataChanges:true}, snap => {
      if (snap.metadata?.hasPendingWrites || snap.metadata?.fromCache) return;
      const text = snap.data()?.welcome;
      next(typeof text === 'string' && text.length <= 160 ? text : '');
    }, fail);
  }

  return {getSparkAdminStatus,applySparkWelcome,applySparkAppearance,watchSparkAppearance,getSparkSourceHandover,listSparkDelegations,inviteSparkDelegate,revokeSparkDelegate,acceptSparkDelegation,watchSparkWelcome};
}
