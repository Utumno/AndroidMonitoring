package gr.uoa.di.monitoring.android.receivers;

import gr.uoa.di.monitoring.android.services.Monitor;
import gr.uoa.di.monitoring.android.services.WifiMonitor;

public final class WifiMonitoringReceiver extends BaseAlarmReceiver {

	@Override
	protected Class<? extends Monitor> getService() {
		return WifiMonitor.class;
	}
}
