package gr.uoa.di.monitoring.android.receivers;

import android.content.Context;
import android.content.Intent;

import gr.uoa.di.monitoring.android.services.Monitor;

/**
 * BroadcastReceiver registered to receive boot events. If this is enabled on
 * Boot it means monitoring should be enabled. See :
 * http://stackoverflow.com/a/5439320/281545
 *
 * @author MrD
 */
public class TriggerMonitoringBootReceiver extends BaseReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		w(intent.toString());
		final String action = intent.getAction(); // NPE ?
		if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
			w("Enabling receivers");
			Monitor.enableMonitoring(context, true);
		} else {
			w("Received bogus intent :\n" + intent + "\nAction : " + action);
		}
	}
}
