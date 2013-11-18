package gr.uoa.di.monitoring.android.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.widget.TextView;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.R;

import static gr.uoa.di.monitoring.android.C.DATA_INTRO_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.DATA_PREFS_KEY_INTENT_KEY;

public class MonitorActivity extends FragmentActivity {

	private Intent monitorActivityIntent;
	private static TextView dataTextView;
	private static String dataKey;
	private static String defaultDataText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);
		monitorActivityIntent = getIntent();
		// intro - set once
		final TextView introTextView = (TextView) findViewById(R.id.data_intro);
		final String introText = monitorActivityIntent
			.getStringExtra(DATA_INTRO_INTENT_KEY);
		introTextView.setText(introText);
		// data
		dataTextView = (TextView) findViewById(R.id.data);
		dataKey = monitorActivityIntent.getStringExtra(DATA_PREFS_KEY_INTENT_KEY);
		defaultDataText = getResources().getString(
			R.string.default_data_updating);
		dataTextView.setText(AccessPreferences.get(this, dataKey,
			defaultDataText));
	}

	public static void onChange(Context ctx) {
		if (dataTextView != null && dataKey != null)
			dataTextView.setText(AccessPreferences.get(ctx, dataKey,
				defaultDataText));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.monitor, menu);
		return true;
	}
}
