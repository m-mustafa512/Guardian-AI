package com.mustafafyp.guardianai.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 10 — Privacy & Permission Awareness
 *
 * Firebase-serializable POJO representing the privacy audit result for a single
 * installed app on the child's device.
 */
public class AppPermissionInfo {

    private String       appName;
    private String       packageName;
    /** "HIGH", "MEDIUM", or "LOW" */
    private String       riskLevel;
    /** Human-readable permission labels, e.g. ["Camera", "Microphone", "Location"] */
    private List<String> sensitivePermissions;
    private long         lastScanned;

    // ── Required no-arg constructor for Firebase ─────────────────────────────

    public AppPermissionInfo() {
        sensitivePermissions = new ArrayList<>();
    }

    public AppPermissionInfo(String appName,
                             String packageName,
                             String riskLevel,
                             List<String> sensitivePermissions,
                             long lastScanned) {
        this.appName              = appName;
        this.packageName          = packageName;
        this.riskLevel            = riskLevel;
        this.sensitivePermissions = sensitivePermissions != null
                ? sensitivePermissions : new ArrayList<>();
        this.lastScanned          = lastScanned;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getAppName()              { return appName; }
    public void   setAppName(String v)      { appName = v; }

    public String getPackageName()          { return packageName; }
    public void   setPackageName(String v)  { packageName = v; }

    public String getRiskLevel()            { return riskLevel; }
    public void   setRiskLevel(String v)    { riskLevel = v; }

    public List<String> getSensitivePermissions()              { return sensitivePermissions; }
    public void         setSensitivePermissions(List<String> v){ sensitivePermissions = v; }

    public long getLastScanned()            { return lastScanned; }
    public void setLastScanned(long v)      { lastScanned = v; }
}
