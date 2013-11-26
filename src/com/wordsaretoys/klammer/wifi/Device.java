package com.wordsaretoys.klammer.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;

import com.wordsaretoys.klammer.util.Logg;

/**
 * maintains wifi direct local device state
 */
public class Device {

	static String TAG = "Device";
	
	// wifi parent object
	Wifi wifi;

	// broadcast receiver
	Receiver receiver;
	
	// configuration and status of the local device
	WifiP2pDevice local;

	/**
	 * ctor
	 * @param wifi parent wifi object
	 */
	public Device(Wifi wifi) {
		receiver = new Receiver();
		this.wifi = wifi;
	}
	
	/**
	 * register for wifi state broadcasts
	 */
	public void register() {
		wifi.getContext().registerReceiver(
			receiver,
			new IntentFilter(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION));
	}
	
	/**
	 * unregister for broadcasts
	 */
	public void unregister() {
		wifi.getContext().unregisterReceiver(receiver);
	}

	/**
	 * get info structure about local device
	 * @return wifi p2p device object, or null
	 */
	public WifiP2pDevice getLocalDevice() {
		return local;
	}

	/**
	 * override in subclass to handle device changes
	 */
	protected void onChange() {}
	
	/**
	 * receiver for state broadcasts
	 */
	class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Logg.d(TAG, "received action " + action);
			local = (WifiP2pDevice) intent.getParcelableExtra(
					WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);				
			onChange();
		}
	}
	
}
