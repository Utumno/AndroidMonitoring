package gr.uoa.di.monitoring.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class MonitorDetailsFragment extends BaseFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		int layout = intent.getIntExtra("DETAILS_LAYOUT", UNDEFINED);
		return inflater.inflate(layout, container, false); // NEVER TRUE !
	}
}
