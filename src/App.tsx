import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  HelpCircle,
  MessageCircle,
  Phone,
  Shield,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import {
  onAuthStateChanged,
  signInWithPopup,
  signOut,
  type User,
} from 'firebase/auth';
import { auth, googleProvider } from './firebase';
import {
  BOOTSTRAPPED_OWNER_EMAILS,
  DEFAULT_SPARK_DYNAMIC_CONFIG,
  checkSparkAdminStatus,
  observePublicWelcome,
  observeSparkAppUpdate,
  observeSparkAppearance,
  observeSparkDynamicConfig,
} from './services/sparkAdminService';
import {
  getStoredLoginLanguage,
  getStoredRememberMe,
  getStoredSairaOneMinEnabled,
  saveStoredLoginLanguage,
  saveStoredRememberMe,
} from './services/storagePrefs';
import { useNetworkStatus } from './hooks/useNetworkStatus';
import { isBengaliLanguage } from './data/worldLanguages';
import type {
  AppTab,
  CustomMenuIconName,
  DemoModal,
  SparkAppearance,
  SparkAppUpdate,
  SparkDynamicConfig,
  UserProfile,
} from './types/chatbox';
import { AdminScreen } from './components/AdminScreen';
import { AuthLoginScreen } from './components/AuthLoginScreen';
import { CallsScreen } from './components/CallsScreen';
import {
  AppUpdateDetailsDialog,
  ChatboxLogoWithUpdateBadge,
  CustomMenuIcon,
  GreenUpdateButton,
  MessengerAvatar,
  NoticeBanner,
} from './components/Common';
import { DemoChatsScreen } from './components/DemoChatsScreen';
import { LiveChatsScreen } from './components/LiveChatsScreen';
import { ProfileModal } from './components/ProfileModal';
import { SairaAiScreen } from './components/SairaAiScreen';

interface NavTabItem {
  id: AppTab;
  label: string;
  shortLabel: string;
  icon?: React.ComponentType<{ className?: string }>;
  customIcon?: CustomMenuIconName;
}

const CORE_NAV_TABS: NavTabItem[] = [
  { id: 'demo', label: 'Demo', shortLabel: 'Demo', icon: MessageCircle },
  { id: 'live', label: 'Live chats', shortLabel: 'Live', icon: Users },
  { id: 'saira', label: 'Saira AI', shortLabel: 'Saira', icon: Sparkles },
  { id: 'calls', label: 'Calls', shortLabel: 'Calls', icon: Phone },
];

