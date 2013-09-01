package gr.uoa.di.monitoring.android.services;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.C;
import gr.uoa.di.monitoring.android.Logging;

public abstract class AlarmService extends WakefulIntentService implements
		Logging {

	private static final int INITIAL_DELAY = 5000;
	private final String tag_ = this.getClass().getSimpleName();

	public AlarmService(String name) {
		super(name);
	}

	public abstract long getInterval();

	public static int getInitialDelay() {
		return INITIAL_DELAY;
	}

	// =========================================================================
	// LOGGING
	// =========================================================================
	@Override
	public void w(String msg) {
		C.w(tag_, msg);
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
