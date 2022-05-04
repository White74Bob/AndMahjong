package wb.game.mahjong.constants;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import wb.game.mahjong.MahjongManager.Location;
import wb.game.mahjong.R;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.Player.Gender;
import wb.game.mahjong.model.Tile;
import wb.game.mahjong.model.WifiPlayer;
import wb.game.utils.Utils;

public class Constants {
    public static final boolean DEBUG = true;

    public static final boolean LOG_WITH_TIME = true;

    public static final String TAG = "HELLO";

    public static enum Network {
        Wifi,
        Hotspot,
        Bluetooth;
    }
    // 暂时不考虑使用bluetooth连接，因为bluetooth只能一对一.
    public static final Network sNetwork = Network.Wifi;

    // true表示wifi下麻将时使用TCP收发消息;
    // false表示wifi下麻将时使用UDP收发消息.
    public static final boolean sMahjongUseTcp = true;

    public static void debug(String log) {
        if (DEBUG) {
            Log.d(TAG, log);
        }
    }

    public static enum Reason {
        MaxGangReached,
        NoTile,
        TileNull,
        FinishActivity,
        Unknown;

        public static Reason getReason(final int ordinal) {
            if (ordinal < 0) return null;
            for (Reason reason : values()) {
                if (reason.ordinal() == ordinal) return reason;
            }
            return null;
        }
    }

    public enum UIMessage {
        MSG_VIEW_INIT,
        MSG_GAME_START,
        MSG_GAME_REFRESH,
        MSG_GAME_NEW,       // 保持当前座次等，新一局开始.
        MSG_GAME_END,       // 游戏由用户主动结束，下次重新排序。
        MSG_GAME_END_NULL,  // 游戏结束，无人胡，荒庄.
        MSG_GAME_OVER,      // 不玩了，返回上一界面.
        MSG_TILES_READY,
        MSG_SHOW_TILE,                    // 杠后花等需要显示杠后最后一张非字的牌.
        MSG_REFRESH_PLAYER,
        MSG_SHOW_DETERMINE_IGNORED,            // 通知manager让player定缺.
        MSG_SHOW_CAN_CHI,                      // 通知manager让player选择吃牌.
        MSG_SHOW_CAN_GANG_TILES,               // 通知manager让player选择暗杠的牌.
        MSG_DETERMINE_IGNORED_TYPE_DONE,
        MSG_SHOW_CAN_HU_TILES,                 // 把可以胡的牌显示给user.
        MSG_NOTIFY_PLAYER_GET_TILE,            // 通知manager让正确的player摸牌
        MSG_NOTIFY_PLAYER_GET_TILE_FROM_END,   // 通知manager让正确的player从尾部摸牌
        MSG_NOTIFY_PLAYER_THROW_TILE,          // 通知manager让正确的player扔牌
        MSG_NOTIFY_PLAYER_ACTION_DONE_ON_NEW_TILE, // 通知manager某个player针对当前牌action done.
        MSG_NOTIFY_PLAYER_ACTION_DONE_ON_GOT_TILE, // 通知manager某个player针对当前牌action done.
        MSG_NOTIFY_PLAYER_ACTION_DONE_ON_THROWN_TILE, // 通知manager某个player针对当前牌action done.
        MSG_NOTIFY_PLAYER_ACTION_DONE_ON_GANGED_TILE,
        MSG_NOTIFY_PLAYER_ACTION_IGNORED,      // 通知manager某个player针对当前牌action ignored.
        MSG_NOTIFY_CHECK_ACTION_ON_THROWN_TILE, // manager通知player(s)检查对该牌可以有什么action(s).
        MSG_NOTIFY_CHECK_ACTION_ON_GOT_TILE,    // manager通知在吃/碰/杠别人的牌后可以有什么action(s).
        MSG_NOTIFY_PLAYER_GANG_FLOWERED,
        MSG_SHOW_ACTIONS_TO_PLAYER,
        MSG_SHOW_ACTIONS_TO_PLAYER_ON_GANGED_TILE,
        MSG_SHOW_PROMPT;                        // 显示提示...

        public final Action action;

        private UIMessage() {
            action = null;
        }

        private UIMessage(final Action action) {
            this.action = action;
        }

        public int getMsgWhat() {
            return ordinal();
        }

        public static UIMessage getMessage(int orderIndex) {
            for (UIMessage msg : values()) {
                if (msg.ordinal() == orderIndex) {
                    return msg;
                }
            }
            return null;
        }

