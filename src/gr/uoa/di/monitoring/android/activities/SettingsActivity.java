package gr.uoa.di.monitoring.android.activities;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.services.Monitor;

import static gr.uoa.di.monitoring.android.C.DEBUG;

public final class SettingsActivity extends BaseSettings implements
		OnSharedPreferenceChangeListener {

	private static final int PREF_HEADERS_XML = R.xml.pref_headers;
	private static CharSequence master_enable;
	private OnPreferenceChangeListener listener;
	private static CheckBoxPreference master_pref;
	private static final String TAG = SettingsActivity.class.getSimpleName();
	private SharedPreferences sp;
	/** Used as canvas for the simple preferences screen */
	private static final int EMPTY_PREF_RESOURCE = R.xml.pref_empty;
	// debug preferences
	private static int PREF_RESOURCE_SETTINGS = (DEBUG)
			? R.xml.pref_data_sync_debug : R.xml.pref_data_sync;
	// dialog (enabling disabling)
	private static ProgressDialog dialog;

	public static void cancelDialog() {
		if (dialog != null) {
			// add it to the receiver
			dialog.dismiss();
			dialog = null;
		}
	}

	@Override
	int getHeadersXmlID() {
		return PREF_HEADERS_XML;
	}

	@Override
	void buildSimplePreferences() {
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		// THIS is a blank preferences layout - which I need so
		// getPreferenceScreen() does not return null - so I can add a header -
		// alternatively you can very well comment everything out apart from
		// addPreferencesFromResource(R.xml.pref_data_sync);
		addPreferencesFromResource(EMPTY_PREF_RESOURCE);
		// Add 'data and sync' preferences, and a corresponding header.
		PreferenceCategory fakeHeader = new PreferenceCategory(this);
		fakeHeader.setTitle(R.string.pref_header_data_sync);
		getPreferenceScreen().addPreference(fakeHeader);
		addPreferencesFromResource(PREF_RESOURCE_SETTINGS);
		// bindPreferenceSummaryToValue(master_pref); // this throws ClassCast
		// Bind the summaries of EditText/List/Dialog/Ringtone preferences to
		// their values. When their values change, their summaries are updated
		// to reflect the new value, per the Android Design guidelines.
		// bindPreferenceSummaryToValue(findPreference("example_list"));
		// bindPreferenceSummaryToValue(findPreference("sync_frequency"));
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		master_enable = getResources().getText(
			R.string.enable_monitoring_master_pref_key);
		listener = new ToggleMonitoringListener();
		// DefaultSharedPreferences - register listener lest Monitor aborts
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		sp.registerOnSharedPreferenceChangeListener(this);
		master_pref = (CheckBoxPreference) findPreference(master_enable
			.toString());
	}

	@Override
	protected void onResume() {
		super.onResume();
		master_pref.setOnPreferenceChangeListener(listener); // no way to
		// unregister, see: http://stackoverflow.com/a/20493608/281545 This
		// listener reacts to *manual* updates - so no need to be active outside
		// onResume()/onPause()
	}

	@Override
	protected void onDestroy() {
		// may not be called (as onDestroy() is killable), but no leak,
		// see: http://stackoverflow.com/a/20493608/281545
		Log.w(TAG, "onDestroy()!!! - unregister");
		sp.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	/**
	 * Toggles monitoring and sets the preference summary. Triggered on *manual*
	 * update of the *single* preference it is registered with, but before this
	 * preference is updated and saved.
	 */
	private static class ToggleMonitoringListener implements
			OnPreferenceChangeListener {

		ToggleMonitoringListener() {}

		private final static String TAG2 = ToggleMonitoringListener.class
			.getName();

		@Override
		public boolean
				onPreferenceChange(Preference preference, Object newValue) {
			if (newValue instanceof Boolean) {
				final boolean enable = (Boolean) newValue;
				Log.v(TAG2, "!master enable : " + enable);
				dialog = ProgressDialog.show(preference.getContext(), "",
					((enable) ? "Enabling" : "Disabling") + " monitoring.");
				Monitor.enableMonitoring(preference.getContext(), enable);
				final CheckBoxPreference p = (CheckBoxPreference) preference;
				preference.setSummary((enable) ? p.getSummaryOn() : p
					.getSummaryOff());
				return true;
			}
			return false;
		}
	}

	/**
	 * This fragment shows data and sync preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public final static class DataSyncPreferenceFragment extends
			PreferenceFragment {

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			Log.w(TAG, "onCreate");
			addPreferencesFromResource(PREF_RESOURCE_SETTINGS);
			master_pref = (CheckBoxPreference) findPreference(master_enable
				.toString());
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (master_enable == null || master_pref == null) return;
		if (master_enable.toString().equals(key)) {
			refreshMasterPreference();
		}
	}

	/**
	 * @param key
	 */
	private void refreshMasterPreference() {
		final Boolean isMonitoringEnabled = AccessPreferences.get(this,
			master_enable.toString(), false);
		Log.w(TAG, "Stored value: " + isMonitoringEnabled);
		final boolean needsRefresh = master_pref.isChecked() != isMonitoringEnabled;
		if (needsRefresh) {
			master_pref.setChecked(isMonitoringEnabled);
			master_pref.setSummary((isMonitoringEnabled) ? master_pref
				.getSummaryOn() : master_pref.getSummaryOff());
		}
	}
}
