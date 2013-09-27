package gr.uoa.di.monitoring.android.services;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.ScanResultsReceiver;
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
	private static final long WIFI_MONITORING_INTERVAL = 1 * 60 * 1000L;
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
	private static WifiLock wifiLock;
	private static volatile boolean done = false;
	// convenience fields
	private static WifiManager wm;

	public WifiMonitor() {
		// needed for service instantiation by Android
		super(WifiMonitor.class.getSimpleName());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		// be careful to return super
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
	}

	@Override
	protected void doWakefulWork(Intent in) {
		StringBuilder sb = debugHeader();
		final CharSequence action = in.getAction();
		try {
			initServices(this);
			if (action == null) {
				// monitor command from the alarm manager
				cleanUp(); // FIXME !
				d("Enabling the receiver");
				receiver(ENABLE);
				d("Check if wireless is enabled");
				if (!wm.isWifiEnabled()) {
					// enable wifi AFTER enabling the receiver
					d("Wifi is not enabled - enabling...");
					final boolean enabled = wm.setWifiEnabled(true);
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
					final boolean startScan = wm.startScan();
					d("Start scan : " + startScan);
					if (!startScan) done = true;
				}
			} else if (ac_scan_wifi_enabled.equals(action)) {
				final boolean startScan = wm.startScan();
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
			}
		} catch (WmNotAvailableException e) {
			w(e.getMessage());
			abort();
			launchNotification(this, Status.WIFI_MANAGER_UNAVAILABLE);
			return;
		} finally {
			synchronized (Gatekeeper.WIFI_MONITOR) {
				if (done) {
					warnScanResults(sb);
					save(wm.getScanResults());
					disableWifiIfIhadItEnableMyself();
					d("Releasing wake lock for the scan");
					releaseWifiLock();
					done = false;
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
			w("Oops - I have enabled wifi and never disabled - receiver not run");
			w("but it is still enabled so disable it first.");
			receiver(DISABLE);
			w("Clean up lock");
			synchronized (Gatekeeper.WIFI_MONITOR) {
				Gatekeeper.WIFI_MONITOR.notify();
			}
			releaseWifiLock();
			disableWifiIfIhadItEnableMyself();
			resetPrefs();
		}
		Gatekeeper.release = false;
	}

	private void releaseWifiLock() {
		synchronized (Gatekeeper.WIFI_MONITOR) {
			if (!getWifiLock().isHeld()) {
				w("Lock is not held");
				return;
			}
			Gatekeeper.release = true;
			Gatekeeper.WIFI_MONITOR.notify();
			while (getWifiLock().isHeld() && Gatekeeper.release) {
				try {
					w("about to wait");
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
			final boolean disabled = wm.setWifiEnabled(false);
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

		private static volatile boolean release = false;
		private static final String TAG = Gatekeeper.class.getSimpleName();
		public static final Object WIFI_MONITOR = new Object();

		public Gatekeeper() {
			super(TAG);
		}

		/**
		 * This implementation actually acquires the wifi lock (if not held) and
		 * then waits on WifiMonitor.WIFI_MONITOR till the scan results are
		 * available whereupon it is notified by WifiMonitor and releases the
		 * lock. FIXME : DEBUG DEBUG DEBUG deadlocks
		 */
		@Override
		protected void doWakefulWork(Intent intent) {
			synchronized (WIFI_MONITOR) {
				if (DEBUG) Log.d(TAG, "Got in sync block !");
				if (!release && !getWifiLock().isHeld()) {
					if (DEBUG)
						Log.d(TAG, "Actually acquiring wake lock for the scan");
					getWifiLock().acquire();
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
				if (getWifiLock().isHeld()) {
					if (DEBUG)
						Log.d(TAG, "Actually releasing wake lock for the scan");
					getWifiLock().release();
				}
				Gatekeeper.release = false;
				WIFI_MONITOR.notify();
			}
		}
	}

	private boolean haveEnabledWifi() {
		return retrieve(HAVE_ENABLED_WIFI_PREF_KEY, false);
	}

	private void keepNoteToDisableWireless(boolean disableAfterwards) {
		persist(HAVE_ENABLED_WIFI_PREF_KEY, disableAfterwards);
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
		List<ScanResult> scanRes = wm.getScanResults();
		if (scanRes == null) {
			// will be null if wireless is disabled
			// TODO : only then ???
			w("Scan results == null - wireless enabled : " + wm.isWifiEnabled());
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

	private static WifiLock getWifiLock() {
		if (wifiLock == null) {
			wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,
				SCAN_LOCK);
		}
		return wifiLock;
	}

	@Override
	public long getInterval() {
		return WIFI_MONITORING_INTERVAL;
	}

	private static void initServices(Context context)
			throws WmNotAvailableException {
		if (wm == null)
			wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		if (wm == null) throw new WmNotAvailableException();
	}

	private static void launchNotification(Context ctx, Status stat) {
		Intent intent = new Intent();
		triggerNotification(ctx, stat.title(), stat.notification(), intent,
			NOTIFICATION_TAG, NOTIFICATION_ID);
	}

	private enum Status {
		WIFI_MANAGER_UNAVAILABLE, CANT_ENABLE_WIFI;

		private String title() {
			switch (this) {
			case CANT_ENABLE_WIFI:
				return "Can not enable wifi";
			case WIFI_MANAGER_UNAVAILABLE:
				return "No wifi detected";
			}
			throw new IllegalStateException("Forgotten enum constant");
		}

		private String notification() {
			switch (this) {
			case WIFI_MANAGER_UNAVAILABLE:
				return "No wireless services. Monitoring has stopped.";
			case CANT_ENABLE_WIFI:
				return "Can not enable wifi. Monitoring has stopped.";
			}
			throw new IllegalStateException("Forgotten enum constant");
		}
	}

	@Override
	void saveResults(List<ScanResult> data) throws FileNotFoundException,
			IOException {
		List<byte[]> listByteArrays = Wifi.WifiFields
				.createListOfByteArrays(data);
		List<List<byte[]>> listOfListsOfByteArrays = Wifi.WifiFields
				.createListOfListsOfByteArrays(data);
		Wifi.saveData(this, listByteArrays, listOfListsOfByteArrays,
			Wifi.WifiFields.class);
	}
}
