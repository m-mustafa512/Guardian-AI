package com.mustafafyp.guardianai.services;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
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
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.mustafafyp.guardianai.models.AppModel;
import com.mustafafyp.guardianai.services.GuardianVpnService;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.activities.BlockedAppActivity;
import com.mustafafyp.guardianai.activities.ChildSignedInActivity;
import com.mustafafyp.guardianai.broadcasts.AppInstalledReceiver;
import com.mustafafyp.guardianai.broadcasts.AppRemovedReceiver;
import com.mustafafyp.guardianai.broadcasts.PhoneStateReceiver;
import com.mustafafyp.guardianai.broadcasts.ScreenTimeReceiver;
import com.mustafafyp.guardianai.broadcasts.SmsReceiver;
import com.mustafafyp.guardianai.models.App;
import com.mustafafyp.guardianai.models.Child;
import com.mustafafyp.guardianai.models.Contact;
import com.mustafafyp.guardianai.models.ScreenLock;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.pm.ApplicationInfo;
import android.os.Handler;
import java.util.concurrent.TimeUnit;

import static com.mustafafyp.guardianai.NotificationChannelCreator.CHANNEL_ID;

public class MainForegroundService extends Service {
	private ArrayList<AppModel> blockedApps = new ArrayList<>();
	private DatabaseReference appsRef;
	private Handler monitorHandler = new Handler();
	private Runnable monitorRunnable;
	private boolean isServiceRunning = false;
	public static final int NOTIFICATION_ID = 27;
	public static final String TAG = "MainServiceTAG";
	public static final String BLOCKED_APP_NAME_EXTRA = "com.mansourappdevelopment.androidapp.kidsafe.services.BLOCKED_APP_NAME_EXTRA";
	public static final int LOCATION_UPDATE_INTERVAL = 1;    //every 5 seconds
	public static final int LOCATION_UPDATE_DISPLACEMENT = 5;  //every 10 meters
	private ExecutorService executorService;
	private ArrayList<App> apps;
	private PhoneStateReceiver phoneStateReceiver;
	private SmsReceiver smsReceiver;
	private AppInstalledReceiver appInstalledReceiver;
	private AppRemovedReceiver appRemovedReceiver;
	private ScreenTimeReceiver screenTimeReceiver;
	private String uid;
	private String childEmail;
	private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
	private DatabaseReference databaseReference = firebaseDatabase.getReference("users");

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		// --- 1. HANDLE QUIZ UNLOCK (Must be first) ---
		if (intent != null && intent.getAction() != null) {
			if ("ACTION_UNLOCK_APP".equals(intent.getAction())) {
				String pkgToUnlock = intent.getStringExtra("PACKAGE_NAME");
				if (pkgToUnlock != null && blockedApps != null) {
					Log.i(TAG, "QUIZ SOLVED: Unlocking " + pkgToUnlock);
					// Find the app in our local list and unblock it temporarily
					for (AppModel app : blockedApps) {
						if (app.getPackageName().equals(pkgToUnlock)) {
							app.setBlocked(false); // Unblock locally so the service ignores it
						}
					}
				}
				// Return immediately so we don't restart the whole service logic
				return START_STICKY;
			}
		}

		// --- 2. SETUP FOREGROUND NOTIFICATION (CRASH FIX) ---
		// Create Intent for Notification Click
		Intent notificationIntent = new Intent(this, ChildSignedInActivity.class);

