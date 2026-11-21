// Default-off adapter for the unfinished blocking lab.
import { getFirestore } from 'firebase/firestore';
import { app } from './firebase.js';
import { createBlockController } from './block-controller.js';
import { watchOwnBlocks, blockAndClose, unblock } from './block-service.js';

export function createBrowserBlockController(onChange) {
  const db=getFirestore(app);
  return createBlockController({
    onChange,
    subscribe:(uid,next,fail)=>watchOwnBlocks(db,uid,next,fail),
    block:(uid,peer,conversation)=>blockAndClose(db,uid,peer,conversation),
    unblock:(uid,peer)=>unblock(db,uid,peer),
  });
}
