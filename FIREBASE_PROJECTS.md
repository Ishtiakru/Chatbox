# Chatbox — Firebase Projects (কোনটা কোনটা)

Chatbox-এর Firebase-এ **দুইটা project** আছে, একই package name দিয়ে।
এই doc টা canonical reference — কনফিউশন দূর করতে এটাই দেখুন।

## Canonical (আসল/লাইভ) project ✅

| জিনিস | মান |
|---|---|
| **Project ID** | `gen-lang-client-0251556445` |
| **Project Number** | `631361942963` |
| **Android App** | `com.aistudio.chatbox.kxmpzq` |
| **App ID** | `1:631361942963:android:12641a60164f07f25047c6` |
| **Firestore Database ID** | `ai-studio-android-chatbox-f5f16960-82e9-4756-8ee4-5c3fe55bdd70` |
| **Storage Bucket** | `gen-lang-client-0251556445.firebasestorage.app` |
| **Release SHA-1** | `e2:f4:a8:6c:4d:29:f3:83:7f:40:16:40:20:53:c8:2c:93:32:58:2a` |

- Repo-র reference copy: `public/assets/aistudio/google-services__*.json`
  (তিনটাই এই project-এর)
- `app/google-services.json` আর GitHub secret `GOOGLE_SERVICES_JSON`
  **এই project-এর file-ই** হতে হবে।

## Duplicate project ⚠️ (স্ক্রিনশটেরটা)

| জিনিস | মান |
|---|---|
| **Project ID** | `gen-lang-client-0532609883` |
| **Project Number** | `838258163823` |
| **Android App** | `com.aistudio.chatbox.kxmpzq` (একই package!) |
| **App ID** | `1:838258163823:android:1d21bc23966a110d794d9a` |

একই package + SHA দুই project-এ থাকায় Firebase Console-এ
*"SHA-1 and package name combination that's already in use"* warning দেখায়।
এই project থেকে data `0251556445`-এ সরাতে চাইলে দেখুন:
[`scripts/firebase-migrate/README.md`](scripts/firebase-migrate/README.md)

## Saira AI data কোথায় থাকে

Firestore (named database উপরে) →

```
users/{uid}/sairaSessions/{sessionId}            ← session header (title, preview, count)
users/{uid}/sairaSessions/{sessionId}/exchanges  ← প্রশ্ন + উত্তর (Saira chat history)
```

কোড: `app/src/main/java/com/example/data/repository/SairaSessionRepository.kt`

## কোন project-এ কী চালু থাকতে হবে (destination-এ)

- **Authentication** → Google Sign-In
- **Firestore Database** → উপরের ID-র database
- **AI Logic** → Saira Gemini (free tier)
- **App Check** → reCAPTCHA / Play Integrity
- **Android app** (`com.aistudio.chatbox.kxmpzq`)-এ release SHA-1 যোগ করা
