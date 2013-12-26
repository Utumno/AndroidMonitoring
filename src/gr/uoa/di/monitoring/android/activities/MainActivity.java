package gr.uoa.di.monitoring.android.activities;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.services.BatteryMonitor;
import gr.uoa.di.monitoring.android.services.LocationMonitor;
import gr.uoa.di.monitoring.android.services.NetworkService;
import gr.uoa.di.monitoring.android.services.WifiMonitor;

import static gr.uoa.di.monitoring.android.C.DATA_INTRO_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.DATA_PREFS_KEY_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.START_SERVICE_INTENT_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.UPDATE_IN_PROGRESS_INTENT_KEY;

public final class MainActivity extends BaseActivity implements OnClickListener {

	private final static int[] BUTTONS = { R.id.battery_button,
			R.id.gps_button, R.id.wifi_button };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// TODO load default preferences :
		// http://stackoverflow.com/questions/2691772/
		for (int button : BUTTONS) {
			findViewById(button).setOnClickListener(this);
		}
		// String msg = null;
		// if (android.os.Build.VERSION.SDK_INT >=
		// Build.VERSION_CODES.HONEYCOMB) {
		// msg = " - isExternalStorageRemovable() : "
		// + Environment.isExternalStorageRemovable() + " Emulated :"
		// + Environment.isExternalStorageEmulated();
		// }
		PackageInfo pInfo = packageInfo();
		// triggerTestNotification(this, this.getClass().getSimpleName()
		// + ".NOTIFICATION", 0);
		v("Kernel: " + System.getProperty("os.version"));
		v("On create finished (" + pInfo.versionCode + ")");
		// SharedPreferences p = PreferenceManager
		// .getDefaultSharedPreferences(this);
		// int i = p.getInt(
		// getResources().getText(R.string.enable_monitoring_master_pref_key)
		// .toString(),
		// 7);
		// v("(Bet I get a )class cast exception : " + i);
	}

	private PackageInfo packageInfo() {
		d(getFilesDir().getAbsolutePath() + " path " + getFilesDir().getPath());
		PackageInfo pInfo = null;
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return pInfo;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		case R.id.menu_send_files:
			WakefulIntentService.sendWakefulWork(this, NetworkService.class);
			return true;
		default:
			return false;
		}
	}

	@Override
	public void onClick(View v) {
		String dataKey = null;
		String dataIntroString = null;
		String updatingKey = null;
		Intent serviceIntent = null;
		switch (v.getId()) {
		case R.id.battery_button:
			dataKey = BatteryMonitor.dataKey();
			updatingKey = BatteryMonitor.updateInProgressKey();
			dataIntroString = getString(R.string.battery_intro);
			serviceIntent = new Intent(this, BatteryMonitor.class);
			break;
		case R.id.gps_button:
			dataKey = LocationMonitor.dataKey();
			updatingKey = LocationMonitor.updateInProgressKey();
			dataIntroString = getString(R.string.location_intro);
			serviceIntent = new Intent(this, LocationMonitor.class);
			break;
		case R.id.wifi_button:
			dataKey = WifiMonitor.dataKey();
			updatingKey = WifiMonitor.updateInProgressKey();
			dataIntroString = getString(R.string.wifi_intro);
			serviceIntent = new Intent(this, WifiMonitor.class);
			break;
		}
		Intent intent = new Intent(this, MonitorActivity.class);
		intent.putExtra(DATA_PREFS_KEY_INTENT_KEY, dataKey);
		intent.putExtra(DATA_INTRO_INTENT_KEY, dataIntroString);
		intent.putExtra(START_SERVICE_INTENT_INTENT_KEY, serviceIntent);
		intent.putExtra(UPDATE_IN_PROGRESS_INTENT_KEY, updatingKey);
		startActivity(intent);
	}
}
