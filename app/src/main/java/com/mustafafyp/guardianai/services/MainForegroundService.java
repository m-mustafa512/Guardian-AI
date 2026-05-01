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
import com.mustafafyp.guardianai.ai.BehaviorAnomalyDetector;
import com.mustafafyp.guardianai.ai.FeatureNormConstants;

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

	private BehaviorAnomalyDetector anomalyDetector;

	// ===================== LIFECYCLE =====================

	@Override
	public void onCreate() {
		super.onCreate();
		scheduler = Executors.newScheduledThreadPool(2);
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		anomalyDetector = new BehaviorAnomalyDetector(this);
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

		// Backfill historical screen time for past 7 days
		backfillHistoricalUsage();

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

		if (anomalyDetector != null) anomalyDetector.close();
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

		// Build map of existing apps from Firebase (to preserve blocked status)
		HashMap<String, App> firebaseMap = new HashMap<>();
		for (App app : apps) {
			firebaseMap.put(app.getPackageName(), app);
		}

		// Check if we've done a full sync with launchable system apps before
		android.content.SharedPreferences prefs = getSharedPreferences("guardian_ai_prefs", MODE_PRIVATE);
		boolean hasCompletedFullSync = prefs.getBoolean("apps_full_sync_v2", false);

		// Force full re-sync if:
		// 1. Firebase has no apps or very few apps (first install)
		// 2. We haven't done a full sync with the new logic (includes launchable system apps)
		boolean forceFullSync = apps.isEmpty() || apps.size() < 5 || !hasCompletedFullSync;

		ArrayList<App> newAppsList = new ArrayList<>();
		boolean changed = false;

		for (ApplicationInfo info : installed) {
			// Check if it's a user-installed app OR a launchable system app
			boolean isUserApp = (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
			Intent launchIntent = pm.getLaunchIntentForPackage(info.packageName);
			boolean isLaunchable = launchIntent != null;

			if (isUserApp || isLaunchable) {
				if (forceFullSync) {
					// Full sync: add all launchable apps, preserve blocked status if exists
					App existingApp = firebaseMap.get(info.packageName);
					if (existingApp != null) {
						newAppsList.add(existingApp); // Preserve existing data (blocked, usage, etc)
					} else {
						newAppsList.add(new App(
								info.loadLabel(pm).toString(),
								info.packageName,
								false
						));
					}
					changed = true;
				} else {
					// Normal merge: only add NEW apps
					if (!firebaseMap.containsKey(info.packageName)) {
						apps.add(new App(
								info.loadLabel(pm).toString(),
								info.packageName,
								false
						));
						changed = true;
					}
				}
			}
		}

		if (forceFullSync && !newAppsList.isEmpty()) {
			apps = newAppsList;
			uploadApps(apps);
			// Mark that we've completed the full sync
			prefs.edit().putBoolean("apps_full_sync_v2", true).apply();
			Log.i(TAG, "mergeInstalledAppsWithFirebase: Full sync completed, " + apps.size() + " apps uploaded (including system apps)");
		} else if (changed) {
			uploadApps(apps);
			Log.i(TAG, "mergeInstalledAppsWithFirebase: Merged new apps, total: " + apps.size());
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

			long total       = 0;
			boolean changed  = false;

			// ── AI feature tracking ──────────────────────────────────
			float aiLaunches     = 0f;
			float aiInteractions = 0f;
			Map<String, Long> categoryUsageMs = new HashMap<>();
			categoryUsageMs.put("Entertainment", 0L);
			categoryUsageMs.put("Productivity",  0L);
			categoryUsageMs.put("Social",        0L);
			categoryUsageMs.put("Utilities",     0L);
			// ─────────────────────────────────────────────────────────

			for (UsageStats s : stats) {
				total += s.getTotalTimeInForeground();

				// AI: only count apps that were actually used
				if (s.getTotalTimeInForeground() > 0) {
					aiInteractions++;


					// Accumulate usage time per category
					String cat = getCategoryForPackage(s.getPackageName());
					long prev = categoryUsageMs.containsKey(cat) ? categoryUsageMs.get(cat) : 0L;
					categoryUsageMs.put(cat, prev + s.getTotalTimeInForeground());
				}

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

			// Approximate launches as number of distinct apps used (interactions)
			aiLaunches = aiInteractions;

			String date = new java.text.SimpleDateFormat(
					"yyyy-MM-dd", Locale.getDefault()).format(new Date());

			databaseReference.child("childs").child(uid)
					.child("totalScreenTime").setValue(total);

			databaseReference.child("childs").child(uid)
					.child("dailyUsage").child(date).setValue(total);

			if (changed) uploadApps(apps);

			throttleBatterySync();

			// ── AI Anomaly Detection ──────────────────────────────────
			float screenTimeMin = total / 60000f;

			// Find dominant category
			String dominantCat = "Utilities";
			long   maxUsage    = -1L;
			for (Map.Entry<String, Long> entry : categoryUsageMs.entrySet()) {
				if (entry.getValue() > maxUsage) {
					maxUsage    = entry.getValue();
					dominantCat = entry.getKey();
				}
			}

			float isProductive    = dominantCat.equals("Productivity")  ? 1f : 0f;
			float catEntertainment = dominantCat.equals("Entertainment") ? 1f : 0f;
			float catProductivity  = dominantCat.equals("Productivity")  ? 1f : 0f;
			float catSocial        = dominantCat.equals("Social")        ? 1f : 0f;
			float catUtilities     = dominantCat.equals("Utilities")     ? 1f : 0f;

			// extra_col_11 to extra_col_23: unknown dataset columns — Option A: pass 0f
			float[] extraCol11to23 = new float[13];

			checkForAnomalousBehavior(
					screenTimeMin,
					aiLaunches,
					aiInteractions,
					isProductive,
					0f,              // youtubeViews    — not trackable on-device
					0f,              // youtubeLikes    — not trackable on-device
					0f,              // youtubeComments — not trackable on-device
					extraCol11to23,
					catEntertainment,
					catProductivity,
					catSocial,
					catUtilities
			);
			// ─────────────────────────────────────────────────────────

		} catch (Exception e) {
			Log.e(TAG, "aggregateUsageStats", e);
		}
	}

	private void backfillHistoricalUsage() {
		try {
			UsageStatsManager usm =
					(UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
			if (usm == null) return;

			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
					"yyyy-MM-dd", Locale.getDefault());

			// Query for each of the past 7 days
			for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);

				long dayStart = cal.getTimeInMillis();
				long dayEnd = dayStart + (24 * 60 * 60 * 1000) - 1;

				// Don't query future times
				if (dayStart > System.currentTimeMillis()) continue;
				if (dayEnd > System.currentTimeMillis()) dayEnd = System.currentTimeMillis();

				List<UsageStats> stats = usm.queryUsageStats(
						UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd);

				if (stats == null || stats.isEmpty()) continue;

				long total = 0;
				for (UsageStats s : stats) {
					total += s.getTotalTimeInForeground();
				}

				String dateKey = sdf.format(cal.getTime());
				databaseReference.child("childs").child(uid)
						.child("dailyUsage").child(dateKey).setValue(total);
			}

			Log.i(TAG, "backfillHistoricalUsage: 7-day history synced");

		} catch (Exception e) {
			Log.e(TAG, "backfillHistoricalUsage", e);
		}
	}

	// ===================== HELPERS =====================

	/**
	 * Maps a package name to one of the four model categories:
	 * Entertainment, Social, Productivity, Utilities (default).
	 */
	private String getCategoryForPackage(String packageName) {
		if (packageName == null) return "Utilities";
		String pkg = packageName.toLowerCase(Locale.US);

		// Entertainment
		if (pkg.contains("youtube") || pkg.contains("netflix") ||
				pkg.contains("spotify") || pkg.contains("tiktok") ||
				pkg.contains("twitch")  || pkg.contains("disney") ||
				pkg.contains("hulu")    || pkg.contains("prime")  ||
				pkg.contains("game")    || pkg.contains("gaming")) {
			return "Entertainment";
		}

		// Social
		if (pkg.contains("instagram")  || pkg.contains("facebook") ||
				pkg.contains("twitter")    || pkg.contains("snapchat") ||
				pkg.contains("whatsapp")   || pkg.contains("telegram") ||
				pkg.contains("tinder")     || pkg.contains("linkedin") ||
				pkg.contains("reddit")     || pkg.contains("discord")  ||
				pkg.contains("messenger")) {
			return "Social";
		}

		// Productivity
		if (pkg.contains("gmail")     || pkg.contains(".docs")    ||
				pkg.contains(".sheets")  || pkg.contains(".slides")  ||
				pkg.contains(".drive")   || pkg.contains("calendar") ||
				pkg.contains("office")   || pkg.contains("outlook")  ||
				pkg.contains("notion")   || pkg.contains("slack")    ||
				pkg.contains("teams")    || pkg.contains("zoom")     ||
				pkg.contains("classroom")|| pkg.contains("evernote")) {
			return "Productivity";
		}

		return "Utilities";
	}

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

	// ===================== AI ANOMALY DETECTION =====================

	/**
	 * Checks whether today's collected usage data constitutes anomalous behaviour.
	 *
	 * Feature ORDER matches guardian_features.txt exactly (27 features):
	 *  [0]  screen_time_min        [7–19] extra_col_11–23
	 *  [1]  launches               [20]   DayOfWeek
	 *  [2]  interactions           [21]   DayOfMonth
	 *  [3]  is_productive          [22]   IsWeekend
	 *  [4]  youtube_views          [23]   Cat_Entertainment
	 *  [5]  youtube_likes          [24]   Cat_Productivity
	 *  [6]  youtube_comments       [25]   Cat_Social
	 *                              [26]   Cat_Utilities
	 *
	 * @param screenTimeMin    Total screen time in minutes
	 * @param launches         Number of app launches
	 * @param interactions     Number of user interactions
	 * @param isProductive     1f if session was productive, 0f otherwise
	 * @param youtubeViews     YouTube view count (0f if not applicable)
	 * @param youtubeLikes     YouTube like count (0f if not applicable)
	 * @param youtubeComments  YouTube comment count (0f if not applicable)
	 * @param extraCol11to23   float[13] — raw values for extra_col_11 through extra_col_23
	 * @param catEntertainment 1f if dominant category is Entertainment, else 0f
	 * @param catProductivity  1f if dominant category is Productivity, else 0f
	 * @param catSocial        1f if dominant category is Social, else 0f
	 * @param catUtilities     1f if dominant category is Utilities, else 0f
	 */
	private void checkForAnomalousBehavior(
			float   screenTimeMin,
			float   launches,
			float   interactions,
			float   isProductive,
			float   youtubeViews,
			float   youtubeLikes,
			float   youtubeComments,
			float[] extraCol11to23,    // must be length 13
			float   catEntertainment,
			float   catProductivity,
			float   catSocial,
			float   catUtilities
	) {
		if (anomalyDetector == null) {
			Log.e(TAG, "checkForAnomalousBehavior: anomalyDetector not initialised.");
			return;
		}
		if (extraCol11to23 == null || extraCol11to23.length != 13) {
			Log.e(TAG, "checkForAnomalousBehavior: extraCol11to23 must be float[13]. Aborting.");
			return;
		}

		Calendar cal = Calendar.getInstance();
		int dayOfWeek  = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0=Monday … 6=Sunday
		int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);    // 1–31
		int isWeekend  = (dayOfWeek >= 5) ? 1 : 0;          // Sat=5, Sun=6

		// ── Build the 27-element normalised feature vector ───────────
		float[] features = new float[BehaviorAnomalyDetector.INPUT_DIM]; // 27

		// [0] screen_time_min
		features[0]  = BehaviorAnomalyDetector.normalize(screenTimeMin,
						FeatureNormConstants.SCREEN_TIME_MIN_MIN,
						FeatureNormConstants.SCREEN_TIME_MIN_MAX);

		// [1] launches
		features[1]  = BehaviorAnomalyDetector.normalize(launches,
						FeatureNormConstants.LAUNCHES_MIN,
						FeatureNormConstants.LAUNCHES_MAX);

		// [2] interactions
		features[2]  = BehaviorAnomalyDetector.normalize(interactions,
						FeatureNormConstants.INTERACTIONS_MIN,
						FeatureNormConstants.INTERACTIONS_MAX);

		// [3] is_productive (already 0 or 1)
		features[3]  = BehaviorAnomalyDetector.normalize(isProductive,
						FeatureNormConstants.IS_PRODUCTIVE_MIN,
						FeatureNormConstants.IS_PRODUCTIVE_MAX);

		// [4] youtube_views
		features[4]  = BehaviorAnomalyDetector.normalize(youtubeViews,
						FeatureNormConstants.YOUTUBE_VIEWS_MIN,
						FeatureNormConstants.YOUTUBE_VIEWS_MAX);

		// [5] youtube_likes
		features[5]  = BehaviorAnomalyDetector.normalize(youtubeLikes,
						FeatureNormConstants.YOUTUBE_LIKES_MIN,
						FeatureNormConstants.YOUTUBE_LIKES_MAX);

		// [6] youtube_comments
		features[6]  = BehaviorAnomalyDetector.normalize(youtubeComments,
						FeatureNormConstants.YOUTUBE_COMMENTS_MIN,
						FeatureNormConstants.YOUTUBE_COMMENTS_MAX);

		// [7–19] extra_col_11 through extra_col_23
		features[7]  = BehaviorAnomalyDetector.normalize(extraCol11to23[0],
						FeatureNormConstants.EXTRA_COL_11_MIN, FeatureNormConstants.EXTRA_COL_11_MAX);
		features[8]  = BehaviorAnomalyDetector.normalize(extraCol11to23[1],
						FeatureNormConstants.EXTRA_COL_12_MIN, FeatureNormConstants.EXTRA_COL_12_MAX);
		features[9]  = BehaviorAnomalyDetector.normalize(extraCol11to23[2],
						FeatureNormConstants.EXTRA_COL_13_MIN, FeatureNormConstants.EXTRA_COL_13_MAX);
		features[10] = BehaviorAnomalyDetector.normalize(extraCol11to23[3],
						FeatureNormConstants.EXTRA_COL_14_MIN, FeatureNormConstants.EXTRA_COL_14_MAX);
		features[11] = BehaviorAnomalyDetector.normalize(extraCol11to23[4],
						FeatureNormConstants.EXTRA_COL_15_MIN, FeatureNormConstants.EXTRA_COL_15_MAX);
		features[12] = BehaviorAnomalyDetector.normalize(extraCol11to23[5],
						FeatureNormConstants.EXTRA_COL_16_MIN, FeatureNormConstants.EXTRA_COL_16_MAX);
		features[13] = BehaviorAnomalyDetector.normalize(extraCol11to23[6],
						FeatureNormConstants.EXTRA_COL_17_MIN, FeatureNormConstants.EXTRA_COL_17_MAX);
		features[14] = BehaviorAnomalyDetector.normalize(extraCol11to23[7],
						FeatureNormConstants.EXTRA_COL_18_MIN, FeatureNormConstants.EXTRA_COL_18_MAX);
		features[15] = BehaviorAnomalyDetector.normalize(extraCol11to23[8],
						FeatureNormConstants.EXTRA_COL_19_MIN, FeatureNormConstants.EXTRA_COL_19_MAX);
		features[16] = BehaviorAnomalyDetector.normalize(extraCol11to23[9],
						FeatureNormConstants.EXTRA_COL_20_MIN, FeatureNormConstants.EXTRA_COL_20_MAX);
		features[17] = BehaviorAnomalyDetector.normalize(extraCol11to23[10],
						FeatureNormConstants.EXTRA_COL_21_MIN, FeatureNormConstants.EXTRA_COL_21_MAX);
		features[18] = BehaviorAnomalyDetector.normalize(extraCol11to23[11],
						FeatureNormConstants.EXTRA_COL_22_MIN, FeatureNormConstants.EXTRA_COL_22_MAX);
		features[19] = BehaviorAnomalyDetector.normalize(extraCol11to23[12],
						FeatureNormConstants.EXTRA_COL_23_MIN, FeatureNormConstants.EXTRA_COL_23_MAX);

		// [20] DayOfWeek
		features[20] = BehaviorAnomalyDetector.normalize(dayOfWeek,
						FeatureNormConstants.DAYOFWEEK_MIN,
						FeatureNormConstants.DAYOFWEEK_MAX);

		// [21] DayOfMonth
		features[21] = BehaviorAnomalyDetector.normalize(dayOfMonth,
						FeatureNormConstants.DAYOFMONTH_MIN,
						FeatureNormConstants.DAYOFMONTH_MAX);

		// [22] IsWeekend (already 0 or 1)
		features[22] = isWeekend;

		// [23–26] Category one-hots (already 0 or 1 — no normalisation needed)
		features[23] = catEntertainment;
		features[24] = catProductivity;
		features[25] = catSocial;
		features[26] = catUtilities;

		// ── Run AI Inference ────────────────────────────────────────
		float   score   = anomalyDetector.getAnomalyScore(features);
		boolean anomaly = anomalyDetector.isAnomaly(features);

		if (anomaly) {
			Log.w(TAG, "🚨 ANOMALY DETECTED! Score: " + score);
			logAlert("Unusual Behaviour Detected",
					"AI flagged abnormal usage pattern. Score: " + String.format(Locale.US, "%.4f", score),
					uid);
		} else {
			Log.d(TAG, "✅ Normal behaviour. Score: " + score);
		}

		// ── Write AI status to Firebase on every check (for parent UI) ──────
		HashMap<String, Object> aiStatus = new HashMap<>();
		aiStatus.put("score",       (double) score);
		aiStatus.put("isAnomaly",   anomaly);
		aiStatus.put("status",      anomaly ? "Anomalous" : "Normal");
		aiStatus.put("lastChecked", System.currentTimeMillis());

		databaseReference.child("childs").child(uid)
				.child("aiStatus")
				.setValue(aiStatus)
				.addOnSuccessListener(v -> Log.d(TAG, "aiStatus updated in Firebase."))
				.addOnFailureListener(e -> Log.e(TAG, "Failed to update aiStatus.", e));
		// ────────────────────────────────────────────────────────────────────
	}
}