        public static UIMessage getMessage(final Action action) {
            for (UIMessage msg : values()) {
                if (msg.action == action) {
                    return msg;
                }
            }
            return null;
        }
    }

    public static class UIMessageInfo {
        public final UIMessage uiMessage;

        public int arg1;

        public UIMessageInfo(UIMessage uiMessage) {
            this.uiMessage = uiMessage;
        }
    }

    public static String sFormatPlayerWaiting;

    private static String[] sDummyPlayersInfo;

    public static final String DEFAULT_ICON_FILENAME = "default.png";

    public static final String EXTRA_GAME_INDEX = "game_index";
    public static final String EXTRA_USER_INFO = "user_info";
    public static final String EXTRA_PLAYERS_INFO = "players";
    public static final String EXTRA_HOST_IP = "host_ip";

    public static Bitmap sDefaultIconBitmap;
    public static int sIconWidth;
    public static int sIconHeight;

    public static String sDataFileDir; // Data directory.

    public static void setDefaultIconBitmap(final Bitmap bm) {
        if (sDefaultIconBitmap != null) {
            sDefaultIconBitmap.recycle();
            sDefaultIconBitmap = null;
        }
        if (bm == null) return;
        sDefaultIconBitmap = bm;
        sIconWidth = bm.getWidth();
        sIconHeight = bm.getHeight();
    }

    public static void initStrings(Context context) {
        sFormatPlayerWaiting = context.getString(R.string.format_prompt_player_waiting_tile);
        sDummyPlayersInfo = context.getResources().getStringArray(R.array.dummy_player_names);
        sDataFileDir = getAppFileDirPath(context); // Data directory.
    }

    // 确保App FileDir path最后加上"/".
    private static String getAppFileDirPath(final Context context) {
        String fileDirPath = context.getFilesDir().getPath();
        if (fileDirPath.charAt(fileDirPath.length() - 1) != '/') {
            return fileDirPath + "/";
        }
        return fileDirPath;
    }

    private static ArrayList<String> sUsedNames = new ArrayList<String>();

    public static void clearUsedNames() {
        sUsedNames.clear();
    }

    public static class Dummy {
        public final String name;
        public final Gender gender;

        public Dummy(String name, Gender gender) {
            this.name = name;
            this.gender = gender;
        }
    }

    public static Dummy getDummyPlayerInfo() {
        String name;
        Gender gender;

        String[] strArray;
        do {
            String dummyInfo = sDummyPlayersInfo[Utils.getRandomInt(0, sDummyPlayersInfo.length - 1)];
            strArray = dummyInfo.split(SEPARATOR_PLAYER_INFO);
            name = strArray[0];
            gender = Gender.getGender(Integer.parseInt(strArray[1]));
        } while (sUsedNames.contains(name));
        sUsedNames.add(name);
        return new Dummy(name, gender);
    }

    // 测试用函数，假的remotePlayer以测试UI等.
    public static WifiPlayer[] getWifiPlayers() {
        String name;
        Gender gender;

        String[] strArray;

        WifiPlayer[] players = new WifiPlayer[sDummyPlayersInfo.length];
        for (int i = 0; i < players.length; i++) {
            String dummyInfo = sDummyPlayersInfo[i];
            strArray = dummyInfo.split(SEPARATOR_PLAYER_INFO);
            name = strArray[0];
            gender = Gender.getGender(Integer.parseInt(strArray[1]));
            players[i] = new WifiPlayer(name, gender, null/*String wifiAddr*/);
        }
        return players;
    }

    private static final String SEPARATOR_PLAYER_INFO = ",";

    public static class User implements Parcelable {
        public final String user_name;
        public final Gender user_gender;
        public final String user_icon_filepath;

        private static final String FORMAT_TOSTRING = "%s%s%d%s%s";

        private static final String FORMAT_ICON_FILENAME = "%s.png";

        public User(String userName, Gender gender, String iconFilepath) {
            user_name = userName;
            user_gender = gender;
            user_icon_filepath = iconFilepath;
        }

        public User(Parcel source) {
            user_name = source.readString();
            user_gender = Gender.getGender(source.readInt());
            user_icon_filepath = source.readString();
        }


        public byte[] getIconBytes() {
            return Utils.readFileData(user_icon_filepath);
        }

        // 必须要创建一个名叫CREATOR的常量.
        public static final Parcelable.Creator<User> CREATOR = new Parcelable.Creator<User>() {
            @Override
            public User createFromParcel(Parcel source) {
                return new User(source);
            }

            // 重写createFromParcel方法，创建并返回一个获得了数据的user对象
            @Override
            public User[] newArray(int size) {
                return new User[size];
            }
        };

