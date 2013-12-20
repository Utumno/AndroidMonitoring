package gr.uoa.di.monitoring.android.receivers;

import gr.uoa.di.monitoring.android.services.AlarmService;
import gr.uoa.di.monitoring.android.services.LatchService;

public final class LatchReceiver extends BaseAlarmReceiver {

	@Override
	protected Class<? extends AlarmService> getService() {
		return LatchService.class;
	}
}
