package gr.uoa.di.monitoring.android.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import gr.uoa.di.monitoring.android.R;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MainActivity extends BaseActivity implements OnClickListener {

	private int detailsLayout = UNDEFINED;
	private int prefsLayout = UNDEFINED;
	private final static String DETAILS_LAYOUT = "DETAILS_LAYOUT";
	private final static String PREFS_LAYOUT = "PREFS_LAYOUT";
	private final static int[] BUTTONS = { R.id.battery_button,
			R.id.gps_button, R.id.wifi_button };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		for (int button : BUTTONS) {
			findViewById(button).setOnClickListener(this);
		}
		String msg = null;
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			msg = " - isExternalStorageRemovable() : "
					+ Environment.isExternalStorageRemovable() + " Emulated :"
					+ Environment.isExternalStorageEmulated();
		}
		d(getFilesDir().getAbsolutePath() + " path " + getFilesDir().getPath());
		v("On create finished" + msg);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		default:
			return false;
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.battery_button:
			detailsLayout = R.layout.battery_details;
			prefsLayout = R.layout.battery_prefs;
			break;
		case R.id.gps_button:
			// crashes of course
			break;
		case R.id.wifi_button:
			// crashes of course
			break;
		}
		Intent intent = new Intent(this, MonitorActivity.class);
		intent.putExtra(DETAILS_LAYOUT, detailsLayout);
		intent.putExtra(PREFS_LAYOUT, prefsLayout);
		startActivity(intent);
	}

	@Override
	protected void onPause() {
		super.onPause();
		detailsLayout = prefsLayout = UNDEFINED;
	}
}
