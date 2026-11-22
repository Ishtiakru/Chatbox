// A display name is user-controlled, not proof of identity. Only the selected
// conversation's peer is observed, and server rules remain authoritative.
export function createPeerProfileController({subscribe, onChange}) {
  if (typeof subscribe !== 'function' || typeof onChange !== 'function') throw new Error('Profile dependencies are required.');
  let generation=0, stop=null, disposed=false;
  let state={uid:'', status:'idle', name:''};
  const snapshot=()=>({...state});
  const update=value=>{state=value; onChange(snapshot());};
  function clearListener() {
    const unsubscribe=stop; stop=null;
    try { unsubscribe?.(); } catch { /* Other resources must still be released. */ }
  }
  function select(uid) {
    if (disposed || typeof uid !== 'string' || !uid || uid.length > 128 || uid.includes('/')) throw new Error('Invalid peer ID.');
    const current=++generation;
    clearListener();
    update({uid,status:'loading',name:''});
    // The callback may synchronously dispose or switch selection.
    if (disposed || current !== generation) return;
    const unavailable=()=>{if (!disposed && current===generation) update({uid,status:'unavailable',name:''});};
    try {
      const unsubscribe=subscribe(uid, snap=>{
        if (disposed || current!==generation) return;
        if (snap.metadata.fromCache || snap.metadata.hasPendingWrites || !snap.exists()) return unavailable();
        const data=snap.data();
        const name=typeof data?.displayName === 'string' ? data.displayName.trim() : '';
        if (data?.uid !== uid || !name || name.length>80) return unavailable();
        update({uid,status:'ready',name});
      }, unavailable);
      if (typeof unsubscribe !== 'function') throw new Error('Invalid subscription.');
      if (disposed || current!==generation) { try { unsubscribe(); } catch {} }
      else stop=unsubscribe;
    } catch { unavailable(); }
  }
  function dispose() {
    if (disposed) return;
    disposed=true; generation++; clearListener();
    state={uid:'',status:'idle',name:''};
  }
  return {select,dispose,getState:snapshot};
}
