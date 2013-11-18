package gr.uoa.di.monitoring.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import gr.uoa.di.monitoring.android.R;

public class MonitorDetailsFragment extends BaseFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final int LAYOUT = R.layout.data_display;
		return inflater.inflate(LAYOUT, container, false); // NEVER TRUE !
	}
}
