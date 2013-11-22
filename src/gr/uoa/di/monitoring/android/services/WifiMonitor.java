package gr.uoa.di.monitoring.android.services;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.activities.MonitorActivity;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.ScanResultsReceiver;
import gr.uoa.di.monitoring.android.receivers.WifiMonitoringReceiver;
import gr.uoa.di.monitoring.model.ParserException;
import gr.uoa.di.monitoring.model.Wifi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static gr.uoa.di.monitoring.android.C.APP_PACKAGE_NAME;
import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.DISABLE;
import static gr.uoa.di.monitoring.android.C.ENABLE;
import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.ac_scan_results_available;
import static gr.uoa.di.monitoring.android.C.ac_scan_wifi_disabled;
import static gr.uoa.di.monitoring.android.C.ac_scan_wifi_enabled;
import static gr.uoa.di.monitoring.android.C.triggerNotification;

public final class WifiMonitor extends Monitor<List<ScanResult>, Wifi> {

	// TODO : timeout wait()
	private static final long WIFI_MONITORING_INTERVAL = MonitoringInterval.ONE
		.getInterval();
	private static final Class<? extends BaseReceiver> PERSONAL_RECEIVER = ScanResultsReceiver.class;
	// Internal preferences keys - persist state even on unloading app classes
	private static final String HAVE_ENABLED_WIFI_PREF_KEY = APP_PACKAGE_NAME
		+ ".HAVE_ENABLED_WIFI_PREF_KEY";
	private static final String HAVE_INITIATED_WIFI_ENABLE_PREF_KEY = APP_PACKAGE_NAME
		+ ".HAVE_INITIATED_WIFI_ENABLE_PREF_KEY";
	// locking
	/** The tag for the wifi lock */
	private static final String SCAN_LOCK = APP_PACKAGE_NAME
		+ ".SCAN_WIFI_LOCK";
	private static final String NOTIFICATION_TAG = WifiMonitor.class
		.getSimpleName() + ".Notification";
	private static final int NOTIFICATION_ID = 9201;
	private static final String WIFI_DATA_KEY = "WIFI_DATA_KEY";
	private static final String SAME_WIFI_COUNT_KEY = "SAME_WIFI_COUNT_KEY";
	private static final String WIFI_INTERVAL_KEY = "WIFI_INTERVAL_KEY";
	private static WifiLock wifiLock;
	private static volatile boolean done = false;
	// convenience fields
	private volatile static WifiManager wm;

