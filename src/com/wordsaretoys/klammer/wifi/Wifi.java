package com.wordsaretoys.klammer.wifi;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;

import com.wordsaretoys.klammer.util.Logg;

/**
 * maintains wifi objects and listener
 */
public class Wifi {

	String TAG = "Wifi";

	/**
	 * listener for wifi intents
	 */
	public interface Listener {
		public void onStateChanged(boolean enabled);
		public void onPeersChanged();
		public void onConnectionChanged(boolean connected);
		public void onConnectionReady();
		public void onDeviceChanged();
		public void onError(int code);
	}
	
	// activity context
	Context context;
	
	// wifi manager object
	WifiP2pManager manager;
	
	// wifi framework channel
	Channel channel;

	// wifi state object
	State state;
	
	// wifi peers list object
	Peers peers;
	
	// wifi connection object
	Connection connection;
	
	// wifi device object
	Device device;

	// event listener
	Listener listener;
	
	/**
	 * ctor
	 */
	public Wifi() {
		
		state = new State(this) {
			@Override
			public void onChange(boolean enabled) {
				listener.onStateChanged(enabled);
			}
		};
		
		peers = new Peers(this) {
			@Override
			public void onChange() {
				listener.onPeersChanged();
			}
			@Override
			public void onError(int reason) {
				listener.onError(reason);
			}
		};
		
		connection = new Connection(this) {
			@Override
			public void onChange(boolean connected) {
				listener.onConnectionChanged(connected);
			}
			@Override
			public void onReady() {
				listener.onConnectionReady();
			}
			@Override
			public void onError(int reason) {
				listener.onError(reason);
			}
		};
		
		device = new Device(this){
			@Override
			public void onChange() {
				listener.onDeviceChanged();
			}
		};
		
		Logg.d(TAG, "created wifi container object");
		
	}
	
	/**
	 * create manager and channel to api
	 * @param context activity context
	 * @parma listener event listener
	 */
	public void onCreate(Context context, Listener listener) {
		this.listener = listener;
		this.context = context;
		manager = (WifiP2pManager) 
				context.getSystemService(Context.WIFI_P2P_SERVICE);
		createChannel();
	}
	
	/**
	 * attempt to create channel until it's established
	 */
	protected void createChannel() {
		channel = manager.initialize(
				context, 
				context.getMainLooper(), 
				new ChannelListener() {
			@Override
			public void onChannelDisconnected() {
				Logg.d(TAG, "Lost wifi channel, reconnecting...");
				createChannel();
			}
		});
	}

	/**
	 * register for wifi events
	 */
	public void register() {
		state.register();
		peers.register();
		connection.register();
		device.register();
	}
	
	/**
	 * unregister for wifi events
	 */
	public void unregister() {
		state.unregister();
		peers.unregister();
		connection.unregister();
		device.unregister();
	}
	
	/**
	 * get wifi direct manager
	 * @return manager object
	 */
	public WifiP2pManager getManager() {
		return manager;
	}
	
	/**
	 * get wifi direct framework channel
	 * @return channel object
	 */
	public Channel getChannel() {
		return channel;
	}

	/**
	 * get current activity context
	 * @return context object
	 */
	public Context getContext() {
		return context;
	}

	/**
	 * get wifi state object
	 * @return state object
	 */
	public State getState() {
		return state;
	}
	
	/**
	 * get wifi peers list object
	 * @return peers list object
	 */
	public Peers getPeers() {
		return peers;
	}
	
	/**
	 * get wifi connection object
	 * @return connection object
	 */
	public Connection getConnection() {
		return connection;
	}
	
	/**
	 * get wifi device object
	 * @return device object
	 */
	public Device getDevice() {
		return device;
	}
}
