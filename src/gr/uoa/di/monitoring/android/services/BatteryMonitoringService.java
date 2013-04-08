package gr.uoa.di.monitoring.android.services;

import android.content.Intent;

import java.util.Date;

public final class BatteryMonitoringService extends Monitor {

	public BatteryMonitoringService() {
		// needed for service instantiation by Android. See :
		// http://stackoverflow.com/questions/6176255/why-do-i-get-an-instantiationexception-if-i-try-to-start-a-service
		// also for "why" of the string parameter :
		// http://stackoverflow.com/questions/8016145/understanding-the-mechanisms-of-intentservice
		super(BatteryMonitoringService.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent arg0) {
		w(new Date().toString());
	}
}
