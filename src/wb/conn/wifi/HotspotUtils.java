package wb.conn.wifi;

import java.lang.reflect.Method;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

public class HotspotUtils {
    public static class Hotspot {
        final String ssid;
        final String preSharedKey;

        boolean connected;

        public Hotspot(String ssid, String preSharedKey) {
            this.ssid = ssid;
            this.preSharedKey = preSharedKey;
        }
    }

    public static boolean setupHotspot(Context context, Hotspot hotspot) {
        return false;
    }

    public static boolean isHotspot() {
        // TODO: not implemented.
        return false;
    }

    public static void disableHotspot() {
        // TODO: not implemented.
    }

    // 连接到热点
    public void connectToHotpot(final Context context, Hotspot hotspot) {
        // 获取wifi管理服务
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfig = setWifiParams(hotspot.ssid, hotspot.preSharedKey);
        int wcgID = wifiManager.addNetwork(wifiConfig);
        hotspot.connected = wifiManager.enableNetwork(wcgID, true);
    }

    private static final String FORMAT_HOTSPOT_PARAM = "\\%s\\";
    // 设置要连接的热点的参数
    private static WifiConfiguration setWifiParams(final String ssid, final String preSharedKey) {
        WifiConfiguration apConfig = new WifiConfiguration();
        apConfig.SSID = String.format(FORMAT_HOTSPOT_PARAM, ssid);
        apConfig.preSharedKey = String.format(FORMAT_HOTSPOT_PARAM, preSharedKey);
        apConfig.hiddenSSID = true;
        apConfig.status = WifiConfiguration.Status.ENABLED;
        apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        apConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        apConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        return apConfig;
    }

    // check whether wifi hotspot on or off.
    public static boolean isApOn(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Method method = null;
        boolean accessibilityChanged = false;
        try {
            method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            if (!method.isAccessible()) {
                method.setAccessible(true);
                accessibilityChanged = true;
            }
            return (Boolean) method.invoke(wifiManager);
        } catch (Throwable ignored) {
            // TODO: check ignored?!
        } finally {
            if (method != null && accessibilityChanged) {
                method.setAccessible(false);
            }
        }
        return false;
    }

    // toggle wifi hotspot on or off.
    public static boolean toggleWifiApEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wificonfiguration = null;
        final String methodName = "setWifiApEnabled";
        try {
            // if WiFi is on, turn it off.
            if(isApOn(context)) {
                wifiManager.setWifiEnabled(false);
            }
            Method method = wifiManager.getClass().getMethod(methodName,
                            WifiConfiguration.class, boolean.class);
            method.invoke(wifiManager, wificonfiguration, !isApOn(context));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class WifiApManager {
        private static final String TAG = "WifiApManager";

        private static final int WIFI_AP_STATE_FAILED = 4;

        private final WifiManager mWifiManager;

        private Method wifiControlMethod;
        private Method wifiApConfigurationMethod;
        private Method wifiApState;

        public WifiApManager(Context context) throws SecurityException, NoSuchMethodException {
            mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final Class<?> wifiManagerClass = mWifiManager.getClass();
            wifiControlMethod = wifiManagerClass.getMethod("setWifiApEnabled",
                            WifiConfiguration.class, boolean.class);
            wifiApConfigurationMethod = wifiManagerClass.getMethod("getWifiApConfiguration", null);
            wifiApState = wifiManagerClass.getMethod("getWifiApState");
        }

        public boolean setWifiApState(WifiConfiguration config, boolean enabled) {
            try {
                if (enabled) {
                    //wifi和热点不能同时打开，所以打开热点的时候需要关闭wifi
                    mWifiManager.setWifiEnabled(false); // disable WiFi in any case.
                }
                return (Boolean) wifiControlMethod.invoke(mWifiManager, config, enabled);
            } catch (Exception e) {
                Log.e(TAG, "Failed to setWifiApState!", e);
                return false;
            }
        }

        public WifiConfiguration getWifiApConfiguration() {
            try {
                return (WifiConfiguration) wifiApConfigurationMethod.invoke(mWifiManager, null);
            } catch (Exception e) {
                return null;
            }
        }

        public int getWifiApState() {
            try {
                return (Integer) wifiApState.invoke(mWifiManager);
            } catch (Exception e) {
                Log.e(TAG, "Failed to getWifiApState!", e);
                return WIFI_AP_STATE_FAILED;
            }
        }
    } // end of WifiApManager
}
