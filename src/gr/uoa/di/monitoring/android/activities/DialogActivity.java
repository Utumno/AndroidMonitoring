package gr.uoa.di.monitoring.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import gr.uoa.di.monitoring.android.R;

public class DialogActivity extends BaseActivity implements OnClickListener {

	private final static int[] BUTTONS = { R.id.dialog_activity_yes_button,
			R.id.dialog_activity_no_button };
	private final static String DIALOG_TITLE_KEY = "Title";
	private final static String DIALOG_TEXT_KEY = "Text";
	private final static String DIALOG_INTENT_KEY = "Intent";
	private String mTitle;
	private String mText;
	private Intent mIntent;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_dialog);
		for (int button : BUTTONS) {
			findViewById(button).setOnClickListener(this);
		}
		Intent in = this.getIntent();
		if (in == null) {
			throw new NullPointerException(getClass().getName()
				+ " must be launched with non null intent");
		}
		final Bundle b = in.getExtras();
		if (b == null) {
			throw new NullPointerException("Null Bundle in "
				+ getClass().getName());
		}
		mTitle = b.getString(DIALOG_TITLE_KEY);
		mText = b.getString(DIALOG_TEXT_KEY);
		mIntent = b.getParcelable(DIALOG_INTENT_KEY);
		setTitle(mTitle);
		TextView tv = (TextView) findViewById(R.id.dialog_text);
		tv.setText(mText);
		// this.setFinishOnTouchOutside(false); // TODO : API check
	}

	// TODO : better API, more null pointers - a builder maybe ?
	public static Bundle setDialogTitle(Bundle b, String s) {
		b.putString(DIALOG_TITLE_KEY, s);
		return b;
	}

	public static Bundle setDialogText(Bundle b, String s) {
		b.putString(DIALOG_TEXT_KEY, s);
		return b;
	}

	public static Bundle setDialogIntent(Bundle b, Intent s) {
		b.putParcelable(DIALOG_INTENT_KEY, s);
		return b;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// You must return true for the menu to be displayed; if you return
		// false it will not be shown
		return false;
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.dialog_activity_yes_button:
			DialogActivity.this.startActivity(mIntent);
			break;
		case R.id.dialog_activity_no_button:
			finish();
			break;
		}
	}
}
