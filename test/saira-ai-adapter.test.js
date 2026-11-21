import test from 'node:test';
import assert from 'node:assert/strict';
import { askSairaWithModel } from '../src/saira-ai-adapter.js';
test('AI adapter sends only the submitted question and limits the answer', async () => {
  const inputs=[]; const model={ generateContent: async text => { inputs.push(text); return {response:{text:()=> 'a'.repeat(5000)}}; } };
  const result = await askSairaWithModel('  Hello Saira  ', model);
  assert.deepEqual(inputs,['Hello Saira']); assert.equal(result.answer.length,4000);
});
test('AI adapter rejects invalid input and unavailable answers', async () => {
  const model={generateContent:async()=>({response:{text:()=>''}})};
  await assert.rejects(askSairaWithModel('', model), /Invalid/);
  await assert.rejects(askSairaWithModel('x'.repeat(1001), model), /Invalid/);
  await assert.rejects(askSairaWithModel('hello', model), /No answer/);
});
