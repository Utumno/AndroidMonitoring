package gr.uoa.di.monitoring.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

/**
 * Application wide Constants
 *
 * @author MrD
 */
public final class C {

	// Standard charsets' NAMES
	// Seven-bit ASCII, a.k.a. ISO646-US, a.k.a. the Basic Latin block of the
	// Unicode character set
	public static final String ASCII = "US-ASCII";
	// ISO Latin Alphabet No. 1, a.k.a. ISO-LATIN-1
	public static final String ISO8859 = "ISO-8859-1";
	// Eight-bit UCS Transformation Format
	public static final String UTF8 = "UTF-8";
	// Sixteen-bit UCS Transformation Format, big-endian byte order
	public static final String UTF16BE = "UTF-16BE";
	// Sixteen-bit UCS Transformation Format, little-endian byte order
	public static final String UTF16LE = "UTF-16LE";
	// Sixteen-bit UCS Transformation Format, byte order identified by an
	// optional byte-order mark
	public static final String UTF16 = "UTF-16";
	// / Standard charsets' NAMES
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
	public static final String LOG_DIR = "";
	public static final String LOG_FILE = "LOG.log";

	// TODO : docs/test
	public static Intent launchSettingsIntent(final String action) {
		// see http://stackoverflow.com/a/7024631/281545
		// and http://stackoverflow.com/a/13385550/281545 for
		// FLAG_ACTIVITY_NEW_TASK
		// final ComponentName gps = new ComponentName("com.android.settings",
		// settingsClassName);
		final Intent i = new Intent(action);
		// i.addCategory(Intent.CATEGORY_LAUNCHER);
		// i.setComponent(gps);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // TODO : needed ?
		return i;
	}

	// TODO : docs/test & understand what the flags do/are needed etc
	// especially to stop having my app's task brought to foreground with the
	// DialogActivity
	public static Intent launchActivityIntent(Context ctx,
			final Class<? extends Activity> cls) {
		final ComponentName toLaunch = new ComponentName(ctx, cls);
		final Intent i = new Intent();
		// i.addCategory(Intent.CATEGORY_LAUNCHER);
		i.setComponent(toLaunch);
		// i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return i;
	}

	// TODO : docs/test & understand what the flags do/are needed etc
	public static void triggerNotification(Context context, CharSequence title,
			CharSequence message, Intent intent, String tag, int id) {
		PendingIntent pi = PendingIntent.getActivity(context, NOT_USED, intent,
			PendingIntent.FLAG_UPDATE_CURRENT); // PendingIntent.FLAG_UPDATE_CURRENT?
		Notification not = new NotificationCompat.Builder(context)
				.setContentTitle(title).setContentText(message)
				.setContentIntent(pi)
				// this ^^^ must be set in my API (2.3.7) version otherwise I
				// get IllegalArgumentException: contentIntent required
				.setAutoCancel(true) // cancel on click
				.setSmallIcon(R.drawable.ic_launcher) // TODO : icons
				// had android.R.drawable.stat_notify_error
				// .setDefaults(Notification.DEFAULT_ALL) // needs permissions
				// .setOngoing(ONGOING) // not needed apparently
				// .setLargeIcon(aBitmap)
				.build();
		NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(tag, id, not);
	}
}
