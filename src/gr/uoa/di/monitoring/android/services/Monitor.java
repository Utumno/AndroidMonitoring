package gr.uoa.di.monitoring.android.services;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.receivers.BaseMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.BaseReceiver;
import gr.uoa.di.monitoring.android.receivers.BatteryMonitoringReceiver;
import gr.uoa.di.monitoring.android.receivers.TriggerMonitoringBootReceiver;

import java.util.ArrayList;
import java.util.List;

public abstract class Monitor extends WakefulIntentService {

	private static CharSequence ac_setup_alarm;
	private static CharSequence ac_cancel_alarm;
	private final String tag_ = this.getClass().getSimpleName();
	private static final String TAG = Monitor.class.getSimpleName();
	private static final List<Class<? extends BaseReceiver>> RECEIVERS = new ArrayList<Class<? extends BaseReceiver>>();
	private static final List<Class<? extends BaseMonitoringReceiver>> SETUP_ALARM_RECEIVERS = new ArrayList<Class<? extends BaseMonitoringReceiver>>();
	static {
		Log.d(TAG, "Static Init");
		RECEIVERS.add(TriggerMonitoringBootReceiver.class);
		RECEIVERS.add(BatteryMonitoringReceiver.class);
		SETUP_ALARM_RECEIVERS.add(BatteryMonitoringReceiver.class);
	}

	public Monitor(String name) {
		super(name);
	}

	public static final int INITIAL_DELAY = 5000; // 5 seconds

	public static long getInterval() {
		return 5 * 60 * 1000;
	}

	public static void enableMonitoring(Context ctx, boolean enable) {
		Log.d(TAG, "enableMonitoring : " + enable);
		Resources resources = ctx.getResources();
		ac_setup_alarm = resources.getText(R.string.intent_action_setup_alarm);
		ac_cancel_alarm = resources
				.getText(R.string.intent_action_cancel_alarm);
		Log.d(TAG, "setup/cancel alarms : "
				+ (enable ? ac_setup_alarm : ac_cancel_alarm));
		if (enable) {
			_enableDisableReceivers(ctx, enable);
			_setupCancelAlarms(ctx, enable);
		} else {
			_setupCancelAlarms(ctx, enable);
			_enableDisableReceivers(ctx, enable);
		}
	}

	private static void _setupCancelAlarms(Context ctx, boolean enable) {
		for (Class<? extends BaseMonitoringReceiver> sEClass : SETUP_ALARM_RECEIVERS) {
			Intent i = new Intent(""
					+ (enable ? ac_setup_alarm : ac_cancel_alarm), Uri.EMPTY,
					ctx, sEClass);
			Log.d(TAG, "setup/cancel alarms int : " + i);
			ctx.sendBroadcast(i);
		}
	}

	private static void _enableDisableReceivers(Context ctx, boolean enable) {
		Log.d(TAG, "enable/disable receivers");
		for (Class<? extends BaseReceiver> receiver : RECEIVERS)
			BaseReceiver.enable(ctx, enable, receiver);
	}

	protected void w(String msg) {
		Log.w(tag_, msg);
	}
}

enum MonitoringInterval {
	FIVE(5), SIX(6), TEN(10), TWELVE(12), FIFTEEN(15), TWENTY(20),
	HALF_HOUR(30), HOUR(60);

	private final long interval;

	MonitoringInterval(long interval) {
		this.interval = interval;
	}

	public long getInterval() {
		return interval;
	}

	MonitoringInterval lessOften(MonitoringInterval mi) {
		int ordinal = mi.ordinal();
		if (ordinal < values().length - 1) {
			++ordinal;
		}
		return values()[ordinal];
	}

	MonitoringInterval moreOften(MonitoringInterval mi) {
		int ordinal = mi.ordinal();
		if (ordinal > 0) {
			--ordinal;
		}
		return values()[ordinal];
	}
}
