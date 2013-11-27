package gr.uoa.di.monitoring.android.services;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.provider.Settings;

import gr.uoa.di.android.helpers.FileIO;
import gr.uoa.di.android.helpers.Net;
import gr.uoa.di.monitoring.android.BuildConfig;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.activities.DialogActivity;
import gr.uoa.di.monitoring.android.persist.Persist;
import gr.uoa.di.monitoring.android.services.Monitor.MonitoringInterval;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.cancelNotification;
import static gr.uoa.di.monitoring.android.C.launchSettingsIntent;
import static gr.uoa.di.monitoring.android.C.triggerDialogNotification;

// TODO : HttpClient (for Froyo and Eclair) - see relevant session
public final class NetworkService extends AlarmService {

	private static final long SEND_DATA_INTERVAL = (BuildConfig.DEBUG) ? MonitoringInterval.FIVE
		.getInterval() : AlarmManager.INTERVAL_HALF_DAY;
	private static final String NOTIFICATION_TAG = NetworkService.class
		.getSimpleName() + ".Notification";
	private static final int NOTIFICATION_ID = 9202;
	// wake the radio up to send the data
	private BroadcastReceiver mConnectionReceiver;
	private volatile static CountDownLatch latch;
	private static final long LATCH_TIMEOUT = 180000;

	private String urlPost() {
		final String defaultServletUrl = getResources().getText(
			R.string.str_pref_server_url_default).toString();
		return getPref(getResources().getText(R.string.server_url_pref_key)
			.toString(), defaultServletUrl);
	}

