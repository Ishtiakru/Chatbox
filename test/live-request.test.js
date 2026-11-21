import test from 'node:test';
import assert from 'node:assert/strict';
import {createLiveRequest} from '../src/live-request.js';
import {createAccountOperationGuard} from '../src/account-operation-guard.js';
import {ChatInputError} from '../src/chat-utils.js';

function deferred() {let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};}
function fixture() {
  let session=1;const initial=session,calls=[];
  const guard=createAccountOperationGuard(()=>session===initial);
  const args={uid:'alice',peerUid:' bob ',guard,
    readPeer:async peer=>{calls.push(['server-read',peer]);return true;},
    writeRequest:async (uid,peer)=>{calls.push(['write',uid,peer]);return 'id';},
  };
  return {args,calls,switchSession:()=>session++};
}
test('a request checks the server for the trimmed recipient before writing',async()=>{
  const f=fixture();assert.equal(await createLiveRequest(f.args),'id');
  assert.deepEqual(f.calls,[['server-read','bob'],['write','alice','bob']]);
});
test('invalid IDs never start network operations',async()=>{
  for(const [uid,peerUid] of [['alice','alice'],['alice','bad/id'],['alice',''],['','bob'],['alice',null]]) {
    const f=fixture();await assert.rejects(createLiveRequest({...f.args,uid,peerUid}),ChatInputError);assert.deepEqual(f.calls,[]);
  }
});
test('a missing or unavailable server profile never starts a write',async()=>{
  const f=fixture();f.args.readPeer=async()=>false;
  await assert.rejects(createLiveRequest(f.args),{code:'profile-not-found'});assert.deepEqual(f.calls,[]);
  f.args.readPeer=async()=>{throw Object.assign(new Error('offline'),{code:'unavailable'});};
  await assert.rejects(createLiveRequest(f.args),{code:'unavailable'});assert.deepEqual(f.calls,[]);
});
test('a session change during a profile read stops the later conversation write',async()=>{
  const f=fixture(),read=deferred();f.args.readPeer=()=>read.promise;
  const request=createLiveRequest(f.args);f.switchSession();read.resolve(true);
  await assert.rejects(request,/account changed/);assert.deepEqual(f.calls,[]);
});
test('an obsolete session cannot begin a profile lookup',async()=>{
  const f=fixture();f.switchSession();await assert.rejects(createLiveRequest(f.args),/account changed/);assert.deepEqual(f.calls,[]);
});
test('after a write starts, changing session does not pretend to cancel it',async()=>{
  const f=fixture(),write=deferred();f.args.writeRequest=(uid,peer)=>{f.calls.push(['write',uid,peer]);return write.promise;};
  const request=createLiveRequest(f.args);await Promise.resolve();assert.deepEqual(f.calls,[['server-read','bob'],['write','alice','bob']]);
  f.switchSession();write.resolve('accepted');assert.equal(await request,'accepted');
});
