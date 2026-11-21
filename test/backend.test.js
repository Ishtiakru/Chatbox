import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { cleanInput, validateProposal, parseProposal, responseText, isAdministrator } from '../functions/logic.js';
import { askProvider } from '../functions/provider.js';

test('only narrowly scoped proposals are allowed', () => {
  assert.deepEqual(validateProposal({action:'set_welcome', value:'  Hello!  '}), {action:'set_welcome',value:'Hello!'});
  assert.deepEqual(parseProposal('{"action":"unsupported"}'), {action:'unsupported'});
  for (const bad of [null, [], {action:'run_code', value:'evil'}, {action:'set_welcome',value:''}, {action:'set_welcome',value:'x'.repeat(161)}, {action:'set_welcome',value:'hello', extra:true}]) assert.throws(() => validateProposal(bad));
  assert.deepEqual(parseProposal('not JSON'), {action:'unsupported'});
});
test('identity checks require exact admin UID', () => {
  assert.equal(isAdministrator({uid:'owner'},'owner'), true);
  assert.equal(isAdministrator({uid:'owner'},'other'), false);
  assert.equal(isAdministrator(null,'owner'), false);
  assert.throws(() => cleanInput(' '.repeat(10)));
});
test('AI adapter sends only requested content and does not leak provider errors', async () => {
  let request;
  const fetchImpl = async (url, options) => { request = {url, ...options}; return {ok:true,json: async () => ({output:[{content:[{type:'output_text',text:'Hello!'}]}]})}; };
  assert.equal(await askProvider({key:'test-key',instructions:'system',input:'question',fetchImpl}), 'Hello!');
  assert.equal(JSON.parse(request.body).store, false);
  assert.equal(JSON.parse(request.body).input, 'question');
  await assert.rejects(askProvider({key:'test-key',instructions:'s',input:'q',fetchImpl:async () => ({ok:false})}), /provider request failed/);
  assert.equal(responseText({output:[]}), '');
});
test('release knowledge corresponds to the curated document', () => {
  const knowledge = JSON.parse(readFileSync('functions/knowledge.generated.json', 'utf8'));
  const content = readFileSync('docs/saira-knowledge.md', 'utf8').trim();
  const version = JSON.parse(readFileSync('package.json', 'utf8')).version;
  assert.equal(knowledge.version, version);
  assert.equal(knowledge.sha256, createHash('sha256').update(content).digest('hex'));
  assert.equal(knowledge.content, content);
});
