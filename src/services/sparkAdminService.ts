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
  AdminAuthStatus,
  SparkAppearance,
  SparkAppUpdate,
  SparkCommandProposal,
  SparkDelegation,
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
        ? `Published green update badge for v${version}.`
        : 'Cleared the app update badge notice.';
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
