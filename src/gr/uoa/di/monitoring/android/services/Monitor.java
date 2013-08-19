package gr.uoa.di.monitoring.android.services;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.AccessPreferences;
import gr.uoa.di.monitoring.android.FileIO;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.persist.FileStore;
import gr.uoa.di.monitoring.android.persist.FileStore.Fields;
import gr.uoa.di.monitoring.android.receivers.BaseMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.BatteryMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.LocationMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.TriggerMonitoringBootReceiver;
import gr.uoa.di.monitoring.android.receivers.WifiMonitoringReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.INFO;
import static gr.uoa.di.monitoring.android.C.ISO8859;
import static gr.uoa.di.monitoring.android.C.LOG_DIR;
import static gr.uoa.di.monitoring.android.C.LOG_FILE;
import static gr.uoa.di.monitoring.android.C.WARN;
import static gr.uoa.di.monitoring.android.C.ac_cancel_alarm;
import static gr.uoa.di.monitoring.android.C.ac_setup_alarm;

public abstract class Monitor extends WakefulIntentService {

	private final String tag_ = this.getClass().getSimpleName();
	private static final String TAG = Monitor.class.getSimpleName();
	// Persistence
	/**
	 * The name of the charset to use when writing to files - ISO-8859-1 for
	 * space and byte preservation
	 */
	static final String CHARSET_NAME = ISO8859;
	private static final String NO_IMEI = "NO_IMEI";
	private static String sImei;
	private static String sRootFolder;
	// Monitoring
	private static final int INITIAL_DELAY = 5000; // 5 seconds
	private static final List<Class<? extends BaseReceiver>> RECEIVERS = new ArrayList<Class<? extends BaseReceiver>>();
	private static final List<Class<? extends BaseMonitoringReceiver>> SETUP_ALARM_RECEIVERS = new ArrayList<Class<? extends BaseMonitoringReceiver>>();
	// subclasses fields - subclasses are final so those have default scope
	/** Delimiter used to separate the items in debug prints */
	static final String DEBUG_DELIMITER = "::";
	static {
		SETUP_ALARM_RECEIVERS.add(BatteryMonitoringReceiver.class);
		SETUP_ALARM_RECEIVERS.add(WifiMonitoringReceiver.class);
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
				final Context applicationContext = getApplicationContext();
				TelephonyManager tm = (TelephonyManager) applicationContext
						.getSystemService(Context.TELEPHONY_SERVICE);
				sImei = tm.getDeviceId();
			}
		} catch (NullPointerException e) {
			w("No imei today : " + e);
			sImei = NO_IMEI;
		}
		sRootFolder = sImei; // have to do it here
	}

	// =========================================================================
	// Abstract methods
	// =========================================================================
	public abstract long getInterval();

	/**
	 * Enforces monitors to return a prefix prepended to the filename where the
	 * data is persisted. This way the various files are differentiated
	 *
	 * @return the prefix used by the file where the data is persisted
	 */
	public abstract String logPrefix();

	/**
	 * Enforces Monitors to define cleanup actions to be performed when
	 * monitoring is disabled and they receive the ac_aborting action
	 */
	abstract void cleanup();

	/**
	 * Enforces monitors to persists their data
	 *
	 * @param <T>
	 *
	 * @return the prefix used by the file where the data is persisted
	 */
	abstract <T> void saveResults(T data);

	// =========================================================================
	// API
	// =========================================================================
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
	<T extends Enum<T> & Fields> void saveData(List<byte[]> listByteArrays,
			List<List<byte[]>> listOfListsOfByteArrays, Class<T> fields) {
		try {
			// if (DEBUG) {
			// // write in external storage so I can see what goes
			// // on
			// File file = FileIO.fileExternalTopLevel(LOG_DIR, fileName(),
			// null);
			// FileStore.persist(file, fields, listByteArrays,
			// listOfListsOfByteArrays);
			// }
			// internal storage
			FileStore.persist(dataFileInInternalStorage(), fields,
				listByteArrays, listOfListsOfByteArrays);
		} catch (FileNotFoundException e) {
			// TODO abort ?
			w("IO exception writing data :" + e.getMessage());
		} catch (IOException e) {
			// TODO abort ?
			w("IO exception writing data :" + e.getMessage());
		}
	}

	void saveData(List<byte[]> listByteArrays) {
		try {
			// if (DEBUG) {
			// // write in external storage so I can see what goes
			// // on
			// File file = FileIO.fileExternalTopLevel(LOG_DIR, fileName(),
			// null);
			// // w("FileIO.fileExternalTopLevel : " + file.getAbsolutePath());
			// FileStore.persist(file, listByteArrays);
			// }
			// internal storage
			// w("dataFileInInternalStorage() : " +
			// dataFileInInternalStorage());
			FileStore.persist(dataFileInInternalStorage(), listByteArrays);
		} catch (FileNotFoundException e) {
			// TODO abort ?
			w("IO exception writing data :" + e.getMessage());
		} catch (IOException e) {
			// TODO abort ?
			w("IO exception writing data :" + e.getMessage());
		}
	}

	<T> void persist(String key, T value) {
		AccessPreferences.persist(this, key, value);
	}

	<T> T retrieve(String key, T value) {
		return AccessPreferences.retrieve(this, key, value);
	}

	void abort() {
		synchronized (Monitor.class) {
			String master_enable = getResources().getText(
				R.string.enable_monitoring_master_pref_key).toString();
			if (!retrieve(master_enable, DISABLE)) return; // already disabled
			persist(master_enable, DISABLE);
			enableMonitoring(this, DISABLE);
		}
	}

	File dataFileInInternalStorage() throws IOException {
		File internalDir = FileIO.createDirInternal(this, sImei);
		return new File(internalDir, fileName());
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
		sb.append(new Date(System.currentTimeMillis()));
		sb.append(DEBUG_DELIMITER);
		return sb;
	}

	/**
	 * Creates the filename of the file the data is persisted. For the moment
	 * the filename is the string returned by {@code filePrefix()} but TODO :
	 * add a preferences-persisted mechanism to break the files via timestamp
	 *
	 * @return
	 */
	String fileName() {
		return logPrefix();
	}

	// =========================================================================
	// LOGGING
	// =========================================================================
	void w(String msg) {
		if (!WARN) return;
		Log.w(tag_, msg); // why I do not use w() is left as an exercise
		if (!DEBUG) return;
		try {
			File outputFile = FileIO.fileExternalApplicationStorage(this,
				sRootFolder, fileName());
			FileIO.append(outputFile, msg + "\n", CHARSET_NAME);
			outputFile = FileIO.fileExternalTopLevel(LOG_DIR, LOG_FILE, null);
			FileIO.append(outputFile, msg + "\n", CHARSET_NAME);
		} catch (FileNotFoundException e) {
			Log.w(tag_, e.getMessage());
		} catch (IOException e) {
			Log.w(tag_, e.getMessage());
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