	public NetworkService() {
		super(NetworkService.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		final CharSequence action = intent.getAction();
		if (action == null) {
			// "monitor" command from the alarm manager
			// http://stackoverflow.com/q/14630255/281545
			HttpURLConnection connection = null;
			OutputStream serverOutputStream = null;
			try {
				if (!Persist.availableData(this)) return; // no need to
				// lock here as data can only be added (except if hacked)
				// so if data exists it won't disappear till I lock
			} catch (IOException e) {
				internalDirException("Could not access internal directory", e);
				return;
			}
			WifiLock _wifiLock = null;
			try {
				// TODO : maybe enable mobile ?
				WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
				boolean failedToConnect = true;
				if (wm != null && wm.isWifiEnabled()) {
					d("WIFI is Enabled in your device");
					_wifiLock = wm.createWifiLock(
					/* WifiManager.WIFI_MODE_FULL_HIGH_PERF */0x3, this
						.getClass().getName() + ".WIFI_LOCK");
					_wifiLock.acquire();
					failedToConnect = !wakeWifiUp(); // TODO : !notify user
														// if!Need
				}
				if (failedToConnect) {
					Intent in = DialogActivity.launchDialogActivityIntent(this,
						getString(R.string.title_enable_wifi),
						getString(R.string.dialog_enable_wifi),
						launchSettingsIntent(Settings.ACTION_WIFI_SETTINGS));
					triggerDialogNotification(this, NOTIFICATION_ID,
						getString(R.string.title_enable_wifi),
						getString(R.string.notification_enable_wifi), in,
						NOTIFICATION_TAG, NOTIFICATION_ID);
					w("No connection !");
					return;
				}
				// Just generate some unique random value.
				// TODO : Part boundaries should not occur in any of the data
				// http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.2
				final String boundary = Long.toHexString(System
					.currentTimeMillis());
				try {
					connection = connection(true, boundary);
					w("con : " + connection + " @ " + time());
				} catch (IOException e) {
					// openConnection() might throw but it is unlikely
					w("IOException opening connection", e);
					return;
				}
				try {
					// outputStream = new DataOutputStream(
					// con.getOutputStream()); // TODO : DataOutputStream ??
					// after connection.getOutputStream() never call
					// setRequestProperty() : java.lang.IllegalStateException:
					// Cannot set request property after connection is made
					serverOutputStream = connection.getOutputStream(); // now
					// this is really where the connection might seriously throw
				} catch (IOException e) {
					// Network unreachable : not connected
					// No route to host : probably on an encrypted network see:
					// http://stackoverflow.com/questions/18558047 (TODO)
					// Connection timed out : Server DOWN
					connection.disconnect();
					w("IOException getting connection stream", e);
					return;
				}
				synchronized (Persist.FILE_STORE_LOCK) {
					d("lock " + Persist.FILE_STORE_LOCK);
					try {
						if (!Persist.availableData(this)) return; // in case the
						// user tried to send the data anyway
					} catch (IOException e) {
						internalDirException(
							"Could not access internal directory", e);
						return;
					}
					File file = null;
					try {
						try {
							file = Persist.file(this);
						} catch (IOException e) {
							internalDirException(
								"IOException accessing files in "
									+ "internal directory", e);
							return;
						}
						try {
							Net.flushMultiPartData(file, serverOutputStream,
								boundary, false);
						} catch (IOException e) {
							w("IOException in flushMultiPartData : ", e);
							return;
						}
						final int serverResponseCode = connection
							.getResponseCode();
						if (serverResponseCode == HttpURLConnection.HTTP_OK) {
							for (File f : Persist.internalFiles(this)) {
								FileIO.delete(f); // check the return value
							}
							cancelNotification(this, NOTIFICATION_TAG,
								NOTIFICATION_ID);
							w("Success !");
						} else {
							w("Error in server communication - "
								+ "ServerResponseCode : " + serverResponseCode);
						}
					} catch (IOException e) {
						w("IOException sending data " + e.getMessage());
					} finally {
						// TODO does disconnect() close the serverOutputStream ?
						// actually it is closed in flushMultipartData
						// (writer.close)
						if (file != null) FileIO.delete(file);
						connection.disconnect();
					}
				}
			} finally {
				if (_wifiLock != null) {
					_wifiLock.release();
				}
			}
		} else if (ac_aborting.equals(action)) {
			// pass
		}
	}

	/**
	 * @param msg
	 * @param e
	 */
	private void internalDirException(final String msg, IOException e) {
		w(msg, e);
		// TODO : abort ?
	}

	@Override
	public long getInterval() {
		return SEND_DATA_INTERVAL;
	}

	/** Always returns the same interval */
	@Override
	public long getCurrentInterval() {
		return getInterval();
	}

	private HttpURLConnection connection(boolean isMultiPart, String boundary)
			throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(urlPost())
			.openConnection();
		// connection.setDoInput(true);
		connection.setDoOutput(true); // triggers POST
		// connection.setUseCaches(false); // needed ?
		// TODO : http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
		connection.setRequestProperty("Connection", "Keep-Alive");
		// // connection.setRequestProperty("Content-Encoding",
		// // "gzip"); // not needed for multipart part !
		connection.setRequestProperty("User-Agent",
			"Android Multipart HTTP Client 1.1"); // needed ?
		// MADE NO DIFFERENCE IN "no route to host" :
		// "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.9.2.3)
		// Gecko/20100401"
		if (isMultiPart) {
			if (boundary == null || "".equals(boundary.trim()))
				throw new IllegalArgumentException("Boundary can't be "
					+ ((boundary == null) ? "null" : "empty"));
			connection.setRequestProperty("Content-Type",
				"multipart/form-data; boundary=" + boundary);
		}
		return connection;
	}

	// =========================================================================
	// Wake the radio up - move to helpers once I can specify the action *here*
	// =========================================================================
	private static void downTheLatch() {
		latch.countDown();
	}

	private boolean wakeWifiUp() {
		WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		final int wifiState = wm.getWifiState();
		if (!wm.isWifiEnabled() || wifiState == WifiManager.WIFI_STATE_DISABLED
			|| wifiState == WifiManager.WIFI_STATE_DISABLING) {
			// Make sure the Wi-Fi is enabled, required for some devices when
			// enable WiFi does not occur immediately
			// ******************* do not enable if not enabled ****************
			// ******************* d("!_wifiManager.isWifiEnabled()");
			// ******************* wm.setWifiEnabled(true);
			// ******************* return false instead ****************
			return false;
		}
		if (!Net.isWifiConnectedOrConnecting(this)) {
			d("Wifi is NOT Connected Or Connecting - "
				+ "wake it up and wait till is up");
			// Do not wait for the OS to initiate a reconnect to a Wi-Fi router
			wm.pingSupplicant();
			// if (wifiState == WifiManager.WIFI_STATE_ENABLED) { // DONT !!!!!!
			// try {
			// // Brute force methods required for some devices
			// _wifiManager.setWifiEnabled(false);
			// _wifiManager.setWifiEnabled(true);
			// } catch (SecurityException e) {
			// // Catching exception which should not occur on most
			// // devices. OS bug details at :
			// // https://code.google.com/p/android/issues/detail?id=22036
			// }
			// }
			// ////////////////// commented those out :
			// _wifiManager.disconnect();
			// _wifiManager.startScan();
			wm.reassociate();
			wm.reconnect();
			try {
				mConnectionReceiver = new WifiConnectionMonitor();
				startMonitoringConnection();
				latch = new CountDownLatch(1);
				w("I wait");
				final boolean await = latch.await(LATCH_TIMEOUT,
					TimeUnit.MILLISECONDS);
				w("Woke up");
				// await should be false if latch timed out
				return await;
			} catch (InterruptedException e) {
				w("Interrupted while waiting for connection", e);
				return false;
			} finally {
				stopMonitoringConnection();
			}
		}
		return true;
	}

