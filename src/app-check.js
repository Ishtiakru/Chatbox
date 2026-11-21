import { initializeAppCheck, ReCaptchaV3Provider } from 'firebase/app-check';
import { app } from './firebase.js';
import { APP_CHECK_SITE_KEY } from './feature-flags.js';

let appCheck;
export function ensureAppCheck() {
  if (!APP_CHECK_SITE_KEY) throw new Error('App Check is not configured.');
  if (!appCheck) appCheck = initializeAppCheck(app, { provider: new ReCaptchaV3Provider(APP_CHECK_SITE_KEY), isTokenAutoRefreshEnabled: true });
  return appCheck;
}
