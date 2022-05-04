package wb.conn.bluetooth;

import wb.game.mahjong.R;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BluetoothUtils {
    private static BluetoothAdapter sBluetoothAdapter;

    static {
        sBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static boolean isBluetoothEnabled() {
        return (sBluetoothAdapter != null && sBluetoothAdapter.isEnabled());
    }

    public static boolean isBluetoothSupported() {
        return sBluetoothAdapter != null;
    }

    public static boolean disableBluetooth() {
        if (sBluetoothAdapter == null) return false;
        if (!sBluetoothAdapter.isEnabled()) return false;
        return sBluetoothAdapter.disable();
    }

    // format:
    // Bluetooth Information:
    // Name:xxx
    // Address: yy:zz:...
    private static final String FORMAT_INFO = "%s:%s\n%s:%s";
    public static String getInfo(Context context) {
        if (sBluetoothAdapter == null) return null;
        return String.format(FORMAT_INFO,
                context.getString(R.string.bluetooth_name),
                sBluetoothAdapter.getName(),
                context.getString(R.string.bluetooth_addr),
                sBluetoothAdapter.getAddress());
    }

    public static String getDeviceName() {
        if (sBluetoothAdapter == null) return null;
        return sBluetoothAdapter.getName();
    }

    public static boolean renameBluetooth(String newName) {
        return sBluetoothAdapter.setName(newName);
    }

    public interface StateChangeListener {
        void onStateOff();
        void onStateOn();
    }

    private static class StateBroadcastReceiver extends BroadcastReceiver {
        private StateChangeListener mStateChangeListener;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                if (BluetoothAdapter.STATE_OFF == bluetoothState) {
                    if (mStateChangeListener != null) {
                        mStateChangeListener.onStateOff();
                    }
                } else if (BluetoothAdapter.STATE_ON == bluetoothState) {
                    if (mStateChangeListener != null) {
                        mStateChangeListener.onStateOn();
                    }
                }
            }
        }

        public void setStateChangeListener(StateChangeListener stateChangeListener) {
            mStateChangeListener = stateChangeListener;
        }
    };

    private static final StateBroadcastReceiver sStateChangeReceiver = new StateBroadcastReceiver();

    public static void registerStateChangeReceiver(Context context,
            final StateChangeListener stateChangeListener) {
        sStateChangeReceiver.setStateChangeListener(stateChangeListener);

        IntentFilter stateChangedFilter = new IntentFilter();
        stateChangedFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(sStateChangeReceiver, stateChangedFilter);
    }

    public static void unegisterStateChangeReceiver(Context context) {
        sStateChangeReceiver.setStateChangeListener(null);
        context.unregisterReceiver(sStateChangeReceiver);
    }

    public interface ScanActionListener {
        void onDeviceFound(BluetoothDevice device);
        void onDiscoveryFinished();
    }

    private static class ScanActionReceiver extends BroadcastReceiver {
        private ScanActionListener mScanActionListener;

        public void setScanActionListener(ScanActionListener scanActionListener) {
            mScanActionListener = scanActionListener;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (mScanActionListener != null) {
                    mScanActionListener.onDeviceFound(device);
                }
            // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (mScanActionListener != null) {
                    mScanActionListener.onDiscoveryFinished();
                }
            }
        }
    };

    private static final ScanActionReceiver sScanActionReceiver = new ScanActionReceiver();
    private static boolean sScanActionReceiverRegistered;

    public static void registerScanActionReceiver(Context context, ScanActionListener scanActionListener) {
        if (sScanActionReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        // Register for broadcasts when discovery has finished
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(sScanActionReceiver, filter);
        sScanActionReceiver.setScanActionListener(scanActionListener);
        sScanActionReceiverRegistered = true;
    }

    public static void unregisterScanActionReceiver(Context context) {
        if (sScanActionReceiverRegistered) {
            sScanActionReceiver.setScanActionListener(null);
            sBluetoothAdapter.cancelDiscovery();
            context.unregisterReceiver(sScanActionReceiver);
            sScanActionReceiverRegistered = false;
        }
    }

    public static boolean startDiscovery() {
        if (sBluetoothAdapter.isDiscovering()) {
            sBluetoothAdapter.cancelDiscovery();
        }
        return sBluetoothAdapter.startDiscovery();
    }
}
