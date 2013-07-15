package gr.uoa.di.monitoring.android.services;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.AccessPreferences;
import gr.uoa.di.monitoring.android.FileIO;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.receivers.BaseMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.TriggerMonitoringBootReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.INFO;
import static gr.uoa.di.monitoring.android.C.WARN;
import static gr.uoa.di.monitoring.android.C.ac_cancel_alarm;
import static gr.uoa.di.monitoring.android.C.ac_setup_alarm;

public abstract class Monitor extends WakefulIntentService {

	private final String tag_ = this.getClass().getSimpleName();
	private static final String TAG = Monitor.class.getSimpleName();
	/**
	 * The name of the charset to use when writing to files - ASCII for space
	 */
	private static final String CHARSET_NAME = "ASCII";
	private static final String NO_IMEI = "NO_IMEI";
	private static String sImei;
	private static final int INITIAL_DELAY = 5000; // 5 seconds
	private static final List<Class<? extends BaseReceiver>> RECEIVERS = new ArrayList<Class<? extends BaseReceiver>>();
	private static final List<Class<? extends BaseMonitoringReceiver>> SETUP_ALARM_RECEIVERS = new ArrayList<Class<? extends BaseMonitoringReceiver>>();
	// subclasses fields - subclasses are final so those have default scope
	static final String DELIMITER = "::";
	static {
		// SETUP_ALARM_RECEIVERS.add(BatteryMonitoringReceiver.class);
		// SETUP_ALARM_RECEIVERS.add(WifiMonitoringReceiver.class);
		SETUP_ALARM_RECEIVERS.add(LocationMonitoringReceiver.class);
		RECEIVERS.addAll(SETUP_ALARM_RECEIVERS);
		RECEIVERS.add(TriggerMonitoringBootReceiver.class);
	}

	public Monitor(String name) {
		super(name);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		try {
			if (sImei == null) {
				final Context applicationContext = this.getApplicationContext();
				TelephonyManager tm = (TelephonyManager) applicationContext
						.getSystemService(Context.TELEPHONY_SERVICE);
				sImei = tm.getDeviceId();
			}
		} catch (NullPointerException e) {
			w("No imei today : " + e);
			sImei = NO_IMEI;
		}
	}

	public abstract long getInterval();

	public static int getInitialDelay() {
		return INITIAL_DELAY;
	}

	public static void enableMonitoring(Context ctx, boolean enable) {
		Log.d(TAG, "enableMonitoring : " + enable);
		Log.d(TAG, "setup/cancel alarms : "
			+ (enable ? ac_setup_alarm : ac_cancel_alarm));
		if (enable) {
			_enableDisableReceivers(ctx, enable);
			_setupCancelAlarms(ctx, enable);
		} else {
			_setupCancelAlarms(ctx, enable);
			_enableDisableReceivers(ctx, enable);
		}
	}

	private static void _setupCancelAlarms(Context ctx, boolean enable) {
		for (Class<? extends BaseMonitoringReceiver> sEClass : SETUP_ALARM_RECEIVERS) {
			Intent i = new Intent(""
				+ (enable ? ac_setup_alarm : ac_cancel_alarm), Uri.EMPTY, ctx,
					sEClass);
			Log.d(TAG, "setup/cancel alarms int : " + i);
			ctx.sendBroadcast(i);
		}
	}

	private static void _enableDisableReceivers(Context ctx, boolean enable) {
		Log.d(TAG, "enable/disable receivers");
		for (Class<? extends BaseReceiver> receiver : RECEIVERS)
			BaseReceiver.enable(ctx, enable, receiver);
	}

	// =========================================================================
	// methods used by the subclasses
	// =========================================================================
	<T> void persist(String key, T value) {
		AccessPreferences.persist(this, key, value);
	}

	<T> T retrieve(String key, T value) {
		return AccessPreferences.retrieve(this, key, value);
	}

	void abort() {
		CharSequence master_enable = getResources().getText(
			R.string.enable_monitoring_master_pref_key);
		AccessPreferences.persist(this, master_enable.toString(), DISABLE);
		enableMonitoring(this, DISABLE);
	}

	/**
	 * All monitoring info is required to provide a timestamp and the IMEI -
	 * this method takes care of that
	 *
	 * @return a StringBuilder containing the common info
	 */
	StringBuilder monitorInfoHeader() {
		StringBuilder sb = new StringBuilder();
		// sb.append(sImei);
		// sb.append(DELIMITER);
		// TODO time...
		// sb.append(System.currentTimeMillis() / 1000);
		sb.append(new Date(System.currentTimeMillis()));
		sb.append(DELIMITER);
		return sb;
	}

	// =========================================================================
	// LOGGING
	// =========================================================================
	void w(String msg) {
		if (!WARN) return;
		Log.w(tag_, msg);
		try {
			// create a File object for the parent directory
			final boolean externalStoragePresent = FileIO
					.isExternalStoragePresent();
			// d("External : " + externalStoragePresent);
			if (externalStoragePresent) {
				File logdir = new File(Environment
						.getExternalStoragePublicDirectory(
							Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
				// have the object build the directory structure, if needed.
				if (FileIO.createDirExternal(logdir)) {
					// create a File object for the output file
					File outputFile = new File(logdir, "LOG.log");
					FileIO.append(outputFile, msg + "\n", CHARSET_NAME);
				} else {
					w("can't create output directory");
				}
			} // else
				// FileIO.append("LOG.log", msg + "\n", this, CHARSET_NAME,
				// Context.MODE_PRIVATE);
		} catch (FileNotFoundException e) {
			Log.w(tag_, e + "");
		} catch (IOException e) {
			Log.w(tag_, e + "");
		}
	}

	void w(String msg, Throwable t) {
		if (WARN) Log.w(tag_, msg, t);
	}

	void w(Throwable t) {
		if (WARN) Log.w(tag_, t);
	}

	void d(String msg) {
		if (DEBUG) Log.d(tag_, msg);
	}

	void i(String msg) {
		if (INFO) Log.i(tag_, msg);
	}
}

enum MonitoringInterval {
	FIVE(5), SIX(6), TEN(10), TWELVE(12), FIFTEEN(15), TWENTY(20),
	HALF_HOUR(30), HOUR(60);

	private final long interval;

	MonitoringInterval(long interval) {
		this.interval = interval;
	}

	public long getInterval() {
		return interval;
	}

	MonitoringInterval lessOften(MonitoringInterval mi) {
		int ordinal = mi.ordinal();
		if (ordinal < values().length - 1) {
			++ordinal;
		}
		return values()[ordinal];
	}

	MonitoringInterval moreOften(MonitoringInterval mi) {
		int ordinal = mi.ordinal();
		if (ordinal > 0) {
			--ordinal;
		}
		return values()[ordinal];
	}
}
