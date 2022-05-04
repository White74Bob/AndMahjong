package wb.conn.wifi;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import wb.game.mahjong.R;
import wb.game.utils.Reflector;

public class WifiUtils {
    private static final int DEFAULT_WIFI_RSSI_LEVELS = 4;

    public static final int WIFI_RSSI_LEVELS;

    static {
        WIFI_RSSI_LEVELS = getWifiRssiLevels();
    }

    private static int getWifiRssiLevels() {
        Class<?> clazz = WifiManager.class;
        int wifiRssiLevels = 0;
        try {
            wifiRssiLevels = (Integer)Reflector.getStaticField(clazz, "RSSI_LEVELS");
        } catch (Exception e) {
        }
        if (wifiRssiLevels < DEFAULT_WIFI_RSSI_LEVELS) {
            wifiRssiLevels = DEFAULT_WIFI_RSSI_LEVELS;
        }
        return wifiRssiLevels;
    }

    public static boolean isWifiConnected(final Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // ConnectivityManager.getAllNetworkInfo() has been deprecated.
            // The javadoc for getNetworkInfo says:
            //   This method was deprecated in API level 23.
            //   This method does not support multiple connected networks of the same type.
            //   Use getAllNetworks() and getNetworkInfo(android.net.Network) instead.
            @SuppressWarnings("deprecation")
            NetworkInfo[] allNetworkInfo = connectivityManager.getAllNetworkInfo();
            if (allNetworkInfo == null) return false;
            for (NetworkInfo anInfo : allNetworkInfo) {
                if (anInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    return anInfo.getState() == NetworkInfo.State.CONNECTED;
                }
            }
            return false;
        }
        // ConnectivityManager.getNetworkInfo() added in LOLLIPOP.
        Network[] networks = connectivityManager.getAllNetworks();
        NetworkInfo networkInfo;
        for (Network mNetwork : networks) {
            networkInfo = connectivityManager.getNetworkInfo(mNetwork);
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                return networkInfo.getState().equals(NetworkInfo.State.CONNECTED);
            }
        }
        return false;
    }

    private static final String FORMAT_WIFI_TOSTRING = "%s [%d/%d]";

    private static final String FORMAT_WIFI_INFO_SIGNAL = "%s:%s %s: [%d/%d]\nIP:%s";

    private static final String FORMAT_WIFI_INFO = "%s:%s\nIP:%s";

    public static class InfoWifi {
        public String name;

        public int signalStrength;

        public String ipV4;

        public String ipV6;

        public String mac;

        @Override
        public String toString() {
            return String.format(FORMAT_WIFI_TOSTRING, name, signalStrength, WIFI_RSSI_LEVELS);
        }

        public String info(Context context) {
            return String.format(FORMAT_WIFI_INFO, context.getString(R.string.wifi_name), name,
                            ipV4);
        }

        public String infoWithSignal(Context context) {
            return String.format(FORMAT_WIFI_INFO_SIGNAL,
                            context.getString(R.string.wifi_name), name,
                            context.getString(R.string.wifi_signal_strength), signalStrength,
                            WIFI_RSSI_LEVELS,
                            ipV4);
        }
    }

    private static final InfoWifi sInfoWifi = new InfoWifi();

    public static String getWifiName(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return getWifiName(wifiManager);
    }

    public static String getWifiName(WifiManager wifiManager) {
        if (!wifiManager.isWifiEnabled()) {
            return null;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return null;
        }
        DetailedState state = WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState());
        if (state == DetailedState.CONNECTED || state == DetailedState.OBTAINING_IPADDR) {
            return wifiInfo.getSSID();
        }
        return null;
    }

    public static int getWifiSignalStrength(WifiManager wifiManager) {
        int rssi = wifiManager.getConnectionInfo().getRssi();
        int signalLevel = WifiManager.calculateSignalLevel(rssi, WIFI_RSSI_LEVELS);
        return signalLevel;
    }

    public static String getWifiInfo(final Context context) {
        refreshWifiInfo(context);
        return sInfoWifi.info(context);
    }

    public enum ConnectionState {
        Connected,
        Disconnected,
        Other;
    }

    public interface ConnectionStateChangeListener {
        public void onConnected();
        public void onDisconnected();
    }

    public enum ConnectionType {
        TypeWifi(ConnectivityManager.TYPE_WIFI),
        TypeEthernet(ConnectivityManager.TYPE_ETHERNET), //有线网络
        TypeMobile(ConnectivityManager.TYPE_MOBILE);

        public final int type;

        private ConnectionType(int type) {
            this.type = type;
        }

        public static ConnectionType getType(int type) {
            for (ConnectionType connectionType : values()) {
                if (type == connectionType.type) {
                    return connectionType;
                }
            }
            return null;
        }
    }

    private static class InfoNetwork {
        public String name;
        public ConnectionType type;
        public int typeInt;

        public ConnectionState connectionState;

        public void setType(int typeInt) {
            this.typeInt = typeInt;
            type = ConnectionType.getType(typeInt);
        }

        public void init() {
            type = null;
            typeInt = -1;
            name = null;
        }

        private static final String FORMAT_NETWORK_INFO = "%s(%s)";
        @Override
        public String toString() {
            if (name == null) return null;
            if (type == null) {
                return String.format(FORMAT_NETWORK_INFO, name, Integer.toString(typeInt));
            }
            return String.format(FORMAT_NETWORK_INFO, name, type.toString());
        }
    }

    public static boolean checkWifiOnAndConnected(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return checkWifiOnAndConnected(wifiManager);
    }

    public static boolean checkWifiOnAndConnected(WifiManager wifiManager) {
        if (wifiManager.isWifiEnabled()) { // WiFi adapter is ON
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo.getNetworkId() == -1) {
                return false; // Not connected to an access-Point
            }
            return true; // Connected to an Access Point
        }
        return false; // WiFi adapter is OFF
    }

    private static ConnectionStateChangeListener sConnectionStateChangeListener;

    private static final InfoNetwork sActiveNetworkInfo = new InfoNetwork();

    private static final BroadcastReceiver sWifiConnectionStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

            if (networkInfo == null) return;

            ConnectionState state = ConnectionState.Other;
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                state = ConnectionState.Connected; // wifi网络连接
                sActiveNetworkInfo.name = networkInfo.getTypeName();
                sActiveNetworkInfo.setType(networkInfo.getType());
            } else {
                state = ConnectionState.Disconnected; // wifi网络断开
                sActiveNetworkInfo.init();
            }
            if (sConnectionStateChangeListener != null &&
                            state != sActiveNetworkInfo.connectionState) {
                switch (state) {
                    case Connected:
                        if (checkWifiOnAndConnected(context)) {
                            refreshWifiInfo(context);
                        }
                        sConnectionStateChangeListener.onConnected();
                        break;
                    case Disconnected:
                        sConnectionStateChangeListener.onDisconnected();
                        break;
                    case Other:
                        break;
                }
                sActiveNetworkInfo.connectionState = state;
            }
        }
    };

    private static void refreshWifiInfo(Context context) {
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        sInfoWifi.name = getWifiName(wifiManager);
        sInfoWifi.signalStrength = getWifiSignalStrength(wifiManager);
        sInfoWifi.ipV4 = getIpV4();
        sInfoWifi.ipV6 = getIpV6();
        sInfoWifi.mac = wifiManager.getConnectionInfo().getMacAddress();
    }

    public static void registerConnectionStateChangeReceiver(Context context,
                    ConnectionStateChangeListener stateChangeListener) {
        sConnectionStateChangeListener = stateChangeListener;
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(sWifiConnectionStateChangeReceiver, filter);
    }

    public static void unregisterConnectionStateChangeReceiver(Context context) {
        context.unregisterReceiver(sWifiConnectionStateChangeReceiver);
        sConnectionStateChangeListener = null;
    }

    public static String getActiveNetworkInfo() {
        return sActiveNetworkInfo.toString();
    }

    public static String getIpInWifi() {
        return sInfoWifi.ipV4;
    }

    private static String getIpV4() {
        try {
            return getLocalIp4Address();
        } catch (SocketException se) {
            return se.toString();
        }
    }

    private static String getIpV6() {
        try {
            return getLocalIpAddress(false);
        } catch (SocketException se) {
            return se.toString();
        }
    }

    private static String getLocalIp4Address() throws SocketException {
        return getLocalIpAddress(true);
    }

    private static String getLocalIpAddress(final boolean isIpV4Needed) throws SocketException {
        for (Enumeration<NetworkInterface> en = NetworkInterface
                .getNetworkInterfaces(); en.hasMoreElements();) {
            NetworkInterface intf = en.nextElement();
            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                    .hasMoreElements();) {
                InetAddress inetAddress = enumIpAddr.nextElement();
                if (inetAddress.isLoopbackAddress()) {
                    continue;
                }
                String hostAddress = inetAddress.getHostAddress();
                if (!isIpV4Needed) {
                    return hostAddress;
                }
                if (inetAddress instanceof Inet4Address) {
                    return hostAddress;
                }
            }
        }
        return "";
    }
} // end of class
