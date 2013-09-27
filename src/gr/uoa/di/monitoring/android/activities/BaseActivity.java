package gr.uoa.di.monitoring.android.activities;

import android.app.Activity;
import android.view.Menu;

import gr.uoa.di.monitoring.android.C;
import gr.uoa.di.monitoring.android.Logging;
import gr.uoa.di.monitoring.android.R;

abstract class BaseActivity extends Activity implements Logging {

	protected final static int UNDEFINED = -1;
	/**
	 * Class name used in d() - derived classes display their names but have no
	 * access to the field
	 */
	private final String tag_ = this.getClass().getSimpleName();

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	// =========================================================================
	// LOGGING
	// =========================================================================
	@Override
	public void w(String msg) {
		C.w(tag_, msg);
	}

	@Override
	public void w(String msg, Throwable t) {
		C.w(tag_, msg, t);
	}

	@Override
	public void d(String msg) {
		C.d(tag_, msg);
	}

	@Override
	public void v(String msg) {
		C.v(tag_, msg);
	}
}
