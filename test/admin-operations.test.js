import test from 'node:test';
import assert from 'node:assert/strict';
import { updateWelcome, changeAdministrator } from '../functions/admin-operations.js';

function fakeDb(adminUid = 'owner') {
  const writes = [];
  return {
    writes,
    doc: path => path,
    collection: path => ({ doc: () => `${path}/event` }),
    async runTransaction(fn) {
      const pending = [];
      const tx = {
        get: async ref => ({data: () => ref === 'system/admin' ? {uid:adminUid} : undefined}),
        set: (...args) => pending.push(['set',...args]),
        create: (...args) => pending.push(['create',...args]),
      };
      const result = await fn(tx); writes.push(...pending); return result;
    },
  };
}
const stamp = () => 'server-time';

test('only the current admin can apply a supported, confirmed welcome update and audit it', async () => {
  const db = fakeDb();
  const proposal = {action:'set_welcome',value:' Welcome '};
  await assert.rejects(updateWelcome(db, 'stranger', proposal, true, stamp), {code:'permission-denied'});
  await assert.rejects(updateWelcome(db, 'owner', proposal, false, stamp), {code:'failed-precondition'});
  await assert.rejects(updateWelcome(db, 'owner', {action:'execute',value:'x'}, true, stamp), {code:'invalid-argument'});
  assert.equal(db.writes.length, 0);
  await updateWelcome(db, 'owner', proposal, true, stamp);
  assert.deepEqual(db.writes.map(write => write[1]), ['settings/public', 'adminAudit/event']);
  assert.equal(db.writes[0][2].welcome, 'Welcome');
  assert.equal(db.writes[1][2].actorUid, 'owner');
});

test('admin transfer requires current admin, confirmation, and verified active target', async () => {
  const db = fakeDb();
  const target = {uid:'next',emailVerified:true,disabled:false};
  await assert.rejects(changeAdministrator(db, 'stranger', target, true, stamp), {code:'permission-denied'});
  await assert.rejects(changeAdministrator(db, 'owner', target, false, stamp), {code:'failed-precondition'});
  await assert.rejects(changeAdministrator(db, 'owner', {...target,emailVerified:false}, true, stamp), {code:'failed-precondition'});
  await assert.rejects(changeAdministrator(db, 'owner', {...target,disabled:true}, true, stamp), {code:'failed-precondition'});
  await assert.rejects(changeAdministrator(db, 'owner', {...target,uid:'owner'}, true, stamp), {code:'failed-precondition'});
  assert.equal(db.writes.length, 0);
  await changeAdministrator(db, 'owner', target, true, stamp);
  assert.deepEqual(db.writes.map(write => write[1]), ['system/admin', 'adminAudit/event']);
  assert.equal(db.writes[0][2].uid, 'next');
  assert.equal(db.writes[1][2].actorUid, 'owner');
});
