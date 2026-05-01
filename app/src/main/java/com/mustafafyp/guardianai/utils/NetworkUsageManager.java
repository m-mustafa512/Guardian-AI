package com.mustafafyp.guardianai.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.TrafficStats;

import com.mustafafyp.guardianai.models.App;
import com.mustafafyp.guardianai.models.AppNetworkUsage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Module 9 — Content Filtering & Network Tracking
 *
 * Tracks per-app network usage using Android's TrafficStats API.
 *
 * Strategy: SESSION-START BASELINE
 *   When initSessionBaseline() is called (as soon as apps are loaded from Firebase),
 *   the current cumulative TrafficStats bytes for every app are captured in memory.
 *   On every subsequent poll, we calculate:
 *       sessionRx = currentRx - baselineRx
 *       sessionTx = currentTx - baselineTx
 *   This represents bytes used SINCE THE CHILD APP WAS OPENED.
 *   Data is available immediately on the FIRST poll (10 seconds after service start),
 *   not after the second poll (5 minutes later).
 *
 * No special permissions are required beyond PACKAGE_USAGE_STATS.
 * TrafficStats is available on all API levels.
 */
public class NetworkUsageManager {

    /** Alert threshold: 50 MB cumulative since session start */
    public static final long LARGE_TRANSFER_THRESHOLD_BYTES = 50L * 1024 * 1024;

    private final Context        context;
    private final PackageManager pm;

    /** Session-start baselines — captured once when apps are loaded from Firebase */
    private final Map<String, Long> sessionBaselineRx = new HashMap<>();
    private final Map<String, Long> sessionBaselineTx = new HashMap<>();

    public NetworkUsageManager(Context context) {
        this.context = context.getApplicationContext();
        this.pm      = this.context.getPackageManager();
    }

    // ── Session baseline ───────────────────────────────────────────────────

    /**
     * Captures the current TrafficStats bytes for all known apps as the
     * session-start baseline. Call this once, immediately after the apps list
     * is first loaded from Firebase (in MainForegroundService's appsListener).
     *
     * After this call, pollNetworkUsage() will report bytes used SINCE this
     * moment — data appears on the very first poll.
     */
    public void initSessionBaseline(List<App> apps) {
        sessionBaselineRx.clear();
        sessionBaselineTx.clear();

        if (apps == null) return;

        for (App app : apps) {
            String pkg = app.getPackageName();
            if (pkg == null) continue;

            int uid = getUidForPackage(pkg);
            if (uid < 0) continue;

            long rx = safeGetRxBytes(uid);
            long tx = safeGetTxBytes(uid);

            if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) continue;

            sessionBaselineRx.put(pkg, rx);
            sessionBaselineTx.put(pkg, tx);
        }
    }

    // ── Result container ───────────────────────────────────────────────────

    public static class PollResult {
        public final List<AppNetworkUsage> usageList;
        public final List<AppNetworkUsage> largeTranferAlerts;

        PollResult(List<AppNetworkUsage> usageList, List<AppNetworkUsage> alerts) {
            this.usageList          = usageList;
            this.largeTranferAlerts = alerts;
        }
    }

    // ── Main polling method ────────────────────────────────────────────────

    /**
     * Reads current TrafficStats for each app, subtracts the session-start
     * baseline, and returns cumulative usage since the service started.
     *
     * If the baseline has not been initialised yet (initSessionBaseline not
     * called), it is initialised now and an empty result is returned so the
     * next poll has a valid baseline to compare against.
     *
     * Should be called on a background thread (ScheduledExecutorService).
     */
    public PollResult pollNetworkUsage(List<App> apps) {
        if (apps == null || apps.isEmpty()) {
            return new PollResult(new ArrayList<>(), new ArrayList<>());
        }

        // Fallback: initialise baseline now if it was never set
        if (sessionBaselineRx.isEmpty()) {
            initSessionBaseline(apps);
            return new PollResult(new ArrayList<>(), new ArrayList<>());
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<AppNetworkUsage> usageList = new ArrayList<>();
        List<AppNetworkUsage> alertList = new ArrayList<>();

        for (App app : apps) {
            String pkg = app.getPackageName();
            if (pkg == null) continue;

            int uid = getUidForPackage(pkg);
            if (uid < 0) continue;

            long currentRx = safeGetRxBytes(uid);
            long currentTx = safeGetTxBytes(uid);

            if (currentRx == TrafficStats.UNSUPPORTED || currentTx == TrafficStats.UNSUPPORTED) continue;

            // If this app was not in the baseline (installed after session start), skip
            if (!sessionBaselineRx.containsKey(pkg)) continue;

            long deltaRx = Math.max(0, currentRx - sessionBaselineRx.get(pkg));
            long deltaTx = Math.max(0, currentTx - sessionBaselineTx.get(pkg));

            // Skip apps that have used zero data since session start
            if (deltaRx == 0 && deltaTx == 0) continue;

            AppNetworkUsage usage = new AppNetworkUsage(
                    pkg,
                    app.getAppName(),
                    today,
                    deltaRx,
                    deltaTx,
                    System.currentTimeMillis()
            );
            usageList.add(usage);

            // Large transfer alert
            if ((deltaRx + deltaTx) > LARGE_TRANSFER_THRESHOLD_BYTES) {
                alertList.add(usage);
            }
        }

        return new PollResult(usageList, alertList);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private int getUidForPackage(String packageName) {
        try {
            return pm.getApplicationInfo(packageName, 0).uid;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private long safeGetRxBytes(int uid) {
        try { return TrafficStats.getUidRxBytes(uid); } catch (Exception e) { return TrafficStats.UNSUPPORTED; }
    }

    private long safeGetTxBytes(int uid) {
        try { return TrafficStats.getUidTxBytes(uid); } catch (Exception e) { return TrafficStats.UNSUPPORTED; }
    }

    // ── Formatting utility (used by adapter) ──────────────────────────────

    /**
     * Formats bytes into a human-readable string: "< 1 KB", "12.3 MB", "1.2 GB".
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024)                  return "< 1 KB";
        if (bytes < 1024 * 1024)          return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)  return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
