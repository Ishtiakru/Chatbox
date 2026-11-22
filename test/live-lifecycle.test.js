import test from 'node:test';
import assert from 'node:assert/strict';
import { disposeLiveResources } from '../src/live-lifecycle.js';

test('live teardown attempts all resources even if a cleanup throws', () => {
  const released=[];
  disposeLiveResources({
    callController:{dispose(){released.push('call');throw new Error('broken call');}},
    stopConversations(){released.push('conversations');throw new Error('broken listener');},
    messageFeed:{stop(){released.push('messages');}},
    peerProfileController:{dispose(){released.push('profile');}},
  });
  assert.deepEqual(released,['call','conversations','messages','profile']);
  assert.doesNotThrow(()=>disposeLiveResources({}));
});
