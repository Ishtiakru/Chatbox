export function validateAndroidFirebaseConfig(config) {
  if (config?.project_info?.project_id !== 'chatbox-afb8c') throw new Error('The Android Firebase configuration must belong to chatbox-afb8c.');
  const client = config?.client?.find(item => item?.client_info?.android_client_info?.package_name === 'com.chatbox.apk');
  if (!client) throw new Error('The Firebase config must include the com.chatbox.apk Android app.');
  if (!client.oauth_client?.some(item => item.client_type === 3 && typeof item.client_id === 'string' && item.client_id.length)) {
    throw new Error('The Firebase config does not contain a web OAuth client. Enable Google sign-in, register Android SHA fingerprints, then download a fresh google-services.json.');
  }
  return true;
}
