package gr.uoa.di.monitoring.android.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.provider.Settings;

import gr.uoa.di.android.helpers.net.Net;
import gr.uoa.di.android.helpers.net.WifiWaker;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.activities.DialogActivity;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationReceiver;
import gr.uoa.di.monitoring.model.Position;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.ENABLE;
import static gr.uoa.di.monitoring.android.C.NOT_USED;
import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.ac_location_data;
import static gr.uoa.di.monitoring.android.C.launchSettingsIntent;
import static gr.uoa.di.monitoring.android.C.triggerDialogNotification;

public final class LocationMonitor extends Monitor<Location, Position> {

	private static final long LOCATION_MONITORING_INTERVAL = MonitoringInterval.ONE
		.getInterval();
	private static final Class<? extends BaseReceiver> PERSONAL_RECEIVER = LocationReceiver.class;
	// Location api calls constants
	private static final int MIN_TIME_BETWEEN_SCANS = 1 * 30 * 1000; // 30 secs
	private static final int MIN_DISTANCE = 0;
	public static final String NOTIFICATION_TAG = LocationMonitor.class
		.getSimpleName() + ".Notification";
	private static final int NOTIFICATION_ID = 9200;
	private static final String LOCATION_DATA_KEY = "LOCATION_DATA_KEY";
	private static final String SAME_LOCATION_COUNT_KEY = "SAME_LOCATION_COUNT_KEY";
	private static final String LOCATION_INTERVAL_KEY = "LOCATION_INTERVAL_KEY";
	private static final String LOCATION_DATA_DISPLAY_KEY = "LOCATION_DATA_DISPLAY_KEY";
	private static final String LOCATION_MANUAL_UPDATE_KEY = "LOCATION_MANUAL_UPDATE_KEY";
	private static final String LOCATION_UPDATE_IN_PROGRESS_KEY = "LOCATION_UPDATE_IN_PROGRESS_KEY";
	// convenience fields
	private static volatile LocationManager lm; // not final - we need a context
	// members
	private static PendingIntent pi; // see above
	private Providers.Status providerStatus;
	// wifi waker
	private volatile static CountDownLatch latch;
	private static final long LATCH_TIMEOUT = MonitoringInterval.TWO
		.getInterval();
	private static final String WIFI_LOCK_TAG = LocationMonitor.class.getName()
		+ ".WIFI_LOCK";

	public LocationMonitor() {
		super(LocationMonitor.class.getSimpleName());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		init();
		// be careful to return super
		return super.onStartCommand(intent, flags, startId);
	}

	private void init() {
		Intent i = new Intent(this, PERSONAL_RECEIVER);
		pi = PendingIntent.getBroadcast(this, NOT_USED, i,
			PendingIntent.FLAG_UPDATE_CURRENT);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		init();
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		final StringBuilder sb = debugHeader();
		final CharSequence action = intent.getAction();
		if (action == null) {
			// monitoring command from the alarm manager or manual update
			if (!proceed(intent)) return;
			// cancelNotification(this, NOTIFICATION_TAG, NOTIFICATION_ID);
			final String provider = Providers.getProvider(lm());
			providerStatus = Providers.getProviderStatus(this, provider);
			if (providerStatus.notAvailabe()) {
				sb.append(" (" + provider + ") ");
				// TODO Listeners providerStatus.waitForChange() instead of
				// abort
				w("Aborting : " + sb.toString());
				abort();
				// Providers.launchDialog(this, providerStatus);
				Providers.launchNotification(this, providerStatus);
				return;
			}
			// FIXME : maybe enable & DISABLE once ?
			d("Enabling the receiver");
			receiver(ENABLE);
			d("Requesting location updates - pi : " + pi);
			lm().requestLocationUpdates(provider, MIN_TIME_BETWEEN_SCANS,
				MIN_DISTANCE, pi);
			// TODO : LATCH WITH TIMEOUT HERE
		} else if (ac_location_data.equals(action)) {
			final Bundle extras = intent.getExtras();
			if (extras != null) {
				final Location loc = (Location) extras
					.get(LocationManager.KEY_LOCATION_CHANGED);
				if (loc == null) {
					w(sb + "NULL LOCATION  - EXTRAS : " + extras);
					if (!extras
						.getBoolean(LocationManager.KEY_PROVIDER_ENABLED)) {
						w(sb + "Provider disabled - disabling "
							+ "LocationReceiver & removing updates");
						receiver(DISABLE);
						lm().removeUpdates(pi);
						updateFinished();
					}
				} else {
					if (DEBUG) {
						final String provider = loc.getProvider();
						sb.append(" (" + provider + ") ");
						final double lon = loc.getLongitude();
						final double lat = loc.getLatitude();
						w(sb + "latitude :" + lat + " -- longitude : " + lon);
					}
					d("Got location - disabling LocationReceiver");
					receiver(DISABLE);
					d("Got location - removing updates");
					// FIXME : will it remove updates for any and all location
					// providers for the same pending intent ?
					// requestLocationUpdates above will register the same
					// updates for the same pending intent pi ?
					// maybe I'll screw up my next update request ?
					lm().removeUpdates(pi);
					// persist
					save(loc);
				}
			} else {
				w(sb + "NULL EXTRAS - disabling LocationReceiver & removing "
					+ "updates");
				receiver(DISABLE);
				lm().removeUpdates(pi);
				updateFinished();
			}
		} else if (ac_aborting.equals(action)) {
			cleanup();
		}
	}

