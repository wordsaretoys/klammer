package com.wordsaretoys.klammer.stream;

import java.io.IOException;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.wordsaretoys.klammer.R;
import com.wordsaretoys.klammer.util.Logg;
import com.wordsaretoys.klammer.util.Misc;

/**
 * handles camera control and local/remote video displays
 */
public class Video {

	static String TAG = "Video";

	// parent streamer object
	Streamer streamer;
	
	// surface view for local preview
	SurfaceView preview;
	
	// surface view for remote image
	SurfaceView remote;
	
	// camera object
	Camera camera;
	
	// information about camera configuration
	CameraInfo cameraInfo;
	
	// hardware id of active camera
	int cameraId;
	
	/**
	 * ctor
	 * @param streamer parent object
	 */
	public Video(Streamer streamer) {
		this.streamer = streamer;
	}

	/**
	 * sets up displays and locates the camera
	 * call on activity creation
	 */
	public void onCreate() {
		Activity activity = (Activity) streamer.getContext();
		preview = (SurfaceView) activity.findViewById(R.id.preview);
		remote = (SurfaceView) activity.findViewById(R.id.remote);

		preview.setZOrderMediaOverlay(true);
		preview.getHolder().addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				try {
					camera.setPreviewDisplay(preview.getHolder());
				} catch (IOException e) {
					Logg.d(TAG, "exception while setting preview display");
					e.printStackTrace();
					onException();
				}
				camera.startPreview();
			}
			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {}
			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {}
		});

		remote.getHolder().addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				streamer.getCodecs().acquireVideoDecoder(remote);
			}
			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {}
			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {}
		});
		
	}
	
	/**
	 * opens camera
	 * typically called on activity resume
	 */
	public void acquireCamera() {
		// TODO: pick camera id from preferences
		// if known, or pick first front-facing camera
		try {
			camera = Camera.open(cameraId);
		} catch (Exception e) {
			Logg.d(TAG, "Exception on opening camera");
			e.printStackTrace();
			onException();
			return;
		}
		cameraInfo = new CameraInfo();
		Camera.getCameraInfo(cameraId, cameraInfo);
		
		// TODO: examine camera profiles to insure availability
		// must insure YV12, 15 fps, 320x240, etc are available
		// adjust to whatever you can, disallow video if u can't
		Camera.Parameters params = camera.getParameters();
		params.setPreviewFormat(ImageFormat.YV12);
		camera.setParameters(params);
		
		final int width = params.getPreviewSize().width;
		final int height = params.getPreviewSize().height;
		Logg.d(TAG, "camera image size (" + width + "," + height + ")");

		streamer.getCodecs().acquireVideoEncoder(width, height);
		
		// preload camera with two buffers
		int sz = Misc.getYV12BufferSize(width, height);
		camera.addCallbackBuffer(new byte[sz]);
		camera.addCallbackBuffer(new byte[sz]);
		
		camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
			byte[] cb;
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				// get current rotation and apply to camera
				int rotation = getRotation();
				try {
					camera.setDisplayOrientation(rotation);
				} catch (RuntimeException e) {
					// preview frame may be called after camera release
				}
				// if peer is available
				if (streamer.getNetwork().isReady()) {
					// give me a conversion buffer if I don't have one
					if (cb == null || cb.length != data.length) {
						cb = new byte[data.length];
					}
					// rotate image and flip UV planes
					Misc.rotateY12toYUV420(data, cb, width, height, rotation);
					// encode and transmit 
					int outlen = streamer.getCodecs().encodeVideoFrame(cb, data);
					streamer.getNetwork().sendVideoFrame(data, outlen);
				}
				// hand the buffer back from another go
				camera.addCallbackBuffer(data);
			}
		});
		
		preview.bringToFront();
	}
	
	/**
	 * tracks display rotation state
	 */
	private int getRotation() {
		Activity activity = (Activity) streamer.getContext();
		Display display = activity.getWindowManager().getDefaultDisplay();
		int rotation = display.getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0: degrees = 0; break;
		case Surface.ROTATION_90: degrees = 90; break;
		case Surface.ROTATION_180: degrees = 180; break;
		case Surface.ROTATION_270: degrees = 270; break;
		}
		int result = 0;
		if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
			result = (cameraInfo.orientation + degrees) % 360;
			result = (360 - result) % 360;	// compensate the mirror
		} else {
			result = (cameraInfo.orientation - degrees + 360) % 360;
		}
		return result;
	}
	
	/**
	 * close camera and stop previewing
	 * typically called on activity pause
	 */
	public void releaseCamera() {
		camera.stopPreview();
		camera.release();
		streamer.getCodecs().releaseVideoCodecs();
	}

	/**
	 * handle video frame packet
	 */
	public void handlePacket(byte[] data, int length) {
		streamer.getCodecs().decodeVideoData(data, length);
	}

	/**
	 * called if camera or display throws an exception
	 */
	protected void onException() {}
}