package com.mustafafyp.guardianai.models;

import java.util.ArrayList;
import java.util.HashMap;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Child extends User {
	private String parentEmail;
	private ArrayList<App> apps = new ArrayList<>();
	private ArrayList<Contact> Contacts = new ArrayList<>();
	private Location location;
	private HashMap<String, Message> messages = new HashMap<>();
	private HashMap<String, Call> calls = new HashMap<>();
	private ScreenLock screenLock;
	private String profileImage;
	private boolean appDeleted;
	private int batteryLevel;
	private String deviceModel;
	private long totalScreenTime;
	private String topAppPackageName;
	private long topAppUsageDuration;
	@com.google.firebase.database.Exclude
	private ArrayList<App> apps = new ArrayList<>();
	private HashMap<String, Alert> alerts = new HashMap<>();
	private HashMap<String, Long> dailyUsage = new HashMap<>();
	
	public Child() {
	}
	
	public Child(String name, String email, String parentEmail) {
		super(name, email);
		this.parentEmail = parentEmail;
	}
	
	public String getParentEmail() {
		return parentEmail;
	}
	
	public void setParentEmail(String parentEmail) {
		this.parentEmail = parentEmail;
	}
	
	public ArrayList<App> getApps() {
		return apps;
	}
	
	public void setApps(ArrayList<App> apps) {
		this.apps = apps;
	}
	
	public ArrayList<Contact> getContacts() {
		return Contacts;
	}
	
	public void setContacts(ArrayList<Contact> contacts) {
		Contacts = contacts;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location location) {
		this.location = location;
	}
	
	public HashMap<String, Message> getMessages() {
		return messages;
	}
	
	public void setMessages(HashMap<String, Message> messages) {
		this.messages = messages;
	}
	
	public HashMap<String, Call> getCalls() {
		return calls;
	}
	
	public void setCalls(HashMap<String, Call> calls) {
		this.calls = calls;
	}
	
	public ScreenLock getScreenLock() {
		return screenLock;
	}
	
	public void setScreenLock(ScreenLock screenLock) {
		this.screenLock = screenLock;
	}
	
	@Override
	public String getProfileImage() {
		return profileImage;
	}
	
	@Override
	public void setProfileImage(String profileImage) {
		this.profileImage = profileImage;
	}
	
	public boolean isAppDeleted() {
		return appDeleted;
	}
	
	public void setAppDeleted(boolean appDeleted) {
		this.appDeleted = appDeleted;
	}

	public int getBatteryLevel() {
		return batteryLevel;
	}

	public void setBatteryLevel(int batteryLevel) {
		this.batteryLevel = batteryLevel;
	}

	public String getDeviceModel() {
		return deviceModel;
	}

	public void setDeviceModel(String deviceModel) {
		this.deviceModel = deviceModel;
	}

	public long getTotalScreenTime() {
		return totalScreenTime;
	}

	public void setTotalScreenTime(long totalScreenTime) {
		this.totalScreenTime = totalScreenTime;
	}

	public HashMap<String, Alert> getAlerts() {
		return alerts;
	}

	public void setAlerts(Object alerts) {
		if (alerts instanceof HashMap) {
			this.alerts = (HashMap<String, Alert>) alerts;
		}
	}

	public HashMap<String, Long> getDailyUsage() {
		return dailyUsage;
	}

	public void setDailyUsage(Object dailyUsage) {
		if (dailyUsage instanceof HashMap) {
			this.dailyUsage = (HashMap<String, Long>) dailyUsage;
		}
	}

	public String getTopAppPackageName() {
		return topAppPackageName;
	}

	public void setTopAppPackageName(String topAppPackageName) {
		this.topAppPackageName = topAppPackageName;
	}

	public long getTopAppUsageDuration() {
		return topAppUsageDuration;
	}

	public void setTopAppUsageDuration(long topAppUsageDuration) {
		this.topAppUsageDuration = topAppUsageDuration;
	}
}
