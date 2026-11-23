/**
 * Phase 0 Dirty Dozen Security TDD Test Suite for Chatbox Firestore Rules.
 * Covers all 12 adversarial attack vectors against firestore.rules.
 */
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { doc, getDoc, getDocs, collection, setDoc, updateDoc, serverTimestamp } from 'firebase/firestore';

export async function runSecurityRulesTests() {
  const testEnv = await initializeTestEnvironment({
    projectId: 'chatbox-security-tdd',
    firestore: {
      rules: readFileSync('firestore.rules', 'utf8'),
    },
  });

  const userA = testEnv.authenticatedContext('userA', {
    email: 'alice@gmail.com',
    email_verified: true,
    firebase: { sign_in_provider: 'google.com' },
    auth_time: Math.floor(Date.now() / 1000),
  });
  const unverifiedA = testEnv.authenticatedContext('userA', {
    email: 'alice@gmail.com',
    email_verified: false,
    firebase: { sign_in_provider: 'google.com' },
  });
  const userB = testEnv.authenticatedContext('userB', {
    email: 'bob@gmail.com',
    email_verified: true,
    firebase: { sign_in_provider: 'google.com' },
    auth_time: Math.floor(Date.now() / 1000),
  });

  // 1. Unverified Token Spoof
  await assertFails(
    setDoc(doc(unverifiedA.firestore(), 'profiles/userA'), { uid: 'userA', displayName: 'Alice' })
  );
  // 2. Profile Impersonation
  await assertFails(
    setDoc(doc(userA.firestore(), 'profiles/userB'), { uid: 'userB', displayName: 'Spoof' })
  );
  // 3. Shadow Field Injection
  await assertFails(
    setDoc(doc(userA.firestore(), 'profiles/userA'), { uid: 'userA', displayName: 'Alice', isAdmin: true })
  );
  // 4. Directory Scraping
  await assertFails(getDocs(collection(userA.firestore(), 'profiles')));
  // 5. Self-Block
  await assertFails(
    setDoc(doc(userA.firestore(), 'userBlocks/userA/blocked/userA'), { createdAt: serverTimestamp() })
  );

  // Seed valid profiles & pending conversation
  await assertSucceeds(
    setDoc(doc(userA.firestore(), 'profiles/userA'), { uid: 'userA', displayName: 'Alice' })
  );
  await assertSucceeds(
    setDoc(doc(userB.firestore(), 'profiles/userB'), { uid: 'userB', displayName: 'Bob' })
  );

  // 6. Bypassing Consent Status on Create
  await assertFails(
    setDoc(doc(userA.firestore(), 'conversations/cBad'), {
      participants: ['userA', 'userB'],
      createdBy: 'userA',
      status: 'active',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    setDoc(doc(userA.firestore(), 'conversations/c1'), {
      participants: ['userA', 'userB'],
      createdBy: 'userA',
      status: 'pending',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // 7. Creator Self-Accept
  await assertFails(
    updateDoc(doc(userA.firestore(), 'conversations/c1'), {
      status: 'active',
      updatedAt: serverTimestamp(),
    })
  );

  // 9a. Messaging in Pending Conversation
  await assertFails(
    setDoc(doc(userA.firestore(), 'conversations/c1/messages/m0'), {
      senderId: 'userA',
      body: 'Premature',
      sentAt: serverTimestamp(),
    })
  );

  // User B accepts
  await assertSucceeds(
    updateDoc(doc(userB.firestore(), 'conversations/c1'), {
      status: 'active',
      updatedAt: serverTimestamp(),
    })
  );

  // 10. Arbitrary External Media URL
  await assertFails(
    setDoc(doc(userA.firestore(), 'conversations/c1/messages/m1'), {
      senderId: 'userA',
      body: '',
      imageUrl: 'https://evil.example.com/payload.png',
      sentAt: serverTimestamp(),
    })
  );

  // 11. Timestamp Manipulation
  await assertFails(
    setDoc(doc(userA.firestore(), 'conversations/c1/messages/m2'), {
      senderId: 'userA',
      body: 'Forged time',
      sentAt: new Date('2020-01-01T00:00:00Z'),
    })
  );

  // Close & 8. Terminal State Reopen
  await assertSucceeds(
    updateDoc(doc(userA.firestore(), 'conversations/c1'), {
      status: 'closed',
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    updateDoc(doc(userB.firestore(), 'conversations/c1'), {
      status: 'active',
      updatedAt: serverTimestamp(),
    })
  );

  // 12. Admin Privilege Escalation
  await assertFails(
    setDoc(doc(userA.firestore(), 'sparkAdmin/current'), {
      uid: 'userA',
      email: 'alice@gmail.com',
      pendingEmail: '',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    setDoc(doc(userA.firestore(), 'settings/sparkTheme'), {
      value: 'lavender',
      updatedBy: 'userA',
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(getDoc(doc(userA.firestore(), 'profiles/userB')));

  await testEnv.cleanup();
}