        @Override
        public int describeContents() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(user_name);
            dest.writeInt(user_gender.ordinal());
            dest.writeString(user_icon_filepath);
        }

        public static String getIconFilename(String username) {
            return String.format(FORMAT_ICON_FILENAME, username);
        }

        @Override
        public String toString() {
            return String.format(FORMAT_TOSTRING, user_name, SEPARATOR_PLAYER_INFO,
                            user_gender.ordinal(), SEPARATOR_PLAYER_INFO, user_icon_filepath);
        }

        public static User parseString(String userInfo) {
            String[] array = userInfo.split(SEPARATOR_PLAYER_INFO);
            if (array.length == 2) { // 为了兼容之前没有支持gender的版本. 默认为男.
                return new User(array[0], Gender.Male, array[1]);
            }
            return new User(array[0], Gender.getGender(Integer.parseInt(array[1])), array[2]);
        }
    }

    public static Bitmap getIcon(final String iconFilepath) {
        if (TextUtils.isEmpty(iconFilepath)) {
            return Constants.sDefaultIconBitmap;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(iconFilepath);
        return bitmap == null ? Constants.sDefaultIconBitmap : bitmap;
    }

    // Assets下存放sound的目录.
    private static final String PREFIX_FILE_PATH_SOUND = "resource/sound/";

    private static final String SOUND_ACTION_CHI = "action_chi.ogg";
    private static final String SOUND_ACTION_GANG = "action_gang.ogg";
    private static final String SOUND_ACTION_HU = "action_hu.ogg";
    private static final String SOUND_ACTION_PENG = "action_peng.ogg";
    private static final String SOUND_ACTION_TING = "action_ting.ogg";

    private static final String SOUND_FENG_BAI = "feng_bai.ogg";
    private static final String SOUND_FENG_BEI = "feng_bei.ogg";
    private static final String SOUND_FENG_DONG = "feng_dong.ogg";
    private static final String SOUND_FENG_FA = "feng_fa.ogg";
    private static final String SOUND_FENG_NAN = "feng_nan.ogg";
    private static final String SOUND_FENG_ZHONG = "feng_zhong.ogg";
    private static final String SOUND_FENG_XI = "feng_xi.ogg";

    private static final String SOUND_TIAO_1 = "tiao1.ogg";
    private static final String SOUND_TIAO_2 = "tiao2.ogg";
    private static final String SOUND_TIAO_3 = "tiao3.ogg";
    private static final String SOUND_TIAO_4 = "tiao4.ogg";
    private static final String SOUND_TIAO_5 = "tiao5.ogg";
    private static final String SOUND_TIAO_6 = "tiao6.ogg";
    private static final String SOUND_TIAO_7 = "tiao7.ogg";
    private static final String SOUND_TIAO_8 = "tiao8.ogg";
    private static final String SOUND_TIAO_9 = "tiao9.ogg";

    private static final String SOUND_TONG_1 = "tong1.ogg";
    private static final String SOUND_TONG_2 = "tong2.ogg";
    private static final String SOUND_TONG_3 = "tong3.ogg";
    private static final String SOUND_TONG_4 = "tong4.ogg";
    private static final String SOUND_TONG_5 = "tong5.ogg";
    private static final String SOUND_TONG_6 = "tong6.ogg";
    private static final String SOUND_TONG_7 = "tong7.ogg";
    private static final String SOUND_TONG_8 = "tong8.ogg";
    private static final String SOUND_TONG_9 = "tong9.ogg";

    private static final String SOUND_WAN_1 = "wan1.ogg";
    private static final String SOUND_WAN_2 = "wan2.ogg";
    private static final String SOUND_WAN_3 = "wan3.ogg";
    private static final String SOUND_WAN_4 = "wan4.ogg";
    private static final String SOUND_WAN_5 = "wan5.ogg";
    private static final String SOUND_WAN_6 = "wan6.ogg";
    private static final String SOUND_WAN_7 = "wan7.ogg";
    private static final String SOUND_WAN_8 = "wan8.ogg";
    private static final String SOUND_WAN_9 = "wan9.ogg";

    public static String getActionSoundAssetFilepath(Location location, Gender gender,
                    Action action) {
        StringBuilder sb = new StringBuilder();

        sb.append(PREFIX_FILE_PATH_SOUND);
        sb.append(location.toString().toLowerCase()).append('/');
        sb.append(gender.toString().toLowerCase()).append('/');
        if (appendAction(sb, action)) return sb.toString();
        return null;
    }

    private static boolean appendAction(final StringBuilder sb, final Action action) {
        switch (action) {
            case Chi:
                sb.append(SOUND_ACTION_CHI);
                break;
            case Gang:
                sb.append(SOUND_ACTION_GANG);
                break;
            case Peng:
                sb.append(SOUND_ACTION_PENG);
                break;
            case Ting:
                sb.append(SOUND_ACTION_TING);
                break;
            case Hu:
                sb.append(SOUND_ACTION_HU);
                break;
            default:
                return false;
        }
        return true;
    }

    public static String getTileSoundAssetFilepath(Location location, Gender gender, Tile tile) {
        StringBuilder sb = new StringBuilder();

        sb.append(PREFIX_FILE_PATH_SOUND);
        sb.append(location.toString().toLowerCase()).append('/');
        sb.append(gender.toString().toLowerCase()).append('/');
        appendTile(sb, tile);
        return sb.toString();
    }

    private static void appendTile(final StringBuilder sb, final Tile tile) {
        switch (tile.tileType) {
            case Tiao:
                sb.append(getTiaoSoundFilePath(tile.tileIndex));
                break;
            case Tong:
                sb.append(getTongSoundFilePath(tile.tileIndex));
                break;
            case Wan:
                sb.append(getWanSoundFilePath(tile.tileIndex));
                break;
            case Feng:
                sb.append(getFengSoundFilePath(tile.tileIndex));
                break;
        }
    }

    private static String getTiaoSoundFilePath(final int tileIndex) {
        switch (tileIndex) {
            case 0: return SOUND_TIAO_1;
            case 1: return SOUND_TIAO_2;
            case 2: return SOUND_TIAO_3;
            case 3: return SOUND_TIAO_4;
            case 4: return SOUND_TIAO_5;
            case 5: return SOUND_TIAO_6;
            case 6: return SOUND_TIAO_7;
            case 7: return SOUND_TIAO_8;
            case 8: return SOUND_TIAO_9;
        }
        return null;
    }

    private static String getTongSoundFilePath(final int tileIndex) {
        switch (tileIndex) {
            case 0: return SOUND_TONG_1;
            case 1: return SOUND_TONG_2;
            case 2: return SOUND_TONG_3;
            case 3: return SOUND_TONG_4;
            case 4: return SOUND_TONG_5;
            case 5: return SOUND_TONG_6;
            case 6: return SOUND_TONG_7;
            case 7: return SOUND_TONG_8;
            case 8: return SOUND_TONG_9;
        }
        return null;
    }

    private static String getWanSoundFilePath(final int tileIndex) {
        switch (tileIndex) {
            case 0: return SOUND_WAN_1;
            case 1: return SOUND_WAN_2;
            case 2: return SOUND_WAN_3;
            case 3: return SOUND_WAN_4;
            case 4: return SOUND_WAN_5;
            case 5: return SOUND_WAN_6;
            case 6: return SOUND_WAN_7;
            case 7: return SOUND_WAN_8;
            case 8: return SOUND_WAN_9;
        }
        return null;
    }

    private static String getFengSoundFilePath(final int tileIndex) {
        switch (tileIndex) {
            case 0: return SOUND_FENG_DONG;
            case 1: return SOUND_FENG_XI;
            case 2: return SOUND_FENG_NAN;
            case 3: return SOUND_FENG_BEI;
            case 4: return SOUND_FENG_ZHONG;
            case 5: return SOUND_FENG_FA;
            case 6: return SOUND_FENG_BAI;
        }
        return null;
    }

    private static final String PREFIX_FILE_PATH_SOUND_CUSTOMIZED = "sound/";

    public static String getCustomizedActionSound(final String userName, final Action action) {
        StringBuilder sb = new StringBuilder();

        sb.append(PREFIX_FILE_PATH_SOUND_CUSTOMIZED);
        sb.append(userName.toLowerCase()).append('/');
        if (appendAction(sb, action)) {
            return sb.toString();
        }
        return null;
    }

    public static String getCustomizedTileSound(String userName, Tile tile) {
        StringBuilder sb = new StringBuilder();

        sb.append(PREFIX_FILE_PATH_SOUND_CUSTOMIZED);
        sb.append(userName.toLowerCase()).append('/');
        appendTile(sb, tile);
        return sb.toString();
    }

    public static String getInternalFilepath(final String internalFilename) {
        return sDataFileDir + internalFilename;
    }
}
