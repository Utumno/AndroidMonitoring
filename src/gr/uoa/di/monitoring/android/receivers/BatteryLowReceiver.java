package gr.uoa.di.monitoring.android.receivers;

import android.content.Context;
import android.content.Intent;

import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.services.Monitor;

import static gr.uoa.di.monitoring.android.C.triggerNotification;

/**
 * BroadcastReceiver registered to receive battery events - for the moment it
 * receives only battery low and disables monitoring
 *
 * @author MrD
 */
public final class BatteryLowReceiver extends BaseReceiver {

	private static final String NOTIFICATION_TAG = BatteryLowReceiver.class
		.getSimpleName() + ".Notification";
	private static final int ID = 0;

	@Override
	public void onReceive(Context context, Intent intent) {
		d(intent.toString());
		final String action = intent.getAction();
		if (Intent.ACTION_BATTERY_LOW.equals(action)) {
			d("Battery low - disable monitoring");
			triggerNotification(context,
				context.getString(R.string.title_battery_low_notification),
				context.getString(R.string.body_battery_low_notification),
				NOTIFICATION_TAG, ID);
			Monitor.abort(context);
		} else {
			w("Received bogus intent :\n" + intent + "\nAction : " + action);
		}
	}
}
