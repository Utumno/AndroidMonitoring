package gr.uoa.di.monitoring.android.receivers;

import gr.uoa.di.monitoring.android.services.LocationMonitor;
import gr.uoa.di.monitoring.android.services.Monitor;

public final class LocationMonitoringReceiver extends BaseAlarmReceiver {

	@Override
	// notice I return a Monitor
	protected Class<? extends Monitor<?, ?>> getService() {
		return LocationMonitor.class;
	}
}
