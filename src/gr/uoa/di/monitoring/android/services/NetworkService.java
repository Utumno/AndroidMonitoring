package gr.uoa.di.monitoring.android.services;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.provider.Settings;

import gr.uoa.di.android.helpers.net.Net;
import gr.uoa.di.android.helpers.net.WifiWaker;
import gr.uoa.di.monitoring.android.BuildConfig;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.activities.DialogActivity;
import gr.uoa.di.monitoring.android.files.Persist;
import gr.uoa.di.monitoring.android.services.Monitor.MonitoringInterval;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.cancelNotification;
import static gr.uoa.di.monitoring.android.C.launchSettingsIntent;
import static gr.uoa.di.monitoring.android.C.triggerDialogNotification;

// TODO : HttpClient (for Froyo and Eclair) - see relevant session
public final class NetworkService extends AlarmService {

	private static final long SEND_DATA_INTERVAL = (BuildConfig.DEBUG)
			? MonitoringInterval.FIVE.getInterval()
			: AlarmManager.INTERVAL_HALF_DAY;
	private static final String NOTIFICATION_TAG = NetworkService.class
		.getSimpleName() + ".Notification";
	private static final int NOTIFICATION_ID = 9202;
	private volatile static CountDownLatch latch;
	private static final long LATCH_TIMEOUT = MonitoringInterval.TWO
		.getInterval();
	private static final String WIFI_LOCK_TAG = NetworkService.class.getName()
		+ ".WIFI_LOCK";

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
			// cancel the notification - will reappear if wifi not connected
			cancelNotification(this, NOTIFICATION_TAG, NOTIFICATION_ID);
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
					d("WIFI is Enabled - locking it");
					_wifiLock = wm.createWifiLock(Net.modeHighPerformanse(),
						WIFI_LOCK_TAG);
					_wifiLock.acquire();
					latch = new CountDownLatch(1);
					WifiWaker ww = new WifiWaker(latch);
					failedToConnect = !ww.wakeWifiUpIfEnabled(this,
						LATCH_TIMEOUT); // TODO : !notify user if!Needed
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
							boolean deleted = Persist.deleteInternalFiles(this);
							cancelNotification(this, NOTIFICATION_TAG,
								NOTIFICATION_ID);
							w("Success ! Files deleted : " + deleted);
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
						if (file != null) file.delete();
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
	public long getBaseInterval() {
		return SEND_DATA_INTERVAL;
	}

	/** Always returns the same interval */
	@Override
	public long getCurrentInterval() {
		return getBaseInterval();
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
}
