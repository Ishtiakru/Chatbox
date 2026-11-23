const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";
const OWNER_EMAIL = "ishtiakahmedru@gmail.com";
const DELEGATE_EMAIL = "delegate.user@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function googleClaims(email, emailVerified = true, authTimeOffsetSec = -10) {
  return {
    email,
    email_verified: emailVerified,
    firebase: { sign_in_provider: "google.com" },
    auth_time: Math.floor(Date.now() / 1000) + authTimeOffsetSec,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write profiles, conversations, messages, blocks, or settings", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("profiles").doc(ALICE_UID).get());
  await assertFails(unauth.collection("profiles").doc(ALICE_UID).set({ uid: ALICE_UID, displayName: "Alice" }));
  await assertFails(unauth.collection("conversations").where("participants", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("settings").doc("sparkPublic").get());
  await assertFails(unauth.collection("userBlocks").doc(ALICE_UID).collection("blocked").get());
});

test("Profiles allow self write and direct UID get, but reject list, cross-user write, and extra fields", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(alice.collection("profiles").doc(ALICE_UID).set({
    uid: ALICE_UID,
    displayName: "Alice",
  }));
  await assertSucceeds(bob.collection("profiles").doc(ALICE_UID).get());

  // Listing profiles must fail
  await assertFails(bob.collection("profiles").get());
  // Cross-user write must fail
  await assertFails(bob.collection("profiles").doc(ALICE_UID).set({
    uid: ALICE_UID,
    displayName: "Spoofed",
  }));
  // Ghost field / @DocumentId field must fail
  await assertFails(alice.collection("profiles").doc(ALICE_UID).set({
    id: ALICE_UID,
    uid: ALICE_UID,
    displayName: "Alice",
  }));
});

