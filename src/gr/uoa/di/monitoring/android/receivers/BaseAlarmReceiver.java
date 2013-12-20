package gr.uoa.di.monitoring.android.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.services.AlarmService;

import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.ac_cancel_alarm;
import static gr.uoa.di.monitoring.android.C.ac_monitor;
import static gr.uoa.di.monitoring.android.C.ac_reschedule_alarm;
import static gr.uoa.di.monitoring.android.C.ac_setup_alarm;

/**
 * Base class for all Alarm Receivers. Responsible for registering the alarms
 * (on Broadcast from boot receiver or activity), canceling the alarms (on
 * Broadcast from activity or on abort) and launching the (Wakeful)
 * IntentServices on receiving the Alarm Manager's Broadcast. Subclasses are
 * used to provide the particular Wakeful services (by defining the abstract
 * getService()) but the common code is in this class's onReceive() which is
 * *final*
 *
 * @author MrD
 */
public abstract class BaseAlarmReceiver extends BaseReceiver {

	/** Defined by subclasses */
	private final Class<? extends AlarmService> monitor_class_ = getService();
	// could be in method setupAlarm() ??? or not ?
	private Intent monitoringIntent;
	// could be in method setupAlarm()
	private PendingIntent pi;
	private AlarmManager am; // static ? final ?
	private static final String UNABLE_TO_SET_ALARMS = "Unable to set the "
		+ "alarms up";
	// constants
	private static final int NOT_USED = 0;

	@Override
	final public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		d("" + action);
		if (ac_setup_alarm.equals(action) || ac_cancel_alarm.equals(action)) {
			monitoringIntent = new Intent(context, this.getClass());
			// (ac_monitor + "") below WOULD END UP MAKING THE ACTION null (!?)
			monitoringIntent.setAction(ac_monitor.toString());
			final boolean enable = ac_setup_alarm.equals(action);
			setupAlarm(context, enable);
		} else if (ac_monitor.equals(action)) {
			// monitoring - got broadcast from ALARM
			WakefulIntentService.sendWakefulWork(context, monitor_class_);
		} else if (ac_reschedule_alarm.equals(action)) {
			monitoringIntent = new Intent(context, this.getClass());
			monitoringIntent.setAction(ac_monitor.toString());
			rescheduleAlarm(context);
		} else {
			w("Received bogus intent : " + intent);
		}
	}

	protected abstract Class<? extends AlarmService> getService();

	private void setupAlarm(Context context, boolean setup) {
		am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		pi = PendingIntent.getBroadcast(context, NOT_USED, monitoringIntent,
			PendingIntent.FLAG_UPDATE_CURRENT);
		if (setup) {
			try {
				am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime()
						+ AlarmService.getInitialDelay(),
					monitor_class_.newInstance().getBaseInterval(), pi);
			} catch (InstantiationException e) {
				// should not happen
				throw new RuntimeException(UNABLE_TO_SET_ALARMS, e);
			} catch (IllegalAccessException e) {
				// should not happen
				throw new RuntimeException(UNABLE_TO_SET_ALARMS, e);
			}
		} else {
			// send message to the monitors that the party is over
			Intent i = new Intent(ac_aborting.toString(), Uri.EMPTY, context,
				monitor_class_);
			WakefulIntentService.sendWakefulWork(context, i);
			// cancel the alarms
			am.cancel(pi);
		}
		d("alarms " + (setup ? "enabled" : "disabled"));
	}

	private void rescheduleAlarm(Context context) {
		am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		pi = PendingIntent.getBroadcast(context, NOT_USED, monitoringIntent,
			PendingIntent.FLAG_CANCEL_CURRENT);
		// I NEED TO CANCEL THE ALARMS
		am.cancel(pi);
		try {
			// NOTICE GET CURRENT INTERVAL
			final long currentInterval = monitor_class_.newInstance()
				.getCurrentInterval();
			am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + currentInterval,
				currentInterval, pi);
		} catch (InstantiationException e) {
			// should not happen
			throw new RuntimeException(UNABLE_TO_SET_ALARMS, e);
		} catch (IllegalAccessException e) {
			// should not happen
			throw new RuntimeException(UNABLE_TO_SET_ALARMS, e);
		}
		d("alarms rescheduled " + monitoringIntent);
	}
}
