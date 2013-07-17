package gr.uoa.di.monitoring.android.receivers;

import android.content.Context;
import android.content.Intent;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.services.LocationMonitor;
import gr.uoa.di.monitoring.android.services.Monitor;

import static gr.uoa.di.monitoring.android.C.ac_location_data;

public final class LocationReceiver extends BaseReceiver {

	private static final Class<? extends Monitor> MONITOR_CLASS = LocationMonitor.class;

	@Override
	public void onReceive(Context context, Intent intent) {
		d("action : " + intent);
		final String action = intent.getAction();
		d("action : " + action);
		final Intent i = new Intent(context, MONITOR_CLASS);
		i.fillIn(intent, 0); // TODO do I need flags ?
		i.setAction(ac_location_data.toString());
		WakefulIntentService.sendWakefulWork(context, i);
	}
}
