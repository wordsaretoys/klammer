package com.wordsaretoys.klammer.util;


/**
 * miscellaneous static functions
 */
public class Misc {

	/**
	 * convert an NV21 preview frame to ARGB format
	 * taken from http://stackoverflow.com/questions/12469730/confusion-on-yuv-nv21-conversion-to-rgb
	 * 
	 * @param argb
	 * @param yuv
	 * @param width
	 * @param height
	 */
	public static void YUV_NV21_TO_RGB(int[] argb, byte[] yuv, int width, int height) {
	    final int frameSize = width * height;

	    final int ii = 0;
	    final int ij = 0;
	    final int di = +1;
	    final int dj = +1;

	    int a = 0;
	    for (int i = 0, ci = ii; i < height; ++i, ci += di) {
	        for (int j = 0, cj = ij; j < width; ++j, cj += dj) {
	            int y = (0xff & ((int) yuv[ci * width + cj]));
	            int v = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 0]));
	            int u = (0xff & ((int) yuv[frameSize + (ci >> 1) * width + (cj & ~1) + 1]));
	            y = y < 16 ? 16 : y;

/*	            
	            int r = (int) (1.164f * (y - 16) + 1.596f * (v - 128));
	            int g = (int) (1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
	            int b = (int) (1.164f * (y - 16) + 2.018f * (u - 128));
*/

	            // removed original FP conversions for performance reasons
	            // switching to scaled integer improved things a lot
	            // constants are original FP constants times 1024
	            // bit shfting right by 10 is a FAST divide by 1024 
	            int a0 = 1192 * (y - 16);
	            int a1 = 1634 * (v - 128);
	            int a2 = 832 * (v - 128);
	            int a3 = 400 * (u - 128);
	            int a4 = 2066 * (u - 128);
	            
	            int r = (a0 + a1) >> 10;
	            int g = (a0 - a2 - a3) >> 10;
	            int b = (a0 + a4) >> 10;
	            
	            r = r < 0 ? 0 : (r > 255 ? 255 : r);
	            g = g < 0 ? 0 : (g > 255 ? 255 : g);
	            b = b < 0 ? 0 : (b > 255 ? 255 : b);

	            argb[a++] = 0xff000000 | (r << 16) | (g << 8) | b;
	        }
	    }
	}
	
	/*
	 * taken from http://stackoverflow.com/questions/13458289/encoding-h-264-from-camera-with-android-mediacodec?rq=1	
	 */
	public static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
		/* 
		 * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
		 * So we just have to reverse U and V.
		 */
		final int frameSize = width * height;
		final int qFrameSize = frameSize >> 2;
		
		System.arraycopy(input, 0, output, 0, frameSize); // Y
		System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
		System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)
		
		return output;
	}
	
	/**
	 * return size of a YV12 buffer for the given dimensions
	 */
	public static int getYV12BufferSize(int width, int height) {
		int yStride = (int) Math.ceil(width / 16.0) * 16;
		int uvStride = (int) Math.ceil( (yStride / 2) / 16.0) * 16;
		int ySize = yStride * height;
		int uvSize = uvStride * height / 2;
		return ySize + uvSize * 2;		
	}

	/**
	 * stretch-copy a YUV image with UV swapping and rotation
	 */
	public static void rotateY12toYUV420(byte[] input, byte[] output, int width, int height, int rotation) {
		boolean swap = (rotation == 90 || rotation == 270);
		boolean flip = (rotation == 90 || rotation == 180);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int xo = x, yo = y;
				int w = width, h = height;
				int xi = xo, yi = yo;
				if (swap) {
					xi = w * yo / h;
					yi = h * xo / w;
				}
				if (flip) {
					xi = w - xi - 1;
					yi = h - yi - 1;
				}
				output[w * yo + xo] = input[w * yi + xi];
				int fs = w * h;
				int qs = (fs >> 2);
				xi = (xi >> 1);
				yi = (yi >> 1);
				xo = (xo >> 1);
				yo = (yo >> 1);
				w = (w >> 1);
				h = (h >> 1);
				int ui = fs + w * yi + xi;
				int uo = fs + w * yo + xo;
				int vi = qs + ui;
				int vo = qs + uo;
				output[uo] = input[vi]; 
				output[vo] = input[ui]; 
			}
		}
	}	

	/**
	 * return the root-mean-square value for an audio buffer
	 */
	public static float getRms(short[] buffer) {
		long mean = 0;
		for (int i = 0; i < buffer.length; i++) {
			mean += buffer[i] * buffer[i];
		}
		return (float) Math.sqrt((float) mean / (float) buffer.length);
	}
	
	public static class ToneGenerator {
		float rate, time;
		public ToneGenerator(int sampleRate, float frequency) {
			rate = (float)(2 * Math.PI * frequency) / (float) sampleRate;
		}
		public void fill(short[] buffer) {
			for (int i = 0; i < buffer.length; i++) {
				buffer[i] = (short)(16384 * Math.sin(time + Math.sin(0.025 * time)));
				time += rate;
			}
		}
	}
	
	public static String byteArrayToString(byte[] b) {
		String s = new String();
		for (int i = 0; i < b.length; i++) {
			s += Integer.toHexString(b[i]) + " ";
		}
		return s;
	}

	public static String shortArrayToString(short[] b) {
		String s = new String();
		for (int i = 0; i < b.length; i++) {
			s += Integer.toHexString(b[i]) + " ";
		}
		return s;
	}
	
	public static int countZeros(short[] a) {
		int count = 0;
		for (int i = 0; i < a.length; i++) {
			if (a[i] == 0) {
				count++;
			}
		}
		return count;
	}
	
}
