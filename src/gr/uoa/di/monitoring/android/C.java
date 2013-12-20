package gr.uoa.di.monitoring.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import gr.uoa.di.android.helpers.files.ExternalStorage;
import gr.uoa.di.android.helpers.files.Writer;
import gr.uoa.di.java.helpers.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Application wide Constants
 *
 * @author MrD
 */
public final class C {

	private C() {}

	public static final String APP_PACKAGE_NAME = C.class.getPackage()
		.toString().split(" ")[1];
	public static final int UNDEFINED = -1;
	// used by fragments and monitor activity
	/**
	 * Intent extra key holding the key of the Preference storing latest data
	 * *for display*
	 */
	public final static String DATA_PREFS_KEY_INTENT_KEY = "DATA_PREFS_KEY_INTENT_KEY";
	/** Intent extra key for the string intro to the data */
	public final static String DATA_INTRO_INTENT_KEY = "DATA_INTRO_KEY";
	/** Intent extra key for the intent that starts the update service */
	public final static String START_SERVICE_INTENT_INTENT_KEY = "START_SERVICE_INTENT_INTENT_KEY";
	/**
	 * Intent extra key holding a boolean indicating if the intent comes from
	 * manual update
	 */
	public final static String MANUAL_UPDATE_INTENT_KEY = "MANUAL_UPDATE_INTENT_KEY";
	/**
	 * Intent extra key holding the key of the boolean Preference indicating
	 * that update is in progress
	 */
	public final static String UPDATE_IN_PROGRESS_INTENT_KEY = "UPDATE_IN_PROGRESS_INTENT_KEY";
	// ACTIONS
	private static final CharSequence ACTION_PREFIX = "gr.uoa.di.monitoring.android.intent.action.";
	public static final CharSequence ac_setup_alarm = ACTION_PREFIX
		+ "SETUP_ALARM";
	public static final CharSequence ac_reschedule_alarm = ACTION_PREFIX
		+ "RESCHEDULE_ALARM";
	public static final CharSequence ac_cancel_alarm = ACTION_PREFIX
		+ "CANCEL_ALARM";
	public static final CharSequence ac_monitor = ACTION_PREFIX + "MONITOR";
	public static final CharSequence ac_scan_results_available = ACTION_PREFIX
		+ "SCAN_RESULTS_AVAILABLE";
	public static final CharSequence ac_scan_wifi_disabled = ACTION_PREFIX
		+ "SCAN_WIFI_DISABLED";
	public static final CharSequence ac_scan_wifi_enabled = ACTION_PREFIX
		+ "SCAN_WIFI_ENABLED";
	public static final CharSequence ac_location_data = ACTION_PREFIX
		+ "LOCATION_DATA";
	public static final CharSequence ac_aborting = ACTION_PREFIX + "ABORTING";
	public static final CharSequence ac_monitoring_aborted = ACTION_PREFIX
		+ "MONITORING_TOGGLED";
	public static final boolean DISABLE = false;
	public static final boolean ENABLE = true;
	public static final boolean VERBOSE = false;
	public static final boolean DEBUG = BuildConfig.DEBUG;
	public static final boolean INFO = true;
	public static final boolean WARN = true;
	public static final boolean ERROR = true;
	public static final int NOT_USED = 0;
	private static final String LOG_DIR = "___MYLOGS";
	private static final String LOG_FILE = "LOG.log";
	private static final String LOG_CHARSET_NAME = Utils.UTF8;

	/**
	 * The log file used during debugging
	 *
	 * @return the File object that corresponds to the logfile - in external
	 *         public storage
	 * @throws IOException
	 */
	public static File logFile() throws IOException {
		return ExternalStorage.fileExternalPublicStorage(LOG_DIR, LOG_FILE,
			null);
	}

	/**
	 * Returns an intent which can be used to launch a Settings activity
	 *
	 * @param action
	 *            only tested with Settings.ACTION_XXX_SETTINGS actions
	 * @return an intent used to fire the settings screen
	 * @throws NullPointerException
	 *             if action is null
	 */
	public static Intent launchSettingsIntent(final String action) {
		if (action == null)
			throw new NullPointerException("Action can't be null");
		// see http://stackoverflow.com/a/7024631/281545
		// and http://stackoverflow.com/a/13385550/281545 for
		// FLAG_ACTIVITY_NEW_TASK
		// final ComponentName gps = new ComponentName("com.android.settings",
		// settingsClassName);
		final Intent i = new Intent(action);
		// i.addCategory(Intent.CATEGORY_LAUNCHER);
		// i.setComponent(gps);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // needed ? see ******
		return i;
	}