	public WifiMonitor() {
		// needed for service instantiation by Android
		super(WifiMonitor.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent in) {
		StringBuilder sb = debugHeader();
		final CharSequence action = in.getAction();
		boolean aborting = false;
		try {
			if (action == null) {
				// monitor command from the alarm manager
				// cancelNotification(this, NOTIFICATION_TAG, NOTIFICATION_ID);
				cleanUp(); // FIXME !
				d("Enabling the receiver");
				receiver(ENABLE);
				d("Check if wireless is enabled");
				if (!wm().isWifiEnabled()) {
					// enable wifi AFTER enabling the receiver
					d("Wifi is not enabled - enabling...");
					final boolean enabled = wm().setWifiEnabled(true);
					if (enabled) {
						setInitiatedWifiEnabling(this, true);
						d("Note to self I should disable again");
						keepNoteToDisableWireless(true);
					} else {
						w("Unable to enable wireless - aborting");
						abort();
						launchNotification(this, Status.CANT_ENABLE_WIFI);
						receiver(DISABLE);
						return;
					}
				}
				// wifi lock AND WAKE LOCK (Gatekeeper)-so must be here!
				getWirelessLock();
				if (!haveEnabledWifi()) {
					// I must initiate the scan here - won't get any
					// ac_scan_wifi_enabled broadcasts
					final boolean startScan = wm().startScan();
					d("Start scan : " + startScan);
					if (!startScan) done = true;
				}
			} else if (ac_scan_wifi_enabled.equals(action)) {
				final boolean startScan = wm().startScan();
				d("Start scan : " + startScan);
				if (!startScan) done = true;
			} else if (ac_scan_results_available.equals(action)) {
				// got my results - got to release the lock BY ALL MEANS
				done = true;
				d("Get the scan results - scan completed !");
			} else if (ac_scan_wifi_disabled.equals(action)) {
				// wifi disabled before I got my results - got to release the
				// lock BY ALL MEANS
				done = true;
				d("Get the scan results (null?) - wireless disabled !");
			} else if (ac_aborting.equals(action)) {
				cleanup();
				aborting = true;
			}
		} catch (WmNotAvailableException e) {
			w(e.getMessage());
			abort();
			launchNotification(this, Status.WIFI_MANAGER_UNAVAILABLE);
			return;
		} finally {
			synchronized (Gatekeeper.WIFI_MONITOR) {
				if (done) {
					if (!aborting) {
						warnScanResults(sb);
						save(wm().getScanResults());
					}
					disableWifiIfIhadItEnableMyself();
					d("Releasing wake lock for the scan");
					releaseWifiLock();
					done = false;
					aborting = false;
				}
			}
			w("Finishing " + action);
		}
	}

	private void receiver(final boolean enable) {
		BaseReceiver.enable(this, enable, PERSONAL_RECEIVER);
	}

	@Override
	void cleanup() {
		zeroCount();
		resetInterval();
		clearLastResults();
		done = true;
		receiver(DISABLE);
	}

	/**
	 * Delegates to Gatekeeper.doWakefulWork(). This way I acquire both a wifi
	 * and a Wake lock - and the Gatekeeper waits on WIFI_MONITOR lock till scan
	 * results are available
	 */
	private void getWirelessLock() {
		d("Acquiring wireless lock for the scan");
		WakefulIntentService.sendWakefulWork(this, Gatekeeper.class);
	}

	private void cleanUp() {
		d("Check for leftovers from previous runs");
		if (haveEnabledWifi()) {
			w("Oops - I have enabled wifi and never disabled - receiver not run"
				+ " - but it is still enabled so disable it first.");
			receiver(DISABLE);
			w("Clean up lock");
			synchronized (Gatekeeper.WIFI_MONITOR) {
				Gatekeeper.WIFI_MONITOR.notify(); // FIXME:findbugs naked notify
			}
			releaseWifiLock();
			disableWifiIfIhadItEnableMyself();
			resetPrefs();
		}
		Gatekeeper.release = false;
	}

	private void releaseWifiLock() {
		synchronized (Gatekeeper.WIFI_MONITOR) {
			if (!getWifiLock(this).isHeld()) {
				w("Lock is not held");
				return;
			}
			Gatekeeper.release = true;
			Gatekeeper.WIFI_MONITOR.notify();
			while (getWifiLock(this).isHeld() && Gatekeeper.release) {
				try {
					d("about to wait");
					Gatekeeper.WIFI_MONITOR.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void disableWifiIfIhadItEnableMyself() {
		d("Check if I have enabled the wifi myself");
		if (haveEnabledWifi()) {
			d("Disabling wifi");
			// FIXME : what if the user has enabled it meanwhile ???
			final boolean disabled = wm().setWifiEnabled(false);
			if (!disabled) {
				w("Failed to disable wireless");
			} else {
				resetPrefs();
			}
		}
	}

	/** Resets internal preferences to default */
	private void resetPrefs() {
		setInitiatedWifiEnabling(this, false); // TODO :should be set in
		// receiver ?
		keepNoteToDisableWireless(false);
	}

	public static class Gatekeeper extends WakefulIntentService {

		static volatile boolean release = false;
		private static final String TAG = Gatekeeper.class.getSimpleName();
		public static final Object WIFI_MONITOR = new Object();

		public Gatekeeper() {
			super(TAG);
		}

		/**
		 * This implementation actually acquires the wifi lock (if not held) and
		 * then waits on {@link Gatekeeper#WIFI_MONITOR} till the scan results
		 * are available whereupon it is notified by WifiMonitor and releases
		 * the lock. FIXME : DEBUG DEBUG DEBUG deadlocks
		 */
		@Override
		protected void doWakefulWork(Intent intent) {
			synchronized (WIFI_MONITOR) {
				if (DEBUG) Log.d(TAG, "Got in sync block !");
				if (!release && !getWifiLock(this).isHeld()) {
					if (DEBUG)
						Log.d(TAG, "Actually acquiring wake lock for the scan");
					getWifiLock(this).acquire();
				}
				// while release is false wait on the monitor - holding the
				// Wakeful CPU wake lock and the wifi lock
				while (!release) {
					// FIXME timeout !
					try {
						WIFI_MONITOR.wait();
					} catch (InterruptedException e) {
						// TODO Handle
						e.printStackTrace();
					}
					if (DEBUG) Log.d(TAG, "Out of wait !");
				}
				if (DEBUG) Log.d(TAG, "Out of while !");
				if (getWifiLock(this).isHeld()) {
					if (DEBUG)
						Log.d(TAG, "Actually releasing wake lock for the scan");
					getWifiLock(this).release();
				}
				Gatekeeper.release = false;
				WIFI_MONITOR.notify();
			}
		}
	}

	private boolean haveEnabledWifi() {
		return getPref(HAVE_ENABLED_WIFI_PREF_KEY, false);
	}

	private void keepNoteToDisableWireless(boolean disableAfterwards) {
		putPref(HAVE_ENABLED_WIFI_PREF_KEY, disableAfterwards);
	}

	public static void keepNoteToDisableWireless(Context ctx,
			boolean disableAfterwards) {
		AccessPreferences.put(ctx, HAVE_ENABLED_WIFI_PREF_KEY,
			disableAfterwards);
	}

	public static boolean didInitiateWifiEnabling(Context ctx) {
		// TODO : default == true ?
		return AccessPreferences.get(ctx, HAVE_INITIATED_WIFI_ENABLE_PREF_KEY,
			true);
	}

	public static void setInitiatedWifiEnabling(Context ctx,
			boolean initiatedWifiEnable) {
		AccessPreferences.put(ctx, HAVE_INITIATED_WIFI_ENABLE_PREF_KEY,
			initiatedWifiEnable);
	}

	private void warnScanResults(StringBuilder sb) {
		List<ScanResult> scanRes = wm().getScanResults();
		if (scanRes == null) {
			// will be null if wireless is disabled
			// TODO : only then ???
			w("Scan results == null - wireless enabled : "
				+ wm().isWifiEnabled());
		} else {
			if (scanRes.isEmpty()) {
				w("No scan results available");
				// TODO : do I have to report it ?
			} else {
				for (ScanResult scanResult : scanRes) {
					sb.append(scanResult.SSID + DEBUG_DELIMITER);
					// sb.append(scanResult.BSSID + DEBUG_DELIMITER);
					// sb.append(scanResult.level + DEBUG_DELIMITER);
				}
				w(sb.toString());
			}
		}
	}

	private static WifiLock getWifiLock(Context ctx) {
		if (wifiLock == null) {
			wifiLock = wm(ctx).createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,
				SCAN_LOCK);
		}
		return wifiLock;
	}

	@Override
	public long getInterval() {
		return WIFI_MONITORING_INTERVAL;
	}

	private WifiManager wm() {
		return wm(this);
	}

	private static WifiManager wm(Context ctx) {
		WifiManager result = wm;
		if (result == null) {
			synchronized (WifiMonitor.class) {
				result = wm;
				if (result == null) {
					result = wm = (WifiManager) ctx
						.getSystemService(Context.WIFI_SERVICE);
					if (result == null) throw new WmNotAvailableException();
				}
			}
		}
		return result;
	}

	private static void launchNotification(Context ctx, Status stat) {
		triggerNotification(ctx, stat.title(ctx), stat.notification(ctx),
			NOTIFICATION_TAG, NOTIFICATION_ID);
	}

	private enum Status {
		WIFI_MANAGER_UNAVAILABLE, CANT_ENABLE_WIFI;

		String title(Context ctx) {
			return ctx.getString(title());
		}

		private int title() {
			switch (this) {
			case CANT_ENABLE_WIFI:
				return R.string.title_cant_enable;
			case WIFI_MANAGER_UNAVAILABLE:
				return R.string.title_no_wifi_detected;
			}
			throw new AssertionError("Forgotten enum constant");
		}

		String notification(Context ctx) {
			return ctx.getString(notification());
		}

		private int notification() {
			switch (this) {
			case WIFI_MANAGER_UNAVAILABLE:
				return R.string.notification_no_wifi_detected;
			case CANT_ENABLE_WIFI:
				return R.string.notification_cant_enable;
			}
			throw new AssertionError("Forgotten enum constant");
		}
	}

	@Override
	void saveResults(List<ScanResult> data) throws FileNotFoundException,
			IOException {
		List<List<byte[]>> listOfListsOfByteArrays = Wifi.WifiFields
			.createListOfListsOfByteArrays(data);
		Wifi.saveData(this, listOfListsOfByteArrays, Wifi.WifiFields.class);
		try {
			final Wifi currentWifi = Wifi.fromBytes(listOfListsOfByteArrays);
			// get the previous data from the preferences store
			String previousData = getPref(WIFI_DATA_KEY, null);
			Wifi previousWifi = Wifi.fromString(previousData);
			// check to see if we need to modify the interval
			updateInterval(currentWifi, previousWifi);
			// store the new data
			putPref(WIFI_DATA_KEY, currentWifi.stringForm());
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					MonitorActivity.onChange(WifiMonitor.this);
				}
			});
		} catch (ParserException e) {
			w("Corrupted data", e);
		}
	}

	public static String dataKey() {
		return WIFI_DATA_KEY;
	}

	@Override
	String getLastResultsPrefKey() {
		return WIFI_DATA_KEY;
	}

	@Override
	String getSameResultsCountPrefKey() {
		return SAME_WIFI_COUNT_KEY;
	}

	@Override
	String getCurrentIntervalPrefKey() {
		return WIFI_INTERVAL_KEY;
	}

	@Override
	void rescheduleAlarms() {
		rescheduleAlarm(WifiMonitoringReceiver.class);
	}
}
