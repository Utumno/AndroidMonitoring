package gr.uoa.di.monitoring.android.fragments;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.Fragment;

public class BaseFragment extends Fragment {

	private Intent intent;

	@Override
	public void onAttach(Activity activity) {
		// hopefully activity is THE activity
		super.onAttach(activity);
		intent = activity.getIntent();
	}
}
