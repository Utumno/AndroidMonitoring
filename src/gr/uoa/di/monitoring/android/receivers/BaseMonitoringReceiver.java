package gr.uoa.di.monitoring.android.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.C;
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
	/**
	 * Defined by subclasses
	 */
	private final Class<? extends Monitor> monitor_class_ = getService();
	// could be in method setupAlarm() ??? or not ?
	private Intent monitoringIntent;
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
		d("" + action);
		if (C.ac_setup_alarm.equals(action) || C.ac_cancel_alarm.equals(action)) {
			monitoringIntent = new Intent(context, this.getClass());
			// (C.ac_monitor + "") below WOULD END UP MAKING THE ACTION null (!?)
			monitoringIntent.setAction(C.ac_monitor.toString());
			monitoringIntent.setPackage(RECEIVERS_PACKAGE_NAME);// TODO: needed?
			final boolean enable = C.ac_setup_alarm.equals(action);
			setupAlarm(context, enable);
		} else if (C.ac_monitor.equals(action)) {
			// monitoring - got broadcast from ALARM
			WakefulIntentService.sendWakefulWork(context, monitor_class_);
		} else {
			w("Received bogus intent : " + intent);
			return;
		}
	}

	protected abstract Class<? extends Monitor> getService();

	private void setupAlarm(Context context, boolean setup) {
		am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		pi = PendingIntent.getBroadcast(context, NOT_USED, monitoringIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		if (setup) {
			try {
				am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
						SystemClock.elapsedRealtime() + Monitor.getInitialDelay(),
						monitor_class_.newInstance().getInterval(), pi);
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			am.cancel(pi);
		}
		w("alarms " + (setup ? "enabled" : "disabled"));
	}
}
