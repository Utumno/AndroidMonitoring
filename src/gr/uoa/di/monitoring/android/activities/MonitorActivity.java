package gr.uoa.di.monitoring.android.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import gr.uoa.di.android.helpers.AccessPreferences;
import gr.uoa.di.monitoring.android.R;

import static gr.uoa.di.monitoring.android.C.DATA_INTRO_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.DATA_PREFS_KEY_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.MANUAL_UPDATE_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.START_SERVICE_INTENT_INTENT_KEY;
import static gr.uoa.di.monitoring.android.C.UPDATE_IN_PROGRESS_INTENT_KEY;

public class MonitorActivity extends FragmentActivity implements
		OnClickListener {

	// constants - but I need a context to retrieve them
	private static String defaultNoDataText;
	private static String defaultUpdatingText;
	private static String updateButtonTxt;
	// intent and its dependent data
	private Intent monitorActivityIntent;
	// depend on particular intent used to start the activity up
	private static String dataKey; // static cause is needed in callbacks
	private String updateInProgressKey;
	private Intent serviceIntent;
	// set on resume - static cause needed in callbacks
	private static TextView dataTextView; // null this onPause() to avoid a leak
	private static Button updateButton; // null this onPause() to avoid a leak

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);
		// those are effectively final constants
		defaultNoDataText = getResources().getString(
			R.string.default_data_no_data);
		defaultUpdatingText = getResources().getString(
			R.string.default_data_updating);
		updateButtonTxt = getResources().getString(R.string.update_button_text);
		// Intent driven
		monitorActivityIntent = getIntent();
		// intro - set once
		final TextView introTextView = (TextView) findViewById(R.id.data_intro);
		final String introText = monitorActivityIntent
			.getStringExtra(DATA_INTRO_INTENT_KEY);
		introTextView.setText(introText);
		// data
		dataKey = monitorActivityIntent
			.getStringExtra(DATA_PREFS_KEY_INTENT_KEY);
		// see if update is in progress
		updateInProgressKey = monitorActivityIntent
			.getStringExtra(UPDATE_IN_PROGRESS_INTENT_KEY);
		// the intent that starts the service
		serviceIntent = (Intent) monitorActivityIntent
			.getParcelableExtra(START_SERVICE_INTENT_INTENT_KEY);
	}

	public static synchronized void onDataUpdated(Context ctx) {
		if (dataTextView != null && dataKey != null)
			dataTextView.setText(AccessPreferences.get(ctx, dataKey,
				defaultNoDataText));
		if (updateButton != null) {
			updateButton.setText(updateButtonTxt);
			updateButton.setEnabled(true);
		}
	}

	public static synchronized void onUpdating() {
		if (dataTextView != null) dataTextView.setText(defaultUpdatingText);
		if (updateButton != null) {
			updateButton.setEnabled(false);
			updateButton.setText(defaultUpdatingText);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.update_data_button:
			serviceIntent.putExtra(MANUAL_UPDATE_INTENT_KEY, true);
			this.startService(serviceIntent);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		synchronized (MonitorActivity.class) {
			Boolean isUpdating = AccessPreferences.get(this,
				updateInProgressKey, false);
			dataTextView = (TextView) findViewById(R.id.data);
			updateButton = (Button) findViewById(R.id.update_data_button);
			updateButton.setOnClickListener(this);
			// set the data text
			dataTextView.setText((isUpdating) ? defaultUpdatingText
					: AccessPreferences.get(this, dataKey, defaultNoDataText));
			// set the button right
			updateButton.setText((isUpdating) ? defaultUpdatingText
					: updateButtonTxt);
			updateButton.setEnabled(!isUpdating);
		}
	}

	@Override
	protected void onPause() {
		synchronized (MonitorActivity.class) {
			updateButton.setOnClickListener(null); // TODO : needed ??
			dataTextView = updateButton = null; // to avoid leaking my activity
		}
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.monitor, menu);
		return true;
	}
}