export function App() {
  const isOnline = useNetworkStatus();

  const [splashDone, setSplashDone] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const signInInFlightRef = useRef(false);

  const [isAdmin, setIsAdmin] = useState(false);
  const [ownProfile, setOwnProfile] = useState<UserProfile | null>(null);
  const [profileModalOpen, setProfileModalOpen] = useState(false);

  const [loginLanguage, setLoginLanguage] = useState(() => getStoredLoginLanguage());
  const [rememberState, setRememberState] = useState(() => getStoredRememberMe());
  const [oneMinAutoReplyEnabled, setOneMinAutoReplyEnabled] = useState(() =>
    getStoredSairaOneMinEnabled()
  );

  const [activeTab, setActiveTab] = useState<AppTab>('live');
  const [demoSelectedId, setDemoSelectedId] = useState('saira');
  const [demoModal, setDemoModal] = useState<DemoModal>(null);

  const [appearance, setAppearance] = useState<SparkAppearance>({
    theme: 'default',
    menu: 'bottom',
  });
  const [welcomeBanner, setWelcomeBanner] = useState('');
  const [appUpdate, setAppUpdate] = useState<SparkAppUpdate>({
    active: false,
    version: '',
    note: '',
    downloadUrl: '',
  });
  const [dynamicConfig, setDynamicConfig] = useState<SparkDynamicConfig>(
    DEFAULT_SPARK_DYNAMIC_CONFIG
  );
  const [showUpdateDialog, setShowUpdateDialog] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setSplashDone(true), 1200);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (user) => {
      setCurrentUser(user);
      setAuthReady(true);
      if (!user) {
        setIsAdmin(false);
        setOwnProfile(null);
        setProfileModalOpen(false);
      }
    });
    const fallbackTimer = window.setTimeout(() => setAuthReady(true), 1500);
    return () => {
      unsub();
      window.clearTimeout(fallbackTimer);
    };
  }, []);

  useEffect(() => {
    if (!currentUser) {
      setIsAdmin(false);
      return;
    }
    const normalizedEmail = (currentUser.email ?? '').trim().toLowerCase();
    const isBootstrappedOwner = (BOOTSTRAPPED_OWNER_EMAILS as readonly string[]).includes(
      normalizedEmail
    );
    setIsAdmin(isBootstrappedOwner);

    let active = true;
    checkSparkAdminStatus(currentUser.uid, currentUser.email)
      .then((status) => {
        if (!active) return;
        const allowed =
          status === 'OWNER' ||
          status === 'DELEGATE' ||
          status === 'INVITED_DELEGATE' ||
          status === 'UNCLAIMED_OWNER';
        setIsAdmin(allowed);
      })
      .catch(() => {
        if (active) setIsAdmin(isBootstrappedOwner);
      });
    return () => {
      active = false;
    };
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser) return;
    const unsubWelcome = observePublicWelcome(setWelcomeBanner);
    const unsubAppearance = observeSparkAppearance(setAppearance);
    const unsubUpdate = observeSparkAppUpdate(setAppUpdate);
    const unsubDynamic = observeSparkDynamicConfig(setDynamicConfig);
    return () => {
      unsubWelcome();
      unsubAppearance();
      unsubUpdate();
      unsubDynamic();
    };
  }, [currentUser]);

  useEffect(() => {
    document.documentElement.setAttribute(
      'data-theme',
      appearance.theme === 'lavender' ? 'lavender' : 'default'
    );
  }, [appearance.theme]);

  useEffect(() => {
    if (activeTab === 'admin' && !isAdmin) {
      setActiveTab('live');
    } else if (activeTab.startsWith('custom:')) {
      const id = activeTab.slice('custom:'.length);
      if (!dynamicConfig.customMenus.some((m) => m.id === id)) {
        setActiveTab('live');
      }
    }
  }, [activeTab, isAdmin, dynamicConfig.customMenus]);

  const effectiveNavTabs = useMemo<NavTabItem[]>(() => {
    const customTabs: NavTabItem[] = dynamicConfig.customMenus.map((menu) => ({
      id: `custom:${menu.id}`,
      label: menu.label,
      shortLabel: menu.label.slice(0, 8),
      customIcon: menu.icon,
    }));
    const tabs = [...CORE_NAV_TABS, ...customTabs];
    if (isAdmin) {
      tabs.push({ id: 'admin', label: 'Admin', shortLabel: 'Admin', icon: Shield });
    }
    return tabs;
  }, [dynamicConfig.customMenus, isAdmin]);

  const activeCustomMenu = useMemo(() => {
    if (!activeTab.startsWith('custom:')) return null;
    const id = activeTab.slice('custom:'.length);
    return dynamicConfig.customMenus.find((m) => m.id === id) ?? null;
  }, [activeTab, dynamicConfig.customMenus]);

  const handleGoogleSignIn = async () => {
    if (signInInFlightRef.current || authLoading) return;
    signInInFlightRef.current = true;
    setAuthLoading(true);
    setAuthError(null);
    const bn = isBengaliLanguage(loginLanguage);
    try {
      const credential = await signInWithPopup(auth, googleProvider);
      if (credential.user) {
        setCurrentUser(credential.user);
      }
    } catch (err) {
      if (auth.currentUser) {
        setCurrentUser(auth.currentUser);
        return;
      }
      const code =
        typeof err === 'object' && err !== null && 'code' in err
          ? String((err as { code?: unknown }).code)
          : '';
      if (code.includes('cancelled-popup-request')) {
        return;
      }
      if (code.includes('popup-closed-by-user')) {
        setAuthError(
          bn
            ? 'Google সাইন-ইন সম্পন্ন হওয়ার আগেই পপআপ বন্ধ হয়ে গেছে। আপনি যদি অ্যাকাউন্ট সিলেক্ট করার পরও লগইন পেজে ফিরে আসেন, তাহলে ব্রাউজারের প্রিভিউ ফ্রেম পপআপ/থার্ড-পার্টি কুকি ব্লক করতে পারে — নিচের "Open in new tab" বাটনে ক্লিক করে নতুন ট্যাবে খুলে সাইন ইন করুন। (auth/popup-closed-by-user)'
            : 'Google sign-in popup closed before completing. If you picked your account and it returned to login, your browser may be blocking cross-site popup cookies inside this preview frame — open Chatbox in a new browser tab or allow popups & third-party cookies. (auth/popup-closed-by-user)'
        );
      } else if (code.includes('popup-blocked')) {
        setAuthError(
          bn
            ? 'ব্রাউজার সাইন-ইন পপআপ ব্লক করেছে। এই সাইটের জন্য Pop-ups allow করে আবার চেষ্টা করুন, অথবা "Open in new tab" ব্যবহার করুন। (auth/popup-blocked)'
            : 'Browser popup was blocked. Please allow popups for this site and click "Continue with Google" again. (auth/popup-blocked)'
        );
      } else if (code.includes('unauthorized-domain')) {
        setAuthError(
          bn
            ? `এই ডোমেইনটি (${window.location.hostname}) Firebase Authentication > Settings > Authorized domains-এ যোগ করা নেই। (${code})`
            : `This domain (${window.location.hostname}) is not authorized in Firebase Authentication > Settings > Authorized domains. (${code})`
        );
      } else if (code.includes('web-storage-unsupported')) {
        setAuthError(
          bn
            ? 'ব্রাউজারের থার্ড-পার্টি কুকি/স্টোরেজ বন্ধ থাকায় সাইন ইন আটকে গেছে। কুকি চালু করুন অথবা "Open in new tab" দিয়ে নতুন ট্যাবে ওপেন করে সাইন ইন করুন। (auth/web-storage-unsupported)'
            : 'Browser storage/cookies are disabled for third-party frames. Enable cookies or open Chatbox in a standalone browser tab to sign in. (auth/web-storage-unsupported)'
        );
      } else {
        setAuthError(err instanceof Error ? err.message : 'Unable to sign in with Google.');
      }
    } finally {
      if (auth.currentUser) {
        setCurrentUser(auth.currentUser);
      }
      signInInFlightRef.current = false;
      setAuthLoading(false);
    }
  };

  const handleSignOut = async () => {
    if (authLoading) return;
    setAuthLoading(true);
    try {
      await signOut(auth);
    } finally {
      setAuthLoading(false);
    }
  };

  if (!splashDone) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-b from-[#f6f9fe] to-white gap-3.5">
        <div className="w-24 h-24 rounded-full bg-white border-2 border-[#8b5cf6]/35 p-1 shadow-xs overflow-hidden">
          <img
            src="/assets/chatbox_logo_lavender.jpg"
            alt="Chatbox logo"
            className="w-full h-full object-cover rounded-full"
          />
        </div>
        <h1 className="font-display text-3xl font-bold text-[#8b5cf6]">Chatbox</h1>
      </div>
    );
  }

  const effectiveDisplayName = ownProfile?.displayName || currentUser?.displayName || 'User';

  return (
    <>
      {authError && (
        <div
          className="fixed inset-0 z-50 bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
          onClick={() => setAuthError(null)}
        >
          <div
            className="max-w-sm w-full rounded-2xl bg-white border border-[#ece9f3] p-5 shadow-xl space-y-3"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h3 className="font-display text-base font-bold text-[#2a2340]">
                Sign-in notice
              </h3>
              <button
                type="button"
                onClick={() => setAuthError(null)}
                className="p-1 rounded-lg hover:bg-[#f6f4fa] text-[#8f89a3] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-sm text-[#5a5468] leading-relaxed break-words">{authError}</p>
            <div className="flex flex-wrap justify-end gap-2">
              {typeof window !== 'undefined' && window.self !== window.top && (
                <a
                  href={window.location.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="px-4 py-2 rounded-xl bg-[#efeaf7] hover:bg-[#e6dff5] text-[#5b4a8f] text-xs font-semibold"
                >
                  Open in new tab
                </a>
              )}
              <button
                type="button"
                onClick={() => setAuthError(null)}
                className="px-4 py-2 rounded-xl bg-[#6250a0] text-white text-xs font-semibold cursor-pointer"
              >
                OK
              </button>
            </div>
          </div>
        </div>
      )}

      {showUpdateDialog && appUpdate.active && (
        <AppUpdateDetailsDialog
          appUpdate={appUpdate}
          onDismiss={() => setShowUpdateDialog(false)}
        />
      )}

      {currentUser && (
        <ProfileModal
          open={profileModalOpen}
          uid={currentUser.uid}
          email={currentUser.email}
          fallbackDisplayName={effectiveDisplayName}
          isOnline={isOnline}
          onClose={() => setProfileModalOpen(false)}
          onProfileUpdated={(profile) => setOwnProfile(profile)}
          onSignOut={() => void handleSignOut()}
        />
      )}

      {!currentUser ? (
        <AuthLoginScreen
          language={loginLanguage}
          authReady={authReady}
          authLoading={authLoading}
          isOnline={isOnline}
          rememberMeEnabled={rememberState.enabled}
          rememberedPhone={rememberState.phone}
          onSaveRememberedPhone={(enabled, phone) => {
            setRememberState({ enabled, phone });
            saveStoredRememberMe(enabled, phone);
          }}
          onLanguageSelected={(code) => {
            setLoginLanguage(code);
            saveStoredLoginLanguage(code);
          }}
          onGoogleSignIn={() => void handleGoogleSignIn()}
        />
      ) : (
        <div className="h-screen flex flex-col md:flex-row bg-[var(--chatbox-bg)] overflow-hidden">
          {/* Desktop / Tablet Sidebar Navigation Rail */}
          <nav
            aria-label="Primary navigation"
            className="hidden md:flex flex-col items-center w-20 shrink-0 py-3 px-2 bg-[var(--chatbox-rail-bg)] border-r border-[var(--chatbox-border)]"
          >
            <div className="mb-4">
              <ChatboxLogoWithUpdateBadge
                appUpdateActive={appUpdate.active}
                onUpdateClick={() => setShowUpdateDialog(true)}
                size={38}
              />
            </div>

            <div className="flex-1 flex flex-col items-center gap-1.5 w-full overflow-y-auto">
              {effectiveNavTabs.map((tab) => {
                const Icon = tab.icon;
                const selected = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setActiveTab(tab.id)}
                    className={`w-full py-2.5 px-1 rounded-2xl flex flex-col items-center gap-1 text-[11px] font-medium transition-colors cursor-pointer shrink-0 ${
                      selected
                        ? 'bg-[var(--chatbox-selected-bg)] text-[#5b4a8f] font-semibold'
                        : 'text-[#6b657d] hover:bg-white/60'
                    }`}
                  >
                    {tab.customIcon ? (
                      <CustomMenuIcon name={tab.customIcon} className="w-5 h-5" />
                    ) : Icon ? (
                      <Icon className="w-5 h-5" />
                    ) : null}
                    <span className="truncate max-w-full">{tab.label}</span>
                  </button>
                );
              })}
            </div>

            <div className="flex flex-col items-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => {
                  setActiveTab('demo');
                  setDemoModal('ABOUT');
                }}
                title="About Chatbox"
                className="w-10 h-10 rounded-xl hover:bg-white/70 text-[#6b657d] flex items-center justify-center cursor-pointer"
              >
                <HelpCircle className="w-5 h-5" />
              </button>
              <button
                type="button"
                onClick={() => setProfileModalOpen(true)}
                title="Your WhatsApp Profile & 6-Digit ID"
                className="rounded-full ring-2 ring-transparent hover:ring-[#8b5cf6]/40 transition cursor-pointer"
              >
                <MessengerAvatar label={effectiveDisplayName} size={34} />
              </button>
            </div>
          </nav>

          {/* Mobile Phone Top Home Strip: App Name + Small Green Update Button + 6-digit ID Profile */}
          <div className="md:hidden flex items-center justify-between gap-2 px-3.5 py-2 bg-[var(--chatbox-surface)] border-b border-[var(--chatbox-border)] shrink-0">
            <div className="flex items-center gap-2 min-w-0">
              <div className="w-7 h-7 rounded-full overflow-hidden border border-[#8b5cf6]/30 bg-white shrink-0">
                <img
                  src="/assets/chatbox_logo_lavender.jpg"
                  alt="Chatbox"
                  className="w-full h-full object-cover"
                />
              </div>
              <span className="font-display text-sm font-bold text-[var(--chatbox-ink)] truncate">
                {dynamicConfig.appTitle || 'Chatbox'}
              </span>
              <GreenUpdateButton
                appUpdateActive={appUpdate.active}
                version={appUpdate.version}
                onClick={() => setShowUpdateDialog(true)}
                compact
              />
            </div>

            <button
              type="button"
              onClick={() => setProfileModalOpen(true)}
              className="inline-flex items-center gap-1.5 pl-1.5 pr-2.5 py-1 rounded-full bg-[#f7f5fb] border border-[var(--chatbox-border)] cursor-pointer shrink-0"
            >
              <MessengerAvatar label={effectiveDisplayName} size={22} />
              <span className="text-[11px] font-mono font-bold text-[#5b4a8f]">
                {ownProfile?.shortId ? `#${ownProfile.shortId}` : 'Profile'}
              </span>
            </button>
          </div>

          {/* Mobile Top Navigation Bar (when Admin sets menu=top) */}
          {appearance.menu === 'top' && (
            <MobileNavBar
              tabs={effectiveNavTabs}
              activeTab={activeTab}
              onSelect={setActiveTab}
              position="top"
            />
          )}

          {/* Main Content Area */}
          <div className="flex-1 min-h-0 min-w-0 overflow-hidden">
            {activeTab === 'demo' && (
              <DemoChatsScreen
                publicWelcomeBanner={dynamicConfig.homeAnnouncement || welcomeBanner}
                userDisplayName={effectiveDisplayName}
                userEmail={currentUser.email}
                isOnline={isOnline}
                appUpdateActive={appUpdate.active}
                onAppUpdateClick={() => setShowUpdateDialog(true)}
                onSignOut={() => void handleSignOut()}
                onOpenCalls={() => setActiveTab('calls')}
                selectedContactId={demoSelectedId}
                onSelectContactId={setDemoSelectedId}
                activeModal={demoModal}
                onSetModal={setDemoModal}
              />
            )}

            {activeTab === 'live' && (
              <LiveChatsScreen
                currentUserId={currentUser.uid}
                displayName={effectiveDisplayName}
                isOnline={isOnline}
                appUpdateActive={appUpdate.active}
                onAppUpdateClick={() => setShowUpdateDialog(true)}
                oneMinAutoReplyEnabled={oneMinAutoReplyEnabled}
                onOpenCalls={() => setActiveTab('calls')}
                ownProfile={ownProfile}
                onProfileLoaded={(p) => setOwnProfile(p)}
                onOpenProfile={() => setProfileModalOpen(true)}
                homeAnnouncement={dynamicConfig.homeAnnouncement || welcomeBanner}
              />
            )}

            {activeTab === 'saira' && (
              <SairaAiScreen
                currentUserId={currentUser.uid}
                isOnline={isOnline}
                appUpdateActive={appUpdate.active}
                onAppUpdateClick={() => setShowUpdateDialog(true)}
                oneMinAutoReplyEnabled={oneMinAutoReplyEnabled}
                onChangeOneMinAutoReply={setOneMinAutoReplyEnabled}
              />
            )}

            {activeTab === 'calls' && (
              <CallsScreen
                isOnline={isOnline}
                onBackToConversations={() => setActiveTab('live')}
              />
            )}

            {activeCustomMenu && (
              <div className="h-full overflow-y-auto bg-[var(--chatbox-bg)] px-5 py-6">
                <div className="max-w-2xl mx-auto space-y-4">
                  <div className="rounded-3xl bg-[var(--chatbox-surface)] border border-[var(--chatbox-border)] p-6 space-y-4 shadow-xs">
                    <div className="flex items-center justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div className="w-11 h-11 rounded-2xl bg-[#efeaf7] text-[#5b4a8f] flex items-center justify-center shrink-0">
                          <CustomMenuIcon name={activeCustomMenu.icon} className="w-5 h-5" />
                        </div>
                        <div>
                          <p className="text-[11px] font-semibold uppercase tracking-wider text-[#8f89a3]">
                            {activeCustomMenu.label}
                          </p>
                          <h1 className="font-display text-xl font-bold text-[var(--chatbox-ink)]">
                            {activeCustomMenu.title}
                          </h1>
                        </div>
                      </div>
                      <GreenUpdateButton
                        appUpdateActive={appUpdate.active}
                        version={appUpdate.version}
                        onClick={() => setShowUpdateDialog(true)}
                      />
                    </div>
                    {dynamicConfig.homeAnnouncement && (
                      <NoticeBanner text={dynamicConfig.homeAnnouncement} />
                    )}
                    <p className="text-sm text-[#3a3347] leading-relaxed whitespace-pre-wrap">
                      {activeCustomMenu.body}
                    </p>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'admin' && isAdmin && (
              <AdminScreen
                currentUserId={currentUser.uid}
                currentUserEmail={currentUser.email}
                isOnline={isOnline}
                appearance={appearance}
                welcomeBanner={welcomeBanner}
                appUpdate={appUpdate}
                dynamicConfig={dynamicConfig}
                onAppUpdateClick={() => setShowUpdateDialog(true)}
              />
            )}
          </div>

          {/* Mobile Bottom Navigation Bar (default) */}
          {appearance.menu !== 'top' && (
            <MobileNavBar
              tabs={effectiveNavTabs}
              activeTab={activeTab}
              onSelect={setActiveTab}
              position="bottom"
            />
          )}
        </div>
      )}
    </>
  );
}

