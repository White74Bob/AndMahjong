package wb.conn;

import wb.conn.wifi.WifiUtils;
import wb.game.mahjong.MahjongManager;
import wb.game.mahjong.MahjongManager.Location;
import wb.game.mahjong.constants.Constants;
import wb.game.mahjong.constants.Constants.User;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.DummyPlayer;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.LocalPlayer;
import wb.game.mahjong.model.Player;
import wb.game.mahjong.model.Player.Gender;
import wb.game.mahjong.model.Player.PlayerAction;
import wb.game.mahjong.model.Player.PlayerActionInfo;
import wb.game.mahjong.model.Tile;
import wb.game.mahjong.model.Tile.TileInfo;
import wb.game.mahjong.model.WifiPlayer;

public class MessageUtils {
    private static final String SEPARATOR_ARGUMENT = ",";

    public static String getIps(final String...ips) {
        if (ips == null || ips.length <= 0) return null;
        StringBuilder sb = new StringBuilder();
        for (String ip : ips) {
            if (sb.length() > 0) sb.append(',');
            sb.append(ip);
        }
        return sb.toString();
    }

    // Format: username,genderIndex,
    private static final String FORMAT_NAME_GENDER = "%s%s%d%s\0";
    public static String messageNameGender(final User user) {
        return String.format(FORMAT_NAME_GENDER, user.user_name, SEPARATOR_ARGUMENT,
                        user.user_gender.ordinal(), SEPARATOR_ARGUMENT);
    }