		// FIX FOR ANDROID 12+ CRASH (Must use FLAG_IMMUTABLE)
		int pendingFlags;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			pendingFlags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
		} else {
			pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingFlags);

		Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle("GuardianAI Active")
				.setContentText("Monitoring child device...")
				.setSmallIcon(R.drawable.ic_kidsafe) // Ensure this icon exists
				.setContentIntent(pendingIntent)
				.build();

		startForeground(NOTIFICATION_ID, notification);


		// --- 3. INITIALIZE FIREBASE & USER DATA ---
		FirebaseAuth auth = FirebaseAuth.getInstance();
		FirebaseUser user = auth.getCurrentUser();

		if (user != null) {
			childEmail = user.getEmail();
			uid = user.getUid();

			if (databaseReference == null) {
				databaseReference = FirebaseDatabase.getInstance().getReference("users");
			}

			// --- 4. FIREBASE LISTENERS (Real-time Sync) ---

			// A. BLOCKED APPS LISTENER (Syncs with Parent Switch)
			appsRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("installed_apps");
			appsRef.addValueEventListener(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot snapshot) {
					blockedApps.clear(); // Clear old list
					for (DataSnapshot appSnap : snapshot.getChildren()) {
						Boolean isBlocked = appSnap.child("blocked").getValue(Boolean.class);
						String packageName = appSnap.child("packageName").getValue(String.class);

						if (isBlocked != null && isBlocked && packageName != null) {
							blockedApps.add(new AppModel(packageName, true));
							Log.d("GuardianAI", "Blocked App Updated: " + packageName);
						}
					}
				}

				@Override
				public void onCancelled(@NonNull DatabaseError error) {}
			});

			// B. GEOFENCE LISTENER
			Query locationQuery = databaseReference.child("childs").child(uid).child("location");
			locationQuery.addValueEventListener(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
					if (dataSnapshot.exists()) {
						setFence(dataSnapshot);
					}
				}
				@Override
				public void onCancelled(@NonNull DatabaseError databaseError) {}
			});

			// C. WEB FILTER LISTENER
			Query webFilterQuery = databaseReference.child("childs").child(uid).child("web_filter");
			webFilterQuery.addValueEventListener(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
					if (dataSnapshot.exists()) {
						boolean blockAdult = Boolean.TRUE.equals(dataSnapshot.child("block_adult").getValue(Boolean.class));
						boolean blockGambling = Boolean.TRUE.equals(dataSnapshot.child("block_gambling").getValue(Boolean.class));

						if (blockAdult || blockGambling) {
							Intent vpnIntent = new Intent(MainForegroundService.this, GuardianVpnService.class);
							startService(vpnIntent);
						} else {
							Intent vpnIntent = new Intent(MainForegroundService.this, GuardianVpnService.class);
							stopService(vpnIntent);
						}
					}
				}
				@Override
				public void onCancelled(@NonNull DatabaseError databaseError) {}
			});

			// D. SCREEN LOCK LISTENER
			Query screenTimeQuery = databaseReference.child("childs").child(uid).child("screenLock");
			screenTimeQuery.addValueEventListener(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
					if (dataSnapshot.exists()) {
						ScreenLock screenLock = dataSnapshot.getValue(ScreenLock.class);
						if (screenLock != null && screenLock.isLocked()) {
							if (screenTimeReceiver == null) {
								screenTimeReceiver = new ScreenTimeReceiver(screenLock);
								IntentFilter filter = new IntentFilter();
								filter.addAction(Intent.ACTION_SCREEN_ON);
								filter.addAction(Intent.ACTION_SCREEN_OFF);
								registerReceiver(screenTimeReceiver, filter);
							}
						} else {
							if (screenTimeReceiver != null) {
								try { unregisterReceiver(screenTimeReceiver); } catch (Exception e) {}
								screenTimeReceiver = null;
							}
						}
					}
				}
				@Override
				public void onCancelled(@NonNull DatabaseError databaseError) {}
			});

			// --- 5. REGISTER LOCAL RECEIVERS (Calls, SMS, Installs) ---
			// (Wrapped in try-catch or checks to prevent double registration crashes)

			if (phoneStateReceiver == null) {
				phoneStateReceiver = new PhoneStateReceiver(user);
				registerReceiver(phoneStateReceiver, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
			}

			if (smsReceiver == null) {
				smsReceiver = new SmsReceiver(user);
				registerReceiver(smsReceiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
			}

			if (appInstalledReceiver == null) {
				appInstalledReceiver = new AppInstalledReceiver(user);
				IntentFilter filter = new IntentFilter();
				filter.addAction(Intent.ACTION_PACKAGE_ADDED);
				filter.addDataScheme("package");
				registerReceiver(appInstalledReceiver, filter);
			}
		}


		// --- 6. START PERIODIC TASKS (Safety Checks) ---
		if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
			getUserLocation();
		}

		if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
			// Run contacts upload on a background thread to avoid ANR
			new Thread(new Runnable() {
				@Override
				public void run() {
					ArrayList<Contact> contacts = getContacts();
					uploadContacts(contacts);
				}
			}).start();
		}


		// --- 7. START APP MONITORING LOOP ---
		if (!isServiceRunning) {
			uploadInstalledApps(); // Upload list once
			isServiceRunning = true;

			monitorRunnable = new Runnable() {
				@Override
				public void run() {
					String currentApp = getTopAppPackageName();

					// CHECK IF BLOCKED
					if (blockedApps != null && !blockedApps.isEmpty()) {
						for (AppModel app : blockedApps) {
							// If app matches AND is blocked
							if (app.getPackageName().equals(currentApp) && app.isBlocked()) {

								Log.d(TAG, "Violation detected! Locking " + currentApp);
								Intent lockIntent = new Intent(getApplicationContext(), com.mustafafyp.guardianai.activities.LockScreenActivity.class);
								lockIntent.putExtra("PACKAGE_NAME", currentApp);
								lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
								startActivity(lockIntent);
								break;
							}
						}
					}
					// Repeat every 1 second
					monitorHandler.postDelayed(this, 1000);
				}
			};
			monitorHandler.post(monitorRunnable);
		}

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (executorService != null) {
			executorService.shutdown();
		}
		if (phoneStateReceiver != null) {
			unregisterReceiver(phoneStateReceiver);
		}
		if (smsReceiver != null) {
			unregisterReceiver(smsReceiver);
		}
		if (appInstalledReceiver != null) {
			unregisterReceiver(appInstalledReceiver);
		}
		if (appRemovedReceiver != null) {
			unregisterReceiver(appRemovedReceiver);
		}
		if (screenTimeReceiver != null) {
			unregisterReceiver(screenTimeReceiver);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public void getApps() {
		Query query = databaseReference.child("childs").orderByChild("email").equalTo(childEmail);
		query.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				if (dataSnapshot.exists()) {
					//Log.i(TAG, "onDataChange: dataSnapshot value: "+dataSnapshot.getValue());
					//Log.i(TAG, "onDataChange: dataSnapshot as a string: "+dataSnapshot.toString());
					//Log.i(TAG, "onDataChange: dataSnapshot children: " + dataSnapshot.getChildren());
					//Log.i(TAG, "onDataChange: dataSnapshot key: " + dataSnapshot.getKey());

					DataSnapshot nodeShot = dataSnapshot.getChildren().iterator().next();
					Child child = nodeShot.getValue(Child.class);
					apps = child.getApps();

					Log.i(TAG, "onDataChange: child name: " + child.getName());
					//updateAppStats(apps);

				}
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}
		});
	}

	private void getUserLocation() {
		Log.i(TAG, "getUserLocation: executed");
		LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

		LocationListener locationListener = new LocationListener() {
			@Override
			public void onLocationChanged(Location location) {
				if (location != null) {
					Log.i(TAG, "onLocationChanged: latitude: " + location.getLatitude());
					Log.i(TAG, "onLocationChanged: longitude: " + location.getLongitude());
					addUserLocationToDatabase(location, uid);
				} else {
					Log.i(TAG, "onLocationChanged: location is null");
				}
			}

			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {

			}

			@Override
			public void onProviderEnabled(String provider) {

			}

			@Override
			public void onProviderDisabled(String provider) {

			}
		};

		//these two statements will be only executed when the permission is granted.
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISPLACEMENT, locationListener);
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISPLACEMENT, locationListener);
			return;
		}

	}

	private void addUserLocationToDatabase(Location location, String uid) {
		double latitude = location.getLatitude();
		double longitude = location.getLongitude();
		HashMap<String, Object> update = new HashMap<>();
		update.put("latitude", latitude);
		update.put("longitude", longitude);
		databaseReference.child("childs").child(uid).child("location").updateChildren(update);
	}

	private void uploadContacts(ArrayList<Contact> contacts) {
		databaseReference.child("childs").child(uid).child("contacts").setValue(contacts);

	}

	private void setFence(DataSnapshot dataSnapshot) {
		final com.mustafafyp.guardianai.models.Location childLocation = dataSnapshot.getValue(com.mustafafyp.guardianai.models.Location.class);
		Log.i(TAG, "setFence: getLatitude " + childLocation.getLatitude());
		Log.i(TAG, "setFence: getLongitude " + childLocation.getLongitude());
		Log.i(TAG, "setFence: isGeoFence " + childLocation.isGeoFence());
		Log.i(TAG, "setFence: isOutOfFence " + childLocation.isOutOfFence());
		Log.i(TAG, "setFence: getFenceCenterLatitude " + childLocation.getFenceCenterLatitude());
		Log.i(TAG, "setFence: getFenceCenterLongitude " + childLocation.getFenceCenterLongitude());
		Log.i(TAG, "setFence: getFenceDiameter " + childLocation.getFenceDiameter());

		if (childLocation.isGeoFence()) {
			Log.i(TAG, "setFence: true");
			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			LocationListener locationListener = new LocationListener() {
				@Override
				public void onLocationChanged(Location location) {
					Log.i(TAG, "setFence: changed");
					if (location != null) {
						float[] distanceInMeters = new float[1];
						Location.distanceBetween(childLocation.getFenceCenterLatitude(), childLocation.getFenceCenterLongitude(), location.getLatitude(), location.getLongitude(), distanceInMeters);

						boolean outOfFence = distanceInMeters[0] > childLocation.getFenceDiameter();
						if (outOfFence) {
							Log.i(TAG, "setFence: OUT OF FENCE");
							databaseReference.child("childs").child(uid).child("location").child("outOfFence").setValue(true);
						} else {
							databaseReference.child("childs").child(uid).child("location").child("outOfFence").setValue(false);
						}
					} else {
						Log.i(TAG, "setFence: location is null");
					}
				}

				@Override
				public void onStatusChanged(String provider, int status, Bundle extras) {

				}

				@Override
				public void onProviderEnabled(String provider) {

				}

				@Override
				public void onProviderDisabled(String provider) {

				}
			};

			//these two statements will be only executed when the permission is granted.
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISPLACEMENT, locationListener);
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISPLACEMENT, locationListener);
				return;
			}


		}

	}

    /*private void changeDNS(String primaryDNS, String secondaryDNS) {
        Settings.System.putString(getContentResolver(), Settings.System.WIFI_STATIC_DNS1, primaryDNS);  //TODO:: DEPRECATED
        Settings.System.putString(getContentResolver(), Settings.System.WIFI_STATIC_DNS2, secondaryDNS);
    }*/

	public ArrayList<Contact> getContacts() {
		ArrayList<Contact> contacts = new ArrayList<>();
		ContentResolver contentResolver = getApplicationContext().getContentResolver();
		Cursor cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
		if (cursor.getCount() > 0) {
			while (cursor.moveToNext()) {
				String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
				if (cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
					Cursor cursorInfo = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);

					while (cursorInfo.moveToNext()) {
						String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
						String contactNumber = cursorInfo.getString(cursorInfo.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
						Contact contact = new Contact(contactName, contactNumber);
						contacts.add(contact);
					}

					cursorInfo.close();
				}
			}
			cursor.close();
		}
		return contacts;
	}

	private void getInstalledApplications(/*ArrayList<App> onlineAppsList*/) {
		PackageManager packageManager = getPackageManager();
		List<ApplicationInfo> applicationInfoList = packageManager.getInstalledApplications(0);
		Collections.sort(applicationInfoList, new ApplicationInfo.DisplayNameComparator(packageManager));
		Iterator<ApplicationInfo> iterator = applicationInfoList.iterator();
		while (iterator.hasNext()) {
			ApplicationInfo applicationInfo = iterator.next();
			if (applicationInfo.packageName.contains("com.google") || applicationInfo.packageName.matches("com.android.chrome"))
				continue;
			if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
				iterator.remove();
			}
		}
		prepareData(applicationInfoList, packageManager/*, onlineAppsList*/);
	}

	private void prepareData(List<ApplicationInfo> applicationInfoList, PackageManager packageManager/*, ArrayList<App> onlineAppsList*/) {
		ArrayList<App> appsList = new ArrayList<>();
		for (ApplicationInfo applicationInfo : applicationInfoList) {
			if (applicationInfo.packageName != null) {
				appsList.add(new App((String) applicationInfo.loadLabel(packageManager), applicationInfo.packageName, false));
			}
		}
        /*if (onlineAppsList.isEmpty()) {
            Log.i(TAG, "prepareData: online appsList empty");
            for (ApplicationInfo applicationInfo : applicationInfoList) {
                if (applicationInfo.packageName != null) {
                    appsList.add(new App((String) applicationInfo.loadLabel(packageManager), (String) applicationInfo.packageName, false));
                }
            }
            //if not, check the app's blocked attribute and update it.
        } else {
            for (ApplicationInfo applicationInfo : applicationInfoList) {
                for (App app : onlineAppsList) {
                    if (app.getPackageName().equals((String) applicationInfo.packageName)) {
                        appsList.add(new App((String) applicationInfo.loadLabel(packageManager), (String) applicationInfo.packageName, app.isBlocked()));
                    }
                }

            }

        }*/

		uploadApps(appsList);

	}

	private void uploadApps(ArrayList<App> appsList) {
		databaseReference.child("childs").child(uid).child("apps").setValue(appsList);
		Log.i(TAG, "uploadApps: done");
	}



	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private String getLollipopForegroundAppPackageName() {
		//Log.i(TAG, "getLollipopForegroundAppPackageName: executed");
		try {
			UsageStatsManager usageStatsManager = (UsageStatsManager) this.getSystemService(USAGE_STATS_SERVICE);
			long milliSecs = 60 * 1000;
			Date date = new Date();
			List<UsageStats> foregroundApps = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, date.getTime() - milliSecs, date.getTime());
			if (foregroundApps.size() == 0) {
				Log.i(TAG, "getLollipopForegroundAppPackageName: queryUsageSize: empty");
			}


			long recentTime = 0;
			String recentPkg = "";
			for (UsageStats stats : foregroundApps) {
                /*if (i == 0 && !"com.mansourappdevelopment.androidapp.kidsafe".equals(stats.getPackageName())) {
                    Log.i(TAG, "PackageName: " + stats.getPackageName() + " " + stats.getLastTimeStamp());
                }*/
				if (stats.getLastTimeStamp() > recentTime) {
					recentTime = stats.getLastTimeStamp();
					recentPkg = stats.getPackageName();
				}

			}

			//Log.i(TAG, "getLollipopForegroundAppPackageName: appPackageName: " + recentPkg);
			return recentPkg;
		} catch (Exception e) {
			e.printStackTrace();
		}


		return "";
	}

	private String getKitkatForegroundAppPackageName() {
		ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningAppProcessInfo> tasks = activityManager.getRunningAppProcesses();
		return tasks.get(0).processName;
	}

	class LockerThread implements Runnable {

		private Intent intent = null;

		public LockerThread() {
			intent = new Intent(MainForegroundService.this, BlockedAppActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}

		@Override
		public void run() {
			while (true) {
				//Log.i(TAG, "run: thread running");

				if (apps != null) {

					String foregroundAppPackageName = getTopAppPackageName();
					Log.i(TAG, "run: foreground app: " + foregroundAppPackageName);

					//TODO:: need to handle com.google.android.gsf &  com.sec.android.provider.badge
					for (final App app : apps) {
						//Log.i(TAG, "run: app name: " + app.getAppName() + " blocked: " + app.isBlocked() + "\n");
						if (foregroundAppPackageName.equals(app.getPackageName()) && app.isBlocked()) {
							//Log.i(TAG, "run: " + app.getPackageName() + " is running");
							intent.putExtra(BLOCKED_APP_NAME_EXTRA, app.getAppName());
							startActivity(intent);
						} /*else if (foregroundAppPackageName.equals(app.getPackageName()) && !app.isBlocked()) {
                            if (app.getScreenLock() != null) {
                                if (app.getScreenLock().isLocked() && app.getScreenLock().getTimeInSeconds() > 0) {
                                    app.getScreenLock().setTimeInSeconds(app.getScreenLock().getTimeInSeconds() - 1);
                                    Log.i(TAG, "run: TimeInSeconds: " + app.getScreenLock().getTimeInSeconds());
                                } else if (app.getScreenLock().isLocked() && app.getScreenLock().getTimeInSeconds() <= 0) {
                                    app.setBlocked(true);
                                    Log.i(TAG, "run: blocked");
                                }
                            } else
                                Log.i(TAG, "run: ScreenLock is null");
                        }*/

					}
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}

	private void uploadDailySummary() {
		// 1. Initialize the UsageStatsManager
		UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
		long endTime = System.currentTimeMillis();

		// 2. Set the start time to the beginning of the current day
		java.util.Calendar calendar = java.util.Calendar.getInstance();
		calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
		calendar.set(java.util.Calendar.MINUTE, 0);
		calendar.set(java.util.Calendar.SECOND, 0);
		long startTime = calendar.getTimeInMillis();

		// 3. Query stats for today
		List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

		long totalMillis = 0;
		String topPkgName = "None";
		long maxTime = 0;

		if (stats != null) {
			for (UsageStats usageStats : stats) {
				totalMillis += usageStats.getTotalTimeInForeground();
				if (usageStats.getTotalTimeInForeground() > maxTime) {
					maxTime = usageStats.getTotalTimeInForeground();
					topPkgName = usageStats.getPackageName();
				}
			}
		}

		// 4. Resolve the Package Name to a Readable App Name
		String topAppName = topPkgName;
		PackageManager pm = getPackageManager();
		try {
			ApplicationInfo ai = pm.getApplicationInfo(topPkgName, 0);
			topAppName = (String) pm.getApplicationLabel(ai);
		} catch (PackageManager.NameNotFoundException e) {
			// Keeps the package name if the app label isn't found
		}

		// 5. Format the total time into "Xh Ym"
		long hours = TimeUnit.MILLISECONDS.toHours(totalMillis);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60;
		String timeFormatted = hours + "h " + minutes + "m";

		// 6. Push to Firebase under the child's node
		// Note: databaseReference is already initialized to "users" in this service
		HashMap<String, Object> summaryUpdate = new HashMap<>();
		summaryUpdate.put("dailyUsage", timeFormatted);
		summaryUpdate.put("topApp", topAppName);

		databaseReference.child("childs").child(uid).updateChildren(summaryUpdate);
	}

	// Keep this ONE copy at the bottom of your class
	private String getTopAppPackageName() {
		String topPackageName = "";
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			UsageStatsManager usage = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
			long time = System.currentTimeMillis();
			List<UsageStats> stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time);

			if (stats != null) {
				TreeMap<Long, UsageStats> sortedMap = new TreeMap<>();
				for (UsageStats usageStats : stats) {
					sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
				}
				if (!sortedMap.isEmpty()) {
					topPackageName = sortedMap.get(sortedMap.lastKey()).getPackageName();
				}
			}
		}
		return topPackageName;
	}
	// --- HELPER METHOD TO UPLOAD APPS ---
	private void uploadInstalledApps() {
		final PackageManager pm = getPackageManager();
		// Get a list of installed apps
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
		if (user == null) return;

		DatabaseReference appsRef = FirebaseDatabase.getInstance()
				.getReference("users")
				.child(user.getUid()) // Child ID
				.child("installed_apps");

		for (ApplicationInfo packageInfo : packages) {
			// Filter out system apps to keep the list clean (Optional)
			if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
				String appName = pm.getApplicationLabel(packageInfo).toString();
				String packageName = packageInfo.packageName;

				// Create a clean map to upload
				HashMap<String, Object> appData = new HashMap<>();
				appData.put("name", appName);
				appData.put("packageName", packageName);

				// Use packageName as the key (replace dots with underscores for Firebase keys)
				String safeKey = packageName.replace(".", "_");

				// We use updateChildren to ensure we don't accidentally overwrite "blocked" status
				// if it already exists in the database
				appsRef.child(safeKey).updateChildren(appData);
			}
		}
	}
}