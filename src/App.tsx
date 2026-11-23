import React, { useEffect, useState } from 'react';
import {
  onAuthStateChanged,
  signInWithPopup,
  signOut,
  type User,
} from 'firebase/auth';
import {
  HelpCircle,
  MessageCircle,
  Phone,
  Shield,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import { auth, googleProvider } from './firebase';
import {
  observePublicWelcome,
  observeSparkAppearance,
  observeSparkAppUpdate,
} from './services/sparkAdminService';
import {
  getStoredLoginLanguage,
  getStoredRememberMe,
  getStoredSairaOneMinEnabled,
  saveStoredLoginLanguage,
  saveStoredRememberMe,
} from './services/storagePrefs';
import { useNetworkStatus } from './hooks/useNetworkStatus';
import type {
  AppTab,
  DemoModal,
  SparkAppearance,
  SparkAppUpdate,
} from './types/chatbox';
import { AdminScreen } from './components/AdminScreen';
import { AuthLoginScreen } from './components/AuthLoginScreen';
import { CallsScreen } from './components/CallsScreen';
import {
  AppUpdateDetailsDialog,
  ChatboxLogoWithUpdateBadge,
  MessengerAvatar,
} from './components/Common';
import { DemoChatsScreen } from './components/DemoChatsScreen';
import { LiveChatsScreen } from './components/LiveChatsScreen';
import { SairaAiScreen } from './components/SairaAiScreen';

const NAV_TABS: Array<{
  id: AppTab;
  label: string;
  shortLabel: string;
  icon: React.ComponentType<{ className?: string }>;
}> = [
  { id: 'demo', label: 'Demo', shortLabel: 'Demo', icon: MessageCircle },
  { id: 'live', label: 'Live chats', shortLabel: 'Live', icon: Users },
  { id: 'saira', label: 'Saira AI', shortLabel: 'Saira', icon: Sparkles },
  { id: 'calls', label: 'Calls', shortLabel: 'Calls', icon: Phone },
  { id: 'admin', label: 'Admin', shortLabel: 'Admin', icon: Shield },
];

export function App() {
  const isOnline = useNetworkStatus();

  const [splashDone, setSplashDone] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

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
  const [showUpdateDialog, setShowUpdateDialog] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setSplashDone(true), 1200);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (user) => {
      setCurrentUser(user);
      setAuthReady(true);
    });
    return () => unsub();
  }, []);

  useEffect(() => {
    if (!currentUser) return;
    const unsubWelcome = observePublicWelcome(setWelcomeBanner);
    const unsubAppearance = observeSparkAppearance(setAppearance);
    const unsubUpdate = observeSparkAppUpdate(setAppUpdate);
    return () => {
      unsubWelcome();
      unsubAppearance();
      unsubUpdate();
    };
  }, [currentUser]);

  useEffect(() => {
    document.documentElement.setAttribute(
      'data-theme',
      appearance.theme === 'lavender' ? 'lavender' : 'default'
    );
  }, [appearance.theme]);

  const handleGoogleSignIn = async () => {
    if (authLoading) return;
    setAuthLoading(true);
    setAuthError(null);
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (err) {
      const code =
        typeof err === 'object' && err !== null && 'code' in err
          ? String((err as { code?: unknown }).code)
          : '';
      if (!code.includes('popup-closed-by-user')) {
        setAuthError(err instanceof Error ? err.message : 'Unable to sign in with Google.');
      }
    } finally {
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
            <p className="text-sm text-[#5a5468]">{authError}</p>
            <div className="flex justify-end">
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

            <div className="flex-1 flex flex-col items-center gap-1.5 w-full">
              {NAV_TABS.map((tab) => {
                const Icon = tab.icon;
                const selected = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setActiveTab(tab.id)}
                    className={`w-full py-2.5 px-1 rounded-2xl flex flex-col items-center gap-1 text-[11px] font-medium transition-colors cursor-pointer ${
                      selected
                        ? 'bg-[var(--chatbox-selected-bg)] text-[#5b4a8f] font-semibold'
                        : 'text-[#6b657d] hover:bg-white/60'
                    }`}
                  >
                    <Icon className="w-5 h-5" />
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
              <MessengerAvatar label={currentUser.displayName || 'Y'} size={34} />
            </div>
          </nav>

          {/* Mobile Top Navigation Bar (when Spark Admin sets menu=top) */}
          {appearance.menu === 'top' && (
            <MobileNavBar activeTab={activeTab} onSelect={setActiveTab} position="top" />
          )}

          {/* Main Content Area */}
          <div className="flex-1 min-h-0 min-w-0 overflow-hidden">
            {activeTab === 'demo' && (
              <DemoChatsScreen
                publicWelcomeBanner={welcomeBanner}
                userDisplayName={currentUser.displayName}
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
                displayName={currentUser.displayName || currentUser.phoneNumber}
                isOnline={isOnline}
                appUpdateActive={appUpdate.active}
                onAppUpdateClick={() => setShowUpdateDialog(true)}
                oneMinAutoReplyEnabled={oneMinAutoReplyEnabled}
                onOpenCalls={() => setActiveTab('calls')}
              />
            )}

            {activeTab === 'saira' && (
              <SairaAiScreen
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

            {activeTab === 'admin' && (
              <AdminScreen
                currentUserId={currentUser.uid}
                currentUserEmail={currentUser.email}
                isOnline={isOnline}
                appearance={appearance}
                welcomeBanner={welcomeBanner}
                appUpdate={appUpdate}
                onAppUpdateClick={() => setShowUpdateDialog(true)}
              />
            )}
          </div>

          {/* Mobile Bottom Navigation Bar (default) */}
          {appearance.menu !== 'top' && (
            <MobileNavBar activeTab={activeTab} onSelect={setActiveTab} position="bottom" />
          )}
        </div>
      )}
    </>
  );
}

function MobileNavBar({
  activeTab,
  onSelect,
  position,
}: {
  activeTab: AppTab;
  onSelect: (tab: AppTab) => void;
  position: 'top' | 'bottom';
}) {
  return (
    <nav
      aria-label="Mobile navigation"
      className={`md:hidden grid grid-cols-5 h-16 shrink-0 bg-[var(--chatbox-rail-bg)] ${
        position === 'top'
          ? 'border-b border-[var(--chatbox-border)]'
          : 'border-t border-[var(--chatbox-border)]'
      }`}
    >
      {NAV_TABS.map((tab) => {
        const Icon = tab.icon;
        const selected = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            onClick={() => onSelect(tab.id)}
            className={`flex flex-col items-center justify-center gap-0.5 text-[10px] font-medium cursor-pointer ${
              selected ? 'text-[#5b4a8f] font-bold' : 'text-[#6b657d]'
            }`}
          >
            <Icon className="w-5 h-5" />
            <span>{tab.shortLabel}</span>
          </button>
        );
      })}
    </nav>
  );
}

export default App;