	/**
	 * Returns an intent which can be used to launch an activity
	 *
	 * @param ctx
	 *            a Context from which the actual package name will be retrieved
	 *            (from docs of
	 *            {@link ComponentName#ComponentName(Context, Class)}
	 * @param cls
	 *            the class of the activity to be launched
	 * @return an intent used to fire the specified activity
	 * @throws NullPointerException
	 *             if any parameter is null
	 */
	// TODO : stop having my app's stack brought to foreground with the
	// DialogActivity
	public static Intent launchActivityIntent(Context ctx,
			final Class<? extends Activity> cls) {
		if (ctx == null || cls == null)
			throw new NullPointerException(
				"Parameters in launchActivityIntent() can't be null");
		final ComponentName toLaunch = new ComponentName(ctx, cls);
		final Intent i = new Intent();
		// i.addCategory(Intent.CATEGORY_LAUNCHER);
		i.setComponent(toLaunch);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // ****** if I use i from non
		// activity a warning is issued "startActivity called from non-Activity
		// context; forcing Intent.FLAG_ACTIVITY_NEW_TASK". So I add this here
		return i;
	}

	/**
	 * Trigger a notification that when clicked launches a dialog activity which
	 * will launch the specified intent on "Yes"
	 *
	 * @param context
	 *            needed to retrieve the NotificationManager System service and
	 *            register the pending intent
	 * @param title
	 *            the title of the notification
	 * @param message
	 *            the message of the notification
	 * @param intent
	 *            the intent for the activity to launch - tested with
	 *            {@link #launchActivityIntent}
	 * @param tag
	 *            the tag of the notification
	 * @param id
	 *            the id of the notification
	 * @param requestCode
	 *            specify this to a different value for your notifications, so
	 *            if launched simultaneously they register different intents
	 */
	public static void triggerDialogNotification(Context context,
			int requestCode, CharSequence title, CharSequence message,
			Intent intent, String tag, int id) {
		PendingIntent pi = PendingIntent.getActivity(context, requestCode,
			intent, PendingIntent.FLAG_UPDATE_CURRENT); // FLAG_UPDATE_CURRENT?
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

	/**
	 * Trigger a notification that when clicked is dismissed
	 *
	 * @param ctx
	 *            needed to retrieve the NotificationManager System service and
	 *            register the pending intent
	 * @param title
	 * @param message
	 * @param tag
	 * @param id
	 */
	public static void triggerNotification(Context ctx, CharSequence title,
			CharSequence message, String tag, int id) {
		final Intent emptyIntent = new Intent(); // can't get rid as it's needed
		// for PendingIntent.getActivity
		PendingIntent pi = PendingIntent.getActivity(ctx, NOT_USED,
			emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT); // FLAG_UPDATE_CURRENT?
		Notification not = new NotificationCompat.Builder(ctx)
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
		NotificationManager notificationManager = (NotificationManager) ctx
			.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(tag, id, not);
	}

	public static void triggerTestNotification(Context ctx, String tag, int id) {
		// see http://stackoverflow.com/questions/20032249/
		// is-setcontentintentpendingintent-required-in-notificationcompat-builder
		final Intent emptyIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
		// emptyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //not needed
		// methinks
		PendingIntent pi = PendingIntent.getActivity(ctx, NOT_USED,
			emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		Notification not = new NotificationCompat.Builder(ctx)
			.setContentTitle("Title").setContentText("Launch wifi settings")
			.setAutoCancel(true) // cancel on click
			.setSmallIcon(R.drawable.ic_launcher).setContentIntent(pi).build();
		NotificationManager notificationManager = (NotificationManager) ctx
			.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(tag, id, not);
	}

	public static void cancelNotification(Context ctx, String tag, int id) {
		NotificationManager notificationManager = (NotificationManager) ctx
			.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(tag, id);
	}

	public static void cancelAllNotifications(Context ctx) {
		NotificationManager notificationManager = (NotificationManager) ctx
			.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();
	}

	// =========================================================================
	// LOGGING
	// =========================================================================
	public static void w(String tag, String msg) {
		if (!WARN) return;
		Log.w(tag, msg);
		if (!DEBUG) return;
		try {
			// File outputFile = FileIO.fileExternalApplicationStorage(this,
			// sRootFolder, fileName());
			// FileIO.append(outputFile, msg + "\n", LOG_CHARSET_NAME);
			File outputFile = logFile();
			Writer.append(outputFile, msg + "\n", LOG_CHARSET_NAME);
		} catch (FileNotFoundException e) {
			Log.w(tag, e.getMessage());
		} catch (IOException e) {
			Log.w(tag, e.getMessage());
		}
	}

	public static void d(String tag, String msg) {
		if (DEBUG) Log.d(tag, msg);
	}

	public static void v(String tag, String msg) {
		if (VERBOSE) Log.v(tag, msg);
	}

	public static void w(String tag, String msg, Throwable t) {
		if (WARN) Log.w(tag, msg, t);
	}
}
