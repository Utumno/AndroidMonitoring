package gr.uoa.di.monitoring.android.receivers;

import gr.uoa.di.monitoring.android.services.BatteryMonitor;
import gr.uoa.di.monitoring.android.services.Monitor;

public final class BatteryMonitoringReceiver extends BaseAlarmReceiver {

	@Override
	// notice I return a Monitor
	protected Class<? extends Monitor<?, ?>> getService() {
		return BatteryMonitor.class;
	}
}
