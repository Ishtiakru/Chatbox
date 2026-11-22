// Local CI test only: Auth and Firestore must both be emulators. Two synthetic
// browser accounts share one Chromium process and fake media devices; this is
// not an actual multi-device, internet, TURN, or production Firebase test.
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import puppeteer from 'puppeteer-core';

if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080' || process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:9099') {
  throw new Error('This test requires the local demo Auth and Firestore emulators.');
}
if (!process.env.CHROME_PATH) throw new Error('Set CHROME_PATH to a local Chromium executable.');
let server, browser;
try {
  server = await createServer({configFile:false,server:{host:'127.0.0.1',port:4175,strictPort:true},logLevel:'error'});
  await server.listen();
  browser = await puppeteer.launch({executablePath:process.env.CHROME_PATH, headless:true, args:['--no-sandbox','--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream']});
  const page = await browser.newPage();
  const errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto('http://127.0.0.1:4175/integration/browser-call-harness.html',{waitUntil:'load',timeout:30000});
  await page.waitForFunction(() => typeof window.runLocalCallHarness === 'function',{timeout:30000});
  const result = await page.evaluate(() => window.runLocalCallHarness());
  assert.equal(result.connected,true);
  assert.equal(result.consentBeforeMedia,true);
  assert.equal(result.localControls,true);
  assert.deepEqual(result.requests,[1,1]);
  assert.equal(result.trackCount,4);
  assert.equal(result.tracksStopped,true);
  assert.deepEqual(result.errors,[]);
  assert.deepEqual(errors,[]);
  console.log('Synthetic same-browser video call: consent, media, controls and hangup passed.');
} finally {
  await browser?.close();
  await server?.close();
}
