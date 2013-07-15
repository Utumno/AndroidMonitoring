package gr.uoa.di.monitoring.android.services;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;

import gr.uoa.di.monitoring.android.activities.DialogActivity;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationReceiver;

import java.util.Arrays;

import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.ENABLE;
import static gr.uoa.di.monitoring.android.C.NOT_USED;
import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.ac_location_data;

public final class LocationMonitor extends Monitor {

	private static final long LOCATION_MONITORING_INTERVAL = 2 * 60 * 1000;
	// GPS
	private static final int MIN_TIME_BETWEEN_SCANS = 1 * 30 * 1000;
	private static final int MIN_DISTANCE = 0;
	// convenience fields
	private static LocationManager lm; // not final cause we need a f*ng context

	public LocationMonitor() {
		super(LocationMonitor.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		final StringBuilder sb = monitorInfoHeader();
		final CharSequence action = intent.getAction();
		if (action == null) {
			// monitor command from the alarm manager
			final String provider = Providers.getProvider(this);
			sb.append(" (" + provider + ") ");
			if (provider == null) {
				// FIXME : alert user & abort
				// http://stackoverflow.com/questions/6031004/check-if-user-has-enabled-gps-after-prompted
				final ComponentName toLaunch = new ComponentName(
						"gr.uoa.di.monitoring.android",
						"gr.uoa.di.monitoring.android.activities.DialogActivity");
				final Intent i = new Intent();
				i.addCategory(Intent.CATEGORY_LAUNCHER);
				i.setComponent(toLaunch);
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				Bundle extras = new Bundle();
				extras = DialogActivity.setDialogText(extras,
					"Your location services are disabled. "
						+ "Monitoring will stop. Would you like to enable "
						+ "them and restart monitoring ? ");
				extras = DialogActivity.setDialogTitle(extras,
					"Please enable location tracking");
				final ComponentName gps = new ComponentName(
						"com.android.settings",
						"com.android.settings.SecuritySettings");
				final Intent locationIntent = new Intent(
						Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				locationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
				locationIntent.setComponent(gps);
				locationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				extras = DialogActivity.setDialogIntent(extras, locationIntent);
				i.putExtras(extras);
				LocationMonitor.this.startActivity(i);
				w("Aborting : " + sb.toString());
				abort();
				return;
			}
			Intent i = new Intent(this, LocationReceiver.class);
			PendingIntent pi = PendingIntent.getBroadcast(this, NOT_USED, i,
				PendingIntent.FLAG_UPDATE_CURRENT);
			d("pi : " + pi);
			d("Enabling the receiver");
			BaseReceiver.enable(this, ENABLE, LocationReceiver.class);
			lm().requestLocationUpdates(provider, MIN_TIME_BETWEEN_SCANS,
				MIN_DISTANCE, pi);
		} else if (ac_location_data.equals(action)) {
			final Bundle extras = intent.getExtras();
			if (extras != null) {
				final Location loc = (Location) extras
						.get(LocationManager.KEY_LOCATION_CHANGED);
				if (loc == null) {
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
					w(sb + "latitude :" + lat + " -- longitude : " + lon);
				}
			} else {
				w(sb + "NULL EXTRAS");
			}
		} else if (ac_aborting.equals(action)) {
			BaseReceiver.enable(this, DISABLE, LocationReceiver.class);
		}
	}

	private LocationManager lm() {
		if (lm == null) {
			lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		}
		return lm;
	}

	private final static class Providers {

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

		@SuppressWarnings("unused")
		private static String allProviders(LocationMonitor m) {
			return Arrays.toString(m.lm().getAllProviders().toArray());
		}
	}

	@Override
	public long getInterval() {
		return LOCATION_MONITORING_INTERVAL;
	}
}
