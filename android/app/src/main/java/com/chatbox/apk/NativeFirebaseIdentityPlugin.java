package com.chatbox.apk;

import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Read-only identity of the Firebase resources compiled into this APK.
 * This bridge is preparation only; it does not enable test builds or sign-in.
 */
@CapacitorPlugin(name = "NativeFirebaseIdentity")
public class NativeFirebaseIdentityPlugin extends Plugin {
    private String requiredString(String name) {
        Resources resources = getContext().getResources();
        int id = resources.getIdentifier(name, "string", getContext().getPackageName());
        if (id == 0) throw new IllegalStateException("Missing Firebase Android configuration.");
        String value = resources.getString(id);
        if (value.isEmpty()) throw new IllegalStateException("Incomplete Firebase Android configuration.");
        return value;
    }

    @PluginMethod
    public void getIdentity(PluginCall call) {
        try {
            JSObject result = new JSObject();
            result.put("projectId", requiredString("project_id"));
            result.put("senderId", requiredString("gcm_defaultSenderId"));
            result.put("androidAppId", requiredString("google_app_id"));
            result.put("packageName", getContext().getPackageName());
            result.put("debuggable", (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            call.resolve(result);
        } catch (Exception error) {
            // Never return resource contents or other error details to the web layer.
            call.reject("Native Firebase identity is unavailable.");
        }
    }
}
