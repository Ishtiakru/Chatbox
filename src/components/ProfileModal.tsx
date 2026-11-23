import React, { useEffect, useState } from 'react';
import { Check, Copy, Hash, Info, Phone, User as UserIcon, X } from 'lucide-react';
import {
  DEFAULT_ABOUT_TEXT,
  loadOwnProfileDetails,
  saveOwnWhatsAppProfile,
} from '../services/liveChatService';
import {
  MAX_ABOUT_LENGTH,
  MAX_DISPLAY_NAME_LENGTH,
  MAX_PHONE_LENGTH,
  type UserProfile,
} from '../types/chatbox';
import { MessengerAvatar, NoticeBanner } from './Common';

const ABOUT_PRESETS = [
  'Hey there! I am using Chatbox.',
  'Available',
  'Busy right now',
  'At work / studying',
  'Sleeping',
];

export function ProfileModal({
  open,
  uid,
  email,
  fallbackDisplayName,
  isOnline,
  onClose,
  onProfileUpdated,
  onSignOut,
}: {
  open: boolean;
  uid: string;
  email: string | null;
  fallbackDisplayName: string | null;
  isOnline: boolean;
  onClose: () => void;
  onProfileUpdated: (profile: UserProfile) => void;
  onSignOut: () => void;
}) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [shortId, setShortId] = useState('');
  const [displayName, setDisplayName] = useState(fallbackDisplayName || '');
  const [about, setAbout] = useState(DEFAULT_ABOUT_TEXT);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [savedPhone, setSavedPhone] = useState('');
  const [copiedId, setCopiedId] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; isError: boolean } | null>(null);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    setFeedback(null);
    loadOwnProfileDetails(uid, fallbackDisplayName)
      .then((res) => {
        if (!active) return;
        setShortId(res.profile.shortId || '');
        setDisplayName(res.profile.displayName || fallbackDisplayName || '');
        setAbout(res.profile.about || DEFAULT_ABOUT_TEXT);
        setPhoneNumber(res.phoneNumber || '');
        setSavedPhone(res.phoneNumber || '');
      })
      .catch((err) => {
        if (!active) return;
        setFeedback({
          text: err instanceof Error ? err.message : 'Unable to load profile.',
          isError: true,
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [open, uid, fallbackDisplayName]);

  if (!open) return null;

  const handleCopyShortId = async () => {
    const val = shortId || uid;
    try {
      await navigator.clipboard.writeText(val);
      setCopiedId(true);
      window.setTimeout(() => setCopiedId(false), 2000);
    } catch {
      // Ignore clipboard failure
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (saving || !isOnline) return;
    setSaving(true);
    setFeedback(null);
    try {
      const result = await saveOwnWhatsAppProfile({
        uid,
        shortId,
        displayName,
        about,
        phoneNumber,
        previousPhoneNumber: savedPhone,
      });
      setShortId(result.profile.shortId || shortId);
      setDisplayName(result.profile.displayName);
      setAbout(result.profile.about || DEFAULT_ABOUT_TEXT);
      setPhoneNumber(result.phoneNumber);
      setSavedPhone(result.phoneNumber);
      onProfileUpdated(result.profile);
      setFeedback({ text: 'Profile saved! Friends can add you with your 6-digit ID or phone number.', isError: false });
    } catch (err) {
      setFeedback({
        text: err instanceof Error ? err.message : 'Could not update profile.',
        isError: true,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div
        className="max-w-md w-full rounded-3xl bg-white border border-[#ece9f3] shadow-xl overflow-hidden flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* WhatsApp-style Header */}
        <div className="bg-gradient-to-r from-[#6250a0] to-[#7c66c4] px-5 py-4 flex items-center justify-between text-white">
          <div>
            <h2 className="font-display text-base font-bold">Profile</h2>
            <p className="text-[11px] text-white/80">Your 6-digit ID & phone discovery settings</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close profile"
            className="w-9 h-9 rounded-full hover:bg-white/15 flex items-center justify-center cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="overflow-y-auto p-5 space-y-4">
          {loading ? (
            <p className="py-10 text-center text-sm text-[#8f89a3]">Loading your profile…</p>
          ) : (
            <form onSubmit={handleSave} className="space-y-4">
              {/* Avatar & 6-Digit ID Hero */}
              <div className="flex flex-col items-center text-center space-y-2.5">
                <MessengerAvatar label={displayName || email || 'U'} size={76} />
                <div>
                  <h3 className="font-display text-lg font-bold text-[#2a2340]">
                    {displayName || 'Chatbox User'}
                  </h3>
                  {email && <p className="text-xs text-[#8f89a3]">{email}</p>}
                </div>

                {/* 6-Digit ID Card */}
                <div className="w-full rounded-2xl bg-[#f7f5fb] border border-[#e3dcf2] px-4 py-3 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2.5 min-w-0 text-left">
                    <div className="w-9 h-9 rounded-xl bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center shrink-0">
                      <Hash className="w-4 h-4" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-[11px] font-semibold text-[#8f89a3]">
                        6-Digit Chatbox ID
                      </p>
                      <p className="font-mono text-lg font-bold tracking-[0.2em] text-[#2a2340] tabular-nums">
                        {shortId || '------'}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={handleCopyShortId}
                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white border border-[#e3dcf2] hover:bg-[#efeaf7] text-xs font-semibold text-[#5b4a8f] shrink-0 cursor-pointer"
                  >
                    {copiedId ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                    {copiedId ? 'Copied' : 'Copy ID'}
                  </button>
                </div>
              </div>

              {feedback && <NoticeBanner isError={feedback.isError} text={feedback.text} />}

              {/* Name Field */}
              <div className="space-y-1">
                <label className="flex items-center gap-1.5 text-xs font-semibold text-[#5b4a8f]">
                  <UserIcon className="w-3.5 h-3.5" />
                  Name
                </label>
                <input
                  type="text"
                  value={displayName}
                  maxLength={MAX_DISPLAY_NAME_LENGTH}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Your display name"
                  className="w-full h-11 px-3.5 rounded-xl bg-[#f7f5fb] border border-[#ece9f3] text-sm text-[#2a2340] focus:outline-none focus:border-[#6250a0]"
                />
                <p className="text-[11px] text-[#8f89a3]">
                  Visible to your Chatbox contacts.
                </p>
              </div>

              {/* About Field (WhatsApp style) */}
              <div className="space-y-1.5">
                <label className="flex items-center gap-1.5 text-xs font-semibold text-[#5b4a8f]">
                  <Info className="w-3.5 h-3.5" />
                  About
                </label>
                <input
                  type="text"
                  value={about}
                  maxLength={MAX_ABOUT_LENGTH}
                  onChange={(e) => setAbout(e.target.value)}
                  placeholder="Hey there! I am using Chatbox."
                  className="w-full h-11 px-3.5 rounded-xl bg-[#f7f5fb] border border-[#ece9f3] text-sm text-[#2a2340] focus:outline-none focus:border-[#6250a0]"
                />
                <div className="flex flex-wrap gap-1.5 pt-0.5">
                  {ABOUT_PRESETS.map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => setAbout(preset)}
                      className={`px-2.5 py-1 rounded-lg text-[11px] border transition-colors cursor-pointer ${
                        about === preset
                          ? 'bg-[#efeaf7] border-[#6250a0] text-[#5b4a8f] font-semibold'
                          : 'bg-white border-[#ece9f3] text-[#6b657d] hover:bg-[#f7f5fb]'
                      }`}
                    >
                      {preset}
                    </button>
                  ))}
                </div>
              </div>

              {/* Phone Number Field */}
              <div className="space-y-1">
                <label className="flex items-center gap-1.5 text-xs font-semibold text-[#5b4a8f]">
                  <Phone className="w-3.5 h-3.5" />
                  Phone number (optional)
                </label>
                <input
                  type="tel"
                  value={phoneNumber}
                  maxLength={MAX_PHONE_LENGTH}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  placeholder="+8801XXXXXXXXX"
                  className="w-full h-11 px-3.5 rounded-xl bg-[#f7f5fb] border border-[#ece9f3] text-sm text-[#2a2340] focus:outline-none focus:border-[#6250a0]"
                />
                <p className="text-[11px] text-[#8f89a3]">
                  Add your number so friends can find and message you using your number or 6-digit ID.
                </p>
              </div>

              <div className="flex items-center justify-between gap-2 pt-2 border-t border-[#ece9f3]">
                <button
                  type="button"
                  onClick={onSignOut}
                  className="px-4 py-2.5 rounded-xl bg-[#fff1ed] hover:bg-[#fde3dc] text-[#9b5145] text-xs font-semibold cursor-pointer"
                >
                  Log out
                </button>
                <button
                  type="submit"
                  disabled={saving || !isOnline || !displayName.trim()}
                  className="px-5 py-2.5 rounded-xl bg-[#6250a0] hover:bg-[#463a76] disabled:opacity-55 text-white text-xs font-semibold cursor-pointer"
                >
                  {saving ? 'Saving…' : 'Save Profile'}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
