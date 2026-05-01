package com.mustafafyp.guardianai.services;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.models.Alert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Module 9 — Content Filtering & Network Tracking
 *
 * An AccessibilityService that reads on-screen text and checks it
 * against the parent-configured keyword filter list.
 *
 * On a keyword match:
 *   1. Uploads a keywordHit record to Firebase.
 *   2. Fires an alert via the alerts/ node.
 *
 * Does NOT block, overlay, or interfere with any app.
 * Requires the user to grant Accessibility Service permission in Android Settings.
 *
 * Keyword list is kept in sync via a local broadcast from MainForegroundService.
 */
public class ContentFilterAccessibilityService extends AccessibilityService {

    public static final String TAG                  = "ContentFilterService";
    public static final String ACTION_UPDATE_KEYWORDS = "GUARDIAN_UPDATE_KEYWORDS";
    public static final String EXTRA_KEYWORDS       = "keywords";

    /** Cooldown: don't re-alert the same keyword within this window (ms) */
    private static final long  KEYWORD_COOLDOWN_MS = 60_000L;

    private List<String>           keywords         = new ArrayList<>();
    private final Map<String, Long> lastAlertTime   = new HashMap<>();

    private DatabaseReference dbRef;
    private String            uid;

    private BroadcastReceiver keywordUpdateReceiver;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected");

        // Initialise Firebase reference
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid   = user.getUid();
            dbRef = FirebaseDatabase.getInstance().getReference("users");
            // Load current keyword list from Firebase
            loadKeywordsFromFirebase();
        }

        // Register receiver for keyword updates pushed by MainForegroundService
        keywordUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                List<String> updated = intent.getStringArrayListExtra(EXTRA_KEYWORDS);
                if (updated != null) {
                    keywords = updated;
                    Log.i(TAG, "Keywords updated via broadcast: " + keywords);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_UPDATE_KEYWORDS);
        registerReceiver(keywordUpdateReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (keywordUpdateReceiver != null) {
            try { unregisterReceiver(keywordUpdateReceiver); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
    }

    // ── Event handling ─────────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (keywords.isEmpty()) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return;

        // Extract all visible text from the window
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String pageText = extractText(root).toLowerCase(Locale.getDefault());
        root.recycle();

        if (pageText.isEmpty()) return;

        // Get foreground app name for the alert message
        String pkgName = event.getPackageName() != null
                ? event.getPackageName().toString()
                : "Unknown App";

        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) continue;
            String kw = keyword.trim().toLowerCase(Locale.getDefault());
            if (!pageText.contains(kw)) continue;

            // Cooldown check: don't flood with duplicate alerts
            Long lastTime = lastAlertTime.get(kw);
            long  now     = System.currentTimeMillis();
            if (lastTime != null && (now - lastTime) < KEYWORD_COOLDOWN_MS) continue;

            lastAlertTime.put(kw, now);
            Log.i(TAG, "Keyword detected: \"" + kw + "\" in " + pkgName);

            uploadKeywordHit(keyword.trim(), pkgName, now);
            break; // One alert per event — avoid multiple hits per scan
        }
    }

    // ── Firebase ───────────────────────────────────────────────────────────

    private void loadKeywordsFromFirebase() {
        if (uid == null) return;
        dbRef.child("childs").child(uid).child("keywordFilterList")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        keywords.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String kw = child.getValue(String.class);
                            if (kw != null && !kw.isEmpty()) keywords.add(kw);
                        }
                        Log.i(TAG, "Keywords loaded from Firebase: " + keywords.size());
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void uploadKeywordHit(String keyword, String appPackage, long timestamp) {
        if (uid == null || dbRef == null) return;

        Map<String, Object> hit = new HashMap<>();
        hit.put("keyword",   keyword);
        hit.put("appName",   friendlyAppName(appPackage));
        hit.put("timestamp", timestamp);

        dbRef.child("childs").child(uid)
                .child("keywordHits").push().setValue(hit);

        // Also write an alert so parent sees it in the Alerts screen
        dbRef.child("childs").child(uid).child("alerts").push()
                .setValue(new Alert(
                        "Restricted Content Detected",
                        "Keyword \"" + keyword + "\" found in " + friendlyAppName(appPackage),
                        timestamp
                ));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String extractText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) {
            sb.append(node.getText()).append(" ");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(extractText(child));
                child.recycle();
            }
        }
        return sb.toString();
    }

    private String friendlyAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
}
