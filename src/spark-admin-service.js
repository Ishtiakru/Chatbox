import { getFirestore } from 'firebase/firestore';
import { app } from './firebase.js';
import { createSparkAdminService } from './spark-admin-data.js';

export const { getSparkAdminStatus, applySparkWelcome, applySparkAppearance, watchSparkAppearance, getSparkSourceHandover, listSparkDelegations, inviteSparkDelegate, revokeSparkDelegate, acceptSparkDelegation, watchSparkWelcome } = createSparkAdminService(getFirestore(app));