    public static WifiPlayer parseNameGender(final String ipv4, final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ARGUMENT);
        String playerName = array[0].trim();
        Gender playerGender = Gender.getGender(Integer.parseInt(array[1].trim()));
        return new WifiPlayer(playerName, playerGender, ipv4);
    }

    public static void parseNameGender(final WifiPlayer wifiPlayer, final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ARGUMENT);
        String playerName = array[0].trim();
        Gender playerGender = Gender.getGender(Integer.parseInt(array[1].trim()));
        wifiPlayer.update(playerName, playerGender);
    }

    // Format: gameIndex,
    private static final String FORMAT_GAME_INDEX = "%d%s\0";
    public static String messageGameIndex(final int gameIndex) {
        return String.format(FORMAT_GAME_INDEX, gameIndex, SEPARATOR_ARGUMENT);
    }

    public static int parseGameIndex(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ARGUMENT);
        String strGameIndex = array[0].trim();
        return Integer.parseInt(strGameIndex);
    }

    // Format: locationOrdinal,
    private static final String FORMAT_LOCATION = "%d%s\0";
    public static String messageWaitingLocation(final int locationOrdinal) {
        return String.format(FORMAT_LOCATION, locationOrdinal, SEPARATOR_ARGUMENT);
    }

    public static Location parseWaitingLocation(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ARGUMENT);
        return Location.getLocation(Integer.parseInt(array[0].trim()));
    }

    // Format: ignoredType,
    private static final String FORMAT_IGNORE_TYPE = "%d%s\0";
    public static String messageIgnoredType(final TileType ignoredType) {
        return String.format(FORMAT_IGNORE_TYPE, ignoredType.ordinal(), SEPARATOR_ARGUMENT);
    }

    public static TileType parseIgnoredType(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ARGUMENT);
        return TileType.getTileType(Integer.parseInt(array[0].trim()));
    }

    public static byte[] parseIcon(final Object content) {
        if (content == null || !(content instanceof byte[])) {
            throw new RuntimeException("Invalid argument!Content NOT byte array:\n" + content);
        }
        return (byte[])content;
    }

    // Format: 0,name,gender,ip,index; // DummyPlayer
    // Format: 1,name,gender,ip,index; // WifiPlayer
    private static final String FORMAT_PLAYER_INFO = "%d%s%s%s%d%s%s%s%d";
    private static final String SEPARATOR_PLAYER = ";";
    private static final int TYPE_DUMMY = 0;
    private static final int TYPE_WIFI = 1;

    public static class PlayerIndex {
        public final Player player;
        public final int index;

        public PlayerIndex(Player player, int index) {
            this.player = player;
            this.index = index;
        }
    }

    public static String messagePlayersInfo(PlayerIndex[] playerIndexes) {
        StringBuilder sb = new StringBuilder();
        Player player;
        String playerIp = null;
        int playerType = TYPE_DUMMY;
        for (PlayerIndex playerIndex : playerIndexes) {
            player = playerIndex.player;
            if (player instanceof DummyPlayer) {
                playerIp = ((DummyPlayer)player).ipv4;
                if (playerIp == null) {
                    playerIp = WifiUtils.getIpInWifi();
                }
                playerType = TYPE_DUMMY;
            } else if (player instanceof WifiPlayer) {
                playerIp = ((WifiPlayer)player).ipv4;
                playerType = TYPE_WIFI;
            } else {
                playerIp = null;
                continue;
            }
            if (sb.length() > 0) {
                sb.append(SEPARATOR_PLAYER);
            }
            sb.append(String.format(FORMAT_PLAYER_INFO, playerType, SEPARATOR_ARGUMENT,
                            player.name, SEPARATOR_ARGUMENT,
                            player.gender.ordinal(), SEPARATOR_ARGUMENT,
                            playerIp, SEPARATOR_ARGUMENT, playerIndex.index));
        }
        return sb.toString();
    }

    public static PlayerIndex[] parsePlayers(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] playerInfoArray = ((String)messageContent).split(SEPARATOR_PLAYER);
        PlayerIndex[] playerIndexes = new PlayerIndex[playerInfoArray.length];

        int playerType;
        String name;
        Gender gender;
        String ipv4;
        Player player;
        int playerIndex;
        String[] infoArray;
        for (int i = 0; i < playerIndexes.length; i++) {
            infoArray = playerInfoArray[i].split(SEPARATOR_ARGUMENT);
            playerType = Integer.parseInt(infoArray[0].trim());
            name = infoArray[1].trim();
            gender = Gender.getGender(Integer.parseInt(infoArray[2].trim()));
            ipv4 = infoArray[3].trim();
            playerIndex = Integer.parseInt(infoArray[4].trim());
            switch (playerType) {
                case TYPE_DUMMY:
                    player = new DummyPlayer(name, gender, ipv4);
                    break;
                case TYPE_WIFI:
                    player = new WifiPlayer(name, gender, ipv4);
                    break;
                default:
                    player = null;
                    break;
            }
            playerIndexes[i] = new PlayerIndex(player, playerIndex);
        }
        return playerIndexes;
    }

    public static String getPlayerIp(final Player player) {
        if (player instanceof LocalPlayer) {
            return WifiUtils.getIpInWifi();
        }

        String playerIp;
        if (player instanceof DummyPlayer) {
            playerIp = ((DummyPlayer)player).ipv4;
            if (playerIp == null) {
                return WifiUtils.getIpInWifi();
            }
            return playerIp;
        }

        if (player instanceof WifiPlayer) {
            return ((WifiPlayer)player).ipv4;
        }
        return null;
    }

    // Format: ip,name,locationInt;
    private static final String FORMAT_PLAYER_LOCATION = "%s%s%s%s%d";
    private static final String SEPARATOR_LOCATION = ";";
    public static String messagePlayersLocationInfo(Player[] players) {
        StringBuilder sb = new StringBuilder();
        String playerIp = null;
        for (Player player : players) {
            playerIp = getPlayerIp(player);
            if (player == null) continue;
            if (sb.length() > 0) {
                sb.append(SEPARATOR_LOCATION);
            }
            sb.append(String.format(FORMAT_PLAYER_LOCATION, playerIp, SEPARATOR_ARGUMENT,
                            player.name, SEPARATOR_ARGUMENT, player.getLocation().ordinal()));
        }
        return sb.toString();
    }

    public static class ActionInfo {
        public final Action action;
        public final TileInfo tileInfo;

        public ActionInfo(Action action, TileInfo tileInfo) {
            this.action = action;
            this.tileInfo = tileInfo;
        }
    }
    // Format: action_tileInfo
    private static final String FORMAT_ACTION_TILEINFO = "%d%s%s";
    private static final String SEPARATOR_ACTION_TILEINFO = "_";
    public static String messageActionInfo(final Action action, final TileInfo tileInfo) {
        return String.format(FORMAT_ACTION_TILEINFO, action.ordinal(), SEPARATOR_ACTION_TILEINFO,
                        tileInfo.tileInfoString());
    }

    public static ActionInfo parseActionInfo(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ACTION_TILEINFO);
        Action action = Action.getAction(Integer.parseInt(array[0].trim()));
        TileInfo tileInfo = TileInfo.parseTileInfoString(array[1].trim());

        return new ActionInfo(action, tileInfo);
    }

    public static class LocationInfo {
        public final String ipv4;
        public final String name;
        public final Location location;

        public LocationInfo(String ipv4, String name, Location location) {
            this.ipv4 = ipv4;
            this.name = name;
            this.location = location;
        }
    }
    public static LocationInfo[] parseLocations(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] infoArray = ((String)messageContent).split(SEPARATOR_LOCATION);
        LocationInfo[] locationInfoArray = new LocationInfo[infoArray.length];

        Location location;
        String ipv4;
        String name;
        String[] array;
        for (int i = 0; i < locationInfoArray.length; i++) {
            array = infoArray[i].split(SEPARATOR_ARGUMENT);
            ipv4 = array[0].trim();
            name = array[1].trim();
            location = Location.getLocation(Integer.parseInt(array[2].trim()));
            locationInfoArray[i] = new LocationInfo(ipv4, name, location);
        }
        return locationInfoArray;
    }

    // Format: remainingTileNum,
    private static final String FORMAT_LIVE_TILE_NUM = "%d%s\0";
    public static String messageLiveTileNum(final int remainingTileNum) {
        return String.format(FORMAT_LIVE_TILE_NUM, remainingTileNum, SEPARATOR_ARGUMENT);
    }

    public static int parseLiveTileNum(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ARGUMENT);
        String strRemainingTileNum = array[0].trim();
        return Integer.parseInt(strRemainingTileNum);
    }

    public static class WhenTilesReadyInfo {
        public final Tile shownTile;

        public WhenTilesReadyInfo(Tile shownTile) {
            this.shownTile = shownTile;
        }
    }
    // Format: shownTile_
    private static final String FORMAT_WHEN_TILES_READY_INFO = "%s%s";
    private static final String SEPARATOR_WHEN_TILES_READY_INFO = "_";
    public static String messageWhenTilesReady(final Tile shownTile) {
        return String.format(FORMAT_WHEN_TILES_READY_INFO, shownTile.toString(),
                        SEPARATOR_ACTION_TILEINFO);
    }

    public static WhenTilesReadyInfo parseWhenTilesReadyInfo(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_WHEN_TILES_READY_INFO);
        Tile shownTile = Tile.parse(array[0].trim());

        return new WhenTilesReadyInfo(shownTile);
    }

    public static class ActionsIgnoredInfo {
        public final TileInfo tileInfo;
        public final Action[] actions;

        public ActionsIgnoredInfo(TileInfo tileInfo, Action[] actions) {
            this.tileInfo = tileInfo;
            this.actions = actions;
        }
    }
    // Format: tileInfo_actions
    private static final String SEPARATOR_TILEINFO_ACTIONS = "_";
    private static final String SEPARATOR_ACTION = ",";
    public static String messagePlayerActionsIgnored(final TileInfo tileInfo, final Action...actions) {
        StringBuilder sb = new StringBuilder();
        sb.append(tileInfo.tileInfoString()).append(SEPARATOR_TILEINFO_ACTIONS);
        if (actions != null && actions.length > 0) {
            for (Action action : actions) {
                sb.append(action.ordinal()).append(SEPARATOR_ACTION);
            }
        }
        return sb.toString();
    }

    public static ActionsIgnoredInfo parsePlayerActionsIgnored(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_TILEINFO_ACTIONS);
        TileInfo tileInfo = TileInfo.parseTileInfoString(array[0].trim());
        String[] actionArray = array[1].trim().split(SEPARATOR_ACTION);
        Action[] actions = new Action[actionArray.length];
        for (int i = 0; i < actions.length; i++) {
            actions[i] = Action.getAction(Integer.parseInt(actionArray[i].trim()));
        }

        return new ActionsIgnoredInfo(tileInfo, actions);
    }

    // Format: UIMessage,
    private static final String FORMAT_UIMESSAGE = "%d%s\0";
    public static String messageUIMessage(final Constants.UIMessage uiMessage) {
        return String.format(FORMAT_UIMESSAGE, uiMessage.ordinal(), SEPARATOR_ARGUMENT);
    }

    public static Constants.UIMessage parseUIMessage(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String) messageContent).split(SEPARATOR_ARGUMENT);
        Constants.UIMessage uiMessage = Constants.UIMessage
                        .getMessage(Integer.parseInt(array[0].trim()));
        return uiMessage;
    }

    // Format: UIMessage,
    private static final String FORMAT_UIMESSAGE_ARG1 = "%d%s%d%s\0";
    public static String messageUIMessageArg1(final Constants.UIMessage uiMessage, final int arg1) {
        return String.format(FORMAT_UIMESSAGE_ARG1, uiMessage.ordinal(), SEPARATOR_ARGUMENT,
                        arg1, SEPARATOR_ARGUMENT);
    }

    public static Constants.UIMessageInfo parseUIMessageArg1(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String) messageContent).split(SEPARATOR_ARGUMENT);
        Constants.UIMessage uiMessage = Constants.UIMessage
                        .getMessage(Integer.parseInt(array[0].trim()));
        Constants.UIMessageInfo uiMessageInfo = new Constants.UIMessageInfo(uiMessage);
        uiMessageInfo.arg1 = Integer.parseInt(array[1].trim());
        return uiMessageInfo;
    }

    // Format: UIMessage,ip,name
    private static final String FORMAT_UIMESSAGE_PLAYER = "%d%s%s%s%s";
    public static String messageUIMessage(final Constants.UIMessage uiMessage, Player player) {
        return String.format(FORMAT_UIMESSAGE_PLAYER, uiMessage.ordinal(), SEPARATOR_ARGUMENT,
                        getPlayerIp(player), SEPARATOR_ARGUMENT, player.name);
    }

    public static class UIMessageInfo {
        public final Constants.UIMessage uiMessage;
        public final Object obj;

        public UIMessageInfo(Constants.UIMessage uiMessage, Object obj) {
            this.uiMessage = uiMessage;
            this.obj = obj;
        }
    }

    public static UIMessageInfo parseUIMessagePlayer(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String) messageContent).split(SEPARATOR_ARGUMENT);
        Constants.UIMessage uiMessage = Constants.UIMessage
                        .getMessage(Integer.parseInt(array[0].trim()));
        String ip = array[1].trim();
        String playerName = array[2].trim();
        Player player = MahjongManager.getInstance().findPlayer(ip, playerName);
        return new UIMessageInfo(uiMessage, player);
    }

    // Format: UIMessage-objType-obj
    private static final String FORMAT_UIMESSAGE_OBJ = "%d%s%d%s%s";
    private static final String SEPARATOR_ARGUMENT_UI_OBJ = "-";
    private static enum ObjType {
        String,
        Tile,
        TileInfo,
        PlayerAction;

        public static ObjType getType(int ordinal) {
            for (ObjType type : values()) {
                if (type.ordinal() == ordinal) return type;
            }
            return null;
        }
    }

    public static String messageUIMessageObj(final Constants.UIMessage uiMessage, Object msgObj) {
        ObjType objType = null;
        String objString = null;

        if (msgObj instanceof String) {
            objType = ObjType.String;
            objString = msgObj.toString();
        }
        if (msgObj instanceof Tile) {
            objType = ObjType.Tile;
            objString = ((Tile)msgObj).toString();
        }
        if (msgObj instanceof TileInfo) {
            objType = ObjType.TileInfo;
            objString = ((TileInfo)msgObj).tileInfoString();
        }
        if (msgObj instanceof PlayerAction) {
            objType = ObjType.PlayerAction;
            objString = ((PlayerAction)msgObj).infoString();
        }

        if (objType == null) {
            throw new RuntimeException("Not supported msgObj type?!\n" + uiMessage + "\n" + msgObj + "," + msgObj.getClass());
        }
        return String.format(FORMAT_UIMESSAGE_OBJ, uiMessage.ordinal(), SEPARATOR_ARGUMENT_UI_OBJ,
                        objType.ordinal(), SEPARATOR_ARGUMENT_UI_OBJ, objString);
    }

    public static UIMessageInfo parseUIMessageObj(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String) messageContent).split(SEPARATOR_ARGUMENT_UI_OBJ);
        Constants.UIMessage uiMessage = Constants.UIMessage
                        .getMessage(Integer.parseInt(array[0].trim()));
        ObjType objType = ObjType.getType(Integer.parseInt(array[1].trim()));
        String strMsgObj = array[2].trim();
        Object msgobj = null;
        switch (objType) {
            case String:
                msgobj = strMsgObj;
                break;
            case Tile:
                msgobj = Tile.parse(strMsgObj);
                break;
            case TileInfo:
                try {msgobj = TileInfo.parseTileInfoString(strMsgObj);}
                catch (Exception e) {
                    throw new RuntimeException(e + "\n" + messageContent + "\n" + uiMessage + ", array length:" + array.length/*strMsgObj*/);
                }
                break;
            case PlayerAction:
                PlayerActionInfo playerActionInfo = PlayerAction.parseInfoString(strMsgObj);
                Player player = MahjongManager.getInstance().findPlayer(playerActionInfo.playerIp,
                                playerActionInfo.playerName);
                msgobj = new PlayerAction(player, playerActionInfo.tileInfo,
                                playerActionInfo.actions);
                break;
            default:
                throw new RuntimeException("Unknown objType?! " + objType + "," + objType.getClass());
        }
        return new UIMessageInfo(uiMessage, msgobj);
    }
}
