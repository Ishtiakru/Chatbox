import type { DemoContact, DemoMessage } from '../types/chatbox';
import { APP_VERSION } from '../types/chatbox';

export const DEMO_CONTACTS: DemoContact[] = [
  {
    id: 'saira',
    name: 'Saira',
    role: 'Your app companion',
    avatar: '✳',
    style: 'SAIRA',
    label: 'AI GUIDE',
    online: true,
  },
  {
    id: 'maya',
    name: 'Maya Rahman',
    role: 'Demo conversation',
    avatar: 'MR',
    style: 'PEACH',
    online: true,
  },
  {
    id: 'arif',
    name: 'Arif Hasan',
    role: 'Demo conversation',
    avatar: 'AH',
    style: 'LAVENDER',
    online: false,
  },
  {
    id: 'nora',
    name: 'Nora Ahmed',
    role: 'Demo conversation',
    avatar: 'NA',
    style: 'YELLOW',
    online: false,
  },
  {
    id: 'together',
    name: 'The good people',
    role: 'Demo group',
    avatar: '✳',
    style: 'BLUE',
    online: false,
  },
];

export const DEMO_SEEDS: Record<string, DemoMessage[]> = {
  saira: [
    {
      from: 'them',
      text: 'হ্যালো! আমি Saira ✨ এই অ্যাপ সম্পর্কে কিছু জানতে চাইলে আমাকে জিজ্ঞেস করুন। আমি এখন একটি অফলাইন গাইড।',
      time: 'এখন',
    },
  ],
  maya: [
    { from: 'them', text: 'Hey! How has your week been going? ☀️', time: '10:24 AM' },
    { from: 'me', text: 'Pretty good, actually! Just taking things one day at a time.', time: '10:26 AM' },
    { from: 'them', text: 'Love that for you. We should catch up soon!', time: '10:28 AM' },
    { from: 'them', text: 'Maybe a little coffee date this weekend? ☕', time: '10:28 AM' },
    { from: 'me', text: 'That sounds like the perfect plan 🤍', time: '10:30 AM' },
  ],
  arif: [{ from: 'them', text: 'Let’s make something great together ✨', time: 'Yesterday' }],
  nora: [{ from: 'them', text: 'Sending you a little sunshine today!', time: 'Yesterday' }],
  together: [{ from: 'them', text: 'A little space for our favorite people 💛', time: 'Monday' }],
};

export function getSairaOfflineGuideReply(input: string): string {
  const text = input.toLowerCase();
  if (/admin|অ্যাডমিন|এডমিন/i.test(text)) {
    return 'অ্যাডমিন প্যানেল লক করা আছে। server-side security rules অনুমোদিত হলে শুধু অনুমোদিত অ্যাডমিন প্রবেশ করতে পারবেন।';
  }
  if (/call|video|voice|কল|ভিডিও|কথা/i.test(text)) {
    return 'ভয়েস ও ভিডিও কলের ডিজাইন প্রস্তুত, কিন্তু কল এখনো সংযুক্ত নয়। এর জন্য নিরাপদ signaling ও calling service প্রয়োজন।';
  }
  if (/update|version|আপডেট|ভার্সন/i.test(text)) {
    return `এই অ্যাপের ভার্সন ${APP_VERSION}। Spark Admin থেকে নতুন আপডেট নোটিশ প্রকাশ করলে লোগোর পাশে সবুজ update ব্যাজ দেখাবে।`;
  }
  if (/firebase|ফায়ারবেস/i.test(text)) {
    return 'Firebase Authentication, Cloud Firestore, Firebase Storage এবং server-side Gemini AI Chatbox-এ সংযুক্ত করা হয়েছে।';
  }
  if (/feature|chat|message|ফিচার|চ্যাট|মেসেজ|কি আছে/i.test(text)) {
    return 'এখন আপনি ডেমো চ্যাট দেখতে ও খুঁজতে পারেন, এবং Google দিয়ে সাইন ইন করলে Live chats, Saira AI ও Spark Admin ব্যবহার করতে পারবেন।';
  }
  if (/develop|built|made|বানানো|তৈরি|কিভাবে/i.test(text)) {
    return 'এই অ্যাপটি React, TypeScript, Tailwind CSS ও Express দিয়ে তৈরি, যা Firebase Auth, Firestore, Storage এবং Gemini AI ব্যবহার করে।';
  }
  return 'আমি এখন ডেমো মোডের একটি সীমিত অফলাইন গাইড। পূর্ণ AI-এর জন্য Saira AI ট্যাব ব্যবহার করুন অথবা চ্যাট, কল, অ্যাডমিন, Firebase বা অ্যাপের ফিচার সম্পর্কে জিজ্ঞেস করুন।';
}
