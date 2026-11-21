import { getFirestore } from 'firebase/firestore';
import { app } from './firebase.js';
import { createSparkAdminService } from './spark-admin-data.js';

export const { getSparkAdminStatus, applySparkWelcome, listSparkDelegations, inviteSparkDelegate, revokeSparkDelegate, acceptSparkDelegation, watchSparkWelcome } = createSparkAdminService(getFirestore(app));
