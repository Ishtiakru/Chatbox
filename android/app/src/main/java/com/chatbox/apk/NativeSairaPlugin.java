package com.chatbox.apk;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** Isolated test-only bridge. UI sign-in is not a backend authorization boundary. */
@CapacitorPlugin(name = "NativeSaira")
public class NativeSairaPlugin extends Plugin {
    private static final String PROJECT = "chatbox-test-2c236731bf";
    private static volatile boolean configured = false;

    static void initializeTestAppCheck(Context context) {
        if (!NativeFirebaseIdentityPlugin.isCallLabEnabled(context)) return;
        try {
            FirebaseApp app = FirebaseApp.initializeApp(context);
            if (app == null) app = FirebaseApp.getInstance();
            if (!PROJECT.equals(app.getOptions().getProjectId())) return;
            FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
            configured = true;
        } catch (RuntimeException ignored) { configured = false; }
    }

    @PluginMethod
    public void ask(PluginCall call) {
        String question = call.getString("question", "").trim();
        String knowledge = call.getString("knowledge", "");
        if (!configured || !NativeFirebaseIdentityPlugin.isCallLabEnabled(getContext())
            || question.isEmpty() || question.length() > 1000 || knowledge.length() > 30000) {
            call.reject("AI is unavailable or the request is invalid.");
            return;
        }
        // Fail closed if attestation is absent; no token is returned to JS or logs.
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener(token -> {
                try {
                    GenerativeModel gm = FirebaseAI.getInstance(GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash");
                    GenerativeModelFutures model = GenerativeModelFutures.from(gm);
                    Content content = new Content.Builder().addText(
                        "You are Saira, a helpful Chatbox guide. Answer in the user's language. " +
                        "Use the following release information for app facts, say when unsure, " +
                        "and never claim planned features are active or execute administrative actions.\n\n" +
                        knowledge + "\n\nUser question:\n" + question).build();
                    Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
                        @Override public void onSuccess(GenerateContentResponse result) {
                            String answer = result != null ? result.getText() : null;
                            if (answer == null || answer.trim().isEmpty()) { call.reject("AI service is unavailable."); return; }
                            JSObject out = new JSObject();
                            out.put("answer", answer);
                            call.resolve(out);
                        }
                        @Override public void onFailure(Throwable error) { call.reject("AI service is unavailable."); }
                    }, MoreExecutors.directExecutor());
                } catch (RuntimeException error) { call.reject("AI service is unavailable."); }
            })
            .addOnFailureListener(error -> call.reject("App Check is unavailable."));
    }
}
