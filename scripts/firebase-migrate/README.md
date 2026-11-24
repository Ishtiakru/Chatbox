# Chatbox — Saira AI Data Migration (Firebase project → project)

এই script টা Saira AI-র সব Firestore data এক Firebase project থেকে
আরেক project-এ copy করে।

- **Source (বর্তমান/স্ক্রিনশটের):** `gen-lang-client-0532609883` (838258163823)
- **Destination (আপনার চাওয়া):** `gen-lang-client-0251556445` (631361942963)
- **যা copy হয়:** `users/{uid}/sairaSessions/{sessionId}/exchanges` — অর্থাৎ
  Saira-র সব chat history (প্রশ্ন + উত্তর), session title, timestamp সহ।
  Script টা subcollection সহ recursive copy করে, document ID অপরিবর্তিত থাকে।

> ⚠️ **গুরুত্বপূর্ণ সীমাবদ্ধতা:** Firebase Auth user (Google Sign-In) এক
> project থেকে আরেক project-এ সরানো যায় না — UID project অনুযায়ী আলাদা।
> তাই migration-এর আগে `--list-auth` দিয়ে দুই project-এর user list মিলিয়ে
> দেখুন। Destination-এ একই UID-এর Auth user না থাকলে session গুলো
> "orphan" হয়ে যাবে (data থাকবে, কিন্তু নতুন login-এ দেখা যাবে না)।
> এই কারণে migration-এর আগে নিশ্চিত হোন কোন project-এ আসল user/data আছে —
> হয়তো data ইতিমধ্যে `0251556445`-এই আছে (repo-র `google-services.json`
> এই project-এরই)।

---

## ধাপে ধাপে (একবার করলেই হবে)

### 1. দুই project-এর Service Account key নিন

Firebase Console → Project settings → **Service accounts** →
**Generate new private key** — দুই project-এর জন্যই।

- Source project (`0532609883`) → download → নাম দিন `source.json`
- Destination project (`0251556445`) → download → নাম দিন `dest.json`

দুটো ফাইল এই folder-এ (`scripts/firebase-migrate/`) রাখুন।
**এই ফাইল কখনো git-এ commit করবেন না** — `.gitignore` দিয়ে সুরক্ষিত আছে।

### 2. Destination project-এ Firestore database আছে কিনা দেখুন

App টা named database ব্যবহার করে:
`ai-studio-android-chatbox-f5f16960-82e9-4756-8ee4-5c3fe55bdd70`

Destination project (`0251556445`) → Firestore Database → এই ID-র
database না থাকলে **Create database** দিয়ে একই ID-তে বানান।
(নাহলে app migrated data খুঁজে পাবে না — কোডে এই ID হার্ডকোড করা।)

### 3. Dry run (কিছু লিখবে না, শুধু গুনবে)

```bash
cd scripts/firebase-migrate
npm install
node migrate-saira.mjs --source-key ./source.json --dest-key ./dest.json --list-auth
```

Output-এ দেখুন: কতগুলো session/exchange পাওয়া গেল, আর দুই project-এ
Auth user-দের UID মিলছে কিনা।

### 4. আসল migration

```bash
node migrate-saira.mjs --source-key ./source.json --dest-key ./dest.json --execute
```

### 5. Verify

Firebase Console → Destination (`0251556445`) → Firestore Database →
`ai-studio-android-chatbox-...` → `users` → `{uid}` → `sairaSessions`
→ session → `exchanges` — data এসেছে কিনা দেখুন।

### 6. App-কে destination project-এ point করুন

- `app/google-services.json` = Destination project (`0251556445`)-এর file
  (repo-তে `public/assets/aistudio/google-services__*.json` এই project-এরই copy)
- GitHub secret `GOOGLE_SERVICES_JSON` = একই file
- Destination project-এ চালু রাখুন: **Authentication (Google)**,
  **Firestore**, **AI Logic** (Saira Gemini), **App Check**
- SHA fingerprint (`e2:f4:a8:6c:...` release key) Destination project-এর
  Android app (`com.aistudio.chatbox.kxmpzq`)-এ যোগ করা আছে কিনা দেখুন

---

## অন্য collection-ও সরাতে চাইলে

Default শুধু `users` (Saira data এর মূল)। সব top-level collection
(profiles, conversations, settings, sparkAdmin ইত্যাদি) সরাতে:

```bash
node migrate-saira.mjs --source-key ./source.json --dest-key ./dest.json \
  --collections users,profiles,shortIds,phoneDirectory,conversations,sparkAdmin,sparkDelegations,settings,userBlocks,userFcmTokens \
  --execute
```

> Storage-এর file (ছবি/ভয়েস নোট) এই script সরায় না — সেগুলো আলাদা
> `gsutil` দিয়ে bucket-to-bucket copy করতে হয়। Saira-র text data-এর
> জন্য এটা লাগে না।
