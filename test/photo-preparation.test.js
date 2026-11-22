import test from 'node:test';
import assert from 'node:assert/strict';
import {photoDimensions,preparePhoto} from '../src/photo-preparation.js';
function png(w=100,h=80) {
  const b=new Uint8Array(33);b.set([137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82]);
  const view=new DataView(b.buffer);view.setUint32(16,w);view.setUint32(20,h);return b;
}
function jpeg(w=100,h=80) {
  return new Uint8Array([255,216,255,224,0,4,0,0,255,192,0,8,8,h>>8,h&255,w>>8,w&255,1,255,218,0,2,255,217]);
}
const output=()=>new Uint8Array([255,216,255,1,255,217]);
test('dimensions parse bounded PNG and JPEG headers without decoding',()=>{
  assert.deepEqual(photoDimensions(png()),{width:100,height:80,type:'image/png'});
  assert.deepEqual(photoDimensions(jpeg()),{width:100,height:80,type:'image/jpeg'});
  for(const invalid of [new Uint8Array(),png(0,80),png(4097,1),png(4000,4000),jpeg(0,3),jpeg(5000,2),jpeg().slice(0,10),new Uint8Array([255,216,255,218,0,2,255,217]),new Uint8Array(4*1024*1024+1)]) assert.throws(()=>photoDimensions(invalid));
  const doubleFrame=jpeg();const extra=new Uint8Array([...doubleFrame.slice(0,18),...doubleFrame.slice(8,18),...doubleFrame.slice(18)]);assert.throws(()=>photoDimensions(extra));
});
test('invalid or oversized input is rejected before opening decoder',async()=>{
  let decoded=0;const opts={decode:async()=>{decoded++;},createCanvas:()=>{throw Error('unexpected');}};
  await assert.rejects(preparePhoto(new Blob([png(4097,1)]),opts));
  await assert.rejects(preparePhoto(new Blob([new Uint8Array(4*1024*1024+1)]),opts));
  await assert.rejects(preparePhoto(new Blob([new Uint8Array([1,2,3])]),opts));
  assert.equal(decoded,0);
});
test('output is bounded, cropped and reencoded, and transient image resources are closed',async()=>{
  let closed=0,formats=[],draw=[],canvas;
  const createCanvas=()=>canvas={width:0,height:0,getContext:()=>({fillRect(){},drawImage(...args){draw.push(args);}}),toBlob:(callback,type,quality)=>{formats.push({type,quality});callback(new Blob([output()],{type}));}};
  const result=await preparePhoto(new Blob([png()]),{decode:async(blob,options)=>{assert.equal(blob.type,'image/png');assert.equal(options.imageOrientation,'from-image');return {width:100,height:80,close(){closed++;}};},createCanvas});
  assert.deepEqual(result,output()); assert.equal(closed,1);assert.equal(draw.length,1);
  assert.deepEqual(draw[0].slice(1),[10,0,80,80,0,0,192,192]);
  assert.equal(canvas.width,0);assert.equal(canvas.height,0);assert.equal(formats[0].type,'image/jpeg');
});
test('decoder failure and size limit fail closed and release image resources',async()=>{
  let closed=0,canvas;const decode=async()=>({width:100,height:80,close(){closed++;}});
  await assert.rejects(preparePhoto(new Blob([png()]),{decode,createCanvas:()=>canvas={getContext:()=>({fillRect(){},drawImage(){}}),toBlob:callback=>callback(new Blob([new Uint8Array(40000)],{type:'image/jpeg'}))}}),/could not be reduced/);
  assert.equal(closed,1);assert.equal(canvas.width,0);
  await assert.rejects(preparePhoto(new Blob([png()]),{decode:async()=>({width:99,height:80,close(){closed++;}})}),/dimensions/);
  assert.equal(closed,2);
});
