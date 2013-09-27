package gr.uoa.di.monitoring.android.activities;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import gr.uoa.di.android.helpers.Net;
import gr.uoa.di.monitoring.android.R;
import gr.uoa.di.monitoring.android.services.NetworkService;

import java.net.SocketException;

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
		// TODO load default preferences :
		// http://stackoverflow.com/questions/2691772/
		for (int button : BUTTONS) {
			findViewById(button).setOnClickListener(this);
		}
		// TODO : get rid of these
		try {
			w("IP : " + Net.getIPAddress(true));
		} catch (SocketException e) {}
		WakefulIntentService.sendWakefulWork(this, NetworkService.class);
		// end todo
		// String msg = null;
		// if (android.os.Build.VERSION.SDK_INT >=
		// Build.VERSION_CODES.HONEYCOMB) {
		// msg = " - isExternalStorageRemovable() : "
		// + Environment.isExternalStorageRemovable() + " Emulated :"
		// + Environment.isExternalStorageEmulated();
		// }
		PackageInfo pInfo = packageInfo();
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
		switch (v.getId()) {
		case R.id.battery_button:
			detailsLayout = R.layout.battery_details;
			prefsLayout = R.layout.battery_prefs;
			break;
		case R.id.gps_button:
			return;
			// break;
		case R.id.wifi_button:
			return;
			// break;
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
