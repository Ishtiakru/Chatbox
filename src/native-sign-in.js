// Categorized diagnostics deliberately exclude provider messages, credentials,
// email addresses and tokens. A stage is a hint, not a root-cause diagnosis.
function stageError(code) {
  const error = new Error('Sign-in could not be completed.');
  error.code = code;
  return error;
}

export async function getNativeGoogleCredential({ready, getToken, makeCredential, legacy = false}) {
  try { await ready(); } catch { throw stageError('native/identity-stage'); }
  let result;
  try { result = await getToken({skipNativeAuth:true, ...(legacy ? {useCredentialManager:false} : {})}); }
  catch { throw stageError('native/google-stage'); }
  const idToken = result?.credential?.idToken;
  if (typeof idToken !== 'string' || !idToken) throw stageError('native/token-stage');
  try { return makeCredential(idToken, result.credential?.accessToken); }
  catch { throw stageError('native/token-stage'); }
}

export async function signInNative({getCredential, signIn}) {
  const credential = await getCredential();
  try { return await signIn(credential); }
  catch (error) {
    const known = new Set(['auth/network-request-failed','auth/operation-not-allowed','auth/invalid-credential','auth/invalid-api-key','auth/too-many-requests','auth/user-disabled']);
    throw stageError(known.has(error?.code) ? error.code : 'native/firebase-stage');
  }
}
