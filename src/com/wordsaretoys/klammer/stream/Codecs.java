package com.wordsaretoys.klammer.stream;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.SurfaceView;

import com.wordsaretoys.klammer.util.Logg;

/**
 * maintains audio and video codec state/functions
 */
public class Codecs {

	static String TAG = "Codecs";
	
	// buffer acquisition timeout
	static final int Timeout = 10000;
	
	// video format parameters
	static String VideoMimeType = "video/mp4v-es";
	static int VideoBitRate = 500000;
	static int VideoFrameRate = 15;
	static int VideoKeyFrameInterval = 5;

	// audio format parameters
	static String AudioMimeType = "audio/3gpp";
	static int AudioBitRate = 12200;
	static int AudioChannelCount = 1;
	static int AudioFrameSize = 160;
	
	// parent streamer object
	Streamer streamer;

	// video codecs
	MediaCodec videoEncoder, videoDecoder;
	
	// audio codecs
	MediaCodec audioEncoder, audioDecoder;
	
	// codec configuration packet data
	byte[] videoConfig;

	/**
	 * ctor
	 */
	public Codecs(Streamer streamer) {
		this.streamer = streamer;
	}
	
	/**
	 * create and configure the video encoder object
	 * @param width camera frame width
	 * @param height camera frame height
	 */
	public void acquireVideoEncoder(int width, int height) {
		// TODO: check available codec parameters more closely
		// don't assume your requested frame rate, bit rate, 
		// picture size, etc, will be available...coordinate
		// with the available camera profiles.
		videoEncoder = MediaCodec.createEncoderByType(VideoMimeType);

		MediaFormat format = 
				MediaFormat.createVideoFormat(VideoMimeType, width, height);
		format.setInteger(MediaFormat.KEY_BIT_RATE, VideoBitRate);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, VideoFrameRate);
		format.setInteger(
				MediaFormat.KEY_COLOR_FORMAT, 
				MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VideoKeyFrameInterval);

