import test from 'node:test';
import assert from 'node:assert/strict';
import {createSparkAdminService} from '../src/spark-admin-data.js';

function listenerFixture() {
  let callback, failure, target, options, stopped=false;
  const ops={
    doc:(_db,...path)=>path.join('/'),
    onSnapshot:(ref,opts,next,fail)=>{
      target=ref;options=opts;callback=next;failure=fail;
      return ()=>{stopped=true;};
    },
  };
  const values=[], errors=[];
  const stop=createSparkAdminService({},ops).watchSparkWelcome(value=>values.push(value),error=>errors.push(error));
  return {emit:(text,metadata)=>callback({metadata,data:()=>text === undefined ? undefined : {welcome:text}}),fail:e=>failure(e),values,errors,stop,get target(){return target;},get options(){return options;},get stopped(){return stopped;}};
}

test('welcome listener waits for server-confirmed snapshots and metadata-only acknowledgements',()=>{
  const f=listenerFixture();
  assert.equal(f.target,'settings/sparkPublic');assert.deepEqual(f.options,{includeMetadataChanges:true});
  f.emit('Cached',{fromCache:true,hasPendingWrites:false});
  f.emit('Pending',{fromCache:false,hasPendingWrites:true});
  assert.deepEqual(f.values,[]);
  f.emit('Confirmed',{fromCache:false,hasPendingWrites:false});
  f.emit('Offline new value',{fromCache:true,hasPendingWrites:false});
  assert.deepEqual(f.values,['Confirmed']);
  f.emit('Confirmed later',{fromCache:false,hasPendingWrites:false});
  assert.deepEqual(f.values,['Confirmed','Confirmed later']);
  f.stop();assert.equal(f.stopped,true);
});

test('confirmed missing or invalid welcome is cleared and listener errors propagate',()=>{
  const f=listenerFixture();
  f.emit('x'.repeat(161),{fromCache:false,hasPendingWrites:false});
  f.emit(undefined,{fromCache:false,hasPendingWrites:false});
  f.emit(123,{fromCache:false,hasPendingWrites:false});
  assert.deepEqual(f.values,['','','']);
  const error=new Error('listener failed');f.fail(error);assert.equal(f.errors[0],error);
});
