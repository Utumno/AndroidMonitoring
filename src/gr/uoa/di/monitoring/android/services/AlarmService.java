package gr.uoa.di.monitoring.android.services;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.C;
import gr.uoa.di.monitoring.android.Logging;

import java.util.Date;

public abstract class AlarmService extends WakefulIntentService implements
		Logging {

	private static final int INITIAL_DELAY = 5000;
	private final String tag_ = this.getClass().getSimpleName();
	// final String NOTIFICATION_TAG=tag_+".Notification"; //nope-must be static

	public AlarmService(String name) {
		super(name);
	}

	public abstract long getInterval();

	public static int getInitialDelay() {
		return INITIAL_DELAY;
	}

	// =========================================================================
	// Methods used by the subclasses
	// =========================================================================
	<T> void putPref(String key, T value) {
		AccessPreferences.put(this, key, value);
	}

	<T> T getPref(String key, T value) {
		return AccessPreferences.get(this, key, value);
	}

	/**
	 * Human readable time
	 *
	 * @return a human readable current time
	 */
	String time() {
		return new Date(System.currentTimeMillis()).toString();
	}

	// =========================================================================
	// LOGGING
	// =========================================================================
	@Override
	public void w(String msg) {
		C.w(tag_, msg);
	}

	@Override
	public void w(String msg, Throwable t) {
		C.w(tag_, msg, t);
	}

	@Override
	public void d(String msg) {
		C.d(tag_, msg);
	}

	@Override
	public void v(String msg) {
		C.v(tag_, msg);
	}
}
