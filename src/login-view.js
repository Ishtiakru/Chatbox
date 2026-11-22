// The phone controls are deliberately disabled until an audited authentication
// service exists. Only the pre-existing Google action is connected.
export function loginView({language = 'bn', authReady = false, authLoading = false} = {}) {
  const bn = language !== 'en';
  const text = bn ? {
    language:'ভাষা', languageLabel:'ভাষা নির্বাচন করুন', title:'আপনার মানুষদের সাথে থাকুন',
    subtitle:'কথোপকথনের জন্য আপনার নিজের জায়গা।', phone:'ফোন নম্বর', password:'পাসওয়ার্ড',
    pending:'ফোন নম্বর দিয়ে লগইন ও অ্যাকাউন্ট তৈরি এখনো চালু হয়নি।', login:'লগ ইন',
    create:'নতুন অ্যাকাউন্ট তৈরি করুন', or:'অথবা', checking:'অ্যাকাউন্ট যাচাই হচ্ছে…', loading:'সাইন ইন হচ্ছে…',
    google:'Google দিয়ে লগ ইন', demo:'ডেমো দেখুন', disclaimer:'ডেমোতে সাইন ইন হয় না; লাইভ ফিচারের জন্য সাইন ইন প্রয়োজন।',
  } : {
    language:'Language', languageLabel:'Select language', title:'Stay close to your people',
    subtitle:'A place for your conversations.', phone:'Phone number', password:'Password',
    pending:'Phone login and account creation are not available yet.', login:'Log in',
    create:'Create new account', or:'or', checking:'Checking account…', loading:'Signing in…',
    google:'Log in with Google', demo:'Explore the demo', disclaimer:'Demo access does not sign you in; live features require an account.',
  };
  return `<main class="lavender-login" lang="${bn ? 'bn' : 'en'}"><div class="login-content">
    <div class="login-top"><label for="login-language" class="login-language-label">${text.language}</label><select id="login-language" aria-label="${text.languageLabel}"><option value="bn" ${bn ? 'selected' : ''}>বাংলা (BD)</option><option value="en" ${!bn ? 'selected' : ''}>English (US)</option></select></div>
    <div class="login-intro"><img class="login-logo" src="/chatbox-logo.jpg" alt="Chatbox"><h1>Chatbox</h1><p>${text.title}<br>${text.subtitle}</p></div>
    <section class="login-actions" aria-label="${bn ? 'লগইন' : 'Login'}">
      <label class="login-field"><span>${text.phone}</span><input type="tel" disabled autocomplete="off" aria-describedby="phone-login-pending" placeholder="${text.phone}"></label>
      <label class="login-field"><span>${text.password}</span><input type="password" disabled autocomplete="off" aria-describedby="phone-login-pending" placeholder="${text.password}"></label>
      <p id="phone-login-pending" class="login-pending">${text.pending}</p>
      <button type="button" class="login-primary phone-login-disabled" disabled>${text.login}</button>
      <button type="button" class="login-create" disabled>${text.create}</button>
      <div class="login-divider"><span></span><b>${text.or}</b><span></span></div>
      <button type="button" class="login-google" data-action="sign-in" ${!authReady || authLoading ? 'disabled' : ''}>${!authReady ? text.checking : authLoading ? text.loading : text.google}</button>
      <button type="button" class="login-demo" data-action="login-guest">${text.demo}</button>
      <p class="login-disclaimer">${text.disclaimer}</p>
    </section><div class="login-wordmark">✦ &nbsp; Chatbox</div>
  </div></main>`;
}
