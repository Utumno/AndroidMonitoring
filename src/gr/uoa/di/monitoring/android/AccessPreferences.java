package gr.uoa.di.monitoring.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public final class AccessPreferences {

	private static final Set<Class<?>> CLASSES = new HashSet<Class<?>>();
	static {
		CLASSES.add(Boolean.class);
		CLASSES.add(Float.class);
		CLASSES.add(Integer.class);
		CLASSES.add(Long.class);
		CLASSES.add(String.class);
		CLASSES.add(Set.class);
	}

	private AccessPreferences() {}

	private static SharedPreferences prefs;

	private static synchronized SharedPreferences getPrefs(Context ctx) {
		if (prefs == null) {
			prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		}
		return prefs;
	}

	// TODO: threads ?
	// TODO : nulls ?
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static <T> void persist(Context ctx, String key, T value) {
		@SuppressLint("CommitPrefEdits")
		final Editor ed = getPrefs(ctx).edit();
		if (value == null) {
			// commit it as that is exactly what the API does - can be retrieved
			// as anything but if you give get() a default non null value it
			// will give this default value back
			ed.putString(key, null);
		} else if (value instanceof Boolean) ed
				.putBoolean(key, (Boolean) value);
		else if (value instanceof Float) ed.putFloat(key, (Float) value);
		else if (value instanceof Integer) ed.putInt(key, (Integer) value);
		else if (value instanceof Long) ed.putLong(key, (Long) value);
		else if (value instanceof String) ed.putString(key, (String) value);
		else if (value instanceof Set) {
			if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
				throw new IllegalStateException(
						"You can add sets in the preferences only after API "
								+ Build.VERSION_CODES.HONEYCOMB);
			}
			// The given set does not contain strings only --> not my problem
			// Set<?> set = (Set<?>) value;
			// if (!set.isEmpty()) {
			// for (Object object : set) {
			// if (!(object instanceof String))
			// throw new IllegalArgumentException(
			// "The given set does not contain strings only");
			// }
			// }
			@SuppressWarnings({ "unchecked", "unused" })
			Editor soIcanAddSuppress = ed
					.putStringSet(key, (Set<String>) value);
		} else throw new IllegalArgumentException("The given value : " + value
				+ " cannot be persisted");
		ed.commit();
	}

	@SuppressWarnings("unchecked")
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static <T> T retrieve(Context ctx, String key, T defaultValue) {
		// if the value provided as defaultValue is null I can't get its class
		if (defaultValue == null) {
			// if the key (which can very well be null btw) !exist I return null
			// which is both the default value provided and what Android would
			// do (as in return the default value) (TODO: test)
			if (!getPrefs(ctx).contains(key)) return null;
			// if the key does exist I get the value and
			final Object value = getPrefs(ctx).getAll().get(key);
			// if null I return null
			if (value == null) return null;
			// if not null I get the value of the class. Problem is that as far
			// as the type system is concerned T is of the type the variable
			// that is to receive the default value is. So :
			// String s = AccessPreferences.retrieve(this, "key", null);
			// if `"key" --> true` or `"key" --> 1.2` a ClassCastException will
			// occur - FIXME
			final Class<?> clazz = value.getClass();
			for (Class<?> cls : CLASSES) {
				if (clazz.isAssignableFrom(cls)) {
					// I can't directly cast to T as value may be boolean for
					// instance
					return (T) clazz.cast(value);
				}
			}
			// that's really Illegal State I guess
			throw new IllegalStateException("Unknown class for value :\n\t"
					+ value + "\nstored in preferences");
		} else if (defaultValue instanceof Boolean) return (T) (Boolean) getPrefs(
				ctx).getBoolean(key, (Boolean) defaultValue);
		else if (defaultValue instanceof Float) return (T) (Float) getPrefs(ctx)
				.getFloat(key, (Float) defaultValue);
		else if (defaultValue instanceof Integer) return (T) (Integer) getPrefs(
				ctx).getInt(key, (Integer) defaultValue);
		else if (defaultValue instanceof Long) return (T) (Long) getPrefs(ctx)
				.getLong(key, (Long) defaultValue);
		else if (defaultValue instanceof String) return (T) getPrefs(ctx)
				.getString(key, (String) defaultValue);
		else if (defaultValue instanceof Set) {
			if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
				throw new IllegalStateException(
						"You can add sets in the preferences only after API "
								+ Build.VERSION_CODES.HONEYCOMB);
			}
			// The given set does not contain strings only --> not my problem
			// Set<?> set = (Set<?>) defaultValue;
			// if (!set.isEmpty()) {
			// for (Object object : set) {
			// if (!(object instanceof String))
			// throw new IllegalArgumentException(
			// "The given set does not contain strings only");
			// }
			// }
			return (T) getPrefs(ctx).getStringSet(key,
					(Set<String>) defaultValue);
		} else throw new IllegalArgumentException(defaultValue
				+ " cannot be persisted in SharedPreferences");
	}
}
