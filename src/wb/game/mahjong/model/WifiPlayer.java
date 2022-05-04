package wb.game.mahjong.model;

import java.io.File;
import java.io.FilenameFilter;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import wb.conn.MessageInfo;
import wb.conn.MessageInfo.PlayerInfo;
import wb.conn.RemoteMessage;
import wb.game.mahjong.RemoteConnector;
import wb.game.mahjong.constants.Constants;
import wb.game.utils.Utils;

public class WifiPlayer extends RemotePlayer implements Parcelable {
    public final String ipv4;

    public WifiPlayer(String name, Gender gender, String ipv4) {
        super(name, gender, getIconFilenameFromIp(ipv4));
        this.ipv4 = ipv4;
    }

    public WifiPlayer(String ipv4) {
        super(null, null, getIconFilenameFromIp(ipv4));
        this.ipv4 = ipv4;
    }

    public WifiPlayer(Parcel source) {
        this(source.readString()/*name*/,
              Gender.getGender(source.readInt())/*gender*/,
              source.readString()/*ipv4*/);
    }

    public void update(final String name, final Gender gender) {
        this.name = name;
        this.gender = gender;
    }

    public void updateIcon(final byte[] data) {
        Utils.saveToFile(iconFilename, data);
    }

    private static final String FORMAT_SHORT_INFO = "%s[%s]";

    public String getShortInfo() {
        if (TextUtils.isEmpty(name)) return ipv4;
        return String.format(FORMAT_SHORT_INFO, name, ipv4);
    }

    private static final String DOT_IN_IP = ".";
    private static final String SLASH_IN_IP = "_";

    private static final String FILENAME_PREFIX = "wifi";
    private static final String FORMAT_ICON_FILENAME = FILENAME_PREFIX + "%s.png";

    private static String getIconFilenameFromIp(final String ipv4) {
        if (TextUtils.isEmpty(ipv4)) return null;
        String filename = String.format(FORMAT_ICON_FILENAME, ipv4.replace(DOT_IN_IP, SLASH_IN_IP));
        return Constants.getInternalFilepath(filename);
    }

    // 必须要创建一个名叫CREATOR的常量.
    public static final Parcelable.Creator<WifiPlayer> CREATOR = new Parcelable.Creator<WifiPlayer>() {
        @Override
        public WifiPlayer createFromParcel(Parcel source) {
            return new WifiPlayer(source);
        }

        // 重写createFromParcel方法，创建并返回一个获得了数据的user对象
        @Override
        public WifiPlayer[] newArray(int size) {
            return new WifiPlayer[size];
        }
    };

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(gender.ordinal());
        dest.writeString(ipv4);
    }

    public static void clearFiles(final File filesDir, final long curTime) {
        File[] wifiFiles = filesDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return (filename.startsWith(FILENAME_PREFIX));
            }
        });
        if (wifiFiles == null || wifiFiles.length <= 0) return;
        for (File file : wifiFiles) {
            if (file.lastModified() < curTime) {
                file.delete();
            }
        }
    }

    private void send2Remote(final RemoteMessage.ConnMessage connMessage,
                    final String uiMessageContent) {
        RemoteConnector remoteConnector = RemoteConnector.getInstance();
        final MessageInfo messageInfo = RemoteMessage.constructStringMessage(connMessage, ipv4,
                        new PlayerInfo(ipv4, name), uiMessageContent);
        switch (Constants.sNetwork) {
            case Wifi:
                remoteConnector.sendMessageTcp(messageInfo);
                break;
            case Bluetooth:
                break;
            case Hotspot:
                break;
        }
    }

    @Override
    public void startPlaying(boolean fromLocalManager) {
        super.startPlaying(fromLocalManager);
        if (fromLocalManager) {
            send2Remote(RemoteMessage.ConnMessage.MSG_REQUEST_START_PLAYING, null);
        }
    }

    @Override
    protected void doDetermineIgnored(boolean fromLocalManager) {
        if (fromLocalManager) {
            send2Remote(RemoteMessage.ConnMessage.MSG_DETERMINE_IGNORED_TYPE, null);
        }
    }

    @Override
    protected void whenReadyToThrow(final long startTime, final boolean fromLocalManager) {
        super.whenReadyToThrow(startTime, fromLocalManager);
        if (fromLocalManager) {
            send2Remote(RemoteMessage.ConnMessage.MSG_PLAYER_READY_TO_THROW, null);
        }
    }

    @Override
    protected void playerThreadNotify(boolean fromRemote) {
        super.playerThreadNotify(fromRemote);
        if (!fromRemote) {
            send2Remote(RemoteMessage.ConnMessage.MSG_PLAYER_THREAD_NOTIFY, null);
        }
    }
}
