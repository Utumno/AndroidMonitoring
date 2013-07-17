package gr.uoa.di.monitoring.android.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;

import gr.uoa.di.monitoring.android.activities.DialogActivity;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationReceiver;

import java.util.Arrays;

import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.ENABLE;
import static gr.uoa.di.monitoring.android.C.NOT_USED;
import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.ac_location_data;
import static gr.uoa.di.monitoring.android.C.launchSettingsIntent;

public final class LocationMonitor extends Monitor {

	private static final long LOCATION_MONITORING_INTERVAL = 2 * 60 * 1000;
	// Location api calls constants
	private static final int MIN_TIME_BETWEEN_SCANS = 1 * 30 * 1000;
	private static final int MIN_DISTANCE = 0;
	// convenience fields
	private static LocationManager lm; // not final cause we need a f*ng context
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
		Intent i = new Intent(this, LocationReceiver.class);
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
		final StringBuilder sb = monitorInfoHeader();
		final CharSequence action = intent.getAction();
		if (action == null) {
			// monitor command from the alarm manager
			final String provider = Providers.getProvider(this);
			providerStatus = Providers.getProviderStatus(this, provider);
			if (providerStatus.notAvailabe()) {
				sb.append(" (" + provider + ") ");
				// FIXME : notification !
				Providers.launchDialog(this, providerStatus);
				// TODO Listeners providerStatus.waitForChange() instead of
				// abort
				w("Aborting : " + sb.toString());
				abort();
				return;
			}
			d("Enabling the receiver");
			BaseReceiver.enable(this, ENABLE, LocationReceiver.class);
			d("Requesting location updates - pi : " + pi);
			lm().requestLocationUpdates(provider, MIN_TIME_BETWEEN_SCANS,
				MIN_DISTANCE, pi);
			// check what happens if I register for location updates once
			BaseReceiver
					.enable(this, DISABLE, LocationMonitoringReceiver.class);
		} else if (ac_location_data.equals(action)) {
			final Bundle extras = intent.getExtras();
			if (extras != null) {
				final Location loc = (Location) extras
						.get(LocationManager.KEY_LOCATION_CHANGED);
				if (loc == null) {
					// FIXME disable the updates
					w(sb + "NULL LOCATION  - EXTRAS : " + extras);
					// if gps is disabled I keep getting this :
					// NULL LOCATION - EXTRAS : Bundle[{providerEnabled=false}]
					// as soon as I enable the provider I get
					// W/GpsLocationProvider(851): Unneeded remove listener for
					// uid 1000
				} else {
					final String provider = loc.getProvider();
					sb.append(" (" + provider + ") ");
					final double lon = loc.getLongitude();
					final double lat = loc.getLatitude();
					final long time = loc.getTime();
					w(sb + "latitude :" + lat + " -- longitude : " + lon);
					d("Got location - disabling LocationReceiver");
					// BaseReceiver.enable(this, DISABLE,
					// LocationReceiver.class);
				}
			} else {
				w(sb + "NULL EXTRAS");
			}
		} else if (ac_aborting.equals(action)) {
			cleanup();
		}
	}

	@Override
	void cleanup() {
		BaseReceiver.enable(this, DISABLE, LocationReceiver.class);
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

		private static String getProvider(LocationMonitor m) {
			return m.lm().getBestProvider(CRITERIA, ENABLE);
		}

		private static Status getProviderStatus(Context ctx, String s) {
			if (s.equals(null)) return Status.NULL;
			if (s.equals(LocationManager.GPS_PROVIDER)) return Status.GPS;
			if (s.equals(LocationManager.NETWORK_PROVIDER)) {
				ConnectivityManager cm = (ConnectivityManager) ctx
						.getSystemService(CONNECTIVITY_SERVICE);
				Boolean isWifi = cm.getNetworkInfo(
					ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
				if (isWifi) {
					return Status.NETWORK;
				}
				return Status.NETWORK_NOT_CONNECTED;
			}
			return Status.UNKNOWN_PROVIDER;
		}

		private static void launchDialog(Context ctx, Status stat) {
			DialogActivity.launchDialogActivity(ctx, stat.title(),
				stat.notification(), stat.launchIntent());
		}

		@SuppressWarnings("unused")
		private static String allProviders(LocationMonitor m) {
			return Arrays.toString(m.lm().getAllProviders().toArray());
		}

		private enum Status {
			GPS, NETWORK, NETWORK_NOT_CONNECTED, NULL, UNKNOWN_PROVIDER;

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
				}
				throw new IllegalStateException("Forgotten enum constant");
			}

			private String notification() {
				switch (this) {
				case NULL:
					return "Your location services are disabled. "
						+ "Monitoring will stop. Would you like to enable "
						+ "them and restart monitoring ?\n";
				case NETWORK_NOT_CONNECTED:
					return "You are not connected to a wireless network. "
						+ "Monitoring will stop. Would you like to connect  "
						+ "a network and restart monitoring ?\n";
				case GPS:
					return "Location is provided by GPS. That is the best "
						+ "option for monitoring the location if outdoors. "
						+ "Please if indoors connect to a wireless network\n";
				case NETWORK:
					return "Location is provided by your connection to a "
						+ "wireless network. That is the best option for "
						+ "monitoring the location if outdoors. Please consider"
						+ " connecting to the GPS provider while outdoors\n";
				case UNKNOWN_PROVIDER:
					return "Your location services are new to us. "
						+ "Monitoring will stop. Please enable GPS or if "
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
}
