package gr.uoa.di.monitoring.android.services;

@SuppressWarnings("serial")
public class WmNotAvailableException extends Exception {

	final static String MESSAGE = "Wifi Manager is not available";

	public WmNotAvailableException() {
		super(MESSAGE);
	}
}
