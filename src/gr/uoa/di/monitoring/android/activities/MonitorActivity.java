package gr.uoa.di.monitoring.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;

import gr.uoa.di.monitoring.android.R;

public class MonitorActivity extends FragmentActivity {

	private final Intent monitorActivityIntent = getIntent();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.monitor, menu);
		return true;
	}
}
