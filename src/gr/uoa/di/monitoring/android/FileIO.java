package gr.uoa.di.monitoring.android;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * WIP !!!! Execute around idiom -
 * http://stackoverflow.com/questions/341971/what-is-the-execute-around-idiom Of
 * course does not work - need methods returning all kinds of things - working
 * on it.
 *
 * @author MrD
 */
public class FileIO {

	private FileIO() {}

	private static final String TAG = FileIO.class.getSimpleName();
	private static final boolean WARN = false;
	private static final int BUFFERED_WRITER_SIZE = 8192;

	/***************************/
	/* execute around methods */
	/*************************/
	/**
	 * Writes to the FileOutputStream and closes it. It uses Java 6 API - not
	 * the android calls for getting the various predefined directories. So
	 * should be used for writing to external storage only
	 *
	 * @param file
	 *            the File instance to write to
	 * @param action
	 *            should be a write action
	 * @param append
	 *            if true it will append to the stream
	 * @throws IOException
	 */
	private static void writeFile(final File file,
			final OutputStreamAction action, final boolean append)
			throws IOException {
		OutputStream stream = new FileOutputStream(file, append);
		try {
			action.useStream(stream);
		} finally {
			close(stream);
		}
	}

	private static byte[] readFile(final String filename,
			final InputStreamAction action) throws IOException {
		InputStream stream = new FileInputStream(filename);
		try {
			return action.useStream(stream);
		} finally {
			close(stream);
		}
	}

	/**
	 * * Writes to a FileOutputStream and closes it. The stream is retrieved via
	 * the openFileOutput() method for the given context. Notice the file won't
	 * be accessible either on DDMS or the phone filesystem in a non rooted
	 * phone. It is saved in internal storage. To get the directory the file is
	 * saved to call ctx.getFilesDir().getPath() on the context passed in (see
	 * stackoverflow.com/questions/4926027/what-file-system-path-is-used-by
	 * -androids-context-openfileoutput)
	 *
	 * @param filename
	 *            the filename of the file to write to
	 * @param action
	 *            should be a write action
	 * @param ctx
	 *            the context openFileOutput() will be called upon
	 * @param mode
	 *            should be one of the modes supported by openFileOutput()
	 * @throws IOException
	 *             if some IO operation failed (including opening the stream but
	 *             not closing it)
	 * @throws IllegalArgumentException
	 *             if the passed in mode is not a valid openFileOutput() mode
	 */
	private static void writeFileInternal(final String filename,
			final OutputStreamAction action, final Context ctx, final int mode)
			throws IOException {
		assertValidMode(mode);
		OutputStream stream = ctx.openFileOutput(filename, mode);
		try {
			action.useStream(stream);
		} finally {
			close(stream);
		}
	}

	private static void assertValidMode(final int mode) {
		@SuppressWarnings("deprecation")
		final int allModes = Context.MODE_WORLD_READABLE
			| Context.MODE_WORLD_WRITEABLE | Context.MODE_APPEND
			| Context.MODE_PRIVATE;
		// w(Integer.toBinaryString(allModes));
		int modeOr = mode | allModes;
		// w(Integer.toBinaryString(modeOr));
		// w(Integer.toBinaryString(mode));
		int modeXor = modeOr ^ allModes;
		if (modeXor != 0) {
			throw new IllegalArgumentException("Invalid mode : " + mode);
		}
	}

	// =========================================================================
	// Read file from external storage - uses the Java API for files
	// =========================================================================
	public static String read(final String filename, final String csName)
			throws IOException {
		final Charset cs = Charset.forName(csName);
		final byte[] ba = readFile(filename, new InputStreamAction());
		return cs.newDecoder().decode(ByteBuffer.wrap(ba)).toString();
	}

	public static byte[] read(final String filename) throws IOException {
		return readFile(filename, new InputStreamAction());
	}

	// =========================================================================
	// Write file in external storage - uses the Java API for files
	// =========================================================================
	public static void append(File file, String data, String charsetName)
			throws FileNotFoundException, IOException {
		_write(file, data, charsetName, true);
	}

	private static void _write(final File file, final String data,
			final String charsetName, final boolean append) throws IOException {
		writeFile(file, new OutputStreamAction() {

			@Override
			public void useStream(OutputStream stream) throws IOException {
				OutputStreamWriter wrt = new OutputStreamWriter(stream,
						charsetName);
				BufferedWriter buffer = new BufferedWriter(wrt,
						BUFFERED_WRITER_SIZE);
				buffer.write(data);
				buffer.flush();
			}
		}, append);
	}

	// =========================================================================
	// application's storage - those methods need a Context
	// =========================================================================
	/**
	 * Append the data to the file named filename written in the application's
	 * internal storage
	 *
	 * @param filename
	 * @param data
	 * @param context
	 * @param charsetName
	 * @param mode
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void append(String filename, String data, Context context,
			String charsetName, int mode) throws FileNotFoundException,
			IOException {
		_write(filename, data, context, mode | Context.MODE_APPEND, charsetName);
	}

	public static void write(String filename, String data, Context context,
			int writeOrAppendMode, String charsetName)
			throws FileNotFoundException, IOException {
		_write(filename, data, context, writeOrAppendMode, charsetName);
	}

	private static void _write(final String filename, final String data,
			final Context ctx, final int mode, final String charsetName)
			throws IOException {
		writeFileInternal(filename, new OutputStreamAction() {

			@Override
			public void useStream(OutputStream stream) throws IOException {
				OutputStreamWriter wrt = new OutputStreamWriter(stream,
						charsetName);
				BufferedWriter buffer = new BufferedWriter(wrt,
						BUFFERED_WRITER_SIZE);
				buffer.write(data);
				buffer.flush();
			}
		}, ctx, mode);
	}

	// =========================================================================
	// Android IO methods wrappers
	// =========================================================================
	public static void deleteFile(String filename, Context context) {
		context.deleteFile(filename);
	}

	public static boolean isExternalStoragePresent() {
		boolean mExternalStorageAvailable = false;
		boolean mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			// Something else is wrong. It may be one of many other states, but
			// all we need
			// to know is we can neither read nor write
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		return (mExternalStorageAvailable) && (mExternalStorageWriteable);
	}

	/**
	 * Given a File which corresponds to a _directory_ path creates this path if
	 * it does not exists. The directory path must lie in EXTERNAL storage
	 *
	 * @param logdir
	 *            the File instance whose path must be created
	 * @return true if the path was created successfully or the path already
	 *         existed and is a directory, false otherwise
	 */
	public static boolean createDirExternal(File logdir) {
		return logdir.mkdirs() || logdir.isDirectory();
	}

	// =========================================================================
	// Helpers
	// =========================================================================
	private static void close(Closeable file) {
		try {
			file.close();
		} catch (IOException e) {
			w("Exception thrown while closing the stream : " + e);
		}
	}

	private static void w(String string) {
		if (WARN) Log.w(TAG, string);
	}
}

class InputStreamAction {

	/**
	 * Based on code by Skeet for reading a file to a string :
	 * http://stackoverflow.com/a/326531/281545
	 *
	 * @param stream
	 * @return
	 * @throws IOException
	 */
	byte[] useStream(final InputStream stream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final int length = 8192;
		byte[] buffer = new byte[length];
		int read;
		while ((read = stream.read(buffer, 0, length)) > 0) {
			baos.write(buffer, 0, read);
		}
		return baos.toByteArray();
	}
}

interface OutputStreamAction {

	void useStream(final OutputStream stream) throws IOException;
}