	private void receiver(final boolean enable) {
		BaseReceiver.enable(this, enable, PERSONAL_RECEIVER);
	}

	@Override
	void cleanup() {
		receiver(DISABLE);
		lm().removeUpdates(pi); // TODO : maybe check if requested ?
		commonCleanup();
	}

	private LocationManager lm() {
		LocationManager result = lm;
		if (result == null) {
			synchronized (LocationMonitor.class) {
				result = lm;
				if (result == null)
					result = lm = (LocationManager) getSystemService(
						Context.LOCATION_SERVICE);
			}
		}
		return result;
	}

	/**
	 * Provides the providers - wrapper around the LocationManager provider
	 * facilities. Includes our Criteria and defines our policies
	 *
	 * @author MrD
	 */
	private final static class Providers {

		private Providers() {}

		private final static Criteria CRITERIA;
		static {
			CRITERIA = new Criteria();
			// we need as much accuracy as we can get
			CRITERIA.setAccuracy(Criteria.ACCURACY_FINE);
			// CRITERIA.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
			// we need as few power consumption as we can get
			CRITERIA.setPowerRequirement(Criteria.POWER_LOW);
			// we need no cost - notice this can't be relaxed
			CRITERIA.setCostAllowed(false);
			// unneeded
			CRITERIA.setAltitudeRequired(false);
			CRITERIA.setBearingRequired(false);
			CRITERIA.setSpeedRequired(false);
		}

		static String getProvider(final LocationManager m) {
			return m.getBestProvider(CRITERIA, ENABLE);
		}

		@SuppressWarnings("synthetic-access")
		static Status getProviderStatus(Context ctx, String s) {
			if (s == null) return Status.NULL;
			if (s.equals(LocationManager.GPS_PROVIDER)) return Status.GPS;
			if (s.equals(LocationManager.NETWORK_PROVIDER)) {
				// Check if wireless is enabled
				WifiManager wm = (WifiManager) ctx
					.getSystemService(Context.WIFI_SERVICE);
				if (wm == null || !wm.isWifiEnabled()) {
					return Status.NETWORK_NOT_ENABLED;
				}
				Boolean isWifi = false;
				WifiLock _wifiLock = null;
				try {
					_wifiLock = wm.createWifiLock(Net.modeHighPerformanse(),
						WIFI_LOCK_TAG);
					_wifiLock.acquire();
					latch = new CountDownLatch(1);
					WifiWaker ww = new WifiWaker(latch);
					isWifi = ww.wakeWifiUpIfEnabled(ctx, LATCH_TIMEOUT);
				} finally {
					if (_wifiLock != null) {
						_wifiLock.release();
					}
				}
				if (isWifi) {
					return Status.NETWORK;
				}
				return Status.NETWORK_NOT_CONNECTED;
			}
			return Status.UNKNOWN_PROVIDER;
		}

		static void launchNotification(Context ctx, Status stat) {
			Intent intent = DialogActivity.launchDialogActivityIntent(ctx,
				stat.title(ctx), stat.dialog(ctx), stat.launchIntent());
			// intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // nope
			triggerDialogNotification(ctx, NOTIFICATION_ID, stat.title(ctx),
				stat.notification(ctx), intent, NOTIFICATION_TAG,
				NOTIFICATION_ID);
		}

		@SuppressWarnings("unused")
		private static String allProviders(LocationManager m) {
			return Arrays.toString(m.getAllProviders().toArray());
		}

		private enum Status {
			// TODO : add a constant on gps status GPS_NO_FIX
			GPS, NETWORK, NETWORK_NOT_ENABLED, NETWORK_NOT_CONNECTED, NULL,
			UNKNOWN_PROVIDER;

