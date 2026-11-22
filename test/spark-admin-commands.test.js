import test from 'node:test';
import assert from 'node:assert/strict';
import { parseSparkCommand, validSparkTarget } from '../src/spark-admin-commands.js';
test('only explicit bounded welcome commands are proposed', () => {
  assert.deepEqual(parseSparkCommand('welcome: Hello everyone'), {action:'set_welcome',value:'Hello everyone'});
  assert.deepEqual(parseSparkCommand('স্বাগতম: সবাইকে স্বাগতম'), {action:'set_welcome',value:'সবাইকে স্বাগতম'});
  for(const input of ['delete all messages', 'welcome:', 'welcome: '+ 'x'.repeat(161), 'please welcome: hello']) assert.equal(parseSparkCommand(input),null);
});
test('admin invitations accept only exact lower-case Gmail addresses', () => {
  assert.equal(validSparkTarget('example.user@gmail.com'),true);
  for(const input of ['foo@example.com','FOO@gmail.com','a@gmail.com.evil.com','',null]) assert.equal(validSparkTarget(input),false);
});

test('experimental Spark rules preserve the existing chat and Functions rules', async () => {
  const { readFileSync } = await import('node:fs');
  const base = readFileSync('firestore.rules','utf8');
  const spark = readFileSync('firestore.spark-admin.rules','utf8');
  const start = '    // Public display settings can only be changed by the trusted backend.';
  assert.notEqual(base.indexOf(start),-1); assert.notEqual(spark.indexOf(start),-1);
  assert.equal(spark.slice(spark.indexOf(start)),base.slice(base.indexOf(start)));
});

test('only narrowly defined English and Bangla requests with exact quoted text are previewed', () => {
  for (const input of ['set the welcome message to "Hello everyone"','Please change welcome to \'Hello everyone\'.','UPDATE WELCOME MESSAGE TO “Hello everyone”']) {
    assert.deepEqual(parseSparkCommand(input),{action:'set_welcome',value:'Hello everyone'});
  }
  for (const input of ['স্বাগত বার্তাটি “সবাইকে স্বাগতম” করে দাও','স্বাগত বার্তা "সবাইকে স্বাগতম" করুন।','স্বাগতম ‘সবাইকে স্বাগতম’ করো']) {
    assert.deepEqual(parseSparkCommand(input),{action:'set_welcome',value:'সবাইকে স্বাগতম'});
  }
  const rejected = [
    'change admin to "bob"', 'delete chat and set welcome to "hi"',
    'set welcome to "hi" and delete chats', 'set welcome to hi',
    'set welcome to "hi\'', 'set welcome to ""', 'set welcome to "   "',
    'স্বাগত বার্তা "হাই" করে দাও তারপর অ্যাকাউন্ট মুছো',
    'স্বাগত বার্তা হাই করে দাও', `set welcome to "${'x'.repeat(161)}"`,
  ];
  for (const input of rejected) assert.equal(parseSparkCommand(input),null, input);
});

test('quoted requests preserve the exact confirmed text, including HTML as plain text', () => {
  assert.deepEqual(parseSparkCommand('set welcome to "<script>alert(1)</script>"'),{action:'set_welcome',value:'<script>alert(1)</script>'});
  assert.equal(parseSparkCommand('set welcome to "first\nsecond"'),null);
  assert.equal(parseSparkCommand(null),null);
});
