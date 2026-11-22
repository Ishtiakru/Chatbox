import test from 'node:test';
import assert from 'node:assert/strict';
import {createAccountOperationGuard} from '../src/account-operation-guard.js';
import {createSparkAdminService} from '../src/spark-admin-data.js';

function deferred() { let resolve, reject; const promise=new Promise((yes,no)=>{resolve=yes;reject=no;}); return {promise,resolve,reject}; }
function fixture() {
  let generation=1, user={uid:'owner',email:'ishtiakahmedru@gmail.com'};
  const initialUser=user, initialGeneration=generation, calls=[];
  const guard=createAccountOperationGuard(()=>generation===initialGeneration && user===initialUser);
  const ops={doc:(_db,...parts)=>parts.join('/'),serverTimestamp:()=>({stamp:true})};
  return {guard,calls,ops,user:initialUser,switchUser(next={uid:'other'}){generation++;user=next;}};
}
test('operation guard runs only in the initiating session, including a same-UID new session',async()=>{
  const f=fixture(); assert.equal(f.guard.run(()=>42),42);
  f.switchUser({uid:'owner',email:f.user.email});
  assert.throws(()=>f.guard.run(()=>assert.fail('must not run')),/account changed/);
});
test('a pending owner lookup cannot begin automatic bootstrap after an account change',async()=>{
  const f=fixture(),lookup=deferred();
  f.ops.getDocFromServer=()=>lookup.promise;
  f.ops.setDoc=()=>{f.calls.push('bootstrap');};
  const api=createSparkAdminService({},f.ops);
  const pending=api.getSparkAdminStatus(f.user,f.guard);
  f.switchUser(); lookup.resolve({exists:()=>false});
  await assert.rejects(pending,/account changed/);
  assert.deepEqual(f.calls,[]);
});
test('permission-denied on an old lookup cannot start bootstrap on the next session',async()=>{
  const f=fixture(),lookup=deferred();
  f.ops.getDocFromServer=()=>lookup.promise;
  f.ops.setDoc=()=>{f.calls.push('bootstrap');};
  const pending=createSparkAdminService({},f.ops).getSparkAdminStatus(f.user,f.guard);
  f.switchUser();lookup.reject(Object.assign(new Error('denied'),{code:'permission-denied'}));
  await assert.rejects(pending,/account changed/); assert.deepEqual(f.calls,[]);
});
test('bootstrap can proceed if the original session remains current',async()=>{
  const f=fixture();let reads=0;
  f.ops.getDocFromServer=async()=> ++reads===1 ? {exists:()=>false} : {exists:()=>true,data:()=>({uid:'owner'})};
  f.ops.setDoc=async(ref,data)=>f.calls.push({ref,data});
  const result=await createSparkAdminService({},f.ops).getSparkAdminStatus(f.user,f.guard);
  assert.equal(result.status,'authorized');assert.equal(f.calls.length,1);assert.equal(f.calls[0].data.uid,'owner');
});
test('a pending invitation transaction cannot stage a write after a session change',async()=>{
  const f=fixture(),lookup=deferred();
  const tx={get:()=>lookup.promise,set:()=>f.calls.push('set'),update:()=>f.calls.push('update')};
  f.ops.runTransaction=(_db,callback)=>callback(tx);
  const pending=createSparkAdminService({},f.ops).inviteSparkDelegate(f.user,'target@gmail.com',true,f.guard);
  f.switchUser();lookup.resolve({exists:()=>false});
  await assert.rejects(pending,/account changed/);assert.deepEqual(f.calls,[]);
});
test('welcome writes require confirmation of the exact preview',async()=>{
  const f=fixture(); f.ops.setDoc=async(ref,data)=>f.calls.push({ref,data});
  const api=createSparkAdminService({},f.ops), preview={action:'set_welcome',value:'Hello'};
  await assert.rejects(api.applySparkWelcome(f.user,'welcome: Changed',true,preview),/matching preview/);
  await assert.rejects(api.applySparkWelcome(f.user,'welcome: Hello',true,null),/matching preview/);
  await assert.rejects(api.applySparkWelcome(f.user,'welcome: Hello',false,preview),/matching preview/);
  assert.deepEqual(f.calls,[]);
  await api.applySparkWelcome(f.user,'welcome: Hello',true,preview);
  assert.equal(f.calls.length,1); assert.equal(f.calls[0].data.welcome,'Hello');
});

test('a transaction callback retried after an account switch does not start another read',async()=>{
  const f=fixture(); let reads=0;
  const tx={get:async()=>{reads++;return {exists:()=>false}},set:()=>f.calls.push('set')};
  f.ops.runTransaction=async(_db,callback)=>{await callback(tx);f.switchUser();return callback(tx);};
  await assert.rejects(createSparkAdminService({},f.ops).inviteSparkDelegate(f.user,'target@gmail.com',true,f.guard),/account changed/);
  assert.equal(reads,1);assert.deepEqual(f.calls,['set']); // staged in the abandoned transaction, not an acknowledgement
});
