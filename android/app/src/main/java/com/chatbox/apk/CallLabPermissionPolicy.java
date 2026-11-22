package com.chatbox.apk;

import java.net.URI;

/** Narrow test-only WebView media policy; not an authorization boundary. */
final class CallLabPermissionPolicy {
    static final String AUDIO = "android.webkit.resource.AUDIO_CAPTURE";
    static final String VIDEO = "android.webkit.resource.VIDEO_CAPTURE";

    static boolean allowed(String origin, String[] resources) {
        if (origin == null || resources == null || resources.length == 0) return false;
        try {
            URI uri = URI.create(origin);
            if (!"https".equals(uri.getScheme()) || !"localhost".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())
                    || uri.getQuery() != null || uri.getFragment() != null) return false;
        } catch (IllegalArgumentException error) { return false; }
        for (String resource : resources) {
            if (!AUDIO.equals(resource) && !VIDEO.equals(resource)) return false;
        }
        return true;
    }
}
