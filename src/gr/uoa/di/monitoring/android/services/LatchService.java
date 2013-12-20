package gr.uoa.di.monitoring.android.services;

import android.content.Intent;

import gr.uoa.di.monitoring.android.services.Monitor.MonitoringInterval;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static gr.uoa.di.monitoring.android.C.ac_aborting;

public final class LatchService extends AlarmService {

	private static final long SEND_DATA_INTERVAL = MonitoringInterval.ONE
		.getInterval();
	private volatile static CountDownLatch latch;
	private static final long LATCH_TIMEOUT = MonitoringInterval.TWO
		.getInterval();

	public LatchService() {
		super(LatchService.class.getSimpleName());
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		final CharSequence action = intent.getAction();
		if (action == null) {
			// "monitor" command from the alarm manager
			w("\"monitor\" command from the alarm manager");
			latch = new CountDownLatch(1);
			try {
				latch.await(LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (ac_aborting.equals(action)) {
			w(ac_aborting.toString());
		}
	}

	@Override
	public long getBaseInterval() {
		return SEND_DATA_INTERVAL;
	}

	/** Always returns the same interval */
	@Override
	public long getCurrentInterval() {
		return getBaseInterval();
	}
}
