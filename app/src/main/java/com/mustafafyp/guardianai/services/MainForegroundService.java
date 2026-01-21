package com.mustafafyp.guardianai.services;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.activities.BlockedAppActivity;
import com.mustafafyp.guardianai.activities.ChildSignedInActivity;
import com.mustafafyp.guardianai.broadcasts.*;
import com.mustafafyp.guardianai.models.*;

import java.util.*;
import java.util.concurrent.*;

import static com.mustafafyp.guardianai.NotificationChannelCreator.CHANNEL_ID;

public class MainForegroundService extends Service {

	public static final int NOTIFICATION_ID = 27;
	public static final String TAG = "MainServiceTAG";
	public static final String BLOCKED_APP_NAME_EXTRA =
			"com.mansourappdevelopment.androidapp.kidsafe.services.BLOCKED_APP_NAME_EXTRA";

	private static final long APP_CHECK_INTERVAL = 2500;
	private static final long USAGE_STATS_INTERVAL = 15 * 60 * 1000;
	private static final long LOCATION_INTERVAL = 30 * 1000;
	private static final float LOCATION_DISPLACEMENT = 50f;

	private ScheduledExecutorService scheduler;

	private ArrayList<App> apps = new ArrayList<>();
	private boolean appsLoadedFromFirebase = false;

	private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
	private DatabaseReference databaseReference = firebaseDatabase.getReference("users");

	private PhoneStateReceiver phoneStateReceiver;
	private SmsReceiver smsReceiver;
	private AppInstalledReceiver appInstalledReceiver;
	private AppRemovedReceiver appRemovedReceiver;
	private ScreenTimeReceiver screenTimeReceiver;

	private ValueEventListener appsListener;
	private ValueEventListener screenLockListener;
	private ValueEventListener geofenceListener;

	private String uid;
	private String childEmail;

	private LocationManager locationManager;
	private LocationListener locationListener;
	private LocationListener geofenceLocationListener;
	private Location lastUploadedLocation;

	private com.mustafafyp.guardianai.models.Location geofenceConfig;
	private boolean wasOutOfFence = false;

	private long lastBatterySync = 0;

	// ===================== LIFECYCLE =====================

	@Override
	public void onCreate() {
		super.onCreate();
		scheduler = Executors.newScheduledThreadPool(2);
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		Log.i(TAG, "Service created");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
		if (user == null) return START_STICKY;

		uid = user.getUid();
		childEmail = user.getEmail();

		startForegroundService();
		registerReceivers();
		setupFirebaseListeners();
		setupLocationTracking();
		Log.i(TAG, "User authenticated: " + (user != null) + ", UID: " + uid);

		// Upload contacts ONCE (same as old behavior)
		uploadContacts(getContacts());

		scheduler.scheduleAtFixedRate(
				this::checkBlockedApps,
				0,
				APP_CHECK_INTERVAL,
				TimeUnit.MILLISECONDS
		);

		scheduler.scheduleAtFixedRate(
				this::aggregateUsageStats,
				1,
				USAGE_STATS_INTERVAL,
				TimeUnit.MILLISECONDS
		);

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (scheduler != null) scheduler.shutdownNow();

		if (locationManager != null) {
			if (locationListener != null) locationManager.removeUpdates(locationListener);
			if (geofenceLocationListener != null)
				locationManager.removeUpdates(geofenceLocationListener);
		}

		unregisterSafe(phoneStateReceiver);
		unregisterSafe(smsReceiver);
		unregisterSafe(appInstalledReceiver);
		unregisterSafe(appRemovedReceiver);
		unregisterSafe(screenTimeReceiver);

		removeFirebaseListeners();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	// ===================== FOREGROUND =====================

	private void startForegroundService() {
		Intent i = new Intent(this, ChildSignedInActivity.class);
		PendingIntent pi = PendingIntent.getActivity(
				this, 0, i, PendingIntent.FLAG_IMMUTABLE);

		Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_kidsafe)
				.setContentIntent(pi)
				.build();

		startForeground(NOTIFICATION_ID, n);
	}

	// ===================== FIREBASE =====================

