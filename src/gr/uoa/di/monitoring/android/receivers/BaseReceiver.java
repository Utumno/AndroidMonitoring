package gr.uoa.di.monitoring.android.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import gr.uoa.di.monitoring.android.C;

public abstract class BaseReceiver extends BroadcastReceiver {

	protected final static String TAG = BaseReceiver.class.getSimpleName();
	private final String tag_ = this.getClass().getSimpleName();

	/**
	 * Disables/enables the receiver. Disabling/enabling will persist the
	 * reboot. It is simply a static method **it must be given a Receiver** to
	 * enable. I tried to make it to an instance method using CurClassNameGetter
	 * but did not work out well (no elegant way to call it for all the
	 * receivers I want to enable). I have found no way to disable the Boot
	 * receiver once its job is done.
	 *
	 * @param context
	 *            the context - this method must be called inside onReceive (to
	 *            receive a context)
	 * @param enable
	 *            if true enable
	 * @param receiver
	 *            the receiver to enable
	 */
	public static void enable(Context context, boolean enable,
			Class<? extends BaseReceiver> receiver) {
		PackageManager pacman = context.getPackageManager();
		final ComponentName componentName = new ComponentName(context, receiver);
		if (C.VERBOSE) Log.v(TAG, componentName.toString());
		final int state = (enable) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
				: PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
		pacman.setComponentEnabledSetting(componentName, state,
				PackageManager.DONT_KILL_APP);
		if (C.VERBOSE)
			Log.v(TAG,
					"pacman : "
							+ pacman.getComponentEnabledSetting(componentName));
	}

	void w(String msg) {
		Log.w(tag_, msg);
	}

	void d(String msg) {
		if (C.DEBUG) Log.d(tag_, msg);
	}
}
