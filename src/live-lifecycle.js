// Tear down each live resource independently. A failing unsubscribe must not
// prevent the active call from releasing its camera/microphone or leave the
// other listener running. This is not an authorization boundary.
export function disposeLiveResources({callController, blockController, stopConversations, messageFeed, peerProfileController, conversationProfiles}) {
  const cleanups = [
    () => callController?.dispose(),
    () => blockController?.dispose(),
    () => stopConversations?.(),
    () => messageFeed?.stop(),
    () => peerProfileController?.dispose(),
    () => conversationProfiles?.dispose(),
  ];
  for (const cleanup of cleanups) {
    try { cleanup(); } catch { /* Continue closing the remaining resources. */ }
  }
}
