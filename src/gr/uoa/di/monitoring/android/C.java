package gr.uoa.di.monitoring.android;

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
}
