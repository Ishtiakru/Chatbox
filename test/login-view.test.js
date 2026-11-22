import test from 'node:test';
import assert from 'node:assert/strict';
import {loginView} from '../src/login-view.js';

test('phone and account creation are explicitly unavailable, Google and demo stay accessible', () => {
  const html=loginView({language:'bn',authReady:true});
  assert.match(html,/ফোন নম্বর দিয়ে লগইন ও অ্যাকাউন্ট তৈরি এখনো চালু হয়নি/);
  assert.match(html,/<input type="tel" disabled/);
  assert.match(html,/<input type="password" disabled/);
  assert.match(html,/class="login-primary phone-login-disabled" disabled/);
  assert.match(html,/class="login-create" disabled/);
  assert.match(html,/data-action="sign-in"\s*>Google দিয়ে লগ ইন/);
  assert.match(html,/data-action="login-guest"/);
  assert.doesNotMatch(html,/<form|data-action="phone-login"/);
});

test('Google sign-in waits for auth readiness and remains disabled while loading', () => {
  assert.match(loginView({language:'en',authReady:false}),/data-action="sign-in" disabled>Checking account/);
  assert.match(loginView({language:'en',authReady:true,authLoading:true}),/data-action="sign-in" disabled>Signing in/);
  assert.match(loginView({language:'en',authReady:true}),/Phone login and account creation are not available yet/);
});