function MobileNavBar({
  tabs,
  activeTab,
  onSelect,
  position,
}: {
  tabs: NavTabItem[];
  activeTab: AppTab;
  onSelect: (tab: AppTab) => void;
  position: 'top' | 'bottom';
}) {
  return (
    <nav
      aria-label="Mobile navigation"
      className={`md:hidden flex items-center justify-around overflow-x-auto h-16 shrink-0 bg-[var(--chatbox-rail-bg)] px-1 ${
        position === 'top'
          ? 'border-b border-[var(--chatbox-border)]'
          : 'border-t border-[var(--chatbox-border)]'
      }`}
    >
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const selected = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            onClick={() => onSelect(tab.id)}
            className={`flex-1 min-w-[58px] h-full flex flex-col items-center justify-center gap-0.5 px-1 text-[10px] font-medium cursor-pointer ${
              selected ? 'text-[#5b4a8f] font-bold' : 'text-[#6b657d]'
            }`}
          >
            {tab.customIcon ? (
              <CustomMenuIcon name={tab.customIcon} className="w-5 h-5" />
            ) : Icon ? (
              <Icon className="w-5 h-5" />
            ) : null}
            <span className="truncate max-w-[64px]">{tab.shortLabel}</span>
          </button>
        );
      })}
    </nav>
  );
}

export default App;

