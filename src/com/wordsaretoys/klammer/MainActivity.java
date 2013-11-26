package com.wordsaretoys.klammer;

import android.app.Activity;
import android.app.FragmentManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.wordsaretoys.klammer.stream.Streamer;
import com.wordsaretoys.klammer.util.Logg;
import com.wordsaretoys.klammer.wifi.Wifi;

public class MainActivity extends Activity {

	static String TAG = "MainActivity";

	// wifi machine
	static Wifi wifi;
	
	// streamer
	static Streamer streamer;
	
	// true if starting for the first time
	boolean starting;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Logg.onCreate(this, R.id.log);
	
		if (wifi == null) {
			wifi = new Wifi();
		}

		if (streamer == null) {
			streamer = new Streamer();
		}

		wifi.onCreate(this, new WifiListener());
		streamer.onCreate(this, new StreamerListener());

		starting = savedInstanceState == null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean enabled = wifi.getState().isEnabled();
		boolean hasPeers = wifi.getPeers().getDeviceList().size() > 0;
		boolean connected = wifi.getConnection().isConnected();
		
		MenuItem connectMenu = menu.findItem(R.id.connect); 
		connectMenu.setVisible(enabled && hasPeers && !connected);
		connectMenu.getSubMenu().clear();
		for (WifiP2pDevice device : wifi.getPeers().getDeviceList()) {
			connectMenu.getSubMenu().add(Menu.NONE, R.id.connectTo, Menu.NONE, device.deviceName);
		}
		
		MenuItem disconnectMenu = menu.findItem(R.id.disconnect);
		disconnectMenu.setVisible(enabled && connected);
		
		return true;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		wifi.register();
		if (starting) {
			wifi.getPeers().discover();
		}
		streamer.getVideo().acquireCamera();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		wifi.unregister();
		if (isFinishing()) {
			streamer.getNetwork().close();
			wifi.getConnection().disconnect();
		}
		streamer.getVideo().releaseCamera();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		
		case R.id.connectTo:
			wifi.getPeers().connectTo((String)item.getTitle());
			break;
			
		case R.id.disconnect:
			wifi.getConnection().disconnect();
			break;
			
		default:
			return false;
		}
		return true;
	}

	/**
	 * handle events from the wifi subsystem
	 */
	class WifiListener implements Wifi.Listener {
		
		@Override
		public void onStateChanged(boolean enabled) {
			invalidateOptionsMenu();
			if (!enabled) {
				FragmentManager fm = getFragmentManager();
				ErrorDialog.newInstance(R.string.actionNoSupport).show(fm, "error");
			}
		}

		@Override
		public void onPeersChanged() {
			invalidateOptionsMenu();
		}

		@Override
		public void onConnectionChanged(boolean connected) {
			invalidateOptionsMenu();
			if (!connected && streamer.getNetwork().isReady()) {
				streamer.getNetwork().disconnect();
			}
		}

		@Override
		public void onConnectionReady() {
			WifiP2pInfo info = wifi.getConnection().getInfo();
			streamer.getNetwork().connect(
					info.groupOwnerAddress.getHostAddress(),
					info.isGroupOwner);
		}
		
		@Override
		public void onDeviceChanged() {
		}

		@Override
		public void onError(int code) {
			FragmentManager fm = getFragmentManager();
			switch (code) {
			case WifiP2pManager.ERROR:
				ErrorDialog.newInstance(R.string.actionError).show(fm, "error");
				break;
				
			case WifiP2pManager.BUSY:
				ErrorDialog.newInstance(R.string.actionBusy).show(fm, "error");
				break;

			case WifiP2pManager.P2P_UNSUPPORTED:
				ErrorDialog.newInstance(R.string.actionNoSupport).show(fm, "error");
				break;
				
			case WifiP2pManager.NO_SERVICE_REQUESTS:
				ErrorDialog.newInstance(R.string.actionNoServices).show(fm, "error");
				break;
				
			default:
				ErrorDialog.newInstance(R.string.actionError).show(fm, "error");
			}
			streamer.getNetwork().disconnect();
		}
	}

	/**
	 * handle events from the data streamer
	 */
	class StreamerListener implements Streamer.Listener {

		@Override
		public void onTermination() {
			wifi.getConnection().disconnect();
		}
	}
}
