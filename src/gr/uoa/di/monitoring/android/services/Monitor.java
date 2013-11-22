package gr.uoa.di.monitoring.android.services;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.receivers.BaseAlarmReceiver;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.BatteryLowReceiver;
import gr.uoa.di.monitoring.android.receivers.BatteryMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.NetworkReceiver;
import gr.uoa.di.monitoring.android.receivers.TriggerMonitoringBootReceiver;
import gr.uoa.di.monitoring.android.receivers.WifiMonitoringReceiver;
import gr.uoa.di.monitoring.model.Data;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.ac_cancel_alarm;
import static gr.uoa.di.monitoring.android.C.ac_reschedule_alarm;
import static gr.uoa.di.monitoring.android.C.ac_setup_alarm;
import static gr.uoa.di.monitoring.android.C.cancelAllNotifications;

public abstract class Monitor<K, Y extends Data> extends AlarmService {

	private static final String TAG = Monitor.class.getSimpleName();
	// Monitoring
	private static final List<Class<? extends BaseReceiver>> RECEIVERS = new ArrayList<Class<? extends BaseReceiver>>();
	private static final List<Class<? extends BaseAlarmReceiver>> SETUP_ALARM_RECEIVERS = new ArrayList<Class<? extends BaseAlarmReceiver>>();
	// subclasses fields - subclasses are final so those have default scope
	/** Delimiter used to separate the items in debug prints */
	private Handler handler;
	static final String DEBUG_DELIMITER = "::";
	static {
		SETUP_ALARM_RECEIVERS.add(BatteryMonitoringReceiver.class);
		SETUP_ALARM_RECEIVERS.add(WifiMonitoringReceiver.class);
		SETUP_ALARM_RECEIVERS.add(LocationMonitoringReceiver.class);
		SETUP_ALARM_RECEIVERS.add(NetworkReceiver.class);
		RECEIVERS.addAll(SETUP_ALARM_RECEIVERS);
		RECEIVERS.add(TriggerMonitoringBootReceiver.class);
		RECEIVERS.add(BatteryLowReceiver.class); // TODO : separate treatment
	}

	public Monitor(String name) {
		super(name);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		handler = new Handler();
	}

	// =========================================================================
	// Abstract methods
	// =========================================================================
	/**
	 * Enforces Monitors to define cleanup actions to be performed when
	 * monitoring is disabled and they receive the ac_aborting action
	 */
	abstract void cleanup();

	/**
	 * Enforces monitors to persist their data
	 *
	 * @param <K>
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	abstract void saveResults(K data) throws FileNotFoundException, IOException;

	// reschedule alarms methods //
	abstract String getSameResultsCountPrefKey();

	abstract String getCurrentIntervalPrefKey();

	abstract String getLastResultsPrefKey();

	abstract void rescheduleAlarms();

	// =========================================================================
	// API
	// =========================================================================
	public static void enableMonitoring(Context ctx, boolean enable) {
		Log.d(TAG, "enableMonitoring : " + enable);
		Log.d(TAG, "setup/cancel alarms : "
			+ (enable ? ac_setup_alarm : ac_cancel_alarm));
		if (enable) {
			_enableDisableReceivers(ctx, enable);
			_setupCancelAlarms(ctx, enable);
			cancelAllNotifications(ctx); // maybe not the network service one
		} else {
			_setupCancelAlarms(ctx, enable);
			_enableDisableReceivers(ctx, enable);
		}
	}

	/**
	 * Disables monitoring and sets the preference to false TODO : turn into
	 * pause
	 *
	 * @param ctx
	 */
	public static void abort(Context ctx) {
		synchronized (Monitor.class) {
			String master_enable = ctx.getResources()
				.getText(R.string.enable_monitoring_master_pref_key).toString();
			if (!AccessPreferences.get(ctx, master_enable, DISABLE)) return; // already
																				// disabled
			AccessPreferences.put(ctx, master_enable, DISABLE);
			enableMonitoring(ctx, DISABLE);
		}
	}

	@Override
	public long getCurrentInterval() {
		return getPref(getCurrentIntervalPrefKey(), getInterval());
	}

	// API helpers
	private static void _setupCancelAlarms(Context ctx, boolean enable) {
		for (Class<? extends BaseAlarmReceiver> sEClass : SETUP_ALARM_RECEIVERS) {
			Intent i = new Intent(""
				+ (enable ? ac_setup_alarm : ac_cancel_alarm), Uri.EMPTY, ctx,
				sEClass);
			Log.d(TAG, "setup/cancel alarms int : " + i);
			ctx.sendBroadcast(i);
		}
	}

	<T extends BaseAlarmReceiver> void rescheduleAlarm(Class<T> receiver) {
		Intent i = new Intent("" + ac_reschedule_alarm, Uri.EMPTY, this,
			receiver);
		Log.d(TAG, "reschedule alarm : " + i);
		sendBroadcast(i);
	}

