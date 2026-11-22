import test from 'node:test';
import assert from 'node:assert/strict';
import { changeAdminTarget } from '../src/admin-target-confirmation.js';

test('changing a confirmed admin target requires fresh confirmation', () => {
  const admin = {target:'alice@gmail.com',confirm:true};
  assert.equal(changeAdminTarget(admin,'bob@gmail.com'),true);
  assert.deepEqual(admin,{target:'bob@gmail.com',confirm:false});
  admin.confirm = true;
  assert.equal(changeAdminTarget(admin,'bob@gmail.com'),false);
  assert.equal(admin.confirm,true);
  changeAdminTarget(admin,'alice@gmail.com');
  assert.equal(admin.confirm,false);
});
