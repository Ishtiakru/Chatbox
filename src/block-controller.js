// Experimental UI state for private block records. Server rules remain the
// authorization boundary. A cached/optimistic snapshot is never confirmation.
export function createBlockController({subscribe, block, unblock, onChange = () => {}}) {
  if ([subscribe,block,unblock,onChange].some(value => typeof value !== 'function')) throw new Error('Block dependencies are required.');
  let uid = null, stop = null, generation = 0, pending = null, lastServer = null, disposed = false;
  let state = {status:'loading', ids:[], error:''};
  const getState = () => ({...state, ids:[...state.ids]});
  const update = patch => { state={...state,...patch}; onChange(getState()); };
  function accept(snapshot) {
    if (disposed || !uid) return;
    if (snapshot.fromCache || snapshot.pendingWrites) {
      lastServer = null;
      if (!pending) update({status:'loading',ids:[],error:''});
      return;
    }
    lastServer = [...new Set(snapshot.ids)];
    if (pending && (!pending.ack || lastServer.includes(pending.peerUid) !== pending.wantBlocked)) return;
    pending = null;
    update({status:'ready',ids:lastServer,error:''});
  }
  function setUser(value) {
    if (disposed || typeof value !== 'string' || !value || value.includes('/')) throw new Error('Invalid user.');
    generation++; pending=null; lastServer=null;
    stop?.(); stop=null; uid=value;
    const current=generation;
    update({status:'loading',ids:[],error:''});
    stop=subscribe(uid, snapshot=>{if(!disposed && current===generation) accept(snapshot);}, ()=>{
      if(disposed || current!==generation) return;
      generation++; pending=null; lastServer=null;
      update({status:'error',ids:[],error:'Unable to confirm blocked users. Reconnect to retry.'});
    });
  }
  async function change(peerUid, conversation, wantBlocked) {
    if (disposed || !uid || state.status !== 'ready' || pending || typeof peerUid !== 'string' || !peerUid || peerUid.includes('/') || peerUid === uid || state.ids.includes(peerUid) === wantBlocked) throw new Error('Cannot change this block.');
    const current=generation;
    const request={peerUid,wantBlocked,ack:false}; pending=request;
    update({status:'saving',error:''});
    try {
      if (wantBlocked) await block(uid,peerUid,conversation);
      else await unblock(uid,peerUid);
      if (disposed || current!==generation || pending!==request) return;
      request.ack=true;
      if (lastServer) accept({ids:lastServer,fromCache:false,pendingWrites:false});
    } catch {
      if (disposed || current!==generation || pending!==request) return;
      pending=null; lastServer=null;
      update({status:'error',ids:[],error:'Could not confirm this change. Reconnect and check before relying on it.'});
    }
  }
  function dispose() {
    if (disposed) return;
    disposed=true; generation++; uid=null; pending=null; lastServer=null;
    const unsubscribe=stop; stop=null; unsubscribe?.();
  }
  return {setUser,block:(peer,conversation)=>change(peer,conversation,true),unblock:peer=>change(peer,null,false),dispose,getState};
}
