package com.mustafafyp.guardianai.models;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Module 9 — Content Filtering & Network Tracking
 * Firebase POJO: stores per-app network usage (bytes rx/tx) per day.
 * Written by NetworkUsageManager and uploaded to:
 *   users/childs/{uid}/networkUsage/{date}/{packageName}
 */
@IgnoreExtraProperties
public class AppNetworkUsage {

    private String packageName;
    private String appName;
    private String date;          // e.g. "2026-05-01"
    private long   rxBytes;       // bytes received (download)
    private long   txBytes;       // bytes sent (upload)
    private long   lastUpdated;   // epoch millis

    /** Required by Firebase */
    public AppNetworkUsage() {}

    public AppNetworkUsage(String packageName, String appName, String date,
                           long rxBytes, long txBytes, long lastUpdated) {
        this.packageName = packageName;
        this.appName     = appName;
        this.date        = date;
        this.rxBytes     = rxBytes;
        this.txBytes     = txBytes;
        this.lastUpdated = lastUpdated;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public String getPackageName() { return packageName; }
    public String getAppName()     { return appName; }
    public String getDate()        { return date; }
    public long   getRxBytes()     { return rxBytes; }
    public long   getTxBytes()     { return txBytes; }
    public long   getLastUpdated() { return lastUpdated; }

    // ── Setters ────────────────────────────────────────────────────────────

    public void setPackageName(String packageName) { this.packageName = packageName; }
    public void setAppName(String appName)         { this.appName = appName; }
    public void setDate(String date)               { this.date = date; }
    public void setRxBytes(long rxBytes)           { this.rxBytes = rxBytes; }
    public void setTxBytes(long txBytes)           { this.txBytes = txBytes; }
    public void setLastUpdated(long lastUpdated)   { this.lastUpdated = lastUpdated; }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Total bytes transferred (rx + tx) */
    public long getTotalBytes() { return rxBytes + txBytes; }
}
