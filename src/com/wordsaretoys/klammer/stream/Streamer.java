package com.wordsaretoys.klammer.stream;

import android.app.Activity;
import android.content.Context;

/**
 * provides streaming services for audio, 
 * visual, and location data
 */

public class Streamer {
	
	static String TAG = "Streamer";
	
	/**
	 * event listener
	 */
	public interface Listener {
		public void onTermination();
	}
	
	// supplied listener
	Listener listener;
	
	// activity context
	Context context;
	
	// network handler
	Network network;
	
	// camera and display manager
	Video video;
	
	// mic and speaker manager
	Audio audio;
	
	// video/audio encoders & decoders
	Codecs codecs;
	
	/**
	 * ctor, creates child objects
	 */
	public Streamer() {
		
		network = new Network(this) {
			@Override
			protected void onConnection() {
				
			}
			@Override
			protected void onTermination() {
				((Activity)context).runOnUiThread(new Runnable() {
					public void run() {
						listener.onTermination();
					}
				});
			}
		};

		video = new Video(this) {
			@Override
			protected void onException() {
				
			}
		};
		
		codecs = new Codecs(this);
		
		audio = new Audio(this) {
			@Override
			protected void onException() {
				
			}
		};
		
	}
	
	/**
	 * call when new activity created
	 * @param context activity context
	 * @param listener event listener
	 */
	public void onCreate(Context context, Listener listener) {
		this.context = context;
		this.listener = listener;
		video.onCreate();
	}

	/**
	 * get activity context
	 * @return activity context
	 */
	public Context getContext() {
		return context;
	}
	
	/**
	 * get network handling object
	 * @return network object
	 */
	public Network getNetwork() {
		return network;
	}
	
	/**
	 * get camera/display manager object
	 * @return video object
	 */
	public Video getVideo() {
		return video;
	}
	
	/**
	 * get mic/speaker manager object 
	 */
	public Audio getAudio() {
		return audio;
	}
	
	/**
	 * get audio/video codec container object
	 * @return codecs container object
	 */
	public Codecs getCodecs() {
		return codecs;
	}
}
