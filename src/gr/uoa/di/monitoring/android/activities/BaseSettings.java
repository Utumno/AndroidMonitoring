package gr.uoa.di.monitoring.android.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import java.util.List;

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
 *
 * Defines two abstract methods that need be implemented by implementators.
 */
public abstract class BaseSettings extends PreferenceActivity {

	/**
	 * Determines whether to always show the simplified settings UI, where
	 * settings are presented in a single list. When false, settings are shown
	 * as a master/detail two-pane view on tablets. When true, a single pane is
	 * shown on tablets.
	 */
	private static final boolean ALWAYS_SIMPLE_PREFS = false;

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

	/** {@inheritDoc} */
	@Override
	public final boolean onIsMultiPane() { // never used by us
		return isXLargeTablet(this) && !isSimplePreferences(this);
	}

	/**
	 * Determines whether the simplified settings UI should be shown. This is
	 * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
	 * doesn't have newer APIs like {@link PreferenceFragment}, or the device
	 * doesn't have an extra-large screen. In these cases, a single-pane
	 * "simplified" settings UI should be shown.
	 */
	private static final boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
			|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
			|| !isXLargeTablet(context);
	}

	@Override
	protected final void onCreate(Bundle savedInstanceState) {
		// disallow onCreate(), see comment in onPostCreate()
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onStart() {
		// disallow onStart(), see comment in onPostCreate()
		// oops - receiver crap - must allow
		super.onStart();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		// onPostCreate() probably is needed because onBuildHeaders() is called
		// after onCreate() ? This piece of err code should be called
		// onPostStart() btw - so yeah
		super.onPostCreate(savedInstanceState);
		setupSimplePreferencesScreen();
		// findPreference will return null if setupSimplePreferencesScreen
		// hasn't run, so I disallow onCreate() and onStart()
	}

	/**
	 * Shows the simplified settings UI if the device configuration if the
	 * device configuration dictates that a simplified, single-pane UI should be
	 * shown.
	 */
	private void setupSimplePreferencesScreen() {
		if (!isSimplePreferences(this)) {
			return;
		}
		buildSimplePreferences();
	}

	/** {@inheritDoc} */
	/*
	 * Subclasses of PreferenceActivity should implement onBuildHeaders(List) to
	 * populate the header list with the desired items. Doing this implicitly
	 * switches the class into its new "headers + fragments" mode rather than
	 * the old style of just showing a single preferences list (from
	 * http://developer
	 * .android.com/reference/android/preference/PreferenceActivity.html) -> IE
	 * this is called automatically - reads the R.xml.pref_headers and creates
	 * the 2 panes view - it was driving me mad - @inheritDoc my - It does not
	 * crash in Froyo cause isSimplePreferences is always true for
	 * Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB - @Override has
	 * nothing to do with runtime and of course on Froyo this is never called by
	 * the system
	 */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public final void onBuildHeaders(List<Header> target) {
		if (!isSimplePreferences(this)) {
			loadHeadersFromResource(getHeadersXmlID(), target);
		}
	}

	// =========================================================================
	// Abstract API
	// =========================================================================
	/**
	 * Must return an id for the headers xml file. There you define the headers
	 * and the corresponding PreferenceFragment for each header which you must
	 * of course implement. This is used in the super implementation of
	 * {@link #onBuildHeaders(List)}
	 *
	 * @return an id from the R file for the xml containing the headers
	 */
	abstract int getHeadersXmlID();

	/**
	 * Builds a pre Honeycomb preference screen. An implementation would use the
	 * (deprecated)
	 * {@link android.preference.PreferenceActivity#addPreferencesFromResource(int)}
	 */
	abstract void buildSimplePreferences();

	// the fluff that follows is for binding preference summary to value -
	// essentially wrappers around OnPreferenceChangeListener - just keep it
	// here so you get an idea of the mess this autogenerated piece of, code was
	// formatter:off
	/**
	 * A preference value change listener that updates the preference's summary
	 * to reflect its new value.
	 */
	/* private static Preference.OnPreferenceChangeListener
			sBindPreferenceSummaryToValueListener =
			new Preference.OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object value) {
				String stringValue = value.toString();
				if (preference instanceof ListPreference) {
					// For list preferences, look up the correct display value
					// in the preference's 'entries' list.
					ListPreference listPreference = (ListPreference) preference;
					int index = listPreference.findIndexOfValue(stringValue);
					// Set the summary to reflect the new value.
					preference.setSummary(index >= 0
							? listPreference.getEntries()[index] : null);
				} else if (preference instanceof RingtonePreference) {
					// For ringtone preferences, look up the correct display
					// value using RingtoneManager.
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
							// Set the summary to reflect the new ringtone
							// display name.
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
					// For all other preferences, set the summary to the value's
					// simple string representation.
					preference.setSummary(stringValue);
				}
				return true;
			}
		}; */

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 *
	 * @see #sBindPreferenceSummaryToValueListener
	 */
	/* private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference
			.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
			preference,
			PreferenceManager.getDefaultSharedPreferences(
				preference.getContext()).getString(preference.getKey(), ""));
	} */
}
