export function authErrorMessage(code) {
  switch (code) {
    case 'native/identity-stage':
      return 'পরীক্ষামূলক APK-এর Firebase পরিচয় যাচাই করা যায়নি। (পর্যায়: ডিভাইস কনফিগারেশন)';
    case 'native/google-stage':
      return 'Google থেকে sign-in সম্পূর্ণ হয়নি। (পর্যায়: Google account) বিকল্প পদ্ধতি চেষ্টা করা যায়।';
    case 'native/token-stage':
      return 'Google থেকে প্রয়োজনীয় sign-in তথ্য পাওয়া যায়নি। (পর্যায়: Google তথ্য)';
    case 'native/firebase-stage':
      return 'Google-এর পরে Firebase sign-in সম্পূর্ণ হয়নি। (পর্যায়: Firebase)';
    case 'auth/invalid-credential':
      return 'Firebase Google sign-in তথ্য গ্রহণ করেনি। (পর্যায়: Firebase credential)';
    case 'auth/invalid-api-key':
      return 'Firebase অ্যাপ configuration গ্রহণ করেনি। (পর্যায়: Firebase configuration)';
    case 'auth/too-many-requests':
      return 'অনেকবার চেষ্টা হয়েছে। কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন।';
    case 'auth/user-disabled':
      return 'এই Firebase ব্যবহারকারী নিষ্ক্রিয়।';
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
