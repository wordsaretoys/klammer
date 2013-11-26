package com.wordsaretoys.klammer.stream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import com.wordsaretoys.klammer.util.Logg;
import com.wordsaretoys.klammer.util.Needle;

/**
 * manages the wifi socket and the read/write threads
 */
public class Network {

	static String TAG = "Network";
	
	// IP port
	static int Port = 54321;
	
	// read and write buffer length
	static int BufferSize = 65000;
	
	// streaming parent object
	Streamer streamer;
	
	// datagram socket
	DatagramSocket socket;

	// destination socket address
	InetSocketAddress sendToAddr;
	
	// stream reader thread
	Reader reader;
	
	// stream writer thread
	Writer writer;
	
	// true if device is owner
	boolean isOwner;
	
	// true if network is fully set up
	boolean isReady;
	
	/**
	 * ctor
	 * @param streamer parent object
	 */
	public Network(Streamer streamer) {
		this.streamer = streamer;
		
		try {
			socket = new DatagramSocket(Port);
			socket.setReceiveBufferSize(BufferSize);
			socket.setSendBufferSize(BufferSize);
		} catch (SocketException e) {
			Logg.d(TAG, "Socket exception while creating datagram socket, terminating");
			e.printStackTrace();
			onTermination();
			return;
		}
		
		reader = new Reader();
		reader.start();
		reader.resume();
		
		writer = new Writer();
		writer.start();
	}
	
	/**
	 * initiate connection to host (or wait for connection, if host)
	 * 
	 * not a "true" connection in the TCP sense; just setting up the
	 * "ready to handle packets" state and who to send packets to
	 * 
	 * @param host IP address of host
	 * @param owner true if peer is owner of P2P group
	 */
	public void connect(String host, boolean owner) {
		isOwner = owner;
		Logg.d(TAG, (owner ? "acting as SERVER" : "acting as CLIENT"));
		
		try {
			// if I'm the "server", I have to wait for a packet from the
			// "client" to know who to send to; if I'm the "client", I'm
			// sending to the "server" address (i.e., the group owner).
			writer.packet = isOwner ? null : new DatagramPacket(
					new byte[BufferSize], 
					BufferSize, 
					new InetSocketAddress(host, Port));
		} catch (SocketException e) {
			Logg.d(TAG, "Socket exception while initiating connection, terminating");
			e.printStackTrace();
			onTermination();
			return;
		}
		
		// signal that we're ready to go
		isReady = true;
		onConnection();
	}

	/**
	 * shut down communications
	 */
	public void disconnect() {
		isReady = false;
	}
	
	/**
	 * close datagram socket
	 * ONLY call if app is finishing!
	 */
	public void close() {
		if (socket != null) {
			socket.close();
		}
	}

	/**
	 * get ready state of network
	 * @return true if network is ready to recv/xmit
	 */
	public boolean isReady() {
		return isReady;
	}

	
	/**
	 * send a frame of video data
	 */
	public synchronized void sendVideoFrame(byte[] data, int length) {
		submitPacket((byte)0, data, length);
	}

	/**
	 * send a frame of audio data
	 */
	public synchronized void sendAudioFrame(byte[] data, int length) {
		submitPacket((byte)1, data, length);
	}

	/**
	 * send a frame of video data
	 */
	private void submitPacket(byte type, byte[] frame, int length) {
		if (length < BufferSize && writer.packet != null) {
			byte[] pd = writer.packet.getData();
			pd[0] = type;
			System.arraycopy(frame, 0, pd, 1, length);
			// TODO: note that synchronization ends
			// once the writer thread has the packet
			// video & audio could overwrite the other
			writer.packet.setLength(length + 1);
			writer.resume();
		}
	}
	
	/**
	 * packet reader thread class
	 */
	class Reader extends Needle {

		public DatagramPacket packet =
				new DatagramPacket(new byte[BufferSize], BufferSize);
		byte[] data = new byte[BufferSize];

		public Reader() {
			super("packet-reader", 1);
		}
		
		@Override
		public void run() {
			
			while (inPump()) {
				
				try {
					socket.receive(packet);
				} catch (IOException e) {
					// might be thrown if we're receiving packets
					// during Wifi Direct disconnection, so we'll
					// just quietly eat it
				}
//				Logg.d(TAG, "received packet from " + readPacket.getAddress().getHostAddress());
				
				// if we're not connected, just drop the packet
				if (!isReady) {
					continue;
				}
				
				// if we don't have a write packet yet
				if (writer.packet == null) {
					// the first packet received tells us 
					// who we should be sending to
					String rip = packet.getAddress().getHostAddress();
					try {
						writer.packet = new DatagramPacket(
								new byte[BufferSize], 
								BufferSize, 
								new InetSocketAddress(rip, Port));
					} catch (SocketException e) {
						Logg.d(TAG, "Socket exception while initiating connection, terminating");
						e.printStackTrace();
						onTermination();
						return;
					}
				}
				
				byte[] pd = packet.getData();
				int l = packet.getLength() - 1;
				System.arraycopy(pd, 1, data, 0, l);
				switch (pd[0]) {

				case 0:	// video frame
					streamer.getVideo().handlePacket(data, l);
					break;
					
				case 1: // audio frame
					streamer.getAudio().handlePacket(data, l);
					break;
				
				default:
					Logg.d(TAG, "received unknown packet type (" + pd[0] + ")");
					break;
				}
			}
		}
	}
	
	/**
	 * packet writer thread class
	 */
	class Writer extends Needle {
		
		public DatagramPacket packet;
		
		public Writer() {
			super("packet-writer", 1);
		}
		
		@Override
		public void run() {
			while (inPump()) {
				
				// ignore spurious submissions if we're not connected
				if (!isReady) {
					continue;
				}
				
				try {
					socket.send(packet);
				} catch (IOException e) {
					// might be thrown if we're sending packets
					// during Wifi Direct disconnection, so we'll
					// just quietly eat it
				}
				
				pause();
			}
		}
	}
	
	/**
	 * called when socket connection is ready
	 * override in subclass
	 */
	protected void onConnection() {}
	
	/**
	 * called when socket terminates for any reason
	 * override in subclass
	 */
	protected void onTermination() {}

}