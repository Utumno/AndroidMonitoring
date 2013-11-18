package gr.uoa.di.monitoring.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import gr.uoa.di.monitoring.android.R;

public class MonitorPrefsFragment extends BaseFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final int layout = R.layout.data_prefs;
		return inflater.inflate(layout, container, false); // NEVER TRUE !
	}
}