	private void setupFirebaseListeners() {

		// 🔹 Apps (real-time + preserves blocked state)
		appsListener = databaseReference.child("childs")
				.orderByChild("email")
				.equalTo(childEmail)
				.addValueEventListener(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot s) {
						if (!s.exists()) return;

						Child child = s.getChildren().iterator().next()
								.getValue(Child.class);

						if (child == null || child.getApps() == null) return;

						apps = child.getApps();
						appsLoadedFromFirebase = true;

						mergeInstalledAppsWithFirebase();
					}

					@Override public void onCancelled(@NonNull DatabaseError e) {}
				});

		// 🔹 Screen Lock
		screenLockListener = databaseReference.child("childs")
				.child(uid)
				.child("screenLock")
				.addValueEventListener(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot s) {
						if (!s.exists()) return;

						ScreenLock lock = s.getValue(ScreenLock.class);
						if (lock == null) return;

						if (lock.isLocked()) {
							if (screenTimeReceiver == null) {
								screenTimeReceiver = new ScreenTimeReceiver(lock);
								IntentFilter f = new IntentFilter();
								f.addAction(Intent.ACTION_SCREEN_ON);
								f.addAction(Intent.ACTION_SCREEN_OFF);
								registerReceiver(screenTimeReceiver, f);
							}
						} else {
							unregisterSafe(screenTimeReceiver);
							screenTimeReceiver = null;
						}
					}

					@Override public void onCancelled(@NonNull DatabaseError e) {}
				});

		// 🔹 Geofence
		geofenceListener = databaseReference.child("childs")
				.child(uid)
				.child("location")
				.addValueEventListener(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot s) {
						geofenceConfig = s.getValue(
								com.mustafafyp.guardianai.models.Location.class);
						setupGeofenceListener();
					}

					@Override public void onCancelled(@NonNull DatabaseError e) {}
				});
	}

	private void removeFirebaseListeners() {
		if (appsListener != null)
			databaseReference.removeEventListener(appsListener);
		if (screenLockListener != null)
			databaseReference.removeEventListener(screenLockListener);
		if (geofenceListener != null)
			databaseReference.removeEventListener(geofenceListener);
	}

	// ===================== CRITICAL FIX =====================

	private void mergeInstalledAppsWithFirebase() {
		PackageManager pm = getPackageManager();
		List<ApplicationInfo> installed = pm.getInstalledApplications(0);

		HashMap<String, App> firebaseMap = new HashMap<>();
		for (App app : apps) {
			firebaseMap.put(app.getPackageName(), app);
		}

		boolean changed = false;

		for (ApplicationInfo info : installed) {
			if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

			if (!firebaseMap.containsKey(info.packageName)) {
				apps.add(new App(
						info.loadLabel(pm).toString(),
						info.packageName,
						false
				));
				changed = true;
			}
		}

		if (changed) {
			uploadApps(apps);
		}
	}

	private void uploadApps(ArrayList<App> appsList) {
		if (appsList == null || uid == null) return;

		databaseReference
				.child("childs")
				.child(uid)
				.child("apps")
				.setValue(appsList)
				.addOnSuccessListener(aVoid ->
						Log.i(TAG, "uploadApps: apps synced successfully"))
				.addOnFailureListener(e ->
						Log.e(TAG, "uploadApps: failed to sync apps", e));
	}


	// ===================== APP BLOCKING =====================

	private void checkBlockedApps() {
		if (!appsLoadedFromFirebase || apps == null || apps.isEmpty()) return;

		String foreground = getTopAppPackageName();
		if (foreground == null) return;

		for (App app : apps) {
			if (foreground.equals(app.getPackageName()) && app.isBlocked()) {
				Log.i(TAG, "BLOCKED_APP: Detected blocked app: " + app.getAppName());
				Intent i = new Intent(this, BlockedAppActivity.class);
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i.putExtra(BLOCKED_APP_NAME_EXTRA, app.getAppName());
				startActivity(i);

				logAlert("Blocked App Accessed",
						"Child tried to open " + app.getAppName(), uid);
				return;
			}
		}
	}

	// ===================== LOCATION =====================

	private void setupLocationTracking() {

		locationListener = new LocationListener() {
			@Override
			public void onLocationChanged(@NonNull Location location) {
				if (lastUploadedLocation != null &&
						location.distanceTo(lastUploadedLocation) < LOCATION_DISPLACEMENT)
					return;

				lastUploadedLocation = location;
				addUserLocationToDatabase(location, uid);
			}

			@Override public void onStatusChanged(String p, int s, Bundle b) {}
			@Override public void onProviderEnabled(@NonNull String p) {}
			@Override public void onProviderDisabled(@NonNull String p) {}
		};

		if (ActivityCompat.checkSelfPermission(
				this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {

			locationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER,
					LOCATION_INTERVAL,
					LOCATION_DISPLACEMENT,
					locationListener);
		}
	}

	private void setupGeofenceListener() {
		if (geofenceConfig == null || !geofenceConfig.isGeoFence()) return;

		if (geofenceLocationListener != null)
			locationManager.removeUpdates(geofenceLocationListener);

		geofenceLocationListener = new LocationListener() {
			@Override
			public void onLocationChanged(@NonNull Location l) {
				float[] d = new float[1];
				Location.distanceBetween(
						geofenceConfig.getFenceCenterLatitude(),
						geofenceConfig.getFenceCenterLongitude(),
						l.getLatitude(),
						l.getLongitude(),
						d);

				boolean out = d[0] > geofenceConfig.getFenceDiameter();
				databaseReference.child("childs").child(uid)
						.child("location").child("outOfFence").setValue(out);

				if (out && !wasOutOfFence) {
					wasOutOfFence = true;
					logAlert("Geofence Violation", "Child left safe zone", uid);
				}

				if (!out) wasOutOfFence = false;
			}

			@Override public void onStatusChanged(String p, int s, Bundle b) {}
			@Override public void onProviderEnabled(@NonNull String p) {}
			@Override public void onProviderDisabled(@NonNull String p) {}
		};

		if (ActivityCompat.checkSelfPermission(
				this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {

			locationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER,
					LOCATION_INTERVAL,
					LOCATION_DISPLACEMENT,
					geofenceLocationListener);
		}
	}

	// ===================== USAGE STATS =====================

	private void aggregateUsageStats() {
		try {
			if (apps == null) return;

			UsageStatsManager usm =
					(UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
			if (usm == null) return;

			long end = System.currentTimeMillis();
			long start = end - (24 * 60 * 60 * 1000);

			List<UsageStats> stats =
					usm.queryUsageStats(
							UsageStatsManager.INTERVAL_DAILY, start, end);

			if (stats == null || stats.isEmpty()) return;

			long total = 0;
			boolean changed = false;

			for (UsageStats s : stats) {
				total += s.getTotalTimeInForeground();
				for (App app : apps) {
					if (app.getPackageName().equals(s.getPackageName())) {
						if (app.getUsageDuration() != s.getTotalTimeInForeground()) {
							app.setUsageDuration(s.getTotalTimeInForeground());
							app.setLastTimeUsed(s.getLastTimeUsed());
							changed = true;
						}
					}
				}
			}

			String date = new java.text.SimpleDateFormat(
					"yyyy-MM-dd", Locale.getDefault()).format(new Date());

			databaseReference.child("childs").child(uid)
					.child("totalScreenTime").setValue(total);

			databaseReference.child("childs").child(uid)
					.child("dailyUsage").child(date).setValue(total);

			if (changed) uploadApps(apps);

			throttleBatterySync();

		} catch (Exception e) {
			Log.e(TAG, "aggregateUsageStats", e);
		}
	}

	// ===================== HELPERS =====================

	private void throttleBatterySync() {
		if (System.currentTimeMillis() - lastBatterySync > 10 * 60 * 1000) {
			updateBatteryAndDeviceInfo(uid);
			lastBatterySync = System.currentTimeMillis();
		}
	}

	private void unregisterSafe(BroadcastReceiver r) {
		try { if (r != null) unregisterReceiver(r); } catch (Exception ignored) {}
	}

	// ===================== EXISTING UTILS =====================

	public String getTopAppPackageName() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			return getLollipopForegroundAppPackageName();

		ActivityManager am =
				(ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		return am.getRunningAppProcesses().get(0).processName;
	}

	private String getLollipopForegroundAppPackageName() {
		UsageStatsManager usm =
				(UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
		if (usm == null) return null;

		long now = System.currentTimeMillis();
		List<UsageStats> list =
				usm.queryUsageStats(
						UsageStatsManager.INTERVAL_DAILY,
						now - 5000,
						now);

		if (list == null) return null;

		UsageStats recent = null;
		for (UsageStats s : list) {
			if (recent == null || s.getLastTimeUsed() > recent.getLastTimeUsed())
				recent = s;
		}
		return recent != null ? recent.getPackageName() : null;
	}

	private ArrayList<Contact> getContacts() {
		ArrayList<Contact> contacts = new ArrayList<>();
		ContentResolver cr = getContentResolver();
		Cursor c = cr.query(
				ContactsContract.Contacts.CONTENT_URI,
				null, null, null, null);

		if (c != null) {
			while (c.moveToNext()) {
				String id = c.getString(
						c.getColumnIndex(ContactsContract.Contacts._ID));

				if (c.getInt(
						c.getColumnIndex(
								ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {

					Cursor p = cr.query(
							ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
							null,
							ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
							new String[]{id},
							null);

					if (p != null) {
						while (p.moveToNext()) {
							contacts.add(new Contact(
									c.getString(
											c.getColumnIndex(
													ContactsContract.Contacts.DISPLAY_NAME)),
									p.getString(
											p.getColumnIndex(
													ContactsContract.CommonDataKinds.Phone.NUMBER))
							));
						}
						p.close();
					}
				}
			}
			c.close();
		}
		return contacts;
	}

	private void uploadContacts(ArrayList<Contact> contacts) {
		databaseReference.child("childs").child(uid)
				.child("contacts").setValue(contacts);
	}

	private void addUserLocationToDatabase(Location l, String uid) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("latitude", l.getLatitude());
		map.put("longitude", l.getLongitude());

		databaseReference.child("childs").child(uid)
				.child("location").updateChildren(map);

		databaseReference.child("childs").child(uid)
				.child("locationHistory")
				.push()
				.setValue(new LocationPoint(
						l.getLatitude(),
						l.getLongitude(),
						System.currentTimeMillis()));
	}

	private void updateBatteryAndDeviceInfo(String uid) {
		BatteryManager bm =
				(BatteryManager) getSystemService(BATTERY_SERVICE);
		int level = bm.getIntProperty(
				BatteryManager.BATTERY_PROPERTY_CAPACITY);

		HashMap<String, Object> map = new HashMap<>();
		map.put("batteryLevel", level);
		map.put("deviceModel", Build.MODEL);

		databaseReference.child("childs")
				.child(uid)
				.updateChildren(map);
	}

	private void logAlert(String title, String msg, String uid) {
		Log.i(TAG, "logAlert: Writing alert - Title: " + title + ", UID: " + uid);
		databaseReference.child("childs").child(uid)
				.child("alerts")
				.push()
				.setValue(new Alert(title, msg, System.currentTimeMillis()))
				.addOnSuccessListener(v -> Log.i(TAG, "logAlert: SUCCESS - Alert written"))
				.addOnFailureListener(e -> Log.e(TAG, "logAlert: FAILED", e));
	}

	private void registerReceivers() {
		FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();

		phoneStateReceiver = new PhoneStateReceiver(u);
		registerReceiver(phoneStateReceiver,
				new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));

		smsReceiver = new SmsReceiver(u);
		registerReceiver(smsReceiver,
				new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));

		appInstalledReceiver = new AppInstalledReceiver(u);
		IntentFilter add = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		add.addDataScheme("package");
		registerReceiver(appInstalledReceiver, add);

		appRemovedReceiver = new AppRemovedReceiver(u);
		IntentFilter remove = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
		remove.addDataScheme("package");
		registerReceiver(appRemovedReceiver, remove);
	}
}
