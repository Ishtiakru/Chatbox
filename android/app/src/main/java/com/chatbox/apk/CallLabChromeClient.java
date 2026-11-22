package com.chatbox.apk;

import android.Manifest;
import android.webkit.PermissionRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Debug-only media permission bridge. Not for production calling. */
final class CallLabChromeClient extends BridgeWebChromeClient {
    private final ActivityResultLauncher<String[]> launcher;
    private PermissionRequest pending;
    private String[] requestedResources;
    private boolean inFlight;
    private boolean started;

    CallLabChromeClient(Bridge bridge) {
        super(bridge);
        launcher = bridge.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::permissionResult);
    }

    void setStarted(boolean value) {
        started = value;
        if (!value && pending != null) {
            PermissionRequest request = pending;
            pending = null;
            requestedResources = null;
            request.deny();
        }
        // Keep inFlight until the Android result arrives. This prevents an old
        // permission result from being applied to a later WebView request.
    }

    @Override public void onPermissionRequest(PermissionRequest request) {
        String[] resources = request.getResources();
        if (!started || inFlight || request.getOrigin() == null
                || !CallLabPermissionPolicy.allowed(request.getOrigin().toString(), resources)) {
            request.deny();
            return;
        }
        List<String> androidPermissions = new ArrayList<>();
        if (Arrays.asList(resources).contains(CallLabPermissionPolicy.AUDIO)) androidPermissions.add(Manifest.permission.RECORD_AUDIO);
        if (Arrays.asList(resources).contains(CallLabPermissionPolicy.VIDEO)) androidPermissions.add(Manifest.permission.CAMERA);
        pending = request;
        requestedResources = resources.clone();
        inFlight = true;
        try { launcher.launch(androidPermissions.toArray(new String[0])); }
        catch (RuntimeException error) {
            inFlight = false; pending = null; requestedResources = null;
            request.deny();
        }
    }

    @Override public void onPermissionRequestCanceled(PermissionRequest request) {
        if (request == pending) { pending = null; requestedResources = null; }
    }

    private void permissionResult(Map<String, Boolean> permissions) {
        PermissionRequest request = pending;
        String[] resources = requestedResources;
        pending = null; requestedResources = null; inFlight = false;
        if (request == null) return;
        boolean allowed = started && resources != null;
        if (allowed && Arrays.asList(resources).contains(CallLabPermissionPolicy.AUDIO)) allowed = Boolean.TRUE.equals(permissions.get(Manifest.permission.RECORD_AUDIO));
        if (allowed && Arrays.asList(resources).contains(CallLabPermissionPolicy.VIDEO)) allowed = Boolean.TRUE.equals(permissions.get(Manifest.permission.CAMERA));
        if (allowed) request.grant(resources); else request.deny();
    }
}
