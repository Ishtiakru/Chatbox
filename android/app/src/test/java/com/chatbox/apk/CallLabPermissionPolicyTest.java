package com.chatbox.apk;

import static org.junit.Assert.*;
import org.junit.Test;

public class CallLabPermissionPolicyTest {
    @Test public void permitsOnlyLocalCaptureResources() {
        String a = CallLabPermissionPolicy.AUDIO, v = CallLabPermissionPolicy.VIDEO;
        assertTrue(CallLabPermissionPolicy.allowed("https://localhost", new String[]{a}));
        assertTrue(CallLabPermissionPolicy.allowed("https://localhost", new String[]{a, v}));
        for (String origin : new String[]{"http://localhost", "https://localhost:443", "https://evil.test", "https://localhost.evil.test", "https://user@localhost", "https://localhost/path", "https://localhost?x=1", "not a uri"}) {
            assertFalse(origin, CallLabPermissionPolicy.allowed(origin, new String[]{a}));
        }
        assertFalse(CallLabPermissionPolicy.allowed("https://localhost", new String[]{a,"android.webkit.resource.PROTECTED_MEDIA_ID"}));
        assertFalse(CallLabPermissionPolicy.allowed("https://localhost", new String[]{}));
        assertFalse(CallLabPermissionPolicy.allowed(null, new String[]{a}));
        assertFalse(CallLabPermissionPolicy.allowed("https://localhost", null));
    }
}
