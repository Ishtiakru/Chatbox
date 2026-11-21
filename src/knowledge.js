// The offline guide is deliberately explicit: it is not an AI service.
export const APP_VERSION = '0.1.0';
export const RELEASE_NOTES = [
  'A local-only chat demo with searchable conversations.',
  'Saira offline guide for questions about this prototype.',
  'Google sign-in configured; call and admin screens await a secure backend.'
];

export function sairaReply(input) {
  const text = input.toLowerCase();
  if (/admin|অ্যাডমিন|এডমিন/.test(text)) return 'অ্যাডমিন প্যানেল এখন লক করা আছে। server-side admin permission যুক্ত হলে শুধু অনুমোদিত অ্যাডমিন প্রবেশ করতে পারবেন।';
  if (/call|video|voice|কল|ভিডিও|কথা/.test(text)) return 'ভয়েস ও ভিডিও কলের ডিজাইন প্রস্তুত, কিন্তু কল এখনো কাজ করে না। এর জন্য নিরাপদ signaling ও WebRTC সংযোগ প্রয়োজন।';
  if (/update|version|আপডেট|ভার্সন/.test(text)) return `এই প্রোটোটাইপের ভার্সন ${APP_VERSION}। ভবিষ্যতের আপডেট স্বয়ংক্রিয়ভাবে জানার সুবিধা এখনো যুক্ত হয়নি।`;
  if (/firebase|ফায়ারবেস/.test(text)) return 'Firebase প্রজেক্ট কনফিগার করা হয়েছে এবং Google sign-in যোগ করা হয়েছে। Firestore database তৈরি করা হয়েছে; পরীক্ষামূলক চ্যাটের নিরাপত্তা নিয়ম লাইভ করা হয়নি।';
  if (/feature|chat|message|ফিচার|চ্যাট|মেসেজ|কি আছে/.test(text)) return 'এখন আপনি ডেমো চ্যাট দেখতে, খুঁজতে এবং এই ডিভাইসে মেসেজ লিখতে পারেন। আসল চ্যাট, কল এবং AI এখনো সংযুক্ত নয়।';
  if (/develop|built|made|বানানো|তৈরি|কিভাবে/.test(text)) return 'এই প্রোটোটাইপটি Vite, JavaScript এবং CSS দিয়ে তৈরি। এটি এখন ব্রাউজারে চলে; Firebase Authentication কনফিগার করা হয়েছে; লাইভ চ্যাট পরীক্ষামূলক অবস্থায় আছে; কোনো AI সেবা এখনো সংযুক্ত নয়।';
  return 'আমি এখন একটি সীমিত অফলাইন গাইড, পূর্ণ AI নই। চ্যাট, কল, অ্যাডমিন, Firebase, অ্যাপের ফিচার বা আপডেট সম্পর্কে জিজ্ঞেস করুন।';
}

export function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
}
