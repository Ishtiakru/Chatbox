import test from 'node:test';
import assert from 'node:assert/strict';
import { createLiveDrafts } from '../src/live-drafts.js';

test('drafts and safe error messages are independent by conversation', () => {
  const d = createLiveDrafts();
  d.setText('first','unsent'); d.setError('first','Send failed');
  d.setText('second','another draft');
  assert.deepEqual(d.read('first'),{text:'unsent',error:'Send failed'});
  assert.deepEqual(d.read('second'),{text:'another draft',error:''});
  const copy=d.read('first'); copy.text='altered';
  assert.equal(d.read('first').text,'unsent');
});

test('an acknowledgement clears only the matching original draft', () => {
  const d = createLiveDrafts();
  d.setText('first','old'); d.setText('second','private');
  d.setText('first','newer');
  assert.equal(d.acknowledge('first','old'),false);
  assert.equal(d.read('first').text,'newer');
  assert.equal(d.acknowledge('first','newer'),true);
  assert.deepEqual(d.read('first'),{text:'',error:''});
  assert.equal(d.read('second').text,'private');
});

test('rejections preserve text, retries clear only errors and reset removes private content', () => {
  const d=createLiveDrafts();
  d.setText('first','draft'); d.setError('first','Rejected');
  d.setError('first',''); assert.equal(d.read('first').text,'draft');
  d.setError('first','Rejected again'); d.clear();
  assert.deepEqual(d.read('first'),{text:'',error:''});
});

test('a stale completion cannot recreate or clear a different account draft when caller scopes its generation', () => {
  const d=createLiveDrafts(); let generation=0;
  d.setText('thread','old account');
  const sendGeneration=generation;
  generation++; d.clear(); d.setText('thread','new account');
  if(sendGeneration===generation) d.acknowledge('thread','old account');
  assert.equal(d.read('thread').text,'new account');
});

test('rejects invalid targets and oversized text', () => {
  const d=createLiveDrafts();
  for(const bad of ['', 'a/b', 'x'.repeat(129)]) assert.throws(()=>d.setText(bad,'draft'));
  assert.throws(()=>d.setText('ok','x'.repeat(2001)));
  assert.throws(()=>d.setText('ok',null));
  assert.deepEqual(d.read(null),{text:'',error:''});
});
