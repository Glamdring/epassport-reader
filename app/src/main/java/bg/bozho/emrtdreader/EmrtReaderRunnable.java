package bg.bozho.emrtdreader;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

public abstract class EmrtReaderRunnable implements Runnable {

	protected Context context;

	protected Activity activity;

	public EmrtReaderRunnable(Context ctx, Activity activity) {
		this.context = ctx;
		this.activity = activity;
	}

	protected void showToast(final String message) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
				toast.show();
			}
		});
	}

}
