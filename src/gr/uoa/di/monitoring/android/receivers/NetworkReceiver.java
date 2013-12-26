package gr.uoa.di.monitoring.android.receivers;

import gr.uoa.di.monitoring.android.services.AlarmService;
import gr.uoa.di.monitoring.android.services.NetworkService;

public final class NetworkReceiver extends BaseAlarmReceiver {

	@Override
	protected Class<? extends AlarmService> getService() {
		return NetworkService.class;
	}
}
