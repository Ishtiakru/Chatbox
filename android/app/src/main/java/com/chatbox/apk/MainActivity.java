package com.chatbox.apk;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private CallLabChromeClient callLabChromeClient;

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(NativeFirebaseIdentityPlugin.class);
        super.onCreate(savedInstanceState);
        if (getBridge() != null && NativeFirebaseIdentityPlugin.isCallLabEnabled(this)) {
            callLabChromeClient = new CallLabChromeClient(getBridge());
            getBridge().getWebView().setWebChromeClient(callLabChromeClient);
        }
    }

    private void notifyCallLab(String script) {
        // WebView may already be destroyed; do not crash during lifecycle cleanup.
        try { getBridge().getWebView().evaluateJavascript(script, null); }
        catch (RuntimeException ignored) { /* Best effort only. */ }
    }

    @Override public void onStart() {
        super.onStart();
        if (callLabChromeClient != null) {
            callLabChromeClient.setStarted(true);
            // Complements WebView visibility events; delivery is best effort.
            notifyCallLab("window.dispatchEvent(new Event('chatbox-call-lab-start'))");
        }
    }

    @Override public void onStop() {
        if (callLabChromeClient != null) {
            callLabChromeClient.setStarted(false);
            // Best effort only: WebView execution can be delayed or suspended.
            // JS tears down local call media and discovery without waiting for
            // a possibly unavailable Firestore end acknowledgement.
            notifyCallLab("window.dispatchEvent(new Event('chatbox-call-lab-stop'))");
        }
        super.onStop();
    }
}
