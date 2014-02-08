package gr.uoa.di.monitoring.android.activities;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.services.Monitor;

import static gr.uoa.di.monitoring.android.C.DEBUG;
import static gr.uoa.di.monitoring.android.C.ac_toggling;

public final class SettingsActivity extends BaseSettings {

	private static final int PREF_HEADERS_XML = R.xml.pref_headers;
	private static CharSequence sMasterKey;
	private OnPreferenceChangeListener mManualListener;
	private CheckBoxPreference mMasterPref;
	private static final String TAG = SettingsActivity.class.getSimpleName();
	// Receiver
	/** If the master preference is changed externally this reacts */
	private BroadcastReceiver mExternalChangeReceiver = new ExternalChangeReceiver();
	private final static String TOGGLING_MONITORING_IN_PROGRESS = "TOGGLING_MONITORING_IN_PROGRESS";
	/** Used as canvas for the simple preferences screen */
	private static final int EMPTY_PREF_RESOURCE = R.xml.pref_empty;
	// debug preferences
	private static final int PREF_RESOURCE_SETTINGS = (DEBUG)
			? R.xml.pref_data_sync_debug : R.xml.pref_data_sync;
	// summaries
	private static CharSequence sSummaryOn;
	private static CharSequence sSummaryOff;
	private static CharSequence sSummaryEnabling;
	private static CharSequence sSummaryDisabling;

	public static void notifyMonitoringStateChange(Context ctx,
			CharSequence action, boolean isToggling) {
		final LocalBroadcastManager lbm = LocalBroadcastManager
			.getInstance(ctx);
		Intent intent = new Intent(ctx, ExternalChangeReceiver.class);
		intent.setAction(action.toString());
		intent.putExtra(TOGGLING_MONITORING_IN_PROGRESS, isToggling);
		lbm.sendBroadcastSync(intent);
	}

	@Override
	int getHeadersXmlID() {
		return PREF_HEADERS_XML;
	}

	@Override
	@SuppressWarnings("deprecation")
	void buildSimplePreferences() {
		// In the simplified UI, fragments are not used at all and we instead
		// use the older PreferenceActivity APIs.
		// THIS is a blank preferences layout - which I need so
		// getPreferenceScreen() does not return null - so I can add a header -
		// alternatively you can very well comment everything out apart from
		// addPreferencesFromResource(PREF_RESOURCE_SETTINGS);
		addPreferencesFromResource(EMPTY_PREF_RESOURCE);
		// Add 'data and sync' preferences, and a corresponding header.
		PreferenceCategory fakeHeader = new PreferenceCategory(this);
		fakeHeader.setTitle(R.string.pref_header_data_sync);
		getPreferenceScreen().addPreference(fakeHeader);
		addPreferencesFromResource(PREF_RESOURCE_SETTINGS);
		// this should be in onPostCreate - I just wanted to suppress all
		// deprecation warnings in one go - REPEAT in FRAGMENT
		sMasterKey = getResources().getText(
			R.string.enable_monitoring_master_pref_key);
		mMasterPref = (CheckBoxPreference) findPreference(sMasterKey.toString());
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		Log.w(TAG, "onPostCretae()");
		// summaries
		sSummaryOn = getResources().getText(R.string.str_pref_monitor_on);
		sSummaryOff = getResources().getText(R.string.str_pref_monitor_off);
		sSummaryEnabling = getResources().getText(
			R.string.pref_monitor_enabling);
		sSummaryDisabling = getResources().getText(
			R.string.pref_monitor_disabling);
		refreshMasterPreference(false);
		// listener
		Log.w(TAG, "LISTENER");
		mManualListener = new ToggleMonitoringListener();
		mMasterPref.setOnPreferenceChangeListener(mManualListener); // no way to
		// unregister, see: http://stackoverflow.com/a/20493608/281545 This
		// listener reacts to *manual* updates - so no need to be active outside
		// onResume()/onPause() - but also no need to register it every Resume
	}

	@Override
	protected void onStart() {
		super.onStart();
		final LocalBroadcastManager lbm = LocalBroadcastManager
			.getInstance(this);
		lbm.registerReceiver(mExternalChangeReceiver, new IntentFilter(
			ac_toggling.toString()));
	}

	@Override
	protected void onStop() {
		// may not be called in Froyo
		final LocalBroadcastManager lbm = LocalBroadcastManager
			.getInstance(this);
		lbm.unregisterReceiver(mExternalChangeReceiver);
		super.onStop();
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
		@SuppressWarnings("synthetic-access")
		public boolean
				onPreferenceChange(Preference preference, Object newValue) {
			if (newValue instanceof Boolean) {
				final boolean enable = (Boolean) newValue;
				Log.v(TAG2, "!master enable : " + enable);
				AccessPreferences.put(preference.getContext(),
					sMasterKey.toString(), enable);
				SettingsActivity.notifyMonitoringStateChange(
					preference.getContext(), ac_toggling, true);
				Monitor.enableMonitoring(preference.getContext(), enable);
				return true;
			}
			return false;
		}
	}

	private final class ExternalChangeReceiver extends BroadcastReceiver {

		ExternalChangeReceiver() {}

		@Override
		@SuppressWarnings("synthetic-access")
		public void onReceive(Context ctx, Intent intent) {
			if (sMasterKey == null || mMasterPref == null) return; // if
			// onPostReceive has not run this will be null
			final String action = intent.getAction();
			if (ac_toggling.equals(action)) {
				final boolean isToggling = intent.getBooleanExtra(
					TOGGLING_MONITORING_IN_PROGRESS, false);
				Log.w(ExternalChangeReceiver.class.getSimpleName(),
					"isToggling " + isToggling);
				new Handler(ctx.getMainLooper()).post(new Runnable() {

					@Override
					public void run() {
						mMasterPref.setEnabled(!isToggling);
						refreshMasterPreference(isToggling);
					}
				});
			}
		}
	}

	/**
	 * Updating finished, set summaries and check/uncheck status
	 *
	 * @param transition
	 *            if true we are in enabling/disabling
	 */
	private void refreshMasterPreference(boolean transition) {
		final Boolean enabling = AccessPreferences.get(this,
			sMasterKey.toString(), false);
		mMasterPref.setChecked(enabling);
		if (transition) {
			mMasterPref.setSummary((enabling) ? sSummaryEnabling
					: sSummaryDisabling);
		} else {
			// mMasterPref.setChecked(enabling);
			mMasterPref.setSummary((enabling) ? sSummaryOn : sSummaryOff);
		}
	}

	/**
	 * This fragment is used when the activity is showing a two-pane settings
	 * UI. Must be in sync with {@link #buildSimplePreferences}
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public final static class DataSyncPreferenceFragment extends
			PreferenceFragment {

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(PREF_RESOURCE_SETTINGS);
		}

		@Override
		@SuppressWarnings("synthetic-access")
		public void onActivityCreated(Bundle savedInstanceState) {
			Log.w("Fragment", "onActCR");
			super.onActivityCreated(savedInstanceState);
			SettingsActivity activity = (SettingsActivity) getActivity();
			sMasterKey = getResources().getText(
				R.string.enable_monitoring_master_pref_key);
			activity.mMasterPref = (CheckBoxPreference) findPreference(sMasterKey
				.toString());
		}
	}
}