	private static void _enableDisableReceivers(Context ctx, boolean enable) {
		Log.d(TAG, "enable/disable receivers");
		for (Class<? extends BaseReceiver> receiver : RECEIVERS)
			BaseReceiver.enable(ctx, enable, receiver);
	}

	// =========================================================================
	// Methods used by the subclasses
	// =========================================================================
	/**
	 * Save the data into internal storage. Encapsulates common behavior of
	 * monitors on failing to write the data
	 *
	 * @param data
	 * @return
	 */
	boolean save(K data) {
		try {
			saveResults(data);
			return true;
		} catch (FileNotFoundException e) {
			// TODO abort ?
			w("IO exception writing data :" + e.getMessage());
		} catch (IOException e) {
			// TODO abort ?
			w("IO exception writing data :" + e.getMessage());
		}
		return false;
	}

	/** Disables monitoring and sets the preference to false */
	void abort() {
		synchronized (Monitor.class) {
			String master_enable = getResources().getText(
				R.string.enable_monitoring_master_pref_key).toString();
			if (!getPref(master_enable, DISABLE)) return; // already disabled
			putPref(master_enable, DISABLE);
			enableMonitoring(this, DISABLE);
		}
	}

	/**
	 * Debug header containing the time in human readable format - uses
	 * DEBUG_DELIMITER
	 *
	 * @return a StringBuilder containing the common info
	 */
	StringBuilder debugHeader() {
		StringBuilder sb = new StringBuilder();
		// sb.append(System.currentTimeMillis() / 1000);
		sb.append(time());
		sb.append(DEBUG_DELIMITER);
		return sb;
	}

	void runOnUiThread(Runnable runnable) {
		handler.post(runnable);
	}

	// reschedule alarms methods used by the subclasses //
	void zeroCount() {
		putPref(getSameResultsCountPrefKey(), 0);
	}

	void resetInterval() {
		putPref(getCurrentIntervalPrefKey(), getInterval());
	}

	void clearLastResults() {
		putPref(getLastResultsPrefKey(), null);
	}

	void updateInterval(Y t1, Y t2) {
		w("Current : " + t1);
		w("Previous : " + t2);
		if (t1.fairlyEqual(t2)) {
			final int sameResultsCount = getSameResultsCount();
			w("sameResultsCount : " + sameResultsCount);
			final long newInterval = MonitoringInterval.getUpdatedInterval(
				sameResultsCount + 1, getCurrentInterval());
			w("newInterval : " + newInterval);
			// enum decides if it is time to update the interval
			if (newInterval == 0) { // no time to update yet
				increaseCount();
			} else {
				zeroCount();
				updateInterval(newInterval);
				rescheduleAlarms();
			}
		} else {
			w("!!!!!!! Different Results");
			zeroCount();
			if (getCurrentInterval() != getInterval()) {
				resetInterval();
				rescheduleAlarms();
			}
		}
	}

	// private reschedule alarms methods used in updateInterval(Y t1, Y t2) //
	private void increaseCount() {
		putPref(getSameResultsCountPrefKey(),
			getPref(getSameResultsCountPrefKey(), 0) + 1);
	}

	private int getSameResultsCount() {
		return getPref(getSameResultsCountPrefKey(), 0);
	}

	private void updateInterval(long newInterval) {
		putPref(getCurrentIntervalPrefKey(), newInterval);
	}

	enum MonitoringInterval {
		ONE(1, 4), TWO(2, 3), FIVE(5, 2), TEN(10, 1), FIFTEEN(15, 1), TWENTY(
				20, 1), HALF_HOUR(30, 1), HOUR(60, Integer.MAX_VALUE);

		private final int retries;
		private final long interval;

		/**
		 * Returns the interval for this enum constant. API
		 *
		 * @return the interval for this enum constant
		 */
		long getInterval() {
			return interval;
		}

		private MonitoringInterval(long interval, int retries) {
			this.retries = retries;
			this.interval = interval * 60 * 1000L;
		}

		/**
		 * Returns the new interval or 0 if no change is needed. Used by Monitor
		 * - that's why it's not private
		 */
		static long getUpdatedInterval(int sameResultsCount,
				long currentInterval) {
			final MonitoringInterval current = fromLong(currentInterval);
			MonitoringInterval result = current;
			if (sameResultsCount >= current.retries)
				result = current.lessOften();
			if (result == current) return 0; // no change
			return result.interval;
		}

		private static MonitoringInterval fromLong(long interval) {
			for (MonitoringInterval mon : MonitoringInterval.values()) {
				if (mon.interval == interval) return mon;
			}
			throw new IllegalArgumentException("Where did you get this "
				+ "interval from ?");
		}

		private MonitoringInterval lessOften() {
			int ordinal = ordinal();
			if (ordinal < values().length - 1) {
				++ordinal;
			}
			return values()[ordinal];
		}
	}
}
