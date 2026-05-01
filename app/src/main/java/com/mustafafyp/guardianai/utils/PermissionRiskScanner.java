package com.mustafafyp.guardianai.utils;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.mustafafyp.guardianai.models.App;
import com.mustafafyp.guardianai.models.AppPermissionInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module 10 — Privacy & Permission Awareness
 *
 * Pure utility class that scans installed apps for sensitive Android permissions,
 * maps them to human-readable labels, and computes a per-app risk score.
 *
 * Risk level logic:
 *   HIGH   — declares Camera OR Microphone OR (Location + ≥1 other sensitive perm)
 *   MEDIUM — declares ≥2 sensitive perms but NOT a HIGH combo
 *   LOW    — declares 0–1 sensitive perms
 */
public class PermissionRiskScanner {

    private static final String TAG = "PermissionRiskScanner";

    /**
     * Maps Android permission constant → human-readable label shown in the UI.
     * Ordered map so labels always appear in a consistent order.
     */
    private static final Map<String, String> SENSITIVE_PERM_MAP = new LinkedHashMap<>();

    static {
        SENSITIVE_PERM_MAP.put(Manifest.permission.CAMERA,                    "Camera");
        SENSITIVE_PERM_MAP.put(Manifest.permission.RECORD_AUDIO,              "Microphone");
        SENSITIVE_PERM_MAP.put(Manifest.permission.ACCESS_FINE_LOCATION,      "Location");
        SENSITIVE_PERM_MAP.put(Manifest.permission.ACCESS_COARSE_LOCATION,    "Location");
        SENSITIVE_PERM_MAP.put(Manifest.permission.READ_CONTACTS,             "Contacts");
        SENSITIVE_PERM_MAP.put(Manifest.permission.READ_SMS,                  "SMS");
        SENSITIVE_PERM_MAP.put(Manifest.permission.RECEIVE_SMS,               "SMS");
        SENSITIVE_PERM_MAP.put(Manifest.permission.READ_CALL_LOG,             "Call Log");
        SENSITIVE_PERM_MAP.put(Manifest.permission.READ_PHONE_STATE,          "Phone Identity");
        SENSITIVE_PERM_MAP.put(Manifest.permission.GET_ACCOUNTS,              "Account Info");
        SENSITIVE_PERM_MAP.put(Manifest.permission.READ_EXTERNAL_STORAGE,     "Storage");
        SENSITIVE_PERM_MAP.put("android.permission.READ_MEDIA_IMAGES",        "Storage");
        SENSITIVE_PERM_MAP.put("android.permission.READ_MEDIA_VIDEO",         "Storage");
        SENSITIVE_PERM_MAP.put(Manifest.permission.USE_BIOMETRIC,             "Biometrics");
        SENSITIVE_PERM_MAP.put("android.permission.USE_FINGERPRINT",          "Biometrics");
        SENSITIVE_PERM_MAP.put(Manifest.permission.BODY_SENSORS,              "Body Sensors");
        SENSITIVE_PERM_MAP.put(Manifest.permission.PROCESS_OUTGOING_CALLS,    "Outgoing Calls");
        SENSITIVE_PERM_MAP.put(Manifest.permission.READ_CALENDAR,             "Calendar");
        SENSITIVE_PERM_MAP.put("android.permission.QUERY_ALL_PACKAGES",       "App History");
        SENSITIVE_PERM_MAP.put(Manifest.permission.SEND_SMS,                  "SMS");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scans every app in {@code knownApps} (the list already stored in Firebase) for
     * sensitive permissions. Returns a list sorted HIGH → MEDIUM → LOW.
     *
     * @param pm        PackageManager from the Service context
     * @param knownApps The app list loaded from Firebase (user-facing apps only)
     */
    public List<AppPermissionInfo> scanInstalledApps(PackageManager pm,
                                                      List<App> knownApps) {
        List<AppPermissionInfo> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (App app : knownApps) {
            if (app == null || app.getPackageName() == null) continue;

            try {
                PackageInfo pi = pm.getPackageInfo(
                        app.getPackageName(),
                        PackageManager.GET_PERMISSIONS);

                List<String> sensitiveLabels = extractSensitiveLabels(pi);
                String riskLevel = computeRiskLevel(sensitiveLabels, pi.requestedPermissions);

                result.add(new AppPermissionInfo(
                        app.getAppName(),
                        app.getPackageName(),
                        riskLevel,
                        sensitiveLabels,
                        now));

            } catch (PackageManager.NameNotFoundException e) {
                // App was uninstalled between loading Firebase list and scanning — skip
                Log.d(TAG, "Package not found (uninstalled?): " + app.getPackageName());
            }
        }

        // Sort: HIGH first, then MEDIUM, then LOW
        result.sort((a, b) -> riskOrder(a.getRiskLevel()) - riskOrder(b.getRiskLevel()));
        return result;
    }

    /**
     * Compute risk level for a single app given its human-readable sensitive labels
     * and its full raw permission list (used to detect specific HIGH combos).
     */
    public String computeRiskLevel(List<String> sensitiveLabels,
                                    String[] requestedPermissions) {
        if (sensitiveLabels == null || sensitiveLabels.isEmpty()) return "LOW";

        boolean hasCamera      = sensitiveLabels.contains("Camera");
        boolean hasMic         = sensitiveLabels.contains("Microphone");
        boolean hasLocation    = sensitiveLabels.contains("Location");
        int     sensitiveCount = sensitiveLabels.size();

        // HIGH: camera alone, mic alone, or location + at least one other sensitive perm
        if (hasCamera || hasMic) return "HIGH";
        if (hasLocation && sensitiveCount >= 2) return "HIGH";

        // MEDIUM: 2+ sensitive perms
        if (sensitiveCount >= 2) return "MEDIUM";

        // LOW: 0 or 1
        return "LOW";
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * From a PackageInfo, extract a deduplicated list of human-readable sensitive
     * permission labels (e.g. ["Camera", "Location"]).
     */
    private List<String> extractSensitiveLabels(PackageInfo pi) {
        List<String> labels = new ArrayList<>();
        if (pi.requestedPermissions == null) return labels;

        for (String perm : pi.requestedPermissions) {
            if (perm == null) continue;
            String label = SENSITIVE_PERM_MAP.get(perm);
            if (label != null && !labels.contains(label)) {
                labels.add(label);
            }
        }
        return labels;
    }

    private static int riskOrder(String riskLevel) {
        if ("HIGH".equals(riskLevel))   return 0;
        if ("MEDIUM".equals(riskLevel)) return 1;
        return 2; // LOW
    }
}