test("Conversations consent lifecycle, message creation, pagination query, and third-party isolation", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(alice.collection("profiles").doc(ALICE_UID).set({ uid: ALICE_UID, displayName: "Alice" }));
  await assertSucceeds(bob.collection("profiles").doc(BOB_UID).set({ uid: BOB_UID, displayName: "Bob" }));

  const now = new Date(Date.now() - 1000);
  // Create pending conversation request
  await assertSucceeds(alice.collection("conversations").doc("conv_1").set({
    participants: [ALICE_UID, BOB_UID],
    createdBy: ALICE_UID,
    status: "pending",
    createdAt: now,
    updatedAt: now,
  }));

  // Filtered query succeeds; unfiltered query fails
  await assertSucceeds(alice.collection("conversations").where("participants", "array-contains", ALICE_UID).get());
  await assertFails(alice.collection("conversations").get());

  // Initiator cannot self-accept
  await assertFails(alice.collection("conversations").doc("conv_1").update({
    status: "active",
    updatedAt: new Date(Date.now() - 500),
  }));

  // Messaging is blocked while pending
  await assertFails(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").set({
    senderId: ALICE_UID,
    body: "Too early",
    sentAt: new Date(Date.now() - 500),
  }));

  // Recipient accepts
  await assertSucceeds(bob.collection("conversations").doc("conv_1").update({
    status: "active",
    updatedAt: new Date(Date.now() - 400),
  }));

  // Active conversation allows messaging, image attachments, and ordered read
  await assertSucceeds(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").set({
    senderId: ALICE_UID,
    body: "Hello Bob!",
    sentAt: new Date(Date.now() - 300),
  }));
  await assertSucceeds(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_img_only").set({
    senderId: ALICE_UID,
    body: "",
    imageUrl: "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0251556445.firebasestorage.app/o/chat_media%2Fconv_1%2Falice%2Fphoto.jpg?alt=media&token=abc",
    sentAt: new Date(Date.now() - 280),
  }));
  await assertSucceeds(bob.collection("conversations").doc("conv_1").collection("messages").doc("msg_img_caption").set({
    senderId: BOB_UID,
    body: "Check this out",
    imageUrl: "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0251556445.firebasestorage.app/o/chat_media%2Fconv_1%2Fbob%2Fphoto2.jpg?alt=media&token=def",
    sentAt: new Date(Date.now() - 260),
  }));
  await assertSucceeds(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_voice_only").set({
    senderId: ALICE_UID,
    body: "",
    audioUrl: "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0251556445.firebasestorage.app/o/chat_media%2Fconv_1%2Falice%2Fvoice_1.m4a?alt=media&token=ghi",
    audioDurationMs: 5400,
    sentAt: new Date(Date.now() - 255),
  }));
  await assertSucceeds(bob.collection("conversations").doc("conv_1").collection("messages").doc("msg_voice_caption").set({
    senderId: BOB_UID,
    body: "Listen to this",
    audioUrl: "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0251556445.firebasestorage.app/o/chat_media%2Fconv_1%2Fbob%2Fvoice_2.m4a?alt=media&token=jkl",
    audioDurationMs: 12000,
    sentAt: new Date(Date.now() - 250),
  }));
  await assertSucceeds(
    bob.collection("conversations").doc("conv_1").collection("messages").orderBy("sentAt").limitToLast(100).get()
  );

  // Empty message without imageUrl or audioUrl rejected
  await assertFails(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_empty").set({
    senderId: ALICE_UID,
    body: "",
    sentAt: new Date(Date.now() - 240),
  }));

  // Non-Firebase-Storage or HTTP imageUrl/audioUrl rejected
  await assertFails(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_bad_url").set({
    senderId: ALICE_UID,
    body: "Bad image",
    imageUrl: "http://evil.example.com/photo.jpg",
    sentAt: new Date(Date.now() - 220),
  }));
  await assertFails(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_bad_audio_url").set({
    senderId: ALICE_UID,
    body: "",
    audioUrl: "https://evil.example.com/voice.m4a",
    audioDurationMs: 3000,
    sentAt: new Date(Date.now() - 215),
  }));

  // Orphan audioDurationMs without audioUrl or negative/oversized duration rejected
  await assertFails(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_orphan_dur").set({
    senderId: ALICE_UID,
    body: "Text only",
    audioDurationMs: 3000,
    sentAt: new Date(Date.now() - 210),
  }));
  await assertFails(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_oversize_dur").set({
    senderId: ALICE_UID,
    body: "",
    audioUrl: "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0251556445.firebasestorage.app/o/chat_media%2Fconv_1%2Falice%2Fvoice_long.m4a?alt=media&token=mno",
    audioDurationMs: 400000,
    sentAt: new Date(Date.now() - 205),
  }));

  // Future timestamp rejected
  await assertFails(alice.collection("conversations").doc("conv_1").collection("messages").doc("msg_future").set({
    senderId: ALICE_UID,
    body: "Future",
    sentAt: new Date(Date.now() + 60000),
  }));

  // Outsider Charlie cannot read or write
  await assertFails(charlie.collection("conversations").doc("conv_1").get());
  await assertFails(charlie.collection("conversations").doc("conv_1").collection("messages").get());
  await assertFails(charlie.collection("conversations").doc("conv_1").collection("messages").doc("msg_2").set({
    senderId: CHARLIE_UID,
    body: "Intruder",
    sentAt: new Date(Date.now() - 200),
  }));

  // Closing locks terminal state
  await assertSucceeds(alice.collection("conversations").doc("conv_1").update({
    status: "closed",
    updatedAt: new Date(Date.now() - 100),
  }));
  await assertFails(bob.collection("conversations").doc("conv_1").update({
    status: "active",
    updatedAt: new Date(Date.now() - 50),
  }));
});

