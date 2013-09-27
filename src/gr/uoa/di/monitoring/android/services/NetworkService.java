package gr.uoa.di.monitoring.android.services;

import android.content.Intent;

import gr.uoa.di.android.helpers.FileIO;
import gr.uoa.di.android.helpers.Net;
import gr.uoa.di.java.helpers.Utils;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.persist.FileStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPOutputStream;

import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.launchSettingsIntent;
import static gr.uoa.di.monitoring.android.C.triggerNotification;

// TODO: HTTPS
// TODO : HttpClient (for Froyo and Eclair) - see relevant session
public final class NetworkService extends AlarmService {

	// TODO field (half day constant)
	// TODO every third minute ???
	private static final long SEND_DATA_INTERVAL = 12 * 60 * 1000L;/* 60 * */
	private static final CharSequence CRLF = "\r\n";
	private static final String NOTIFICATION_TAG = NetworkService.class
			.getSimpleName() + ".Notification";
	private static final int NOTIFICATION_ID = 9202;
	private boolean isServerGzip /* = true */;
	private String charsetForMultipartHeaders = Utils.UTF8;

	private String urlPost() {
		final String defaultServletUrl = getResources().getText(
			R.string.str_pref_server_url_default).toString();
		return retrieve(getResources().getText(R.string.server_url_pref_key)
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
				if (!FileStore.availableData(this)) return; // no need to
				// lock here as data can only be added (except if hacked)
				// so if data exists it won't disappear till I lock
			} catch (IOException e) {
				w("Could not access internal directory", e);
				// TODO : abort ?
				return;
			}
			// TODO : maybe enable mobile ?
			if (!Net.hasWifiConnection(this)) {
				Intent intentius = launchSettingsIntent(WIFI_SERVICE);
				// TODO : setting : send data
				triggerNotification(this, "Enable wifi",
					"There is data available to be sent. Enable wifi and try to"
						+ " send them manually from the Settings screen",
					intentius, NOTIFICATION_TAG, NOTIFICATION_ID);
				return;
			}
			// Just generate some unique random value.
			// TODO : Part boundaries should not occur in any of the data
			// http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.2
			final String boundary = Long
					.toHexString(System.currentTimeMillis());
			try {
				connection = connection(true, boundary);
				w("con : " + connection + " @ " + time());
			} catch (IOException e) {
				// openConnection() might throw but it is unlikely
				w("IOException opening connection", e);
				return;
			}
			File file = null;
			synchronized (FileStore.FILE_STORE_LOCK) {
				try {
					try {
						file = FileStore.file(this);
					} catch (IOException e) {
						w("IOException accessing files in internal directory",
							e);
						// TODO : abort ?
						return;
					}
					// outputStream = new DataOutputStream(
					// con.getOutputStream()); // TODO : DataOutputStream ??
					// after connection.getOutputStream() never call
					// setRequestProperty() : java.lang.IllegalStateException:
					// Cannot set request property after connection is made
					serverOutputStream = connection.getOutputStream(); // now
					// this is really where the connection might seriously throw
					try {
						flushMultiPartData(file, serverOutputStream, boundary);
					} catch (IOException e) {
						w("IOException in flushMultiPartData : ", e);
						return;
					}
					final int serverResponseCode = connection.getResponseCode();
					if (serverResponseCode == HttpURLConnection.HTTP_OK) {
						for (File f : FileStore.internalFiles(this)) {
							FileIO.delete(f);
						}
					} else {
						w("Error in server communication - ServerResponseCode : "
							+ serverResponseCode);
					}
				} catch (IOException e) {
					w("IOException sending data", e);
					// Network unreachable : not connected
					// No route to host : probably on an encrypted network see:
					// http://stackoverflow.com/questions/18558047 (TODO)
					// Connection timed out : Server DOWN
				} finally {
					// TODO does disconnect() close the serverOutputStream ?
					// actually it is closed in flushMultipartData
					// (writer.close)
					if (file != null) FileIO.delete(file);
					if (connection != null) connection.disconnect();
				}
			}
		} else if (ac_aborting.equals(action)) {
			// pass
		}
	}

	@Override
	public long getInterval() {
		return SEND_DATA_INTERVAL;
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
	// Multipart
	// =========================================================================
	private void flushMultiPartData(File file, OutputStream serverOutputStream,
			String boundary) throws FileNotFoundException, IOException {
		// connection.setRequestProperty("accept", "text/html,application/xhtml"
		// + "+xml,application/xml;q=0.9,*/*;q=0.8");
		// TODO : chunks
		PrintWriter writer = null;
		try {
			// http://stackoverflow.com/a/2793153/281545
			// true = autoFlush, important!
			writer = new PrintWriter(new OutputStreamWriter(serverOutputStream,
					charsetForMultipartHeaders), true);
			appendBinary(file, boundary, writer, serverOutputStream);
			// End of multipart/form-data.
			writer.append("--" + boundary + "--").append(CRLF);
		} finally {
			if (writer != null) writer.close();
		}
	}

	private void appendBinary(File file, String boundary, PrintWriter writer,
			OutputStream output) throws FileNotFoundException, IOException {
		// Send binary file.
		writer.append("--" + boundary).append(CRLF);
		writer.append(
			"Content-Disposition: form-data; name=\"binaryFile\"; filename=\""
				+ file.getName() + "\"").append(CRLF);
		writer.append(
			"Content-Type: "
				+ ((isServerGzip) ? "application/gzip" : URLConnection
						.guessContentTypeFromName(file.getName())))
				.append(CRLF);
		writer.append("Content-Transfer-Encoding: binary").append(CRLF);
		writer.append(CRLF).flush();
		InputStream input = null;
		OutputStream output2 = output;
		if (isServerGzip) {
			output2 = new GZIPOutputStream(output);
		}
		try {
			input = new FileInputStream(file);
			byte[] buffer = new byte[1024];
			for (int length = 0; (length = input.read(buffer)) > 0;) {
				output2.write(buffer, 0, length);
			}
			if (isServerGzip) {
				// Write the compressed parts,
				// http://stackoverflow.com/a/18858420/281545
				((GZIPOutputStream) output2).finish();
			}
			output2.flush(); // Important! Output cannot be closed. Close of
			// writer will close output as well.
		} finally {
			if (input != null) try {
				input.close();
			} catch (IOException logOrIgnore) {
				w(logOrIgnore.getMessage());
			}
		}
		writer.append(CRLF).flush(); // CRLF is important! It indicates end of
		// binary boundary.
	}

	@SuppressWarnings("unused")
	private void appendTextFile(String boundary, PrintWriter writer,
			File textFile) throws UnsupportedEncodingException,
			FileNotFoundException, IOException {
		// Send text file.
		writer.append("--" + boundary).append(CRLF);
		writer.append(
			"Content-Disposition: form-data; name=\"textFile\"; filename=\""
				+ textFile.getName() + "\"").append(CRLF);
		writer.append(
			"Content-Type: text/plain; charset=" + charsetForMultipartHeaders)
				.append(CRLF);
		writer.append(CRLF).flush();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(textFile), charsetForMultipartHeaders));
			for (String line; (line = reader.readLine()) != null;) {
				writer.append(line).append(CRLF);
			}
		} finally {
			if (reader != null) try {
				reader.close();
			} catch (IOException logOrIgnore) {
				w(logOrIgnore.getMessage());
			}
		}
		writer.flush();
	}

	@SuppressWarnings("unused")
	private void appendParameter(String boundary, PrintWriter writer,
			CharSequence param) {
		// Send normal param.
		writer.append("--" + boundary).append(CRLF);
		writer.append("Content-Disposition: form-data; name=\"param\"").append(
			CRLF);
		writer.append(
			"Content-Type: text/plain; charset=" + charsetForMultipartHeaders)
				.append(CRLF);
		writer.append(CRLF);
		writer.append(param).append(CRLF).flush();
	}
}
