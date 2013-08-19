package gr.uoa.di.monitoring.android.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;

import gr.uoa.di.monitoring.android.activities.DialogActivity;
import gr.uoa.di.monitoring.android.persist.FileStore;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationReceiver;

import org.apache.http.util.EncodingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.ENABLE;
import static gr.uoa.di.monitoring.android.C.NOT_USED;
import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.ac_location_data;
import static gr.uoa.di.monitoring.android.C.launchSettingsIntent;
import static gr.uoa.di.monitoring.android.C.triggerNotification;

public final class LocationMonitor extends Monitor {

	private static final String LOG_PREFIX = "loc";
	private static final long LOCATION_MONITORING_INTERVAL = 2 * 60 * 1000;
	private static final Class<? extends BaseReceiver> PERSONAL_RECEIVER = LocationReceiver.class;
	// Location api calls constants
	private static final int MIN_TIME_BETWEEN_SCANS = 1 * 30 * 1000; // 30 secs
	private static final int MIN_DISTANCE = 0;
	public static final String NOTIFICATION_TAG = LocationMonitor.class
			.getSimpleName() + ".Notification";
	public static final int NOTIFICATION_ID = 9200;
	// convenience fields
	private static LocationManager lm; // not final cause we need a f*ng context
	// members
	private static PendingIntent pi; // see above
	private Providers.Status providerStatus;

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
			// monitor command from the alarm manager
			final String provider = Providers.getProvider(this);
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
		} else if (ac_location_data.equals(action)) {
			final Bundle extras = intent.getExtras();
			if (extras != null) {
				final Location loc = (Location) extras
						.get(LocationManager.KEY_LOCATION_CHANGED);
				if (loc == null) {
					w(sb + "NULL LOCATION  - EXTRAS : " + extras);
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
					saveResults(loc);
				}
			} else {
				w(sb + "NULL EXTRAS");
			}
		} else if (ac_aborting.equals(action)) {
			cleanup();
		}
	}

	private static enum LocationFields implements FileStore.Fields {
		TIME {

			@Override
			public <T> List<byte[]> getData(T data) {
				Location loc = (Location) data;
				List<byte[]> arrayList = new ArrayList<byte[]>();
				arrayList.add(EncodingUtils.getAsciiBytes(loc.getTime() + ""));
				return arrayList;
			}
		},
		LAT {

			@Override
			public <T> List<byte[]> getData(T data) {
				Location loc = (Location) data;
				List<byte[]> arrayList = new ArrayList<byte[]>();
				arrayList.add(EncodingUtils.getAsciiBytes(loc.getLatitude()
					+ ""));
				return arrayList;
			}
		},
		LONG {

			@Override
			public <T> List<byte[]> getData(T data) {
				Location loc = (Location) data;
				List<byte[]> arrayList = new ArrayList<byte[]>();
				arrayList.add(EncodingUtils.getAsciiBytes(loc.getLongitude()
					+ ""));
				return arrayList;
			}
		},
		PROVIDER {

			@Override
			public <T> List<byte[]> getData(T data) {
				Location loc = (Location) data;
				List<byte[]> arrayList = new ArrayList<byte[]>();
				arrayList.add(EncodingUtils.getAsciiBytes(loc.getProvider()
					+ ""));
				return arrayList;
			}
		};

		@Override
		public boolean isList() {
			return false; // no lists here
		}

		public static <T> List<byte[]> createListOfByteArrays(T data) {
			final List<byte[]> listByteArrays = new ArrayList<byte[]>();
			for (LocationFields bs : LocationFields.values()) {
				listByteArrays.add(bs.getData(data).get(0));
			}
			return listByteArrays;
		}
	}

	private void receiver(final boolean enable) {
		BaseReceiver.enable(this, enable, PERSONAL_RECEIVER);
	}

	@Override
	void cleanup() {
		receiver(DISABLE);
		lm().removeUpdates(pi); // TODO : maybe check if requested ?
	}

	private LocationManager lm() {
		if (lm == null) {
			lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		}
		return lm;
	}

	/**
	 * Provides the providers - wrapper around the LocationManager provider
	 * facilities. Includes our Criteria and defines our policies
	 *
	 * @author MrD
	 */
	private final static class Providers {

		private Providers() {}

		// private static Context daFrigginCtx;
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

		private static String getProvider(final LocationMonitor m) {
			return m.lm().getBestProvider(CRITERIA, ENABLE);
		}

		private static Status getProviderStatus(Context ctx, String s) {
			if (s.equals(null)) return Status.NULL;
			if (s.equals(LocationManager.GPS_PROVIDER)) return Status.GPS;
			if (s.equals(LocationManager.NETWORK_PROVIDER)) {
				// Check if wireless is enabled
				WifiManager wm = (WifiManager) ctx
						.getSystemService(Context.WIFI_SERVICE);
				if (!wm.isWifiEnabled()) {
					return Status.NETWORK_NOT_ENABLED;
				}
				ConnectivityManager cm = (ConnectivityManager) ctx
						.getSystemService(CONNECTIVITY_SERVICE);
				Boolean isWifi = cm.getNetworkInfo(
				// TODO : isConnectedOrConnecting ? long periods of no
				// connection go unnoticed
					ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
				if (isWifi) {
					return Status.NETWORK;
				}
				return Status.NETWORK_NOT_CONNECTED;
			}
			return Status.UNKNOWN_PROVIDER;
		}

		@SuppressWarnings("unused")
		private static void launchDialog(Context ctx, Status stat) {
			DialogActivity.launchDialogActivity(ctx, stat.title(),
				stat.notification(), stat.launchIntent());
		}

		private static void launchNotification(Context ctx, Status stat) {
			Intent intent = DialogActivity.launchDialogActivityIntent(ctx,
				stat.title(), stat.dialog(), stat.launchIntent());
			// intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // nope
			triggerNotification(ctx, stat.title(), stat.notification(), intent,
				LocationMonitor.NOTIFICATION_TAG,
				LocationMonitor.NOTIFICATION_ID);
		}

		@SuppressWarnings("unused")
		private static String allProviders(LocationMonitor m) {
			return Arrays.toString(m.lm().getAllProviders().toArray());
		}

		private enum Status {
			GPS, NETWORK, NETWORK_NOT_ENABLED, NETWORK_NOT_CONNECTED, NULL,
			UNKNOWN_PROVIDER;

			// TODO : add a constant on gps status GPS_NO_FIX
			// FIXME : move messages to resources
			//
			private String title() {
				switch (this) {
				case NULL:
					return "Enable location tracking";
				case NETWORK_NOT_CONNECTED:
					return "Connect to a network";
				case GPS:
					return "GPS location";
				case NETWORK:
					return "Network location";
				case UNKNOWN_PROVIDER:
					return "Unknown location provider";
				case NETWORK_NOT_ENABLED:
					return "Enable wifi";
				}
				throw new IllegalStateException("Forgotten enum constant");
			}

			private String notification() {
				switch (this) {
				case NULL:
					return "No location services. Monitoring has stopped.";
				case NETWORK_NOT_CONNECTED:
					return "No wireless connection. Monitoring has stopped.";
				case NETWORK_NOT_ENABLED:
					return "No network/gps open. Monitoring has stopped.";
				case GPS:
					return "Location is provided by GPS. That is the best "
						+ "option for monitoring the location if outdoors. "
						+ "Please if indoors connect to a wireless network\n";
				case NETWORK:
					return "Location is provided by your wireless connection. "
						+ "That is the best option for monitoring the location"
						+ " if outdoors. Please consider connecting to the GPS "
						+ "provider while outdoors\n";
				case UNKNOWN_PROVIDER:
					return "No known location services. Monitoring has "
						+ "stopped.";
				}
				throw new IllegalStateException("Forgotten enum constant");
			}

			private String dialog() {
				switch (this) {
				case NULL:
					return "Your location services are disabled. "
						+ "Monitoring has stopped. Would you like to enable "
						+ "them and restart monitoring ?\n";
				case NETWORK_NOT_CONNECTED:
					return "You are not connected to a wireless network. "
						+ "Monitoring has stopped. Would you like to connect  "
						+ "a network and restart monitoring ?\n";
				case NETWORK_NOT_ENABLED:
					return "You must enable wifi (or preferably GPS if you "
						+ "are outdoors). Monitoring has stopped.  Would you "
						+ "like to enable wifi and restart monitoring ?\n";
				case GPS:
					return "Location is provided by GPS. That is the best "
						+ "option for monitoring the location if outdoors. "
						+ "Please if indoors connect to a wireless network\n";
				case NETWORK:
					return "Location is provided by your wireless connection. "
						+ "That is the best option for monitoring the location"
						+ " if outdoors. Please consider connecting to the GPS "
						+ "provider while outdoors\n";
				case UNKNOWN_PROVIDER:
					return "Your location services are new to us. "
						+ "Monitoring has stopped. Please enable GPS or if "
						+ "indoors connect to a wireless network\n";
				}
				throw new IllegalStateException("Forgotten enum constant");
			}

			private Intent launchIntent() {
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
				throw new IllegalStateException("Forgotten enum constant");
			}

			private boolean notAvailabe() {
				if (this == GPS || this == NETWORK) return false;
				return true;
			}
		}
	}

	@Override
	public long getInterval() {
		return LOCATION_MONITORING_INTERVAL;
	}

	@Override
	public String logPrefix() {
		return LOG_PREFIX;
	}

	@Override
	public <T> void saveResults(T data) {
		List<byte[]> listByteArrays = LocationFields
				.createListOfByteArrays(data);
		saveData(listByteArrays);
	}
}
