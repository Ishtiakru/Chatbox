import React, { useMemo, useState } from 'react';
import {
  Check,
  ChevronDown,
  Eye,
  EyeOff,
  Globe,
  Info,
  Lock,
  Mail,
  Phone,
  Search,
  X,
} from 'lucide-react';
import { sendPasswordResetEmail } from 'firebase/auth';
import { auth } from '../firebase';
import {
  findLanguageByCode,
  isBengaliLanguage,
  WORLD_LANGUAGES,
} from '../data/worldLanguages';
import { NoticeBanner } from './Common';

export function AuthLoginScreen({
  language,
  authReady,
  authLoading,
  isOnline,
  rememberMeEnabled,
  rememberedPhone,
  onSaveRememberedPhone,
  onLanguageSelected,
  onGoogleSignIn,
}: {
  language: string;
  authReady: boolean;
  authLoading: boolean;
  isOnline: boolean;
  rememberMeEnabled: boolean;
  rememberedPhone: string;
  onSaveRememberedPhone: (rememberMe: boolean, phone: string) => void;
  onLanguageSelected: (code: string) => void;
  onGoogleSignIn: () => void;
}) {
  const bn = isBengaliLanguage(language);
  const selectedLang = useMemo(() => findLanguageByCode(language), [language]);

  const [showLanguageModal, setShowLanguageModal] = useState(false);
  const [langSearch, setLangSearch] = useState('');
  const [showInfoModal, setShowInfoModal] = useState(false);
  const [showForgotModal, setShowForgotModal] = useState(false);

  const [phoneInput, setPhoneInput] = useState(rememberedPhone || '');
  const [passwordInput, setPasswordInput] = useState('');
  const [confirmPasswordInput, setConfirmPasswordInput] = useState('');
  const [rememberMeChecked, setRememberMeChecked] = useState(rememberMeEnabled);
  const [isCreateAccountMode, setIsCreateAccountMode] = useState(false);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [formNotice, setFormNotice] = useState<string | null>(null);

  const [resetEmail, setResetEmail] = useState('');
  const [resetBusy, setResetBusy] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);
  const [resetSentTo, setResetSentTo] = useState<string | null>(null);

  const filteredLanguages = useMemo(() => {
    const q = langSearch.trim().toLowerCase();
    if (!q) return WORLD_LANGUAGES;
    return WORLD_LANGUAGES.filter(
      (opt) =>
        opt.nativeLabel.toLowerCase().includes(q) ||
        opt.englishName.toLowerCase().includes(q) ||
        opt.countryName.toLowerCase().includes(q) ||
        opt.code.toLowerCase().includes(q)
    );
  }, [langSearch]);

  const handlePhoneFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!authReady || authLoading) return;
    if (!isOnline) {
      setFormNotice(
        bn
          ? 'লগ ইন করতে ইন্টারনেট সংযোগ প্রয়োজন।'
          : 'An internet connection is required to sign in.'
      );
      return;
    }
    const cleanPhone = phoneInput.trim();
    if (cleanPhone.length < 7) {
      setFormNotice(bn ? 'সঠিক মোবাইল নম্বর দিন।' : 'Please enter a valid phone number.');
      return;
    }
    if (passwordInput.length < 6) {
      setFormNotice(
        bn ? 'পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।' : 'Password must be at least 6 characters.'
      );
      return;
    }
    if (isCreateAccountMode && confirmPasswordInput !== passwordInput) {
      setFormNotice(bn ? 'পাসওয়ার্ড দুটি মিলছে না।' : 'Passwords do not match.');
      return;
    }
    onSaveRememberedPhone(rememberMeChecked, cleanPhone);
    setFormNotice(
      bn
        ? 'এই ওয়েব সংস্করণে Google Sign-In সক্রিয় আছে। নিচে "Google দিয়ে চালিয়ে যান" বাটনে ক্লিক করে চালিয়ে যান।'
        : 'Google Sign-In is active for this web build. Click "Continue with Google" below to sign in securely.'
    );
  };

  const handlePasswordReset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (resetBusy) return;
    if (!isOnline) {
      setResetError(
        bn
          ? 'পাসওয়ার্ড রিসেট ইমেল পাঠাতে ইন্টারনেট সংযোগ প্রয়োজন।'
          : 'An internet connection is required to send a reset email.'
      );
      return;
    }
    const trimmed = resetEmail.trim();
    if (!trimmed || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      setResetError(
        bn ? 'সঠিক ইমেল / Gmail ঠিকানা দিন।' : 'Please enter a valid email address.'
      );
      return;
    }
    setResetBusy(true);
    setResetError(null);
    setResetSentTo(null);
    try {
      await sendPasswordResetEmail(auth, trimmed);
      setResetSentTo(trimmed);
    } catch (err) {
      setResetError(
        err instanceof Error
          ? err.message
          : bn
            ? 'পাসওয়ার্ড রিসেট লিংক পাঠানো যায়নি।'
            : 'Could not send password reset link.'
      );
    } finally {
      setResetBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-between bg-gradient-to-b from-[#f6f9fe] to-white px-5 py-4">
      {/* Top Language Selector */}
      <div className="flex justify-center pt-1">
        <button
          type="button"
          onClick={() => setShowLanguageModal(true)}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full text-sm text-[#606770] hover:bg-[#efeaf7]/60 transition-colors cursor-pointer"
        >
          <Globe className="w-4 h-4" />
          <span className="truncate max-w-[240px]">
            {selectedLang.nativeLabel} ({selectedLang.countryName})
          </span>
          <ChevronDown className="w-4 h-4" />
        </button>
      </div>

      {/* Main Auth Container */}
      <div className="w-full max-w-[420px] mx-auto my-auto py-6 flex flex-col items-center">
        {/* Brand Logo */}
        <div className="flex flex-col items-center gap-2 mb-6">
          <div className="w-20 h-20 rounded-full bg-white border border-[#8b5cf6]/35 p-1 shadow-xs overflow-hidden">
            <img
              src="/assets/chatbox_logo_lavender.jpg"
              alt="Chatbox logo"
              className="w-full h-full object-cover rounded-full"
            />
          </div>
          <h1 className="font-display text-2xl font-bold text-[#8b5cf6] tracking-tight">
            Chatbox
          </h1>
          <p className="text-xs text-[#606770] text-center">
            {bn
              ? 'কাছের মানুষদের সাথে থাকার একটি শান্ত জায়গা'
              : 'A calmer space to stay close'}
          </p>
        </div>

        {/* Offline / Form Notice */}
        {!isOnline && (
          <NoticeBanner
            isError
            text={
              bn
                ? 'ডাটা বা ইন্টারনেট সংযোগ নেই। সাইন ইন করতে ইন্টারনেট সংযোগ প্রয়োজন।'
                : 'No internet connection. Data connection is required to log in.'
            }
            className="w-full mb-3"
          />
        )}
        {formNotice && (
          <NoticeBanner text={formNotice} className="w-full mb-3" />
        )}

        {/* Phone & Password Card */}
        <form onSubmit={handlePhoneFormSubmit} className="w-full space-y-2.5">
          <div className="relative">
            <Phone className="w-4 h-4 text-[#606770] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="tel"
              value={phoneInput}
              disabled={!authReady || authLoading || !isOnline}
              onChange={(e) => {
                const next = e.target.value.slice(0, 24);
                setPhoneInput(next);
                setFormNotice(null);
                if (rememberMeChecked) onSaveRememberedPhone(true, next);
              }}
              placeholder={bn ? 'মোবাইল নম্বর (+880...)' : 'Phone number (+8801XXXXXXXXX)'}
              className="w-full h-12 pl-10 pr-4 rounded-2xl bg-white border border-[#cbd2d9] text-sm text-[#1c1e21] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#8b5cf6] disabled:opacity-60"
            />
          </div>

          <div className="relative">
            <Lock className="w-4 h-4 text-[#606770] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type={passwordVisible ? 'text' : 'password'}
              value={passwordInput}
              disabled={!authReady || authLoading || !isOnline}
              onChange={(e) => {
                setPasswordInput(e.target.value.slice(0, 64));
                setFormNotice(null);
              }}
              placeholder={bn ? 'পাসওয়ার্ড লিখুন' : 'Enter password'}
              className="w-full h-12 pl-10 pr-11 rounded-2xl bg-white border border-[#cbd2d9] text-sm text-[#1c1e21] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#8b5cf6] disabled:opacity-60"
            />
            <button
              type="button"
              onClick={() => setPasswordVisible((v) => !v)}
              aria-label={passwordVisible ? 'Hide password' : 'Show password'}
              className="absolute right-2 top-1/2 -translate-y-1/2 w-9 h-9 rounded-lg flex items-center justify-center text-[#606770] hover:bg-[#f6f4fa] cursor-pointer"
            >
              {passwordVisible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>

          {isCreateAccountMode && (
            <div className="relative">
              <Lock className="w-4 h-4 text-[#606770] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                type={passwordVisible ? 'text' : 'password'}
                value={confirmPasswordInput}
                disabled={!authReady || authLoading || !isOnline}
                onChange={(e) => {
                  setConfirmPasswordInput(e.target.value.slice(0, 64));
                  setFormNotice(null);
                }}
                placeholder={bn ? 'পাসওয়ার্ড নিশ্চিত করুন' : 'Confirm password'}
                className="w-full h-12 pl-10 pr-4 rounded-2xl bg-white border border-[#cbd2d9] text-sm text-[#1c1e21] placeholder:text-[#8f89a3] focus:outline-none focus:border-[#8b5cf6] disabled:opacity-60"
              />
            </div>
          )}

          {/* Remember me + Forgot password */}
          <div className="flex items-center justify-between pt-0.5 px-1">
            <label className="inline-flex items-center gap-2 text-xs text-[#606770] cursor-pointer select-none">
              <input
                type="checkbox"
                checked={rememberMeChecked}
                onChange={(e) => {
                  const checked = e.target.checked;
                  setRememberMeChecked(checked);
                  onSaveRememberedPhone(checked, phoneInput);
                }}
                className="rounded border-[#cbd2d9] accent-[#8b5cf6] w-4 h-4"
              />
              {bn ? 'আমাকে মনে রাখুন' : 'Remember me'}
            </label>
            <button
              type="button"
              onClick={() => setShowForgotModal(true)}
              className="text-xs font-medium text-[#7658b4] hover:underline cursor-pointer"
            >
              {bn ? 'পাসওয়ার্ড ভুলে গেছেন?' : 'Forgot password?'}
            </button>
          </div>

          <button
            type="submit"
            disabled={!authReady || authLoading || !isOnline}
            className="w-full h-12 rounded-2xl bg-[#7658b4] hover:bg-[#5b4a8f] disabled:opacity-55 text-white font-semibold text-sm transition-colors cursor-pointer"
          >
            {isCreateAccountMode
              ? bn
                ? 'নতুন অ্যাকাউন্ট তৈরি করুন'
                : 'Create account'
              : bn
                ? 'লগ ইন'
                : 'Log in'}
          </button>

          <div className="flex items-center justify-between text-xs pt-0.5">
            <button
              type="button"
              onClick={() => {
                setIsCreateAccountMode((m) => !m);
                setFormNotice(null);
              }}
              className="text-[#7658b4] font-medium hover:underline cursor-pointer"
            >
              {isCreateAccountMode
                ? bn
                  ? 'আগে থেকেই অ্যাকাউন্ট আছে? লগ ইন করুন'
                  : 'Already have an account? Log in'
                : bn
                  ? 'নতুন অ্যাকাউন্ট তৈরি করুন'
                  : 'Create new account'}
            </button>
            <button
              type="button"
              onClick={() => setShowInfoModal(true)}
              className="inline-flex items-center gap-1 text-[#606770] hover:text-[#2a2340] cursor-pointer"
            >
              <Info className="w-3.5 h-3.5" />
              {bn ? 'তথ্য' : 'Info'}
            </button>
          </div>
        </form>

        {/* Divider */}
        <div className="w-full flex items-center gap-3 my-4">
          <div className="flex-1 h-px bg-[#ece9f3]" />
          <span className="text-xs text-[#8f89a3] uppercase tracking-wider">
            {bn ? 'অথবা' : 'or'}
          </span>
          <div className="flex-1 h-px bg-[#ece9f3]" />
        </div>

        {/* Google Sign-In CTA */}
        <button
          type="button"
          disabled={!authReady || authLoading || !isOnline}
          onClick={onGoogleSignIn}
          className="w-full h-12 rounded-2xl bg-[#7658b4] hover:bg-[#5b4a8f] text-white font-semibold text-sm flex items-center justify-center gap-2.5 shadow-xs transition-colors disabled:opacity-55 cursor-pointer"
        >
          <span className="w-5 h-5 rounded-full bg-white text-[#5b4a8f] font-bold text-xs flex items-center justify-center">
            G
          </span>
          {authLoading
            ? bn
              ? 'সাইন ইন হচ্ছে…'
              : 'Signing in…'
            : bn
              ? 'Google দিয়ে চালিয়ে যান'
              : 'Continue with Google'}
        </button>

        {typeof window !== 'undefined' && window.self !== window.top && (
          <a
            href={window.location.href}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-2.5 text-xs font-medium text-[#7658b4] hover:underline"
          >
            {bn
              ? 'লগইনে সমস্যা হলে নতুন ব্রাউজার ট্যাবে খুলুন'
              : 'Having trouble signing in inside preview? Open in browser tab'}
          </a>
        )}
      </div>

      {/* Footer Note */}
      <div className="text-center pb-2">
        <p className="text-[11px] text-[#8f89a3]">
          Chatbox · Secured by Firebase Authentication & Cloud Firestore
        </p>
      </div>

      {/* Language Picker Modal */}
      {showLanguageModal && (
        <div
          className="fixed inset-0 z-50 bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
          onClick={() => setShowLanguageModal(false)}
        >
          <div
            className="max-w-md w-full rounded-2xl bg-white border border-[#ece9f3] p-5 shadow-xl flex flex-col max-h-[80vh]"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-display text-base font-bold text-[#2a2340]">
                {bn ? 'সব দেশের ভাষা নির্বাচন করুন' : 'Choose language'}
              </h2>
              <button
                type="button"
                onClick={() => setShowLanguageModal(false)}
                className="p-1.5 rounded-lg hover:bg-[#f6f4fa] text-[#8f89a3] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="relative mb-3">
              <Search className="w-4 h-4 text-[#8f89a3] absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={langSearch}
                onChange={(e) => setLangSearch(e.target.value)}
                placeholder="Search country or language…"
                className="w-full h-10 pl-9 pr-8 rounded-xl bg-[#f7f5fb] border border-[#ece9f3] text-sm focus:outline-none focus:border-[#8b5cf6]"
              />
            </div>
            <div className="overflow-y-auto divide-y divide-[#ece9f3]">
              {filteredLanguages.map((opt) => {
                const selected = opt.code === selectedLang.code;
                return (
                  <button
                    key={opt.code}
                    type="button"
                    onClick={() => {
                      onLanguageSelected(opt.code);
                      setShowLanguageModal(false);
                    }}
                    className="w-full flex items-center justify-between py-2.5 px-2 hover:bg-[#f7f5fb] rounded-lg text-left cursor-pointer"
                  >
                    <div>
                      <div
                        className={`text-sm ${
                          selected ? 'font-semibold text-[#8b5cf6]' : 'text-[#2a2340]'
                        }`}
                      >
                        {opt.nativeLabel}
                      </div>
                      <div className="text-xs text-[#8f89a3]">
                        {opt.englishName} · {opt.countryName}
                      </div>
                    </div>
                    {selected && <Check className="w-4 h-4 text-[#8b5cf6] shrink-0" />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Info Modal */}
      {showInfoModal && (
        <div
          className="fixed inset-0 z-50 bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
          onClick={() => setShowInfoModal(false)}
        >
          <div
            className="max-w-md w-full rounded-2xl bg-white border border-[#ece9f3] p-5 shadow-xl space-y-3"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h3 className="font-display text-base font-bold text-[#2a2340]">
                {bn ? 'সাইন-ইন তথ্য' : 'Sign-in information'}
              </h3>
              <button
                type="button"
                onClick={() => setShowInfoModal(false)}
                className="p-1.5 rounded-lg hover:bg-[#f6f4fa] text-[#8f89a3] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-sm text-[#5a5468] leading-relaxed">
              {bn
                ? 'Chatbox-এ Google দিয়ে সাইন ইন করলে আপনার Chatbox ID তৈরি হয়, যা দিয়ে বন্ধুদের সাথে রিয়েল-টাইম Live chats, Saira AI এবং Spark Admin ব্যবহার করতে পারবেন।'
                : 'Signing in with Google creates your private Chatbox ID for real-time Firestore chats, voice notes, images, Saira AI, and Spark Admin Studio.'}
            </p>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => setShowInfoModal(false)}
                className="px-4 py-2 rounded-xl bg-[#efeaf7] text-[#5b4a8f] text-sm font-medium cursor-pointer"
              >
                {bn ? 'বন্ধ করুন' : 'Close'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Forgot Password Modal */}
      {showForgotModal && (
        <div
          className="fixed inset-0 z-50 bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
          onClick={() => setShowForgotModal(false)}
        >
          <div
            className="max-w-md w-full rounded-2xl bg-white border border-[#ece9f3] p-5 shadow-xl space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center gap-2.5">
              <Mail className="w-5 h-5 text-[#8b5cf6]" />
              <h3 className="font-display text-base font-bold text-[#2a2340] flex-1">
                {bn ? 'পাসওয়ার্ড রিসেট করুন' : 'Reset password'}
              </h3>
              <button
                type="button"
                onClick={() => setShowForgotModal(false)}
                className="p-1.5 rounded-lg hover:bg-[#f6f4fa] text-[#8f89a3] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-[#606770] leading-relaxed">
              {bn
                ? 'আপনার ফোনের Gmail বা অ্যাকাউন্ট ইমেল ঠিকানা দিন। Firebase-এর মাধ্যমে পাসওয়ার্ড রিসেট লিংক পাঠানো হবে।'
                : 'Enter your account email or Gmail address below to receive a Firebase password reset link.'}
            </p>
            <form onSubmit={handlePasswordReset} className="space-y-3">
              <input
                type="email"
                value={resetEmail}
                disabled={resetBusy}
                onChange={(e) => {
                  setResetEmail(e.target.value);
                  setResetError(null);
                  setResetSentTo(null);
                }}
                placeholder="name@gmail.com"
                className="w-full h-11 px-3.5 rounded-xl bg-[#f7f5fb] border border-[#ece9f3] text-sm focus:outline-none focus:border-[#8b5cf6]"
              />
              {resetError && <p className="text-xs text-[#9b5145]">{resetError}</p>}
              {resetSentTo && (
                <div className="rounded-xl bg-[#8b5cf6]/10 p-3 space-y-2">
                  <p className="text-xs text-[#2a2340]">
                    {bn
                      ? `${resetSentTo} ঠিকানায় পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে।`
                      : `Password reset link sent to ${resetSentTo}. Check your Gmail inbox or Spam folder.`}
                  </p>
                  <a
                    href="https://mail.google.com/"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center justify-center w-full py-2 rounded-lg border border-[#8b5cf6]/30 text-xs font-medium text-[#5b4a8f] hover:bg-white"
                  >
                    {bn ? 'Gmail খুলুন' : 'Open Gmail'}
                  </a>
                </div>
              )}
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowForgotModal(false)}
                  className="px-4 py-2 rounded-xl text-sm text-[#606770] hover:bg-[#f6f4fa] cursor-pointer"
                >
                  {bn ? 'বাতিল' : 'Cancel'}
                </button>
                <button
                  type="submit"
                  disabled={resetBusy || !resetEmail.trim()}
                  className="px-4 py-2 rounded-xl bg-[#8b5cf6] hover:bg-[#7658b4] disabled:opacity-55 text-white text-sm font-medium cursor-pointer"
                >
                  {resetBusy
                    ? '...'
                    : bn
                      ? 'রিসেট লিংক পাঠান'
                      : 'Send reset link'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
