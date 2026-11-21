export function authErrorMessage(code) {
  switch (code) {
    case 'auth/unauthorized-domain':
      return 'এই ওয়েব ঠিকানাটি Firebase Authentication-এর Authorized domains-এ যোগ করতে হবে। প্রিভিউ বা প্রকাশিত সাইটের ঠিকানা যোগ করে আবার চেষ্টা করুন।';
    case 'auth/operation-not-allowed':
      return 'Firebase Console-এর Authentication → Sign-in method থেকে Google চালু করে আবার চেষ্টা করুন।';
    case 'auth/popup-blocked':
    case 'auth/cancelled-popup-request':
      return 'সাইন-ইন উইন্ডো খোলা যায়নি। ব্রাউজারে pop-up অনুমতি দিয়ে আবার চেষ্টা করুন।';
    case 'auth/popup-closed-by-user':
      return 'সাইন-ইন উইন্ডোটি সম্পূর্ণ হওয়ার আগে বন্ধ হয়েছে। চাইলে আবার চেষ্টা করুন।';
    case 'auth/network-request-failed':
      return 'নেটওয়ার্কে সমস্যা হয়েছে। ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।';
    default:
      return 'সাইন-ইন সম্পূর্ণ হয়নি। Firebase-এ Google sign-in ও অনুমোদিত ডোমেইন পরীক্ষা করে আবার চেষ্টা করুন।';
  }
}
