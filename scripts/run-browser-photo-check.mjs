// Browser image decoding/encoding against generated files only; no Firebase.
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import puppeteer from 'puppeteer-core';
if (!process.env.CHROME_PATH) throw new Error('Set CHROME_PATH to a local Chromium executable.');
let server,browser;
try {
  server=await createServer({configFile:false,server:{host:'127.0.0.1',port:4176,strictPort:true},logLevel:'error'});
  await server.listen();
  browser=await puppeteer.launch({executablePath:process.env.CHROME_PATH,headless:true,args:['--no-sandbox']});
  const page=await browser.newPage(), errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto('http://127.0.0.1:4176/integration/browser-photo-harness.html',{waitUntil:'load',timeout:30000});
  await page.waitForFunction(()=>typeof window.runPhotoHarness==='function',{timeout:30000});
  const result=await page.evaluate(()=>window.runPhotoHarness());
  assert.deepEqual(result,{pngConverted:true,jpegConverted:true,syntheticExifRemoved:true,cropped:true,underLimit:true});
  assert.deepEqual(errors,[]);
  console.log('Generated PNG/JPEG converted, cropped, bounded, and synthetic EXIF removed.');
} finally {await browser?.close();await server?.close();}
