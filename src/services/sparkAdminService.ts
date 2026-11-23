import { reauthenticateWithPopup } from 'firebase/auth';
import {
  collection,
  doc,
  getDoc,
  onSnapshot,
  serverTimestamp,
  setDoc,
  updateDoc,
  type Unsubscribe,
} from 'firebase/firestore';
import { auth, db, googleProvider, handleFirestoreError, OperationType } from '../firebase';
import { isValidSparkTarget } from '../data/sparkAdminCommands';
import type {
  AdminAiUpdatePlan,
  AdminAuthStatus,
  CustomMenuIconName,
  QeReleaseInfo,
  SparkAppearance,
  SparkAppUpdate,
  SparkCommandProposal,
  SparkCustomMenu,
  SparkDelegation,
  SparkDynamicConfig,
} from '../types/chatbox';
import {
  MAX_UPDATE_NOTE_LENGTH,
  MAX_UPDATE_URL_LENGTH,
  MAX_UPDATE_VERSION_LENGTH,
  MAX_WELCOME_LENGTH,
} from '../types/chatbox';

export const BOOTSTRAPPED_OWNER_EMAILS = [
  'ishtiakahmedru@gmail.com',
  'ishtiakahmedus@gmail.com',
] as const;

const ALLOWED_CUSTOM_MENU_ICONS = new Set<CustomMenuIconName>([
  'sparkles',
  'bell',
  'megaphone',
  'book',
  'star',
  'info',
  'grid',
]);

export const DEFAULT_SPARK_DYNAMIC_CONFIG: SparkDynamicConfig = {
  appTitle: 'Chatbox',
  homeAnnouncement: '',
  customMenus: [],
};

function sanitizeCustomMenu(raw: Partial<SparkCustomMenu>): SparkCustomMenu | null {
  const id = String(raw.id ?? '')
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, '')
    .slice(0, 32);
  const label = String(raw.label ?? '').trim().slice(0, 24);
  const title = String(raw.title ?? label).trim().slice(0, 80);
  const body = String(raw.body ?? '').trim().slice(0, 1000);
  const icon = ALLOWED_CUSTOM_MENU_ICONS.has(raw.icon as CustomMenuIconName)
    ? (raw.icon as CustomMenuIconName)
    : 'sparkles';
  if (!id || !label || !title || !body) return null;
  return { id, label, icon, title, body };
}

export function observePublicWelcome(onChange: (welcome: string) => void): Unsubscribe {
  return onSnapshot(
    doc(db, 'settings', 'sparkPublic'),
    (snap) => {
      if (snap.exists()) {
        const val = snap.data().welcome;
        onChange(typeof val === 'string' && val.trim() ? val.trim() : '');
      } else {
        onChange('');
      }
    },
    (error) => {
      handleFirestoreError(error, OperationType.GET, 'settings/sparkPublic', false);
    }
  );
}

export function observeSparkAppearance(onChange: (appearance: SparkAppearance) => void): Unsubscribe {
  let theme: 'default' | 'lavender' = 'default';
  let menu: 'bottom' | 'top' = 'bottom';

  const unsubTheme = onSnapshot(
    doc(db, 'settings', 'sparkTheme'),
    (snap) => {
      const val = snap.exists() ? snap.data().value : 'default';
      theme = val === 'lavender' ? 'lavender' : 'default';
      onChange({ theme, menu });
    },
    (error) => {
      handleFirestoreError(error, OperationType.GET, 'settings/sparkTheme', false);
    }
  );

  const unsubMenu = onSnapshot(
    doc(db, 'settings', 'sparkMenu'),
    (snap) => {
      const val = snap.exists() ? snap.data().value : 'bottom';
      menu = val === 'top' ? 'top' : 'bottom';
      onChange({ theme, menu });
    },
    (error) => {
      handleFirestoreError(error, OperationType.GET, 'settings/sparkMenu', false);
    }
  );

  return () => {
    unsubTheme();
    unsubMenu();
  };
}

export function observeSparkDynamicConfig(
  onChange: (config: SparkDynamicConfig) => void
): Unsubscribe {
  return onSnapshot(
    doc(db, 'settings', 'sparkConfig'),
    (snap) => {
      if (!snap.exists()) {
        onChange(DEFAULT_SPARK_DYNAMIC_CONFIG);
        return;
      }
      const data = snap.data();
      const rawMenus = Array.isArray(data.customMenus) ? data.customMenus : [];
      const customMenus = rawMenus
        .slice(0, 5)
        .map((m) => sanitizeCustomMenu((m ?? {}) as Partial<SparkCustomMenu>))
        .filter((m): m is SparkCustomMenu => Boolean(m));
      onChange({
        appTitle:
          typeof data.appTitle === 'string' && data.appTitle.trim()
            ? data.appTitle.trim().slice(0, 40)
            : 'Chatbox',
        homeAnnouncement:
          typeof data.homeAnnouncement === 'string'
            ? data.homeAnnouncement.trim().slice(0, 240)
            : '',
        customMenus,
      });
    },
    (error) => {
      handleFirestoreError(error, OperationType.GET, 'settings/sparkConfig', false);
    }
  );
}