			String title(Context ctx) {
				switch (this) {
				case NULL:
					return ctx.getString(R.string.title_null);
				case NETWORK_NOT_CONNECTED:
					return ctx.getString(R.string.title_network_not_connected);
				case GPS:
					return ctx.getString(R.string.title_gps);
				case NETWORK:
					return ctx.getString(R.string.title_network);
				case UNKNOWN_PROVIDER:
					return ctx.getString(R.string.title_unknown_provider);
				case NETWORK_NOT_ENABLED:
					return ctx.getString(R.string.title_network_not_enabled);
				}
				throw new AssertionError("Forgotten enum constant");
			}

			String notification(Context ctx) {
				switch (this) {
				case NULL:
					return ctx.getString(R.string.notification_null);
				case NETWORK_NOT_CONNECTED:
					return ctx
						.getString(R.string.notification_network_not_connected);
				case GPS:
					return ctx.getString(R.string.notification_gps);
				case NETWORK:
					return ctx.getString(R.string.notification_network);
				case UNKNOWN_PROVIDER:
					return ctx
						.getString(R.string.notification_unknown_provider);
				case NETWORK_NOT_ENABLED:
					return ctx
						.getString(R.string.notification_network_not_enabled);
				}
				throw new AssertionError("Forgotten enum constant");
			}

			String dialog(Context ctx) {
				return ctx.getString(dialog());
			}

			private int dialog() {
				switch (this) {
				case NULL:
					return R.string.dialog_null;
				case NETWORK_NOT_CONNECTED:
					return R.string.dialog_network_not_connected;
				case NETWORK_NOT_ENABLED:
					return R.string.dialog_network_not_enabled;
				case GPS:
					return R.string.dialog_gps;
				case NETWORK:
					return R.string.dialog_network;
				case UNKNOWN_PROVIDER:
					return R.string.dialog_unknown_provider;
				}
				throw new AssertionError("Forgotten enum constant");
			}

			Intent launchIntent() {
				switch (this) {
				case NULL:
				case UNKNOWN_PROVIDER:
					return launchSettingsIntent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				case NETWORK_NOT_CONNECTED:
				case NETWORK_NOT_ENABLED:
					return launchSettingsIntent(Settings.ACTION_WIFI_SETTINGS);
				case GPS:
				case NETWORK:
					return null;
				}
				throw new AssertionError("Forgotten enum constant");
			}

			boolean notAvailabe() {
				return !(this == GPS || this == NETWORK);
			}
		}
	}

	@Override
	public long getBaseInterval() {
		return LOCATION_MONITORING_INTERVAL;
	}

	@Override
	void saveResults(Location data) throws IOException {
		final Position currentPosition = Position.saveData(this, data);
		if (!getPref(getManualUpdatePrefKey(), false)) {
			// get the previous data from the preferences store
			String previousData = getPref(LOCATION_DATA_KEY, null);
			Position previousPosition = Position.fromString(previousData);
			// check to see if we need to modify the interval
			updateInterval(currentPosition, previousPosition);
			// store the new data
			putPref(LOCATION_DATA_KEY, currentPosition.stringForm());
		}
		putPref(dataKey(), currentPosition.toString());
	}

	public static String dataKey() {
		return LOCATION_DATA_DISPLAY_KEY;
	}

	public static String updateInProgressKey() {
		return LOCATION_UPDATE_IN_PROGRESS_KEY;
	}

	@Override
	String getLastResultsPrefKey() {
		return LOCATION_DATA_KEY;
	}

	@Override
	String getSameResultsCountPrefKey() {
		return SAME_LOCATION_COUNT_KEY;
	}

	@Override
	String getCurrentIntervalPrefKey() {
		return LOCATION_INTERVAL_KEY;
	}

	@Override
	String getManualUpdatePrefKey() {
		return LOCATION_MANUAL_UPDATE_KEY;
	}

	@Override
	void rescheduleAlarms() {
		rescheduleAlarm(LocationMonitoringReceiver.class);
	}

	@Override
	boolean isUpdateInProgress() {
		return getPref(LOCATION_UPDATE_IN_PROGRESS_KEY, false);
	}

	@Override
	void setUpdateInProgress(boolean updating) {
		putPref(LOCATION_UPDATE_IN_PROGRESS_KEY, updating);
	}

	@Override
	void clearManualUpdateFlag() {
		putPref(LOCATION_MANUAL_UPDATE_KEY, false);
	}
}