	private final class WifiConnectionMonitor extends BroadcastReceiver {

		// http://stackoverflow.com/a/4504110/281545
		// http://stackoverflow.com/questions/5276032/
		@Override
		public void onReceive(Context context, Intent in) {
			String action = in.getAction();
			if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
				NetworkInfo networkInfo = in
					.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				d("NETWORK_STATE_CHANGED_ACTION :" + networkInfo);
				if (networkInfo.isConnected()) {
					d("Wifi is connected!");
					NetworkService.downTheLatch(); // *here*
				}
			} else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
				NetworkInfo networkInfo = in
					.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
				// boolean noConnectivity = intent.getBooleanExtra(
				// ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
				if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI
				/* && !networkInfo.isConnected() */) {
					d("CONNECTIVITY_ACTION - " + networkInfo);
				}
			} else if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION
				.equals(action)) {
				d("SUPPLICANT_CONNECTION_CHANGE_ACTION - "
					+ "EXTRA_SUPPLICANT_CONNECTED :"
					+ in.getBooleanExtra(
						WifiManager.EXTRA_SUPPLICANT_CONNECTED, false));
			} else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION
				.equals(action)) {
				d("SUPPLICANT_STATE_CHANGED_ACTION - EXTRA_NEW_STATE: "
					+ in.getParcelableExtra(WifiManager.EXTRA_NEW_STATE)
					+ " - EXTRA_SUPPLICANT_ERROR: "
					+ in.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1));
			} else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
				d("WIFI_STATE_CHANGED_ACTION - EXTRA_WIFI_STATE: "
					+ wifiState(in, WifiManager.EXTRA_WIFI_STATE)
					+ " - EXTRA_PREVIOUS_WIFI_STATE: "
					+ wifiState(in, WifiManager.EXTRA_PREVIOUS_WIFI_STATE));
			}
		}

		/**
		 * Returns a string describing the state of the Wifi. Meant to be used
		 * with an intent received by a BroadcastReceiver whose action is
		 * WifiManager.WIFI_STATE_CHANGED_ACTION
		 *
		 * @param in
		 *            an intent received by a BR whose action is
		 *            WifiManager.WIFI_STATE_CHANGED_ACTION
		 * @param key
		 *            must be either WifiManager.EXTRA_WIFI_STATE OR
		 *            WifiManager.EXTRA_PREVIOUS_WIFI_STATE. NOTHING else
		 * @return a string for the state instead of an int
		 */
		private String wifiState(Intent in, String key) {
			switch (in.getIntExtra(key, -1)) {
			case WifiManager.WIFI_STATE_DISABLED:
				return "WIFI_STATE_DISABLED";
			case WifiManager.WIFI_STATE_DISABLING:
				return "WIFI_STATE_DISABLING";
			case WifiManager.WIFI_STATE_ENABLED:
				return "WIFI_STATE_ENABLED";
			case WifiManager.WIFI_STATE_ENABLING:
				return "WIFI_STATE_ENABLING";
			case WifiManager.WIFI_STATE_UNKNOWN:
				return "WIFI_STATE_UNKNOWN";
			}
			throw new RuntimeException("Forgot Wifi State");
		}
	}

	private synchronized void startMonitoringConnection() {
		IntentFilter aFilter = new IntentFilter(
			ConnectivityManager.CONNECTIVITY_ACTION);
		// aFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
		aFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		// aFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
		// aFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION); // !!!!
		aFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		aFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		aFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(mConnectionReceiver, aFilter);
	}

	private synchronized void stopMonitoringConnection() {
		unregisterReceiver(mConnectionReceiver);
	}
}
