package com.wordsaretoys.klammer.util;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.TextView;

public class Logg {

	static Context context;
	static TextView view;
	
	public static void onCreate(Context c, int resId) {
		context = c;
		view = (TextView) ((Activity) context).findViewById(resId);
	}
	
	public static void d(String tag, String msg) {
		Log.d(tag, msg);
		final String ln = tag + " / " + msg + "\n";
		((Activity)context).runOnUiThread(new Runnable() {
			public void run() {
				view.append(ln);
			}
		});
	}
	
}
