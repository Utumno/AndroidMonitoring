package gr.uoa.di.monitoring.android.activities;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import gr.uoa.di.monitoring.android.R;

import static gr.uoa.di.monitoring.android.C.launchActivityIntent;

public final class DialogActivity extends BaseActivity implements
		OnClickListener {

	private final static int[] BUTTONS = { R.id.dialog_activity_yes_button,
			R.id.dialog_activity_no_button };
	private final static String DIALOG_TITLE_KEY = "Title";
	private final static String DIALOG_TEXT_KEY = "Text";
	private final static String DIALOG_INTENT_KEY = "Intent";
	// should be final but they are provided in the intent available in onCreate
	private String mTitle;
	private String mText;
	private Intent mIntentToLaunchOnYes;
	private final static String TAG = DialogActivity.class.getName();

	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_dialog);
		for (int button : BUTTONS) {
			findViewById(button).setOnClickListener(this);
		}
		Intent in = this.getIntent();
		if (in == null) {
			throw new NullPointerException(TAG
				+ " must be launched with non null intent");
		}
		final Bundle b = in.getExtras();
		if (b == null) {
			throw new NullPointerException("Null Bundle in " + TAG);
		}
		mTitle = b.getString(DIALOG_TITLE_KEY);
		mText = b.getString(DIALOG_TEXT_KEY);
		mIntentToLaunchOnYes = b.getParcelable(DIALOG_INTENT_KEY);
		setTitle(mTitle);
		TextView tv = (TextView) findViewById(R.id.dialog_text);
		tv.setText(mText);
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			this.setFinishOnTouchOutside(true);
		// default is probably false
		// :http://androidxref.com/4.0.3_r1/xref/frameworks/base/core/java/android/view/Window.java#123
	}

	private static final class IntentBuilder {

		private final Bundle extras = new Bundle();

		IntentBuilder() {}

		IntentBuilder dialogTitle(String s) {
			extras.putString(DIALOG_TITLE_KEY, s);
			return this;
		}

		IntentBuilder dialogText(String s) {
			extras.putString(DIALOG_TEXT_KEY, s);
			return this;
		}

		IntentBuilder dialogIntent(Intent intentToLaunchOnYes) {
			extras.putParcelable(DIALOG_INTENT_KEY, intentToLaunchOnYes);
			return this;
		}

		/**
		 * Returns an intent which can be used to launch a Dialog activity
		 *
		 * @param ctx
		 *            a Context from which the actual package name will be
		 *            retrieved (from docs of
		 *            {@link ComponentName#ComponentName(Context, Class)}
		 * @return an intent used to fire the specified Dialog activity
		 * @throws NullPointerException
		 *             if ctx is null
		 */
		Intent build(Context ctx) {
			// no need for defensive copying I guess
			final Intent i = launchActivityIntent(ctx, DialogActivity.class);
			i.putExtras(extras);
			return i;
		}
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
			try {
				DialogActivity.this.startActivity(mIntentToLaunchOnYes);
			} catch (ActivityNotFoundException e) {
				// see http://stackoverflow.com/questions/20018082/
				// safeguard-against-a-matching-activity-may-not-exist
				// -in-android-settings
				w("The activity was not found" + e.getMessage());
				// not much can be done - maybe display a toast...
			}
			finish(); // TODO : wait for result and launch another activity (my
			// Settings activity) - check CATEGORY_PREFERENCE ^^^^^^^^^^^^^^^^^^
			break;
		case R.id.dialog_activity_no_button:
			finish();
			break;
		}
	}

	public static Intent launchDialogActivityIntent(Context ctx,
			final String title, final String text,
			final Intent intentToLaunchOnYes) {
		return new IntentBuilder().dialogText(text).dialogTitle(title)
			.dialogIntent(intentToLaunchOnYes).build(ctx);
	}
	// directly launches a Dialog Activity
	// public static void launchDialogActivity(Context ctx, final String title,
	// final String text, final Intent intentToLaunchOnYes) {
	// final Intent i = launchDialogActivityIntent(ctx, title, text,
	// intentToLaunchOnYes);
	// ctx.startActivity(i);
	// }
}
