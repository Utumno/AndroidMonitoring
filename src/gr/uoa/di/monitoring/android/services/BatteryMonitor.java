package gr.uoa.di.monitoring.android.services;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import gr.uoa.di.monitoring.android.activities.MonitorActivity;
import gr.uoa.di.monitoring.android.receivers.BatteryMonitoringReceiver;
import gr.uoa.di.monitoring.model.Battery;
import gr.uoa.di.monitoring.model.ParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static gr.uoa.di.monitoring.android.C.ac_aborting;

public final class BatteryMonitor extends Monitor<Intent, Battery> {

	private static final long BATTERY_MONITORING_INTERVAL = MonitoringInterval.ONE
		.getInterval();
	private static final String BATTERY_DATA_KEY = "BATTERY_DATA_KEY";
	private static final String SAME_BATTERY_COUNT_KEY = "SAME_BATTERY_COUNT_KEY";
	private static final String BATTERY_INTERVAL_KEY = "BATTERY_INTERVAL_KEY";

	public BatteryMonitor() {
		// needed for service instantiation by Android. See :
		// http://stackoverflow.com/questions/6176255/
		// why-do-i-get-an-instantiationexception-if-i-try-to-start-a-service
		// also for "why" of the string parameter :
		// http://stackoverflow.com/questions/8016145/
		// understanding-the-mechanisms-of-intentservice
		super(BatteryMonitor.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent in) {
		StringBuilder sb = debugHeader();
		final CharSequence action = in.getAction();
		if (action == null) {
			// monitoring command from the alarm manager
			final Intent batteryStatus = registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			// set the default to -1 as per :
			// http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
			sb.append(batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
			w(sb.toString());
			save(batteryStatus);
		} else if (ac_aborting.equals(action)) {
			cleanup();
		}
	}

	@Override
	public long getInterval() {
		return BATTERY_MONITORING_INTERVAL;
	}

	@Override
	void cleanup() {
		zeroCount();
		resetInterval();
		clearLastResults();
	}

	@Override
	void saveResults(Intent data) throws FileNotFoundException, IOException {
		List<byte[]> listByteArrays = Battery.BatteryFields
			.createListOfByteArrays(data);
		Battery.saveData(this, listByteArrays);
		try {
			final Battery currentBattery = Battery.fromBytes(listByteArrays);
			// get the previous data from the preferences store
			String previousData = getPref(BATTERY_DATA_KEY, null);
			Battery previousBattery = Battery.fromString(previousData);
			// check to see if we need to modify the interval
			updateInterval(currentBattery, previousBattery);
			// store the new data
			putPref(BATTERY_DATA_KEY, currentBattery.stringForm());
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					MonitorActivity.onChange(BatteryMonitor.this);
				}
			});
		} catch (ParserException e) {
			w("Corrupted data", e);
		}
	}

	public static String dataKey() {
		return BATTERY_DATA_KEY;
	}

	@Override
	String getLastResultsPrefKey() {
		return BATTERY_DATA_KEY;
	}

	@Override
	String getSameResultsCountPrefKey() {
		return SAME_BATTERY_COUNT_KEY;
	}

	@Override
	String getCurrentIntervalPrefKey() {
		return BATTERY_INTERVAL_KEY;
	}

	@Override
	void rescheduleAlarms() {
		rescheduleAlarm(BatteryMonitoringReceiver.class);
	}
}
