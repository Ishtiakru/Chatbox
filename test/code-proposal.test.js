import test from 'node:test';
import assert from 'node:assert/strict';
import { codeSourceUrl, loadCodeSource, copyCodeProposal } from '../src/code-proposal.js';
const ref='a'.repeat(40);
function response(data, ok=true) {
  return {ok, body:new ReadableStream({start(c) { c.enqueue(new TextEncoder().encode(data)); c.close(); }})};
}
test('only public approved source paths and exact pinned commits are requested', async () => {
  const url=codeSourceUrl('src/live-view.js',ref);
  assert.equal(url,`https://api.github.com/repos/Ishtiakru/Chatbox/contents/src/live-view.js?ref=${ref}`);
  for (const path of ['../src/file.js','src/../test/file.js','src/.secret.js','src/file.js?x=1','.env','src/file.png','src//file.js','functions/../../.env','src/🚫.js']) assert.throws(()=>codeSourceUrl(path,ref));
  assert.throws(()=>codeSourceUrl('src/live-view.js','main'));
  let sent;
  const text = await loadCodeSource('src/live-view.js',ref,async (...args)=>{sent=args; return response('const x = "বাংলা";');});
  assert.equal(text,'const x = "বাংলা";');
  assert.equal(sent[0],url); assert.equal(sent[1].redirect,'error'); assert.equal(sent[1].credentials,'omit');
});
test('source fetch fails closed on errors, empty, binary or over-sized input', async () => {
  for (const r of [response('x',false), response(''), response('x\0y'), response('x'.repeat(24001))]) await assert.rejects(loadCodeSource('src/a.js',ref,async()=>r));
});
test('copy proposal is explicit, bounded and handles unavailable clipboard', async () => {
  let copied=''; assert.equal(await copyCodeProposal('<x>',{writeText:t=>{copied=t;}}),true); assert.equal(copied,'<x>');
  assert.equal(await copyCodeProposal('x'.repeat(50001),{writeText(){throw Error('bad')}}),false);
  assert.equal(await copyCodeProposal('hello',null),false);
  assert.equal(await copyCodeProposal('hello',{writeText(){return Promise.reject(Error('denied'));}}),false);
});
