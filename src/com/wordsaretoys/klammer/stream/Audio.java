package com.wordsaretoys.klammer.stream;

import java.util.Arrays;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;

import com.wordsaretoys.klammer.util.Logg;
import com.wordsaretoys.klammer.util.Needle;

/**
 * handles microphone and speaker
 */
public class Audio {

	static String TAG = "Audio";

	static int SampleRate = 8000;
	
	// streamer parent object
	Streamer streamer;

	// speaker playback thread
	Player player;
	
	// mic recording thread
	Recorder recorder;
	
	// audio buffer size in samples
	int bufferSize;

	/**
	 * ctor
	 */
	public Audio(Streamer streamer) {
		this.streamer = streamer;

		Logg.d(TAG, "sample rate " + SampleRate + " Hz");
		
		// use largest of minimum in/out buffer sizes
		bufferSize = Math.max(
			AudioRecord.getMinBufferSize(SampleRate, 
				AudioFormat.CHANNEL_IN_MONO, 
				AudioFormat.ENCODING_PCM_16BIT),
			AudioTrack.getMinBufferSize(SampleRate, 
				AudioFormat.CHANNEL_OUT_MONO, 
				AudioFormat.ENCODING_PCM_16BIT));
		// need multiple of AMR frame size
		bufferSize = (int)Math.ceil(
				bufferSize / Codecs.AudioFrameSize) * Codecs.AudioFrameSize;
		Logg.d(TAG, "buffer size " + bufferSize + " samples");
		
		// initialize codecs
		streamer.getCodecs().acquireAudioCodecs(SampleRate, bufferSize);
		
		// start mic recording thread
		recorder = new Recorder();
		recorder.start();
		recorder.resume();
		
		// start audio playback thread
		player = new Player();
		player.start();
		player.resume();
	}

	/**
	 * copies a packet of audio data to the speaker buffer
	 */
	public void handlePacket(byte[] data, int length) {
		player.collectPacketData(data, length);
	}
	
	/**
	 * buffers microphone audio and submits packets
	 */
	class Recorder extends Needle {

		// audio in object
		AudioRecord mic;
		
		// audio buffer
		short[] buffer;
		
		// packet buffer
		byte[] packet;
		
		public Recorder() {
			super("recorder", 1);
			buffer = new short[bufferSize];
			packet = new byte[Network.BufferSize];
		}
		
		@Override
		public void run() {

			mic = new AudioRecord(
					AudioSource.VOICE_COMMUNICATION,
					SampleRate,
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					buffer.length * 2);
			if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
				Logg.d(TAG, "couldn't initialize microphone");
				onException();
				return;
			}
			
			mic.startRecording();

			while (inPump()) {
				mic.read(buffer, 0, buffer.length);
				for (int i = 0; i < buffer.length; i += Codecs.AudioFrameSize) {
					int outlen = streamer.getCodecs().encodeAudioFrame(buffer, i, packet);
					if (outlen > 0) {
						if (streamer.getNetwork().isReady()) {
							streamer.getNetwork().sendAudioFrame(packet, outlen);
						}
					}
				}
			}
			
			mic.stop();
			mic.release();
		}
	}
	
	/**
	 * streams audio buffer to speaker
	 */
	class Player extends Needle {

		// audio out
		AudioTrack speaker;
		
		// audio & collection buffers
		short[] buffer, collect;
		
		// collection offset
		int offset;

		public Player() {
			super("player", 1);
			buffer = new short[bufferSize];
			collect = new short[bufferSize];
		}
		
		public void collectPacketData(byte[] data, int length) {
			streamer.getCodecs().decodeAudioData(data, length, collect, offset);
			offset += Codecs.AudioFrameSize;
			if (offset >= collect.length) {
				synchronized(this) {
					System.arraycopy(collect, 0, buffer, 0, bufferSize);
				}
				offset = 0;
			}
		}
		
		@Override
		public void run() {
			
			speaker = new AudioTrack(
				AudioManager.STREAM_VOICE_CALL,
				SampleRate,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				buffer.length * 2,	// byte length
				AudioTrack.MODE_STREAM);
			if (speaker.getState() != AudioTrack.STATE_INITIALIZED) {
				Logg.d(TAG, "couldn't initialize speaker");
				onException();
				return;
			}
			speaker.setStereoVolume(1, 1);
			speaker.play();
			
			while (inPump()) {
				synchronized(this) {
					speaker.write(buffer, 0, buffer.length);
					Arrays.fill(buffer, (short) 0);
				}
			}
			
			speaker.stop();
			speaker.release();
		}
	}

	/**
	 * called if mic or speaker throws an exception
	 */
	protected void onException() {}
	
}
