package com.wordsaretoys.klammer.wifi;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;

import com.wordsaretoys.klammer.util.Logg;

/**
 * maintains list of wifi peers
 */
public class Peers {

	static String TAG = "Peers";
	
	// wifi parent object
	Wifi wifi;
	
	// broadcast receiver
	Receiver receiver;
	
	// list of last known peer devices
	ArrayList<WifiP2pDevice> devices;
	
	/**
	 * ctor
	 * @param wifi parent wifi object
	 */
	public Peers(Wifi wifi) {
		this.wifi = wifi;
		receiver = new Receiver();
		devices = new ArrayList<WifiP2pDevice>();
	}
	
	/**
	 * register for wifi peer broadcasts
	 */
	public void register() {
		wifi.getContext().registerReceiver(
			receiver,
			new IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION));
	}
	
	/**
	 * unregister for broadcasts
	 */
	public void unregister() {
		wifi.getContext().unregisterReceiver(receiver);
	}

	/**
	 * get last known list of peers
	 * @return list of peers
	 */
	public ArrayList<WifiP2pDevice> getDeviceList() {
		return devices;
	}
	
	/**
	 * initial peer discovery
	 */
	public void discover() {
		wifi.getManager().discoverPeers(
				wifi.getChannel(), new ActionListener() {
			@Override
			public void onSuccess() {
				Logg.d(TAG, "discoverPeers succeeded");
			}
			@Override
			public void onFailure(int reason) {
				Logg.d(TAG, "discoverPeers FAILED for reason " + reason);
				onError(reason);
			}
		});
	}
	
	/**
	 * connect to an existing peer device
	 * @param deviceName name of peer device
	 */
	public void connectTo(String deviceName) {
		// look for name in collection
		WifiP2pDevice device = null;
		for (WifiP2pDevice d : devices) {
			if (d.deviceName.equals(deviceName)) {
				device = d;
				break;
			}
		}
		
		// TODO: test for device == null
		
		WifiP2pConfig config = new WifiP2pConfig();
		config.deviceAddress = device.deviceAddress;
		wifi.getManager().connect(
				wifi.getChannel(), config, new ActionListener() {
			@Override
			public void onSuccess() {
				Logg.d(TAG, "manager.connect succeeded");
			}
			@Override
			public void onFailure(int reason) {
				Logg.d(TAG, "manager.connect FAILED for reason " + reason);
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
	 * override in subclass to handle peer list changes
	 */
	protected void onChange() {}
	
	/**
	 * receiver for peer broadcasts
	 */
	class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Logg.d(TAG, "received action " + action);
			// request list of peers
			wifi.getManager().requestPeers(
					wifi.getChannel(), new PeerListListener() {
				@Override
				public void onPeersAvailable(WifiP2pDeviceList peers) {
					// retreive list of devices
					devices.clear();
					for (WifiP2pDevice device : peers.getDeviceList()) {
						devices.add(device);
					}
					onChange();
					
					// if number of peers drops to zero
					if (devices.size() == 0) {
						// check again
						discover();
					}
				}
			});
		}
	}
	
}
