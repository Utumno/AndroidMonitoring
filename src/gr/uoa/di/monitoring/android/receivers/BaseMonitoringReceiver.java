package gr.uoa.di.monitoring.android.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.services.LocationMonitor;
import gr.uoa.di.monitoring.android.services.Monitor;
import gr.uoa.di.monitoring.android.services.WifiMonitor;

import java.util.HashSet;
import java.util.Set;

import static gr.uoa.di.monitoring.android.C.ac_aborting;
import static gr.uoa.di.monitoring.android.C.ac_cancel_alarm;
import static gr.uoa.di.monitoring.android.C.ac_monitor;
import static gr.uoa.di.monitoring.android.C.ac_setup_alarm;

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
	/** Defined by subclasses */
	private final Class<? extends Monitor> monitor_class_ = getService();
	// could be in method setupAlarm() ??? or not ?
	private Intent monitoringIntent;
	// could be in method setupAlarm()
	private PendingIntent pi;
	private AlarmManager am; // static ? final ?
	private static final String UNABLE_TO_SET_ALARMS = "Unable to set the alarms up";
	// constants
	private static final int NOT_USED = 0;
	@SuppressWarnings("unused")
	private static final int NULL_FLAGS = 0;
	private static final Set<Class<? extends Monitor>> NEED_CLEANUP = new HashSet<Class<? extends Monitor>>();
	static {
		NEED_CLEANUP.add(LocationMonitor.class);
		NEED_CLEANUP.add(WifiMonitor.class);
	}

	@Override
	final public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		d("" + action);
		if (ac_setup_alarm.equals(action) || ac_cancel_alarm.equals(action)) {
			monitoringIntent = new Intent(context, this.getClass());
			// (ac_monitor + "") below WOULD END UP MAKING THE ACTION null (!?)
			monitoringIntent.setAction(ac_monitor.toString());
			monitoringIntent.setPackage(RECEIVERS_PACKAGE_NAME);// TODO: needed?
			final boolean enable = ac_setup_alarm.equals(action);
			setupAlarm(context, enable);
		} else if (ac_monitor.equals(action)) {
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
				// should not happen
				throw new IllegalStateException(UNABLE_TO_SET_ALARMS, e);
			} catch (IllegalAccessException e) {
				// should not happen
				throw new IllegalStateException(UNABLE_TO_SET_ALARMS, e);
			}
		} else {
			if (NEED_CLEANUP.contains(monitor_class_)) {
				Intent i = new Intent(ac_aborting.toString(), Uri.EMPTY,
						context, monitor_class_);
				WakefulIntentService.sendWakefulWork(context, i);
			}
			am.cancel(pi);
		}
		w("alarms " + (setup ? "enabled" : "disabled"));
	}
}