test("Blocking prevents messages and new requests while remaining private to owner", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(alice.collection("profiles").doc(ALICE_UID).set({ uid: ALICE_UID, displayName: "Alice" }));
  await assertSucceeds(bob.collection("profiles").doc(BOB_UID).set({ uid: BOB_UID, displayName: "Bob" }));

  const now = new Date(Date.now() - 2000);
  await assertSucceeds(alice.collection("conversations").doc("conv_block").set({
    participants: [ALICE_UID, BOB_UID],
    createdBy: ALICE_UID,
    status: "pending",
    createdAt: now,
    updatedAt: now,
  }));
  await assertSucceeds(bob.collection("conversations").doc("conv_block").update({
    status: "active",
    updatedAt: new Date(Date.now() - 1500),
  }));

  // Atomic block + close batch mirrors BlockRepository
  const batch = alice.batch();
  batch.set(alice.collection("userBlocks").doc(ALICE_UID).collection("blocked").doc(BOB_UID), {
    createdAt: new Date(Date.now() - 1000),
  });
  batch.update(alice.collection("conversations").doc("conv_block"), {
    status: "closed",
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertSucceeds(batch.commit());

  // Bob cannot read Alice's block list
  await assertFails(bob.collection("userBlocks").doc(ALICE_UID).collection("blocked").get());

  // New conversation request while blocked must fail
  await assertFails(bob.collection("conversations").doc("conv_after_block").set({
    participants: [BOB_UID, ALICE_UID],
    createdBy: BOB_UID,
    status: "pending",
    createdAt: new Date(Date.now() - 500),
    updatedAt: new Date(Date.now() - 500),
  }));

  // Unblock allows new request
  await assertSucceeds(alice.collection("userBlocks").doc(ALICE_UID).collection("blocked").doc(BOB_UID).delete());
  await assertSucceeds(bob.collection("conversations").doc("conv_after_unblock").set({
    participants: [BOB_UID, ALICE_UID],
    createdBy: BOB_UID,
    status: "pending",
    createdAt: new Date(Date.now() - 300),
    updatedAt: new Date(Date.now() - 300),
  }));
});

test("Spark Admin enforces verified Google owner, blocks spoofed email, and supports delegate lifecycle", async () => {
  const spoofedOwner = testEnv.authenticatedContext(ALICE_UID, googleClaims(OWNER_EMAIL, false)).firestore();
  const realOwner = testEnv.authenticatedContext(ALICE_UID, googleClaims(OWNER_EMAIL, true)).firestore();
  const delegateCtx = testEnv.authenticatedContext(BOB_UID, googleClaims(DELEGATE_EMAIL, true)).firestore();
  const outsider = testEnv.authenticatedContext(CHARLIE_UID, googleClaims("other@gmail.com", true)).firestore();

  const now = new Date(Date.now() - 1000);
  // Spoofed unverified email cannot bootstrap owner
  await assertFails(spoofedOwner.collection("sparkAdmin").doc("current").set({
    uid: ALICE_UID,
    email: OWNER_EMAIL,
    pendingEmail: "",
    createdAt: now,
    updatedAt: now,
  }));

  // Verified owner bootstraps sparkAdmin/current
  await assertSucceeds(realOwner.collection("sparkAdmin").doc("current").set({
    uid: ALICE_UID,
    email: OWNER_EMAIL,
    pendingEmail: "",
    createdAt: now,
    updatedAt: now,
  }));

  // Owner updates welcome, appearance, & app update notice
  await assertSucceeds(realOwner.collection("settings").doc("sparkPublic").set({
    welcome: "Welcome to Chatbox!",
    updatedBy: ALICE_UID,
    updatedAt: new Date(Date.now() - 800),
  }));
  await assertSucceeds(realOwner.collection("settings").doc("sparkTheme").set({
    value: "lavender",
    updatedBy: ALICE_UID,
    updatedAt: new Date(Date.now() - 700),
  }));
  await assertSucceeds(realOwner.collection("settings").doc("sparkUpdate").set({
    active: true,
    version: "1.1.0",
    note: "Voice notes and lavender branding",
    downloadUrl: "https://example.com/chatbox-v1.1.0.apk",
    updatedBy: ALICE_UID,
    updatedAt: new Date(Date.now() - 650),
  }));
  await assertSucceeds(outsider.collection("settings").doc("sparkUpdate").get());

  // Invalid theme value and invalid sparkUpdate payloads rejected
  await assertFails(realOwner.collection("settings").doc("sparkTheme").set({
    value: "neon-red",
    updatedBy: ALICE_UID,
    updatedAt: new Date(Date.now() - 600),
  }));
  await assertFails(realOwner.collection("settings").doc("sparkUpdate").set({
    active: true,
    version: "",
    note: "Missing version",
    downloadUrl: "",
    updatedBy: ALICE_UID,
    updatedAt: new Date(Date.now() - 600),
  }));
  await assertFails(realOwner.collection("settings").doc("sparkUpdate").set({
    active: true,
    version: "1.2.0",
    note: "Bad scheme",
    downloadUrl: "http://insecure.example.com/app.apk",
    updatedBy: ALICE_UID,
    updatedAt: new Date(Date.now() - 600),
  }));
  await assertFails(outsider.collection("settings").doc("sparkUpdate").set({
    active: true,
    version: "9.9.9",
    note: "Unauthorized",
    downloadUrl: "",
    updatedBy: CHARLIE_UID,
    updatedAt: new Date(Date.now() - 600),
  }));

  // Owner invites delegate
  await assertSucceeds(realOwner.collection("sparkDelegations").doc(DELEGATE_EMAIL).set({
    email: DELEGATE_EMAIL,
    uid: "",
    invitedBy: ALICE_UID,
    status: "invited",
    createdAt: new Date(Date.now() - 500),
    updatedAt: new Date(Date.now() - 500),
  }));

  // Outsider cannot read delegation or write welcome
  await assertFails(outsider.collection("sparkDelegations").doc(DELEGATE_EMAIL).get());
  await assertFails(outsider.collection("settings").doc("sparkPublic").set({
    welcome: "Hacked",
    updatedBy: CHARLIE_UID,
    updatedAt: new Date(Date.now() - 400),
  }));

  // Invited delegate accepts and can update sparkMenu
  await assertSucceeds(delegateCtx.collection("sparkDelegations").doc(DELEGATE_EMAIL).update({
    uid: BOB_UID,
    status: "active",
    updatedAt: new Date(Date.now() - 300),
  }));
  await assertSucceeds(delegateCtx.collection("settings").doc("sparkMenu").set({
    value: "top",
    updatedBy: BOB_UID,
    updatedAt: new Date(Date.now() - 200),
  }));

  // Owner revokes delegate -> delegate immediately loses write access
  await assertSucceeds(realOwner.collection("sparkDelegations").doc(DELEGATE_EMAIL).update({
    status: "revoked",
    updatedAt: new Date(Date.now() - 100),
  }));
  await assertFails(delegateCtx.collection("settings").doc("sparkPublic").set({
    welcome: "After revoke",
    updatedBy: BOB_UID,
    updatedAt: new Date(Date.now() - 50),
  }));
});

test("FCM device tokens are private to the owner and validate token payload", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const deviceId = "device_sha256_abc123";
  const validPayload = {
    token: "fcm_sample_token_value_123456",
    platform: "android",
    updatedAt: new Date(Date.now() - 1000),
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).get());
  await assertFails(unauthDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set(validPayload));

  // Owner create, update, get, list succeed
  await assertSucceeds(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set(validPayload));
  await assertSucceeds(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set({
    token: "fcm_rotated_token_value_7890",
    platform: "android",
    updatedAt: new Date(Date.now() - 500),
  }));
  await assertSucceeds(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).get());
  await assertSucceeds(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").get());

  // Cross-user read, list, write, and delete denied
  await assertFails(bobDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).get());
  await assertFails(bobDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set(validPayload));
  await assertFails(bobDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).delete());

  // Invalid payloads rejected (empty token, wrong platform, future timestamp, extra field)
  await assertFails(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set({
    token: "",
    platform: "android",
    updatedAt: new Date(Date.now() - 1000),
  }));
  await assertFails(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set({
    token: "valid_token",
    platform: "ios",
    updatedAt: new Date(Date.now() - 1000),
  }));
  await assertFails(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set({
    token: "valid_token",
    platform: "android",
    updatedAt: new Date(Date.now() + 60000),
  }));
  await assertFails(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).set({
    token: "valid_token",
    platform: "android",
    updatedAt: new Date(Date.now() - 1000),
    extra: true,
  }));

  // Owner delete succeeds
  await assertSucceeds(aliceDb.collection("userFcmTokens").doc(ALICE_UID).collection("devices").doc(deviceId).delete());
});

