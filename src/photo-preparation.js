// Local image preparation only. Not wired to UI or Firebase. A bounded header
// is not a substitute for a hardened decoder, backend validation or review.
import {hasBoundedJpegSignature} from './conversation-photo.js';
const MAX_FILE_BYTES=4 * 1024 * 1024;
const MAX_DIMENSION=4096;
const MAX_PIXELS=12_000_000;
const starts=(array,prefix)=>prefix.every((byte,i)=>array[i]===byte);
const uint16=(a,p)=>(a[p]<<8)|a[p+1];
const uint32=(a,p)=>((a[p]*0x1000000)+(a[p+1]<<16)+(a[p+2]<<8)+a[p+3]);
const validDimensions=(w,h)=>Number.isInteger(w) && Number.isInteger(h) && w>0 && h>0 && w<=MAX_DIMENSION && h<=MAX_DIMENSION && w*h<=MAX_PIXELS;

// Read PNG IHDR or JPEG SOF before invoking a decoder; reject unknown formats,
// excessive dimensions and conflicting JPEG frame headers. Decode can still
// fail or behave differently on different platforms.
export function photoDimensions(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.length<12 || bytes.length>MAX_FILE_BYTES) throw new Error('Invalid photo.');
  const png=[137,80,78,71,13,10,26,10];
  if (starts(bytes,png)) {
    if (bytes.length<33 || uint32(bytes,8)!==13 || !starts(bytes.subarray(12,16),[73,72,68,82])) throw new Error('Invalid photo.');
    const width=uint32(bytes,16),height=uint32(bytes,20);
    if (!validDimensions(width,height)) throw new Error('Photo dimensions are unsupported.');
    return {width,height,type:'image/png'};
  }
  if (!starts(bytes,[255,216])) throw new Error('Unsupported photo format.');
  const sof=new Set([0xc0,0xc1,0xc2,0xc3,0xc5,0xc6,0xc7,0xc9,0xca,0xcb,0xcd,0xce,0xcf]);
  let p=2, width=0,height=0,found=false;
  while (p<bytes.length) {
    if (bytes[p++]!==255) throw new Error('Invalid photo.');
    while (p<bytes.length && bytes[p]===255) p++;
    if (p>=bytes.length) throw new Error('Invalid photo.');
    const marker=bytes[p++];
    if (marker===0xd9 || marker===0xd8 || marker===0 || (marker>=0xd0 && marker<=0xd7)) throw new Error('Invalid photo.');
    if (marker===1) continue;
    if (p+2>bytes.length) throw new Error('Invalid photo.');
    const length=uint16(bytes,p);
    if (length<2 || p+length>bytes.length) throw new Error('Invalid photo.');
    if (sof.has(marker)) {
      if (found || length<8) throw new Error('Invalid photo.');
      height=uint16(bytes,p+3); width=uint16(bytes,p+5); found=true;
      if (!validDimensions(width,height)) throw new Error('Photo dimensions are unsupported.');
    }
    if (marker===0xda) {
      if (!found) throw new Error('Invalid photo.');
      return {width,height,type:'image/jpeg'};
    }
    p+=length;
  }
  throw new Error('Invalid photo.');
}
const canvasBlob=(canvas,quality)=>new Promise((resolve,reject)=>{
  try {canvas.toBlob(blob=>blob ? resolve(blob) : reject(new Error('Photo encoding failed.')),'image/jpeg',quality);}
  catch {reject(new Error('Photo encoding failed.'));}
});

// Converts a bounded local PNG or JPEG to a centered square JPEG with no input
// metadata copied. This is an experiment: output is not saved or sent here.
export async function preparePhoto(file, {decode=globalThis.createImageBitmap,createCanvas=()=>document.createElement('canvas')}={}) {
  if (!file || typeof file.arrayBuffer!=='function' || !Number.isInteger(file.size) || file.size<1 || file.size>MAX_FILE_BYTES || typeof decode!=='function' || typeof createCanvas!=='function') throw new Error('Photo preparation is unavailable or the file is too large.');
  const input=new Uint8Array(await file.arrayBuffer());
  if (input.length!==file.size) throw new Error('Invalid photo.');
  const header=photoDimensions(input);
  let image,canvas;
  try {
    image=await decode(new Blob([input],{type:header.type}),{imageOrientation:'from-image'});
    if (!validDimensions(image?.width,image?.height) || !((image.width===header.width && image.height===header.height) || (image.width===header.height && image.height===header.width))) throw new Error('Photo dimensions are unsupported.');
    canvas=createCanvas();
    if (!canvas || typeof canvas.getContext!=='function' || typeof canvas.toBlob!=='function') throw new Error('Photo preparation is unavailable.');
    const context=canvas.getContext('2d',{alpha:false});
    if (!context) throw new Error('Photo preparation is unavailable.');
    const side=Math.min(image.width,image.height), sx=(image.width-side)/2, sy=(image.height-side)/2;
    for (const dimension of [192,144,112]) {
      canvas.width=dimension;canvas.height=dimension;
      context.fillStyle='#fff';context.fillRect(0,0,dimension,dimension);
      context.drawImage(image,sx,sy,side,side,0,0,dimension,dimension);
      for (const quality of [0.82,0.7,0.55,0.4]) {
        const blob=await canvasBlob(canvas,quality);
        if (blob.type!=='image/jpeg' || blob.size>32768 || blob.size<5) continue;
        const jpeg=new Uint8Array(await blob.arrayBuffer());
        if (hasBoundedJpegSignature(jpeg)) return jpeg;
      }
    }
    throw new Error('Photo could not be reduced to the required size.');
  } finally {
    try {image?.close?.();} catch {}
    if (canvas) {canvas.width=0;canvas.height=0;}
  }
}
