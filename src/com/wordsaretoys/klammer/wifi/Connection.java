package com.wordsaretoys.klammer.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;

import com.wordsaretoys.klammer.util.Logg;

/**
 * maintains wifi direct connection state
 */
public class Connection {

	static String TAG = "Connection";
	
	// wifi parent object
	Wifi wifi;
	
	// broadcast receiver
	Receiver receiver; 
	
	// true if peer is connected to another peer
	boolean connected;
	
	// information about the p2p group
	WifiP2pInfo info;
	
	/**
	 * ctor
	 * @param wifi parent wifi object
	 */
	public Connection(Wifi wifi) {
		receiver = new Receiver();
		this.wifi = wifi;
	}
	
	/**
	 * register for wifi state broadcasts
	 */
	public void register() {
		wifi.getContext().registerReceiver(
			receiver,
			new IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION));
	}
	
	/**
	 * unregister for broadcasts
	 */
	public void unregister() {
		wifi.getContext().unregisterReceiver(receiver);
	}

	/**
	 * get connection state
	 * @return true if we're connected to a peer
	 */
	public boolean isConnected() {
		return connected;
	}

	/**
	 * get information about connected group
	 * NOTE: will be null unless connected!!
	 * @return wifi p2p group info object, or null
	 */
	public WifiP2pInfo getInfo() {
		return info;
	}
	
	/**
	 * disconnect from currently connected peer
	 */
	public void disconnect() {
		wifi.getManager().removeGroup(
				wifi.getChannel(), new ActionListener() {
			@Override
			public void onSuccess() {
				Logg.d(TAG, "manager.removeGroup succeeded");
			}
			@Override
			public void onFailure(int reason) {
				Logg.d(TAG, "manager.removeGroup FAILED for reason " + reason);
				onError(reason);
			}
		});
	}
	
	/**
	 * override in subclass to handle errors
	 * @param reason error code
	 */
	protected void onError(int reason) {}
	
	/**
	 * override in subclass to handle connection state changes
	 * @param connected true if peer is connected
	 */
	protected void onChange(boolean connected) {}
	
	/**
	 * override in subclass to handle connection ready event
	 */
	protected void onReady() {}
	
	/**
	 * receiver for state broadcasts
	 */
	class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Logg.d(TAG, "received action " + action);
			NetworkInfo info = (NetworkInfo) intent.
					getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
			if (info.isConnected()) {
				Logg.d(TAG, "connected");
				// if we're going from disconnected to connected
				if (!connected) {
					connected = true;
					wifi.getManager().requestConnectionInfo(
							wifi.getChannel(), new ConnectionInfoListener() {
						@Override
						public void onConnectionInfoAvailable(WifiP2pInfo info) {
							Connection.this.info = info;
							if (info.groupFormed) {
								Logg.d(TAG, "group formed, connection ready for use");
								onReady();
							} else {
								Logg.d(TAG, "group NOT formed, disconnecting");
								disconnect();
							}
						}
					});
				}
			} else {
				Logg.d(TAG, "disconnected");
				// if we've gone from connected to disconnected
				if (connected) {
					// kicking off another peer discovery
					// helps speed post-disconnect recovery
					wifi.getPeers().discover();
				}
				connected = false;
				info = null;
			}
			onChange(connected);
		}
	}
	
}