		videoEncoder.configure(format, null, null, 
				MediaCodec.CONFIGURE_FLAG_ENCODE);
		videoEncoder.start();
	}

	/**
	 * create and configure a video decoder from target surface
	 * @param surface view that will render images
	 */
	public void acquireVideoDecoder(SurfaceView surfaceView) {
		
		videoDecoder = MediaCodec.createDecoderByType(VideoMimeType);

		MediaFormat format = 
				MediaFormat.createVideoFormat(VideoMimeType,
						surfaceView.getWidth(),
						surfaceView.getHeight() );
		videoDecoder.configure(format, 
				surfaceView.getHolder().getSurface(), null, 0);
		videoDecoder.start();
	}

	/**
	 * stop and release the video codecs
	 */
	public void releaseVideoCodecs() {
		try { 
			videoDecoder.stop();
			videoDecoder.release();			
		} catch (IllegalStateException e) {}
		try { 
			videoEncoder.stop();
			videoEncoder.release();			
		} catch (IllegalStateException e) {}
	}
	
	/**
	 * create and configure the audio encoder & decoder objects
	 * @param sampleRate audio sampling rate in Hz
	 */
	public void acquireAudioCodecs(int sampleRate, int bufferSize) {

		MediaFormat format = MediaFormat.createAudioFormat(
				AudioMimeType, sampleRate, AudioChannelCount);
		format.setInteger(MediaFormat.KEY_BIT_RATE, AudioBitRate);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize * 2);

		audioEncoder = MediaCodec.createEncoderByType(AudioMimeType);
		audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		audioEncoder.start();
		
		audioDecoder = MediaCodec.createDecoderByType(AudioMimeType);
		audioDecoder.configure(format, null, null, 0);
		audioDecoder.start();
	}

	/**
	 * stop and release the audio codecs
	 */
	public void releaseAudioCodecs() {
		try {
			audioDecoder.stop();
			audioDecoder.release();
		} catch (IllegalStateException e) {}
		try {
			audioEncoder.stop();
			audioEncoder.release();
		} catch (IllegalStateException e) {}
	}
	
	/**
	 * encode a frame of video data
	 */
	public int encodeVideoFrame(byte[] frame, byte[] data) {

		try {
			// place the frame data into the encoder
			ByteBuffer[] inBuffers = videoEncoder.getInputBuffers(); 
			int index = videoEncoder.dequeueInputBuffer(Timeout);
			if (index >= 0) {
				ByteBuffer ib = inBuffers[index];
				ib.clear();
				ib.put(frame);
				videoEncoder.queueInputBuffer(
						index, 0, 
						frame.length, 0, 0);
			}
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during video encode input");
			return 0;
		}
		
		try {
			// retreive the encoded data into a buffer
			ByteBuffer[] outBuffers = videoEncoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			int index = videoEncoder.dequeueOutputBuffer(info, Timeout);
			if (index >= 0) {
				ByteBuffer ob = outBuffers[index];
				// if this is codec config data
				if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// make a copy of it
					videoConfig = new byte[info.size];
					ob.get(data, 0, info.size);
					System.arraycopy(data, 0, videoConfig, 0, info.size);
				} else {
					// attach the config data as a header to all others
					// (initial packet with config data may be lost...)
					System.arraycopy(videoConfig, 0, data, 0, videoConfig.length);
					ob.get(data, videoConfig.length, info.size);
					info.size += videoConfig.length;
				}
				ob.clear();
				videoEncoder.releaseOutputBuffer(index, false);
			} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				outBuffers = videoEncoder.getOutputBuffers();
			} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				Logg.d(TAG, "video encoder format changed: "
						+ videoEncoder.getOutputFormat());
			}
			return info.size;
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during video encode output");
			return 0;
		}
	}
	
	/**
	 * decode a packet of video data
	 * will render frame to the configured surface
	 */
	public void decodeVideoData(byte[] data, int length) {
		
		try {
			ByteBuffer[] inBuffers = videoDecoder.getInputBuffers(); 
			int index = videoDecoder.dequeueInputBuffer(Timeout);
			if (index >= 0) {
				ByteBuffer ib = inBuffers[index];
				ib.clear();
				ib.put(data, 0, length);
				videoDecoder.queueInputBuffer(index, 0, length, 0, 0);
			}
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during video decode input");
			return;
		}
		
		try {
			BufferInfo info = new BufferInfo();
			int index = videoDecoder.dequeueOutputBuffer(info, Timeout);
			if (index >= 0) {
				videoDecoder.releaseOutputBuffer(index, true);
			} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				//outBuffers = videoEncoder.getOutputBuffers();
			} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				Logg.d(TAG, "video decoder format changed: "
					+ videoDecoder.getOutputFormat());
			}
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during video decode output");
		}
	}

	/**
	 * encode a frame of audio data
	 */
	public int encodeAudioFrame(short[] frame, int offset, byte[] data) {

		try {
			// place the frame data into the encoder
			ByteBuffer[] inBuffers = audioEncoder.getInputBuffers(); 
			int index = audioEncoder.dequeueInputBuffer(Timeout);
			if (index >= 0) {
				ShortBuffer ib = inBuffers[index].asShortBuffer();
				ib.clear();
				ib.put(frame, offset, AudioFrameSize);
				audioEncoder.queueInputBuffer(
						index, 0, AudioFrameSize * 2, 0, 0);
			}
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during audio encode input");
			return 0;
		}
		
		try {
			// retreive the encoded data into a buffer
			ByteBuffer[] outBuffers = audioEncoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			int index = audioEncoder.dequeueOutputBuffer(info, Timeout);
			if (index >= 0) {
				ByteBuffer ob = outBuffers[index];
				ob.get(data, 0, info.size);
				ob.clear();
				audioEncoder.releaseOutputBuffer(index, false);
			} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				outBuffers = audioEncoder.getOutputBuffers();
			} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				Logg.d(TAG, "audio encoder format changed: "
						+ audioEncoder.getOutputFormat());
			}
			return info.size;
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during audio encode output");
			return 0;
		}
	}
	
	/**
	 * decode a packet of audio data
	 */
	public void decodeAudioData(byte[] data, int length, short[] frame, int offset) {

		// place packet data into the encoder
		try {
			ByteBuffer[] inBuffers = audioDecoder.getInputBuffers(); 
			int index = audioDecoder.dequeueInputBuffer(Timeout);
			if (index >= 0) {
				ByteBuffer ib = inBuffers[index];
				ib.clear();
				ib.put(data, 0, length);
				audioDecoder.queueInputBuffer(index, 0, length, 0, 0);
			}
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during audio decode input");
			return;
		}

		try {
			// retreive the decoded data into a buffer
			ByteBuffer[] outBuffers = audioDecoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			int index = audioDecoder.dequeueOutputBuffer(info, Timeout);
			if (index >= 0) {
				ShortBuffer ob = outBuffers[index].asShortBuffer();
				ob.get(frame, offset, info.size >> 1);
				ob.clear();
				audioDecoder.releaseOutputBuffer(index, false);
			} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				outBuffers = audioDecoder.getOutputBuffers();
			} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				Logg.d(TAG, "audio decoder format changed: "
						+ audioDecoder.getOutputFormat());
			}
		} catch (IllegalStateException e) {
			Logg.d(TAG, "illegal state encountered during audio decode output");
		}
	}
}
