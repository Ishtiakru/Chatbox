// Local-emulator candidate only. Never deploy or select these rules.
// Stores small per-conversation photo copies in Firestore, not Cloud Storage.
// Does not provide image validation, retention/abuse controls or a full profile.
import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
import {restrictedCombinedTestRules} from './generate-isolated-combined-rules.mjs';

export function isolatedPhotoRules(source, firstUid, secondUid) {
  const base=restrictedCombinedTestRules(source,firstUid,secondUid);
  const marker='      // Experimental call signaling; deliberately not selected by firebase.json.';
  if (base.split(marker).length !== 2) throw new Error('Unexpected combined template.');
  const addition=`      // Photo copies for the two-account lab only; not production rules.
      // Type and byte count do not prove an image is a valid or safe JPEG.
      match /sharedPhotos/{ownerId} {
        allow get: if conversationActive(conversationId);
        allow list: if false;
        allow create, update: if conversationActive(conversationId)
          && request.auth.uid == ownerId
          && request.resource.data.keys().hasAll(['ownerId', 'jpeg', 'updatedAt'])
          && request.resource.data.keys().hasOnly(['ownerId', 'jpeg', 'updatedAt'])
          && request.resource.data.ownerId == ownerId
          && request.resource.data.jpeg is bytes
          && request.resource.data.jpeg.size() > 0 && request.resource.data.jpeg.size() <= 32768
          && request.resource.data.updatedAt == request.time;
        // The owner can remove a copy even after closure or blocking.
        allow delete: if member(conversationId) && request.auth.uid == ownerId;
      }

`;
  return base.replace(marker,()=>addition+marker);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 4) throw new Error('Usage: node scripts/generate-isolated-photo-rules.mjs FIRST_UID SECOND_UID');
    process.stdout.write(isolatedPhotoRules(readFileSync(new URL('../firestore.combined-experimental.rules', import.meta.url),'utf8'),process.argv[2],process.argv[3]));
  } catch (error) { console.error(error.message); process.exitCode=1; }
}
