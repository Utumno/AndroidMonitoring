package gr.uoa.di.monitoring.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Application wide Constants
 *
 * @author MrD
 */
public final class C {

	public static final String APP_PACKAGE_NAME = C.class.getPackage()
			.toString().split(" ")[1];
	public static final int UNDEFINED = -1;
	// ACTIONS
	private static final CharSequence ACTION_PREFIX = "gr.uoa.di.monitoring.android.intent.action.";
	public static final CharSequence ac_setup_alarm = ACTION_PREFIX
		+ "SETUP_ALARM";
	public static final CharSequence ac_cancel_alarm = ACTION_PREFIX
		+ "CANCEL_ALARM";
	public static final CharSequence ac_monitor = ACTION_PREFIX + "MONITOR";
	public static final CharSequence ac_scan_results_available = ACTION_PREFIX
		+ "SCAN_RESULTS_AVAILABLE";
	public static final CharSequence ac_scan_wifi_disabled = ACTION_PREFIX
		+ "SCAN_WIFI_DISABLED";
	public static final CharSequence ac_scan_wifi_enabled = ACTION_PREFIX
		+ "SCAN_WIFI_ENABLED";
	public static final CharSequence ac_location_update = ACTION_PREFIX
		+ "LOCATION_UPDATE";
	public static final CharSequence ac_location_data = ACTION_PREFIX
		+ "LOCATION_DATA";
	public static final CharSequence ac_aborting = ACTION_PREFIX + "ABORTING";
	public static final boolean DISABLE = false;
	public static final boolean ENABLE = true;
	public static final boolean VERBOSE = false;
	public static final boolean INFO = true;
	public static final boolean DEBUG = true;
	public static final boolean WARN = true;
	public static final boolean ERROR = true;
	public static final int NOT_USED = 0;

	// TODO : docs
	public static Intent launchSettings(final String action) {
		// see http://stackoverflow.com/a/7024631/281545
		// and http://stackoverflow.com/a/13385550/281545 for
		// FLAG_ACTIVITY_NEW_TASK
//		final ComponentName gps = new ComponentName("com.android.settings",
//				settingsClassName);
		final Intent i = new Intent(action);
//		i.addCategory(Intent.CATEGORY_LAUNCHER);
//		i.setComponent(gps);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // TODO : needed ?
		return i;
	}

	// TODO : docs & understand what the flags do/are needed etc
	public static Intent launchActivity(Context ctx,
			final Class<? extends Activity> cls) {
		final ComponentName toLaunch = new ComponentName(ctx, cls);
		final Intent i = new Intent();
		i.addCategory(Intent.CATEGORY_LAUNCHER);
		i.setComponent(toLaunch);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return i;
	}
}
