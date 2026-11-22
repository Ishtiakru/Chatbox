// The identity provider/Firebase SDK must enforce that reauthentication belongs
// to the currently signed-in user. These checks protect UI state, not admin access.
export function createGoogleReauth({getUser,isNative,reauthPopup,getNativeCredential,reauthCredential,refreshToken}) {
  if ([getUser,isNative,reauthPopup,getNativeCredential,reauthCredential,refreshToken].some(value=>typeof value!=='function')) throw new Error('Reauthentication dependencies are required.');
  return async function reauthenticate() {
    const user=getUser();
    if (!user?.uid) throw new Error('Sign in first.');
    const ensureSameUser=()=>{if(getUser()!==user) throw new Error('The signed-in account changed.');};
    if (isNative()) {
      const credential=await getNativeCredential();
      ensureSameUser();
      await reauthCredential(user,credential);
    } else {
      await reauthPopup(user);
    }
    ensureSameUser();
    await refreshToken(user);
    ensureSameUser();
  };
}
