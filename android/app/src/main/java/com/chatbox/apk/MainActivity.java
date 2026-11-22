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

    @Override public void onStart() {
        super.onStart();
        if (callLabChromeClient != null) callLabChromeClient.setStarted(true);
    }

    @Override public void onStop() {
        if (callLabChromeClient != null) callLabChromeClient.setStarted(false);
        super.onStop();
    }
}
