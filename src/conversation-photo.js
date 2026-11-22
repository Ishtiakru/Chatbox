// Review-only service for an isolated two-account Firestore experiment. Not
// connected to the UI or production rules. Every copy is per-conversation;
// access, membership, blocking and size limits must also be enforced by rules.
import {Bytes,doc,setDoc,getDocFromServer,deleteDoc,serverTimestamp} from 'firebase/firestore';
const MAX_PHOTO_BYTES=32768;
const pathPart=value=>typeof value==='string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);

// This is a bounds/signature check, NOT full image validation or decoding.
// Never treat the result as trustworthy markup or proof of a valid JPEG.
export function hasBoundedJpegSignature(value) {
  return value instanceof Uint8Array && value.length >= 5 && value.length <= MAX_PHOTO_BYTES
    && value[0]===0xff && value[1]===0xd8 && value[2]===0xff
    && value[value.length-2]===0xff && value[value.length-1]===0xd9;
}
export function createConversationPhotoService({db,guard,api={doc,setDoc,getDocFromServer,deleteDoc,serverTimestamp,bytesFrom:value=>Bytes.fromUint8Array(value)}}) {
  if (!db || typeof guard?.run !== 'function' || !api || ['doc','setDoc','getDocFromServer','deleteDoc','serverTimestamp','bytesFrom'].some(name=>typeof api[name] !== 'function')) throw new Error('Photo dependencies are required.');
  const ref=(conversationId,ownerId)=>{
    if (!pathPart(conversationId) || !pathPart(ownerId)) throw new Error('Invalid photo path.');
    return api.doc(db,'conversations',conversationId,'sharedPhotos',ownerId);
  };
  return {
    async put(conversationId,ownerId,jpeg) {
      const target=ref(conversationId,ownerId);
      if (!hasBoundedJpegSignature(jpeg)) throw new Error('Invalid photo.');
      const copy=jpeg.slice();
      // An already sent write cannot be revoked; the backend rules must check
      // ownership and membership at commit time.
      return guard.run(()=>api.setDoc(target,{ownerId,jpeg:api.bytesFrom(copy),updatedAt:api.serverTimestamp()}));
    },
    async get(conversationId,ownerId) {
      const target=ref(conversationId,ownerId);
      const snap=await guard.run(()=>api.getDocFromServer(target));
      return guard.run(()=>{
        if (snap?.metadata?.fromCache !== false || snap?.metadata?.hasPendingWrites !== false) throw new Error('Photo is not server-confirmed.');
        if (!snap?.exists()) return null;
        const value=snap.data();
        if (value?.ownerId !== ownerId || typeof value?.jpeg?.toUint8Array !== 'function') throw new Error('Invalid photo.');
        const jpeg=value.jpeg.toUint8Array();
        if (!hasBoundedJpegSignature(jpeg)) throw new Error('Invalid photo.');
        return jpeg.slice();
      });
    },
    async remove(conversationId,ownerId) {
      const target=ref(conversationId,ownerId);
      return guard.run(()=>api.deleteDoc(target));
    },
  };
}
