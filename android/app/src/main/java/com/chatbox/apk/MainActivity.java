package com.chatbox.apk;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(NativeFirebaseIdentityPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
