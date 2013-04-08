package gr.uoa.di.monitoring.android.activities;

import android.app.Activity;
import android.util.Log;
import android.view.Menu;

import gr.uoa.di.monitoring.android.R;

class BaseActivity extends Activity {

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

	protected void d(String msg) {
		Log.d(tag_, msg);
	}
}
