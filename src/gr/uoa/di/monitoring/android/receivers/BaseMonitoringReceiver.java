package gr.uoa.di.monitoring.android.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.SystemClock;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.services.Monitor;

/**
 * Base class for all Monitoring Receivers. Responsible for registering the
 * alarms (on Broadcast from boot receiver or activity) , canceling the alarms
 * (on Broadcast from activity) and launching the (Wakeful) IntentServices on
 * receiving the Alarm Manager's Broadcast. Subclasses are used to provide the
 * particular Wakeful services (by defining the abstract getService()) but the
 * common code is in this class's onReceive() which is *final*
 *
 * @author MrD
 */
public abstract class BaseMonitoringReceiver extends BaseReceiver {

	protected static final String RECEIVERS_PACKAGE_NAME = BaseMonitoringReceiver.class
			.getPackage().toString().split(" ")[1];
	// could be in method setupAlarm() ??? or not ?
	private Intent monitoringIntent;
	// any way to make those static ? final ?
	private Resources resources;
	private CharSequence ac_setup_alarm;
	private CharSequence ac_cancel_alarm;
	private CharSequence ac_monitor;
	// could be in method setupAlarm()
	private PendingIntent pi;
	private AlarmManager am; // static ? final ?
	// constants
	private static final int NOT_USED = 0;
	@SuppressWarnings("unused")
	private static final int NULL_FLAGS = 0;

	@Override
	final public void onReceive(Context context, Intent intent) {
		// FIXME : possible NPE below ?
		final String action = intent.getAction(); // can intent==null ?
		w("" + action);
		resources = context.getResources();
		ac_setup_alarm = resources.getText(R.string.intent_action_setup_alarm);
		ac_cancel_alarm = resources
				.getText(R.string.intent_action_cancel_alarm);
		ac_monitor = resources.getText(R.string.intent_action_monitor);
		if (ac_setup_alarm.equals(action) || ac_cancel_alarm.equals(action)) {
			monitoringIntent = new Intent(context, this.getClass());
			// (ac_monitor + "") below WOULD END UP MAKING THE ACTION null (!?)
			monitoringIntent.setAction(ac_monitor.toString());
			monitoringIntent.setPackage(RECEIVERS_PACKAGE_NAME);// TODO: needed?
			final boolean enable = ac_setup_alarm.equals(action);
			setupAlarm(context, enable);
		} else if (ac_monitor.equals(action)) {
			// monitoring - got broadcast from ALARM
			Class<? extends WakefulIntentService> serviceClass = getService();
			WakefulIntentService.sendWakefulWork(context, serviceClass);
		} else {
			w("Received bogus intent : " + intent);
			return;
		}
	}

	// protected Class<? extends WakefulIntentService> getService() {
	// throw new IllegalStateException("This should never be called directly");
	// }
	protected abstract Class<? extends WakefulIntentService> getService();

	private void setupAlarm(Context context, boolean setup) {
		am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		pi = PendingIntent.getBroadcast(context, NOT_USED, monitoringIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		if (setup) {
			am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime() + Monitor.INITIAL_DELAY,
					Monitor.getInterval(), pi);
		} else {
			am.cancel(pi);
		}
		w("alarms " + (setup ? "enabled" : "disabled"));
	}
}
