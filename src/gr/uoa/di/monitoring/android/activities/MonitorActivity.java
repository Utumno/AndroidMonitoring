package gr.uoa.di.monitoring.android.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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

public final class MonitorActivity extends FragmentActivity implements
		OnClickListener, OnSharedPreferenceChangeListener {

	// constants - but I need a context to retrieve them
	private static String sDefaultNoDataText;
	private static String sDefaultUpdatingText;
	private static String sUpdateButtonTxt;
	// intent and its dependent data
	private Intent mMonitorActivityIntent;
	// depend on particular intent used to start the activity up
	private String mDataKey;
	private String mUpdateInProgressKey;
	private Intent mServiceIntent;
	// set onStart()
	private TextView mDataTextView;
	private Button mUpdateButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);
		// those are effectively final constants
		sDefaultNoDataText = getResources().getString(
			R.string.default_data_no_data);
		sDefaultUpdatingText = getResources().getString(
			R.string.default_data_updating);
		sUpdateButtonTxt = getResources()
			.getString(R.string.update_button_text);
		// Intent driven
		mMonitorActivityIntent = getIntent();
		// intro - set once
		final TextView introTextView = (TextView) findViewById(R.id.data_intro);
		final String introText = mMonitorActivityIntent
			.getStringExtra(DATA_INTRO_INTENT_KEY);
		introTextView.setText(introText);
		// data
		mDataKey = mMonitorActivityIntent
			.getStringExtra(DATA_PREFS_KEY_INTENT_KEY);
		// see if update is in progress
		mUpdateInProgressKey = mMonitorActivityIntent
			.getStringExtra(UPDATE_IN_PROGRESS_INTENT_KEY);
		// the intent that starts the service
		mServiceIntent = (Intent) mMonitorActivityIntent
			.getParcelableExtra(START_SERVICE_INTENT_INTENT_KEY);
		// and the "movable" parts
		mDataTextView = (TextView) findViewById(R.id.data);
		mUpdateButton = (Button) findViewById(R.id.update_data_button);
		mUpdateButton.setOnClickListener(this); // no need to unregister methinks
	}

	@Override
	public synchronized void onSharedPreferenceChanged(
			SharedPreferences sharedPreferences, String key) {
		if (mUpdateInProgressKey.equals(key)) {
			final Boolean isUpdating = AccessPreferences.get(this,
				mUpdateInProgressKey, false);
			// set the data text
			mDataTextView.setText((isUpdating) ? sDefaultUpdatingText
					: AccessPreferences.get(this, mDataKey, sDefaultNoDataText));
			// set the button right
			mUpdateButton.setText((isUpdating) ? sDefaultUpdatingText
					: sUpdateButtonTxt);
			mUpdateButton.setEnabled(!isUpdating);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.update_data_button:
			mServiceIntent.putExtra(MANUAL_UPDATE_INTENT_KEY, true);
			this.startService(mServiceIntent);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		AccessPreferences.registerListener(this, this);
		AccessPreferences.callListener(this, this, mUpdateInProgressKey);
	}

	@Override
	protected void onStop() {
		// may not be called (as onStop() is killable), but no leak,
		// see: http://stackoverflow.com/a/20493608/281545
		AccessPreferences.unregisterListener(this, this);
		super.onStop();
	}

	// boilerplate
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.monitor, menu);
		return true;
	}
}
