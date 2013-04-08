package gr.uoa.di.monitoring.android.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;

public class BaseFragment extends Fragment {

	/**
	 * Used for default layout - TODO - make a default
	 */
	protected static final int UNDEFINED = -1;
	protected Intent intent;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onAttach(Activity activity) {
		// hopefully activity is THE activity
		super.onAttach(activity);
		intent = activity.getIntent();
	}
}