export function observeSparkAppUpdate(onChange: (update: SparkAppUpdate) => void): Unsubscribe {
  return onSnapshot(
    doc(db, 'settings', 'sparkUpdate'),
    (snap) => {
      if (snap.exists()) {
        const data = snap.data();
        onChange({
          active: Boolean(data.active),
          version: typeof data.version === 'string' ? data.version : '',
          note: typeof data.note === 'string' ? data.note : '',
          downloadUrl: typeof data.downloadUrl === 'string' ? data.downloadUrl : '',
        });
      } else {
        onChange({ active: false, version: '', note: '', downloadUrl: '' });
      }
    },
    (error) => {
      handleFirestoreError(error, OperationType.GET, 'settings/sparkUpdate', false);
    }
  );
}

export async function checkSparkAdminStatus(
  uid: string,
  rawEmail?: string | null
): Promise<AdminAuthStatus> {
  const email = (rawEmail ?? '').trim().toLowerCase();
  if (!uid || !email) return 'DENIED';

  try {
    const adminSnap = await getDoc(doc(db, 'sparkAdmin', 'current'));
    if (adminSnap.exists()) {
      const data = adminSnap.data();
      if (data.uid === uid) return 'OWNER';
    }
  } catch {
    // Non-owner will receive permission-denied on sparkAdmin/current GET; inspect delegation or bootstrap next
  }

  try {
    const delegationSnap = await getDoc(doc(db, 'sparkDelegations', email));
    if (delegationSnap.exists()) {
      const d = delegationSnap.data();
      if (d.status === 'active' && d.uid === uid) return 'DELEGATE';
      if (d.status === 'invited') return 'INVITED_DELEGATE';
    }
  } catch {
    // Ignore permission denied if not invited
  }

  if (BOOTSTRAPPED_OWNER_EMAILS.includes(email as (typeof BOOTSTRAPPED_OWNER_EMAILS)[number])) {
    try {
      await setDoc(doc(db, 'sparkAdmin', 'current'), {
        uid,
        email,
        pendingEmail: '',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return 'OWNER';
    } catch {
      return 'UNCLAIMED_OWNER';
    }
  }

  return 'DENIED';
}

export async function claimSparkOwnership(uid: string, rawEmail: string): Promise<void> {
  const email = rawEmail.trim().toLowerCase();
  const path = 'sparkAdmin/current';
  try {
    await setDoc(doc(db, 'sparkAdmin', 'current'), {
      uid,
      email,
      pendingEmail: '',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, path, true);
  }
}

export async function reauthenticateWithGooglePopup(): Promise<void> {
  const currentUser = auth.currentUser;
  if (!currentUser) {
    throw new Error('Sign in with Google first.');
  }
  await reauthenticateWithPopup(currentUser, googleProvider);
}

export async function saveSparkDynamicConfig(
  uid: string,
  nextConfig: SparkDynamicConfig
): Promise<void> {
  const path = 'settings/sparkConfig';
  const cleanMenus = nextConfig.customMenus
    .slice(0, 5)
    .map((m) => sanitizeCustomMenu(m))
    .filter((m): m is SparkCustomMenu => Boolean(m));
  try {
    await setDoc(doc(db, 'settings', 'sparkConfig'), {
      appTitle: nextConfig.appTitle.trim().slice(0, 40) || 'Chatbox',
      homeAnnouncement: nextConfig.homeAnnouncement.trim().slice(0, 240),
      customMenus: cleanMenus,
      updatedBy: uid,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path, true);
  }
}

export async function interpretAdminUpdatePrompt(
  instruction: string,
  currentMenus: SparkCustomMenu[]
): Promise<AdminAiUpdatePlan> {
  const user = auth.currentUser;
  if (!user) throw new Error('Sign in with Google first.');
  const idToken = await user.getIdToken();

  const resp = await fetch('/api/admin/interpret-update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${idToken}`,
    },
    body: JSON.stringify({
      instruction,
      currentMenus: currentMenus.map((m) => ({ id: m.id, label: m.label })),
    }),
  });
  const data = (await resp.json()) as { plan?: AdminAiUpdatePlan; error?: string };
  if (!resp.ok || !data.plan) {
    throw new Error(data.error || 'Could not interpret update instruction.');
  }
  return data.plan;
}

export async function applyAdminAiUpdatePlan(
  uid: string,
  currentConfig: SparkDynamicConfig,
  plan: AdminAiUpdatePlan
): Promise<string> {
  if (plan.theme === 'lavender' || plan.theme === 'default') {
    await applySparkProposal(uid, { kind: 'setTheme', value: plan.theme });
  }
  if (plan.menuPosition === 'top' || plan.menuPosition === 'bottom') {
    await applySparkProposal(uid, { kind: 'setMenu', value: plan.menuPosition });
  }
  if (typeof plan.welcomeBanner === 'string' && plan.welcomeBanner.trim()) {
    await applySparkProposal(uid, { kind: 'setWelcome', value: plan.welcomeBanner.trim() });
  }
  if (plan.appUpdate && typeof plan.appUpdate.active === 'boolean') {
    await applySparkProposal(uid, {
      kind: 'setAppUpdate',
      active: plan.appUpdate.active,
      version: plan.appUpdate.version || '1.0.1',
      note: plan.appUpdate.note || '',
      downloadUrl: '',
    });
  }

  let updatedMenus = [...currentConfig.customMenus];
  let configChanged = false;

  if (plan.removeMenuId) {
    updatedMenus = updatedMenus.filter((m) => m.id !== plan.removeMenuId);
    configChanged = true;
  }

  if (plan.addOrUpdateMenu) {
    const clean = sanitizeCustomMenu(plan.addOrUpdateMenu);
    if (clean) {
      const idx = updatedMenus.findIndex((m) => m.id === clean.id);
      if (idx >= 0) {
        updatedMenus[idx] = clean;
      } else {
        updatedMenus = [...updatedMenus.slice(-4), clean];
      }
      configChanged = true;
    }
  }

  const nextAppTitle =
    typeof plan.appTitle === 'string' && plan.appTitle.trim()
      ? plan.appTitle.trim().slice(0, 40)
      : currentConfig.appTitle;
  const nextAnnouncement =
    typeof plan.homeAnnouncement === 'string'
      ? plan.homeAnnouncement.trim().slice(0, 240)
      : currentConfig.homeAnnouncement;

  if (
    configChanged ||
    nextAppTitle !== currentConfig.appTitle ||
    nextAnnouncement !== currentConfig.homeAnnouncement
  ) {
    await saveSparkDynamicConfig(uid, {
      appTitle: nextAppTitle,
      homeAnnouncement: nextAnnouncement,
      customMenus: updatedMenus,
    });
  }

  return plan.summary || 'App updated successfully!';
}

export async function fetchCurrentQeRelease(): Promise<QeReleaseInfo | null> {
  try {
    const resp = await fetch('/api/releases/current');
    if (!resp.ok) return null;
    const data = (await resp.json()) as { release?: QeReleaseInfo | null };
    return data.release ?? null;
  } catch {
    return null;
  }
}

export async function buildAndReleaseQeZip(params: {
  uid: string;
  version: string;
  note: string;
}): Promise<QeReleaseInfo> {
  const user = auth.currentUser;
  if (!user) throw new Error('Sign in with Google first.');
  const idToken = await user.getIdToken();

  const resp = await fetch('/api/admin/qe-release', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${idToken}`,
    },
    body: JSON.stringify({
      version: params.version,
      note: params.note,
    }),
  });

  const data = (await resp.json()) as { release?: QeReleaseInfo; error?: string };
  if (!resp.ok || !data.release) {
    throw new Error(data.error || 'Failed to package new release ZIP.');
  }

  const fullDownloadUrl = `${window.location.origin}${data.release.downloadUrl}`;
  await applySparkProposal(params.uid, {
    kind: 'setAppUpdate',
    active: true,
    version: data.release.version,
    note: params.note || `New release v${data.release.version} is available.`,
    downloadUrl: fullDownloadUrl.startsWith('https://') ? fullDownloadUrl : '',
  });

  return data.release;
}

export async function applySparkProposal(
  uid: string,
  proposal: SparkCommandProposal
): Promise<string> {
  if (proposal.kind === 'setWelcome') {
    const welcome = proposal.value.trim().slice(0, MAX_WELCOME_LENGTH);
    if (!welcome) throw new Error('Welcome message cannot be empty.');
    const path = 'settings/sparkPublic';
    try {
      await setDoc(doc(db, 'settings', 'sparkPublic'), {
        welcome,
        updatedBy: uid,
        updatedAt: serverTimestamp(),
      });
      return `Updated public welcome banner to "${welcome}".`;
    } catch (error) {
      handleFirestoreError(error, OperationType.WRITE, path, true);
    }
  }

  if (proposal.kind === 'setTheme') {
    const path = 'settings/sparkTheme';
    try {
      await setDoc(doc(db, 'settings', 'sparkTheme'), {
        value: proposal.value,
        updatedBy: uid,
        updatedAt: serverTimestamp(),
      });
      return `Home appearance updated to ${proposal.value}.`;
    } catch (error) {
      handleFirestoreError(error, OperationType.WRITE, path, true);
    }
  }

  if (proposal.kind === 'setMenu') {
    const path = 'settings/sparkMenu';
    try {
      await setDoc(doc(db, 'settings', 'sparkMenu'), {
        value: proposal.value,
        updatedBy: uid,
        updatedAt: serverTimestamp(),
      });
      return `Mobile menu position updated to ${proposal.value}.`;
    } catch (error) {
      handleFirestoreError(error, OperationType.WRITE, path, true);
    }
  }

  if (proposal.kind === 'setAppUpdate') {
    const version = proposal.version.trim().slice(0, MAX_UPDATE_VERSION_LENGTH);
    const note = proposal.note.trim().slice(0, MAX_UPDATE_NOTE_LENGTH);
    const downloadUrl = proposal.downloadUrl.trim().slice(0, MAX_UPDATE_URL_LENGTH);
    if (proposal.active && !version) {
      throw new Error('Enter a target version to publish an app update notice.');
    }
    if (downloadUrl && !downloadUrl.startsWith('https://')) {
      throw new Error('Download URL must start with https://');
    }
    const path = 'settings/sparkUpdate';
    try {
      await setDoc(doc(db, 'settings', 'sparkUpdate'), {
        active: proposal.active,
        version: proposal.active ? version : '',
        note: proposal.active ? note : '',
        downloadUrl: proposal.active ? downloadUrl : '',
        updatedBy: uid,
        updatedAt: serverTimestamp(),
      });
      return proposal.active
        ? `Published green update button for v${version}.`
        : 'Cleared the app update button.';
    } catch (error) {
      handleFirestoreError(error, OperationType.WRITE, path, true);
    }
  }

  throw new Error('Unsupported command proposal.');
}

export function observeSparkDelegations(
  onChange: (delegations: SparkDelegation[]) => void
): Unsubscribe {
  return onSnapshot(
    collection(db, 'sparkDelegations'),
    (snapshot) => {
      const items: SparkDelegation[] = snapshot.docs.map((d) => {
        const data = d.data();
        return {
          email: String(data.email ?? d.id),
          uid: String(data.uid ?? ''),
          invitedBy: String(data.invitedBy ?? ''),
          status: (data.status as SparkDelegation['status']) ?? 'invited',
          createdAt: data.createdAt ?? null,
          updatedAt: data.updatedAt ?? null,
        };
      });
      items.sort((a, b) => a.email.localeCompare(b.email));
      onChange(items);
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, 'sparkDelegations', false);
    }
  );
}

export async function inviteSparkDelegate(ownerUid: string, rawEmail: string): Promise<void> {
  const email = rawEmail.trim().toLowerCase();
  if (!isValidSparkTarget(email)) {
    throw new Error('Enter a valid Gmail address (e.g., colleague@gmail.com).');
  }
  const path = `sparkDelegations/${email}`;
  try {
    const existing = await getDoc(doc(db, 'sparkDelegations', email));
    if (existing.exists() && existing.data().status === 'revoked') {
      await updateDoc(doc(db, 'sparkDelegations', email), {
        status: 'invited',
        uid: '',
        invitedBy: ownerUid,
        updatedAt: serverTimestamp(),
      });
    } else {
      await setDoc(doc(db, 'sparkDelegations', email), {
        email,
        uid: '',
        invitedBy: ownerUid,
        status: 'invited',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    }
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path, true);
  }
}

export async function revokeSparkDelegate(rawEmail: string): Promise<void> {
  const email = rawEmail.trim().toLowerCase();
  const path = `sparkDelegations/${email}`;
  try {
    await updateDoc(doc(db, 'sparkDelegations', email), {
      status: 'revoked',
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path, true);
  }
}

export async function acceptSparkDelegation(uid: string, rawEmail: string): Promise<void> {
  const email = rawEmail.trim().toLowerCase();
  const path = `sparkDelegations/${email}`;
  try {
    await updateDoc(doc(db, 'sparkDelegations', email), {
      status: 'active',
      uid,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path, true);
  }
}
