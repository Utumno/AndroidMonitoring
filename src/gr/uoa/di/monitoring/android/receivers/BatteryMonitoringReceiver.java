package gr.uoa.di.monitoring.android.receivers;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.services.BatteryMonitoringService;

public final class BatteryMonitoringReceiver extends BaseMonitoringReceiver {

	@Override
	protected Class<? extends WakefulIntentService> getService() {
		return BatteryMonitoringService.class;
	}
}
