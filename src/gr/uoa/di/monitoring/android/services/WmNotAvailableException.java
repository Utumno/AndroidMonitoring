package gr.uoa.di.monitoring.android.services;

public class WmNotAvailableException extends RuntimeException {

	private static final long serialVersionUID = 8734273345573907907L;
	final static String MESSAGE = "Wifi Manager is not available";

	public WmNotAvailableException() {
		super(MESSAGE);
	}
}
