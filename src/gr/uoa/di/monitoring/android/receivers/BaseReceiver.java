package gr.uoa.di.monitoring.android.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import gr.uoa.di.monitoring.android.FileIO;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.VERBOSE;
import static gr.uoa.di.monitoring.android.C.WARN;

public abstract class BaseReceiver extends BroadcastReceiver {

	protected final static String TAG = BaseReceiver.class.getSimpleName();
	private static final String CHARSET_NAME = "ASCII";
	private final String tag_ = this.getClass().getSimpleName();

	/**
	 * Disables/enables the receiver. Disabling/enabling will persist the
	 * reboot. It is simply a static method **it must be given a Receiver** to
	 * enable. I tried to make it to an instance method using CurClassNameGetter
	 * but did not work out well (no elegant way to call it for all the
	 * receivers I want to enable). I have found no way to disable the Boot
	 * receiver once its job is done.
	 *
	 * @param context
	 *            the context - this method must be called inside onReceive (to
	 *            receive a context)
	 * @param enable
	 *            if true enable
	 * @param receiver
	 *            the receiver to enable
	 */
	public static void enable(Context context, boolean enable,
			Class<? extends BaseReceiver> receiver) {
		PackageManager pacman = context.getPackageManager();
		final ComponentName componentName = new ComponentName(context, receiver);
		if (VERBOSE) Log.v(TAG, componentName.toString());
		final int state = (enable) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
				: PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
		pacman.setComponentEnabledSetting(componentName, state,
			PackageManager.DONT_KILL_APP);
		if (VERBOSE)
			Log.v(TAG,
				"pacman : " + pacman.getComponentEnabledSetting(componentName));
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
			}
		} catch (FileNotFoundException e) {
			Log.w(tag_, e + "");
		} catch (IOException e) {
			Log.w(tag_, e + "");
		}
	}

	void d(String msg) {
		if (DEBUG) Log.d(tag_, msg);
	}
}
