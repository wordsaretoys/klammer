package com.wordsaretoys.klammer.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pManager;

import com.wordsaretoys.klammer.util.Logg;

/**
 * maintains wifi direct availability status
 */
public class State {

	static String TAG = "State";
	
	// wifi parent object
	Wifi wifi;
	
	// broadcast receiver
	Receiver receiver;
	
	// true if Wifi Direct is available/enabled
	boolean enabled;

	/**
	 * ctor
	 * @param wifi parent wifi object
	 */
	public State(Wifi wifi) {
		this.wifi = wifi;
		receiver = new Receiver();
	}

	/**
	 * register for wifi state broadcasts
	 */
	public void register() {
		wifi.getContext().registerReceiver(
			receiver,
			new IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION));
	}
	
	/**
	 * unregister for broadcasts
	 */
	public void unregister() {
		wifi.getContext().unregisterReceiver(receiver);
	}

	/**
	 * return state of wifi direct
	 * @return true if wifi direct enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * override in subclass
	 * @param enabled true if wifi direct available
	 */
	protected void onChange(boolean enabled) {}
	
	/**
	 * receiver for state broadcasts
	 */
	class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Logg.d(TAG, "received action " + action);
			// check for Wifi Direct support
			int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
			enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
			onChange(enabled);
		}
	}

}
