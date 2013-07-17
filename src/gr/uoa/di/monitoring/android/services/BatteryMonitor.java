package gr.uoa.di.monitoring.android.services;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public final class BatteryMonitor extends Monitor {

	private static final long BATTERY_MONITORING_INTERVAL = 10 * 60 * 1000;

	public BatteryMonitor() {
		// needed for service instantiation by Android. See :
		// http://stackoverflow.com/questions/6176255/why-do-i-get-an-instantiationexception-if-i-try-to-start-a-service
		// also for "why" of the string parameter :
		// http://stackoverflow.com/questions/8016145/understanding-the-mechanisms-of-intentservice
		super(BatteryMonitor.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent arg0) {
		StringBuilder sb = monitorInfoHeader();
		final Intent batteryStatus = registerReceiver(null, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));
		// set the default to -1 as per :
		// http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
		sb.append(batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
		sb.append(DELIMITER);
		w(sb.toString());
	}

	@Override
	public long getInterval() {
		return BATTERY_MONITORING_INTERVAL;
	}

	@Override
	void cleanup() {
		// pass
	}
}
