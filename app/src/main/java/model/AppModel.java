package com.mustafafyp.guardianai.models;

public class AppModel {
    private String name;
    private String packageName;
    private boolean blocked;

    // Empty constructor required for Firebase
    public AppModel() {
    }

    // Constructor for manual creation
    public AppModel(String packageName, boolean blocked) {
        this.packageName = packageName;
        this.blocked = blocked;
    }

    // Full Constructor
    public AppModel(String name, String packageName, boolean blocked) {
        this.name = name;
        this.packageName = packageName;
        this.blocked = blocked;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }
}