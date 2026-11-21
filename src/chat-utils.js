export const MAX_MESSAGE_LENGTH = 2000;

// Only locally generated, allowlisted input errors are safe to present. Never
// display arbitrary server errors, which may contain internal project details.
export class ChatInputError extends Error {
  constructor(code) {
    super('Invalid chat input.');
    this.name = 'ChatInputError';
    this.code = code;
  }
}
const INPUT_ERRORS = {
  'invalid-peer': 'Enter another user’s valid Chatbox ID.',
  'profile-not-found': 'No Chatbox profile found for this ID. Ask them to sign in to Live chats first.',
  'invalid-message': 'Write a message between 1 and 2000 characters.',
};

export function validPeerUid(uid, ownUid) {
  return typeof uid === 'string' && uid.length > 0 && uid.length <= 128 && !uid.includes('/') && uid !== ownUid;
}
export function normalizeMessage(body) {
  const text = typeof body === 'string' ? body.trim() : '';
  if (!text || text.length > MAX_MESSAGE_LENGTH) throw new ChatInputError('invalid-message');
  return text;
}
export function otherParticipant(participants, uid) {
  return Array.isArray(participants) ? participants.find(id => id !== uid) || '' : '';
}
export function chatErrorMessage(error) {
  if (error instanceof ChatInputError && Object.hasOwn(INPUT_ERRORS, error.code)) return INPUT_ERRORS[error.code];
  if (error?.code === 'permission-denied') return 'Access denied. Check that the Firestore security rules are deployed and your account has permission.';
  if (error?.code === 'unavailable') return 'The chat service is unavailable. Check your connection and try again.';
  if (error?.code === 'not-found') return 'The database or profile was not found. Check the Firestore setup.';
  return 'Something went wrong. Please try again.';
}
