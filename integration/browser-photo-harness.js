// Local synthetic canvas-only image test; no real photo, Firebase or upload.
import {photoDimensions,preparePhoto} from '../src/photo-preparation.js';
const asBlob=(canvas,type)=>new Promise((resolve,reject)=>canvas.toBlob(blob=>blob?resolve(blob):reject(new Error('Image generation failed.')),type,0.9));
function taggedJpeg(original) {
  // Valid, synthetic EXIF ImageDescription field to test metadata removal.
  const text=new TextEncoder().encode('SYNTHETIC_PRIVATE_TAG\0');
  const exif=new Uint8Array(6+26+text.length);exif.set([69,120,105,102,0,0,73,73,42,0,8,0,0,0]);
  const view=new DataView(exif.buffer,6);view.setUint16(8,1,true);view.setUint16(10,0x010e,true);view.setUint16(12,2,true);view.setUint32(14,text.length,true);view.setUint32(18,26,true);view.setUint32(22,0,true);exif.set(text,32);
  const length=exif.length+2, output=new Uint8Array(original.length+exif.length+4);
  output.set(original.subarray(0,2));output.set([255,225,length>>8,length&255],2);output.set(exif,6);output.set(original.subarray(2),6+exif.length);
  return output;
}
const contains=(bytes,text)=>{
  const target=new TextEncoder().encode(text);
  for(let i=0;i<=bytes.length-target.length;i++) if(target.every((b,j)=>bytes[i+j]===b))return true;
  return false;
};
export async function run() {
  const canvas=document.createElement('canvas');canvas.width=360;canvas.height=240;
  const context=canvas.getContext('2d');
  context.fillStyle='#f00';context.fillRect(0,0,180,240);
  context.fillStyle='#00f';context.fillRect(180,0,180,240);
  const png=await asBlob(canvas,'image/png');
  const pngResult=await preparePhoto(png);
  const pngHeader=photoDimensions(pngResult);
  if(pngHeader.width!==192 || pngHeader.height!==192 || pngHeader.type!=='image/jpeg') throw Error('PNG conversion dimensions are wrong.');
  const jpeg=new Uint8Array(await (await asBlob(canvas,'image/jpeg')).arrayBuffer());
  const tagged=taggedJpeg(jpeg);
  if(!contains(tagged,'SYNTHETIC_PRIVATE_TAG')) throw Error('Synthetic metadata is missing.');
  const jpegResult=await preparePhoto(new Blob([tagged],{type:'image/jpeg'}));
  if(contains(jpegResult,'SYNTHETIC_PRIVATE_TAG') || contains(jpegResult,'Exif\0\0')) throw Error('Metadata was preserved.');
  if(jpegResult.length>32768) throw Error('Output is too large.');
  const resultBitmap=await createImageBitmap(new Blob([jpegResult],{type:'image/jpeg'}));
  try {
    if(resultBitmap.width!==192 || resultBitmap.height!==192)throw Error('JPEG dimensions are wrong.');
    const resultCanvas=document.createElement('canvas');resultCanvas.width=192;resultCanvas.height=192;
    const resultContext=resultCanvas.getContext('2d');resultContext.drawImage(resultBitmap,0,0);
    const left=resultContext.getImageData(24,96,1,1).data,right=resultContext.getImageData(168,96,1,1).data;
    if(left[0]<150 || left[2]>100 || right[2]<150 || right[0]>100)throw Error('Expected crop colors not present.');
  } finally {resultBitmap.close();}
  return {pngConverted:true,jpegConverted:true,syntheticExifRemoved:true,cropped:true,underLimit:true};
}
