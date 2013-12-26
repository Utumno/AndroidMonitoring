package gr.uoa.di.monitoring.android;

public interface Logging {

	void w(String msg);

	void w(String msg, Throwable t);

	void d(String msg);

	void v(String msg);
}
