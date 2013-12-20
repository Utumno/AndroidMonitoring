package gr.uoa.di.monitoring.android.activities;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.util.Log;

import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.services.Monitor;

import java.util.List;

import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.ac_monitoring_aborted;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public final class SettingsActivity extends PreferenceActivity {

	/**
	 * Determines whether to always show the simplified settings UI, where
	 * settings are presented in a single list. When false, settings are shown
	 * as a master/detail two-pane view on tablets. When true, a single pane is
	 * shown on tablets.
	 */
	private static final boolean ALWAYS_SIMPLE_PREFS = false;
	private static CharSequence master_enable;
	private static final String TAG = SettingsActivity.class.getSimpleName();
	private OnPreferenceChangeListener listener;
	private static Preference master_pref;
	// debug preferences
	private static final int PREF_RESOURCE = R.xml.pref_general;
	private static int PREF_RESOURCE_SETTINGS = (DEBUG)
			? R.xml.pref_data_sync_debug : R.xml.pref_data_sync;
	// dialog (enabling disabling)
	private static ProgressDialog dialog;
	// Receiver
	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (ac_monitoring_aborted.equals(intent.getAction())) {
				reloadPrefs();
			}
		}
	};

	public static void cancelDialog() {
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@SuppressWarnings("deprecation")
	private void reloadPrefs() {
		// on a multipane environment setupSimplePreferencesScreen() won't be
		// called and onBuildHeaders won't be called and this will result in an
		// exception - but happily setupSimplePreferencesScreen() will be called
		// on pre HONEYCOMB while after HONEYCOMB we have recreate()
		if (!isSimplePreferences(this)) {
			recreate();
			// onBuildHeaders(target);
		} else {
			setPreferenceScreen(null);
			setupSimplePreferencesScreen();
		}
		// onContentChanged(); // not needed
		master_pref.setOnPreferenceChangeListener(listener);
	}

	@Override
	protected void onStart() {
		super.onStart();
		registerReceiver(receiver,
			new IntentFilter(ac_monitoring_aborted.toString()));
	}

	@Override
	protected void onStop() {
		// may not be called in Froyo
		unregisterReceiver(receiver);
		super.onStop();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		master_enable = getResources().getText(
			R.string.enable_monitoring_master_pref_key);
		listener = new ToggleMonitoringListener();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		// TODO : why onPostCreate
		super.onPostCreate(savedInstanceState);
		setupSimplePreferencesScreen();
		master_pref.setOnPreferenceChangeListener(listener);
		// findPreference will return null if setupSimplePreferencesScreen
		// hasn't run
	}

	@Override
	protected void onResume() {
		super.onResume();
		// sBindPreferenceSummaryToValueListener.onPreferenceChange(
		// master_pref,
		// PreferenceManager.getDefaultSharedPreferences(
		// master_pref.getContext())
		// .getBoolean(master_pref.getKey(), true));
		// boolean enable = AccessPreferences.retrieve(this,
		// master_enable.toString(), null);
		// Log.w(TAG, "onResume - enable : " + enable);
		// boolean checked = ((CheckBoxPreference) master_pref).isChecked();
		// Log.w(TAG, "onResume - master pref : " + checked);
		// ((CheckBoxPreference) master_pref).setChecked(enable);
		// reloadPrefs();
		// bindCheckBoxPreferenceSummaryToValue(master_pref); // LOL only 1 list
	}

	@Override
	protected void onPause() {
		super.onPause();
		// master_pref.setOnPreferenceChangeListener(listener);
		// FIXME unregister ????
	}

	/**
	 * Shows the simplified settings UI if the device configuration if the
	 * device configuration dictates that a simplified, single-pane UI should be
	 * shown.
	 */
	@SuppressWarnings("deprecation")
	private void setupSimplePreferencesScreen() {
		if (!isSimplePreferences(this)) {
			return;
		}
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		// THIS is a blank preferences layout - which I need so
		// getPreferenceScreen() does not return null - so I can add a header -
		// alternatively I can very well comment everything out apart from
		// addPreferencesFromResource(R.xml.pref_data_sync);
		addPreferencesFromResource(PREF_RESOURCE);
		// Add 'data and sync' preferences, and a corresponding header.
		PreferenceCategory fakeHeader = new PreferenceCategory(this);
		fakeHeader.setTitle(R.string.pref_header_data_sync);
		getPreferenceScreen().addPreference(fakeHeader);
		addPreferencesFromResource(PREF_RESOURCE_SETTINGS);
		master_pref = findPreference(master_enable.toString());
		// bindPreferenceSummaryToValue(master_pref); // this throws ClassCast
		// Bind the summaries of EditText/List/Dialog/Ringtone preferences to
		// their values. When their values change, their summaries are updated
		// to reflect the new value, per the Android Design guidelines.
		// bindPreferenceSummaryToValue(findPreference("example_list"));
		// bindPreferenceSummaryToValue(findPreference("sync_frequency"));
	}

	/** {@inheritDoc} */
	@Override
	public boolean onIsMultiPane() {
		return isXLargeTablet(this) && !isSimplePreferences(this);
	}

	/**
	 * Helper method to determine if the device has an extra-large screen. For
	 * example, 10" tablets are extra-large.
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout &
				Configuration.SCREENLAYOUT_SIZE_MASK)
				>= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	/**
	 * Determines whether the simplified settings UI should be shown. This is
	 * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
	 * doesn't have newer APIs like {@link PreferenceFragment}, or the device
	 * doesn't have an extra-large screen. In these cases, a single-pane
	 * "simplified" settings UI should be shown.
	 */
	private static boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
			|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
			|| !isXLargeTablet(context);
	}

	/** {@inheritDoc} */
	/*
	 * Subclasses of PreferenceActivity should implement onBuildHeaders(List) to
	 * populate the header list with the desired items. Doing this implicitly
	 * switches the class into its new "headers + fragments" mode rather than
	 * the old style of just showing a single preferences list (from
	 * http://developer
	 * .android.com/reference/android/preference/PreferenceActivity.html) -> IE
	 * this is called automatically - reads the R.xml.pref_headers and freaking
	 * creates the 2 panes view - it was driving me mad - @inheritDoc my *** It
	 * does not crash in Froyo cause isSimplePreferences is always true in
	 * Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB - @Override has
	 * nothing to do with runtime and of course on Froyo this is never called by
	 * the system (since it does not exist)
	 */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onBuildHeaders(List<Header> target) {
		// Log.w("TARGET!!!!", target + "");
		if (!isSimplePreferences(this)) {
			loadHeadersFromResource(R.xml.pref_headers, target);
		}
	}

	/**
	 * A preference value change listener that updates the preference's summary
	 * to reflect its new value.
	 */
	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {

		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();
			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);
				// Set the summary to reflect the new value.
				preference.setSummary(index >= 0
						? listPreference.getEntries()[index] : null);
			} else if (preference instanceof RingtonePreference) {
				// For ringtone preferences, look up the correct display value
				// using RingtoneManager.
				if (TextUtils.isEmpty(stringValue)) {
					// Empty values correspond to 'silent' (no ringtone).
					// preference.setSummary(R.string.pref_ringtone_silent);
				} else {
					Ringtone ringtone = RingtoneManager.getRingtone(
						preference.getContext(), Uri.parse(stringValue));
					if (ringtone == null) {
						// Clear the summary if there was a lookup error.
						preference.setSummary(null);
					} else {
						// Set the summary to reflect the new ringtone display
						// name.
						String name = ringtone
							.getTitle(preference.getContext());
						preference.setSummary(name);
					}
				}
			} else if (preference instanceof CheckBoxPreference) {
				boolean b = (Boolean) value;
				Log.w(TAG, "::::value " + b);
				final CheckBoxPreference p = (CheckBoxPreference) preference;
				preference.setSummary((b) ? p.getSummaryOn() : p
					.getSummaryOff());
				Log.w(TAG, p.getKey() + " :: " + p.isChecked());
			} else {
				// if (!MASTER_ENABLE.equals(value.toString())) {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
				// if ("true".equals(value.toString())) {
				// TriggerMonitoringBootReceiver.enable(getBaseContext(),
				// true);
				// } else {
				// Toast.makeText(getApplicationContext(),
				// "CB: " + "false", Toast.LENGTH_SHORT).show();
				// }
				// }
			}
			return true;
		}
	};

	private class ToggleMonitoringListener implements
			OnPreferenceChangeListener {

		ToggleMonitoringListener() {}

		@Override
		public boolean
				onPreferenceChange(Preference preference, Object newValue) {
			if (newValue instanceof Boolean) {
				final boolean enable = (Boolean) newValue;
				Log.v(TAG, "!master enable : " + enable);
				dialog = ProgressDialog.show(SettingsActivity.this, "",
					((enable) ? "Enabling" : "Disabling") + " monitoring.");
				Monitor.enableMonitoring(getApplicationContext(), enable);
				final CheckBoxPreference p = (CheckBoxPreference) preference;
				preference.setSummary((enable) ? p.getSummaryOn() : p
					.getSummaryOff());
				return true;
			}
			return false;
		}
	}

	/**
	 * Binds a BOOLEAN preference's summary to its value. More specifically,
	 * when the preference's value is changed, its summary (line of text below
	 * the preference title) is updated to reflect the value. The summary is
	 * also immediately updated upon calling this method. The exact display
	 * format is dependent on the type of preference.
	 *
	 * @see #sBindPreferenceSummaryToValueListener
	 */
	private static void bindCheckBoxPreferenceSummaryToValue(
			Preference preference) {
		// Set the listener to watch for value changes.
		preference
			.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
			preference,
			PreferenceManager.getDefaultSharedPreferences(
				preference.getContext()).getBoolean(preference.getKey(), true));
	}

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 *
	 * @see #sBindPreferenceSummaryToValueListener
	 */
	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference
			.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
			preference,
			PreferenceManager.getDefaultSharedPreferences(
				preference.getContext()).getString(preference.getKey(), ""));
	}

	/**
	 * This fragment shows data and sync preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class DataSyncPreferenceFragment extends PreferenceFragment {

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(PREF_RESOURCE_SETTINGS);
			// Bind the summaries of EditText/List/Dialog/Ringtone preferences
			// to their values. When their values change, their summaries are
			// updated to reflect the new value, per the Android Design
			// guidelines.
			// bindPreferenceSummaryToValue(findPreference("sync_frequency"));
			master_pref = findPreference(master_enable.toString());
		}
	}
}
