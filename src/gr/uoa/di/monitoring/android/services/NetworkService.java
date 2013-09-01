package gr.uoa.di.monitoring.android.services;

import android.content.Intent;

import gr.uoa.di.monitoring.android.C;

import java.io.BufferedReader;
import java.io.DataOutputStream;
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

import static gr.uoa.di.monitoring.android.C.UTF8;

// TODO: HTTPS
// TODO : HttpClient (for Froyo and Eclair) - see relevant session
public final class NetworkService extends AlarmService {

	private static final CharSequence CRLF = "\r\n";
	private String urlPost = "http://192.168.1.66:8080/DataCollectionServlet/datacollection";
	private boolean isServerGzip;
	private String charset = UTF8;

	public NetworkService() {
		super(NetworkService.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		// http://stackoverflow.com/q/14630255/281545
		HttpURLConnection connection = null;
		GZIPOutputStream gz = null;
		DataOutputStream outputStream = null;
		OutputStream serverOutputStream = null;
		try {
			// FIXME : check for connectivity (network unreachable)
			connection = connection();
			w("connection");
			final OutputStream connOutStream = connection.getOutputStream();
			w("No route to host"); // FIXME :
									// http://stackoverflow.com/questions/18558047/no-route-to-host-on-device-on-getoutputstream
			if (isServerGzip) {
				connection.setRequestProperty("Content-Encoding", "gzip");
				gz = new GZIPOutputStream(connOutStream);
				serverOutputStream = gz;
			} else {
				outputStream = new DataOutputStream(connOutStream); // FIXME ??
				serverOutputStream = outputStream;
			}
			File file;
			try {
				file = C.logFile();
				// Just generate some unique random value.
				// TODO : Part boundaries should not occur in any of the data
				// http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.2
				final String boundary = Long.toHexString(System
						.currentTimeMillis());
				flushMultiPartData(file, serverOutputStream, boundary,
					connection);
			} catch (IOException e) {
				w("error opening logfile or ...");
			}
			int serverResponseCode = connection.getResponseCode();
			w("serverResponseCode" + serverResponseCode);
		} catch (IOException e) {
			w("lol : "); // network unreachable, No route to host
			e.printStackTrace();
		} finally {
			if (connection != null) connection.disconnect();
		}
	}

	@Override
	public long getInterval() {
		// TODO field (half day constant)
		return 12 * 3600 * 1000;
	}

	private HttpURLConnection connection() throws MalformedURLException,
			IOException {
		w("HERE");
		HttpURLConnection connection = (HttpURLConnection) new URL(urlPost)
				.openConnection();
		w("HERE2");
		// connection.setDoInput(true);
		connection.setDoOutput(true); // triggers POST
		// connection.setUseCaches(false); // needed ?
		// TODO : http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
		connection.setRequestProperty("Connection", "Keep-Alive");
		connection.setRequestProperty("User-Agent",
			"Android Multipart HTTP Client 1.1"); // needed ?
		return connection;
	}

	// =========================================================================
	// Multipart
	// =========================================================================
	private void flushMultiPartData(File file, OutputStream serverOutputStream,
			String boundary, URLConnection connection) throws IOException {
		connection.setRequestProperty("Content-Type",
			"multipart/form-data; boundary=" + boundary);
		connection.setRequestProperty("accept-encoding", "gzip,deflate");
		// connection.setRequestProperty("accept", "text/html,application/xhtml"
		// + "+xml,application/xml;q=0.9,*/*;q=0.8");
		// TODO : chunks
		PrintWriter writer = null;
		try {
			// http://stackoverflow.com/a/2793153/281545
			OutputStream output = connection.getOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(output, charset),
					true); // true = autoFlush, important!
			// appendParameter(boundary, writer, param);
			// appendTextFile(boundary, writer, textFile);
			appendBinary(file, boundary, writer, output);
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
				+ URLConnection.guessContentTypeFromName(file.getName()))
				.append(CRLF);
		writer.append("Content-Transfer-Encoding: binary").append(CRLF);
		writer.append(CRLF).flush();
		InputStream input = null;
		try {
			input = new FileInputStream(file);
			byte[] buffer = new byte[1024];
			for (int length = 0; (length = input.read(buffer)) > 0;) {
				output.write(buffer, 0, length);
			}
			output.flush(); // Important! Output cannot be closed. Close of
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
		writer.append("Content-Type: text/plain; charset=" + charset).append(
			CRLF);
		writer.append(CRLF).flush();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(textFile), charset));
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
		writer.append("Content-Type: text/plain; charset=" + charset).append(
			CRLF);
		writer.append(CRLF);
		writer.append(param).append(CRLF).flush();
	}
}