test("Saira chat sessions & exchanges enforce owner isolation, parent gate, immutability, and schema bounds", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_01");
  const ex1Ref = sessionRef.collection("exchanges").doc("ex_01");
  const ex2Ref = sessionRef.collection("exchanges").doc("ex_02");
  const now = new Date(Date.now() - 2000);

  // Unauthenticated denied
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sairaSessions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_01").set({
      title: "Unauth session",
      lastPreview: "Hi",
      exchangeCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Orphan exchange without parent session rejected
  await assertFails(
    ex1Ref.set({
      question: "Hello Saira",
      answer: "Good Evening! How can I help?",
      sequence: 0,
      createdAt: now,
    })
  );

  // Atomic batch creates session + first exchange (exercises existsAfter)
  const batch = aliceDb.batch();
  batch.set(sessionRef, {
    title: "Evening greeting & study help",
    lastPreview: "Good Evening! How can I help?",
    exchangeCount: 1,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(ex1Ref, {
    question: "Hello Saira",
    answer: "Good Evening! How can I help?",
    sequence: 0,
    createdAt: now,
  });
  await assertSucceeds(batch.commit());

  // Subsequent exchange + session header update succeed
  const later = new Date(Date.now() - 1000);
  const batch2 = aliceDb.batch();
  batch2.update(sessionRef, {
    lastPreview: "Here is the summary...",
    exchangeCount: 2,
    updatedAt: later,
  });
  batch2.set(ex2Ref, {
    question: "Summarize offline SMS rules",
    answer: "Here is the summary...",
    sequence: 1,
    createdAt: later,
  });
  await assertSucceeds(batch2.commit());

  // Owner queries matching Kotlin repository succeed (list sessions + ordered exchanges)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sairaSessions").orderBy("updatedAt", "desc").limit(50).get()
  );
  await assertSucceeds(
    sessionRef.collection("exchanges").orderBy("sequence", "asc").get()
  );

  // Bob cannot get, list, create, update, or delete Alice's sessions or exchanges
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sairaSessions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_01").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_01").collection("exchanges").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_02").set({
      title: "Hijack",
      lastPreview: "No",
      exchangeCount: 1,
      createdAt: later,
      updatedAt: later,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_01").delete());

  // Schema bounds & immutability guards
  await assertFails(ex1Ref.update({ answer: "Tampered" }));
  await assertFails(
    sessionRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_bad_id").set({
      id: "session_bad_id",
      title: "Has @DocumentId field",
      lastPreview: "No",
      exchangeCount: 1,
      createdAt: later,
      updatedAt: later,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sairaSessions").doc("session_future").set({
      title: "Future",
      lastPreview: "No",
      exchangeCount: 1,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Owner cleanup batch delete succeeds
  const delBatch = aliceDb.batch();
  delBatch.delete(ex1Ref);
  delBatch.delete(ex2Ref);
  delBatch.delete(sessionRef);
  await assertSucceeds(delBatch.commit());
});

