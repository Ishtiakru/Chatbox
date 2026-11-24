# Chatbox Firestore Security Specification

## 1. Data Invariants (Master Source of Truth)
1. **Profiles (`/profiles/{uid}`)**: Only verified authenticated owner (`request.auth.uid == uid`) may create/update their own lookup profile (`uid`, `displayName` 1..80 chars). Listing all profiles is disabled (`allow list: if false`) to prevent user directory scraping.
2. **User Blocks (`/userBlocks/{ownerId}/blocked/{blockedId}`)**: Strictly owner-private (`request.auth.uid == ownerId`). A user cannot block themselves (`blockedId != ownerId`). `createdAt` must equal server time (`request.time`).
3. **Conversations (`/conversations/{conversationId}`)**: Exactly two distinct participant UIDs (`participants.size() == 2`), both with existing `/profiles/{uid}` documents, unblocked in both directions. Creation starts strictly in `status == 'pending'` by `createdBy == request.auth.uid`. Only the non-creator participant may transition `pending -> active` when unblocked; either participant may transition `pending/active -> closed` (terminal state — closed conversations cannot be reopened).
4. **Messages (`/conversations/{conversationId}/messages/{messageId}`)**: Derived strictly from parent conversation (`conversationActive(conversationId)` requires `status == 'active'` and `pairUnblocked`). Sender must match `request.auth.uid`, timestamps must match `request.time`, media URLs must match Firebase/GCS HTTPS domains, and messages are immutable (`update, delete: if false`).
5. **Saira Saved Sessions (`/users/{userId}/sairaSessions/{sessionId}` & `/exchanges/{exchangeId}`)**: Strictly owner-private (`request.auth.uid == userId`), immutable `createdAt`, bounded lengths (`title` 1..120, `question` 1..2000, `answer` 1..12000), atomic parent session creation check (`exists` or `existsAfter`).
6. **Spark Admin & Settings (`/sparkAdmin/current`, `/sparkDelegations/{email}`, `/settings/*`)**: Protected by `verifiedGoogle()` + `recentlyAuthenticated()` (5-minute Google auth window). Bootstrap owner email allowlist: `sojibsheikhuk@gmail.com`. All settings fields (`sparkPublic`, `sparkTheme`, `sparkMenu`, `sparkUpdate`) strictly bounded and validated via dedicated helpers.

## 2. The "Dirty Dozen" Attack Payloads
1. **Unverified Token Spoof**: `{ uid: "userA", displayName: "Alice" }` written to `/profiles/userA` with `email_verified: false`.
2. **Profile Impersonation**: `{ uid: "userB", displayName: "Spoof" }` written by `userA` to `/profiles/userB`.
3. **Shadow Field Injection on Profile**: `{ uid: "userA", displayName: "Alice", isAdmin: true }` written to `/profiles/userA`.
4. **Directory Scraping**: `list` query on `/profiles` by authenticated user.
5. **Self-Block**: `{ createdAt: serverTimestamp }` written to `/userBlocks/userA/blocked/userA`.
6. **Bypassing Consent Status on Create**: Creating `/conversations/c1` with `status: "active"` directly instead of `"pending"`.
7. **Creator Self-Accept**: `userA` (who is `createdBy`) updating `/conversations/c1` from `status: "pending"` to `status: "active"`.
8. **Terminal State Reopen**: Participant updating `/conversations/c1` from `status: "closed"` back to `"active"`.
9. **Messaging in Pending/Closed/Blocked Conversation**: Creating `/conversations/c1/messages/m1` while `c1.status != "active"` or peer is blocked.
10. **Arbitrary External Media URL**: Creating message with `imageUrl: "https://evil.example.com/payload.png"`.
11. **Timestamp Manipulation**: Creating message with forged `sentAt` far in the past or future instead of `request.time`.
12. **Admin Privilege Escalation**: Non-owner user attempting to create `/sparkAdmin/current` or mutate `/settings/sparkTheme` to `"hacked"`.
