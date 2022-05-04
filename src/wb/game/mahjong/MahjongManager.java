package wb.game.mahjong;

import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import wb.conn.MessageInfo;
import wb.conn.MessageInfo.PlayerInfo;
import wb.conn.MessageUtils;
import wb.conn.MessageUtils.LocationInfo;
import wb.conn.MessageUtils.PlayerIndex;
import wb.conn.RemoteMessage;
import wb.conn.RemoteMessage.ConnMessage;
import wb.conn.RemoteMessage.DataType;
import wb.game.mahjong.constants.Constants;
import wb.game.mahjong.constants.Constants.Reason;
import wb.game.mahjong.constants.Constants.UIMessage;
import wb.game.mahjong.constants.HuConstants;
import wb.game.mahjong.constants.HuConstants.HuedType;
import wb.game.mahjong.model.BluetoothPlayer;
import wb.game.mahjong.model.DummyPlayer;
import wb.game.mahjong.model.GameResource;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.GameResource.Game;
import wb.game.mahjong.model.HandlerThreadExt;
import wb.game.mahjong.model.LocalPlayer;
import wb.game.mahjong.model.Player;
import wb.game.mahjong.model.RemotePlayer;
import wb.game.mahjong.model.Tile;
import wb.game.mahjong.model.Tile.HuTile;
import wb.game.mahjong.model.Tile.TileInfo;
import wb.game.mahjong.model.WifiPlayer;
import wb.game.utils.Utils;

/*
 * UI                  MahjongManager                        Player
 * |                        |                                  |
 * |   --UserAction--->     |                                  |
 * |   <--UI request---     |           <---PlayerCallback--   |
 * |   ---UI updated-->     |                                  |
 * |                        |           ---NotifyPlayerToDo->  |
 * |                        |                                  |
 */
public class MahjongManager {
    // 4 players.
    // player0 is always the user, in the bottom position.
    // The other 3 players can be machine or the connected guys.
    private final Player[] mPlayers = new Player[Location.values().length];

    private static class RemoteManagerState {
        public boolean _4PlayersOK;
        public boolean _4PlayersLocationOK;
        public boolean bankerInfoOK;

        public boolean _13TilesOK;

        public void init(boolean value) {
            _4PlayersOK = value;
            _4PlayersLocationOK = value;
            bankerInfoOK = value;
            _13TilesOK = value;
        }

        public boolean allOkToStart() {
            return _4PlayersOK && _4PlayersLocationOK && bankerInfoOK;
        }
    }

    private final RemoteManagerState[] mRemoteManagerStates = new RemoteManagerState[mPlayers.length];

    private static class BankerInfo {
        private Location location;
        private int count; // 连庄数.

        public void setLocation(Location location) {
            this.location = location;
            count = 0;
        }

        public Location getLocation() {
            return location;
        }

        public boolean sameLocation(Location location) {
            return this.location == location;
        }

        public void keepLocation() {
            count++;
        }

        public int getCount() {
            return count;
        }

        public void init(BankerInfo bankerInfo) {
            this.location = bankerInfo.location;
            this.count = bankerInfo.count;
        }

        private static final String FORMAT_BANKER_INFO = "%d%s%d";
        private static final String SEPARATOR_INFO = ",";
        public String infoString() {
            return String.format(FORMAT_BANKER_INFO, location.ordinal(), SEPARATOR_INFO, count);
        }

        public static BankerInfo parse(final String infoString) {
            String[] array = infoString.split(SEPARATOR_INFO);
            Location location = Location.getLocation(Integer.parseInt(array[0].trim()));
            int count = Integer.parseInt(array[1].trim());

            BankerInfo bankerInfo = new BankerInfo();
            bankerInfo.setLocation(location);
            bankerInfo.count = count;
            return bankerInfo;
        }
    }
    private final BankerInfo mBankerInfo = new BankerInfo();

    // 上次game中的第一个胡牌player.
    private Player mFirstWinnerInLastGame;

    // 记录正在等待的players，最多3家正在等待，收到反馈后删掉对应的player.
    private final ArrayList<Player> mWaitingQueue = new ArrayList<Player>(3);

    private Handler mMainThreadHandler;

    private GameThread mGameThread; // manager工作线程.

    private SoundThread mSoundThread; // 播放声音的线程.

    private int mGameIndex;

    private String mHostIp;

    private volatile boolean mMuted; // 是否静音.

    private static MahjongManager sInstance;

    public static MahjongManager getInstance() {
        if (sInstance == null) {
            sInstance = new MahjongManager();
        }
        return sInstance;
    }

    private MahjongManager() {
        for (int i = 0; i < mRemoteManagerStates.length; i++) {
            mRemoteManagerStates[i] = new RemoteManagerState();
        }
    }

    public void setMute(boolean isMuted) {
        mMuted = isMuted;
    }

    public boolean isMute() {
        return mMuted;
    }

    public void playSound(final Player player, final Action action, final TileInfo tileInfo) {
        if (mMuted) return; // 已设置静音.
        if (player == null) {
            Player tileOwner = getPlayer(tileInfo.fromWhere);
            if (tileOwner.getPosition() != Position.BOTTOM && !(tileOwner instanceof DummyPlayer)) {
                // 只播放bottom player或DummyPlayer的声音。
                return;
            }
        }
        if (player != null && player.getPosition() != Position.BOTTOM
                        && !(player instanceof DummyPlayer)) {
            // 只播放bottom player或DummyPlayer的声音。
            return;
        }
        mSoundThread.post(new Runnable() {
            @Override
            public void run() {Constants.debug("playSound(" + player + "," + action + "," + tileInfo + ")");
                String actionSoundFilepath =getActionSoundAssetFilepath();
                if (actionSoundFilepath != null) {
                    playSound(actionSoundFilepath);
                }

                final boolean isBlackGang = (action == Action.Gang && player != null
                                && player.isBlackGanged(tileInfo.tile));
                if (isBlackGang) return; // 暗杠不能报牌.

                String tileSoundFilepath = getTileSoundAssetFilepath();
                if (tileSoundFilepath != null) {
                    if (actionSoundFilepath != null) {
                        // 连续两个声音之间等待一下以便前一个声音播放完整.
                        try {
                            Thread.sleep(400L);
                        } catch (Exception e) {
                            // TODO: nothing should do?
                        }
                    }
                    playSound(tileSoundFilepath);
                }
            }

            private String getActionSoundAssetFilepath() {
                if (player == null || action == null) return null;
                if (player instanceof LocalPlayer) {
                    LocalPlayer localPlayer = (LocalPlayer) player;
                    if (localPlayer.soundCustomized()) {
                        String customizedSound = Constants.getCustomizedActionSound(player.name,
                                        action);
                        if (!TextUtils.isEmpty(customizedSound)) {
                            return customizedSound;
                        }
                    }
                }
                return Constants.getActionSoundAssetFilepath(player.getLocation(), player.gender,
                                action);
            }

            private String getTileSoundAssetFilepath() {
                Player tileOwner = (player == null ? getPlayer(tileInfo.fromWhere) : player);
                if (tileOwner instanceof LocalPlayer) {
                    LocalPlayer localPlayer = (LocalPlayer) tileOwner;
                    if (localPlayer.soundCustomized()) {
                        String customizedSound = Constants.getCustomizedTileSound(player.name,
                                        tileInfo.tile);
                        if (!TextUtils.isEmpty(customizedSound)) {
                            return customizedSound;
                        }
                    }
                }
                return Constants.getTileSoundAssetFilepath(tileOwner.getLocation(),
                                tileOwner.gender, tileInfo.tile);
            }
        });
    }

    private void playSound(final String soundFilepathInAssets) {
        if (Thread.currentThread() != mSoundThread) {
            throw new RuntimeException("playSound must run in SoundThread!");
        }
        if (TextUtils.isEmpty(soundFilepathInAssets)) {
            Constants.debug("failed to play sound: EMPTY filepath!");
            return;
        }
        mSoundThread.playSound(soundFilepathInAssets);
    }

    public synchronized void setMainThreadHandler(Handler mainHandler) {
        /*if (mainHandler != null && mMainThreadHandler != null) {
            throw new IllegalStateException("Why there has been a main thread handler?!");
        }*/
        mMainThreadHandler = mainHandler;
    }

    // 麻将里面的东南西北四个方位是按天文位置的东南西北方位排列的,
    // 我们平时看地图的方位是上北下南左西右东,
    // 而看天文图则是上北下南左东右西.
    public enum Location {
        // 逆时针方向：东南西北
        East(R.string.dir_east),
        South(R.string.dir_south),
        West(R.string.dir_west),
        North(R.string.dir_north);

        public final int labelResId;

        private Location(int labelResId) {
            this.labelResId = labelResId;
        }

        public static Location getLocation(int order) {
            for (Location location : values()) {
                if (location.ordinal() == order) return location;
            }
            return null;
        }

        // 获得下家的位置.
        public static Location getNextLocation(Location location) {
            if (location == null) return East;
            Location[] values = values();
            return getLocation((location.ordinal() + 1) % values.length);
        }

        // 获得上家位置.
        public static Location getPreviousLocation(Location location) {
            if (location == null) return East;
            Location[] values = values();
            return getLocation((location.ordinal() + values.length - 1) % values.length);
        }
    }

    // Position in screen.
    // 逆时针方向顺序： 逆时针出牌
    // INDEX_BOTTOM = 0;
    // INDEX_RIGHT  = 1;
    // INDEX_TOP    = 2;
    // INDEX_LEFT   = 3;
    public static enum Position {
        // 逆时针方向, 逆时针出牌
        /* I am always in the bottom */
        BOTTOM(R.id.text_location_bottom,
               R.id.location_indicator_bottom,
               R.id.player_name_bottom,
               R.id.player_icon_bottom,
               R.id.player_ignored_type_bottom,
               R.id.banker_bottom,
               R.id.banker_count_bottom,
               R.id.bottom_tile_list,
               R.id.new_tile_bottom,
               R.drawable.bg_vertical,
               R.drawable.back_vertical,
               R.id.bottom_chi_tiles,
               R.id.bottom_peng_tiles,
               R.id.bottom_gang_tiles,
               R.id.bottom_hu_tiles,
               R.id.bottom_thrown_tiles,
               R.layout.tile_item_bottom_chi,
               R.layout.tile_item_bottom_peng,
               R.layout.tile_item_bottom_gang,
               R.layout.tile_item_bottom_gang,
               R.layout.tile_item_bottom_hu,
               R.layout.tile_item_bottom_open,
               R.layout.tile_item_bottom_close,
               R.id.tile_indicator_bottom),
        RIGHT(R.id.text_location_right,
              R.id.location_indicator_right,
              R.id.player_name_right,
              R.id.player_icon_right,
              R.id.player_ignored_type_right,
              R.id.banker_right,
              R.id.banker_count_right,
              R.id.right_tile_list,
              R.id.new_tile_right,
              R.drawable.bg_right,
              R.drawable.back_right,
              R.id.right_chi_tiles,
              R.id.right_peng_tiles,
              R.id.right_gang_tiles,
              R.id.right_hu_tiles,
              R.id.right_thrown_tiles,
              R.layout.tile_item_right_chi,
              R.layout.tile_item_right_peng,
              R.layout.tile_item_right_gang,
              R.layout.tile_item_right_gang_black,
              R.layout.tile_item_right_hu,
              R.layout.tile_item_right_open,
              R.layout.tile_item_right_close,
              R.id.tile_indicator_right),
        TOP(R.id.text_location_top,
            R.id.location_indicator_top,
            R.id.player_name_top,
            R.id.player_icon_top,
            R.id.player_ignored_type_top,
            R.id.banker_top,
            R.id.banker_count_top,
            R.id.top_tile_list,
            R.id.new_tile_top,
            R.drawable.bg_vertical,
            R.drawable.back_vertical,
            R.id.top_chi_tiles,
            R.id.top_peng_tiles,
            R.id.top_gang_tiles,
            R.id.top_hu_tiles,
            R.id.top_thrown_tiles,
            R.layout.tile_item_top_chi,
            R.layout.tile_item_top_peng,
            R.layout.tile_item_top_gang,
            R.layout.tile_item_top_gang_black,
            R.layout.tile_item_top_hu,
            R.layout.tile_item_top_open,
            R.layout.tile_item_top_close,
            R.id.tile_indicator_top),
        LEFT(R.id.text_location_left,
             R.id.location_indicator_left,
             R.id.player_name_left,
             R.id.player_icon_left,
             R.id.player_ignored_type_left,
             R.id.banker_left,
             R.id.banker_count_left,
             R.id.left_tile_list,
             R.id.new_tile_left,
             R.drawable.bg_left,
             R.drawable.back_left,
             R.id.left_chi_tiles,
             R.id.left_peng_tiles,
             R.id.left_gang_tiles,
             R.id.left_hu_tiles,
             R.id.left_thrown_tiles,
             R.layout.tile_item_left_chi,
             R.layout.tile_item_left_peng,
             R.layout.tile_item_left_gang,
             R.layout.tile_item_left_gang_black,
             R.layout.tile_item_left_hu,
             R.layout.tile_item_left_open,
             R.layout.tile_item_left_close,
             R.id.tile_indicator_left);

        private final int locationResId;
        private final int activeIndicatorResId;
        private final int nameResId;
        private final int iconResId;
        private final int ignoredResId;
        private final int bankerResId, bankerCountResId;
        private final int tileListResId;
        private final int newTileResId;
        private final int tileBackgroundDrawableResId;
        private final int tileBackDrawableResId;
        private final int tileListChiResId;
        private final int tileListPengResId;
        private final int tileListGangResId;
        private final int tileListHuResId;
        private final int thrownTileListResId;

        private final int tileChiLayoutId;
        private final int tilePengLayoutId;
        private final int tileGangLayoutId;
        private final int tileGangBlackLayoutId;
        private final int huedTileLayoutId;

        public final int tileOpenResId;
        public final int tileCloseResId;

        private final int tileIndicatorViewId;

        private LinearLayout mTileListView;
        private ViewGroup mThrownTilesView;

        public Drawable tileBackgroundDrawable;
        public Drawable tileBackDrawable;

        private TextView mTextIgnoredType;
        private TextView mTextBanker;
        private TextView mTextBankerCount;

        private View mCurPlayerIndicatorView;

        private Player mPlayer;

        private Position(int locationResId, int activeIndicatorResId, int nameResId, int iconResId,
                int ignoredResId, int bankerResId, int bankerCountResId, int tileListResId,
                int newTileResId, int tileBackgroundDrawableResId, int tileBackDrawableResId,
                int tileListChiResId, int tileListPengResId, int tileListGangResId,
                int tileListHuResId, int thrownTilesViewResId, int tileChiLayoutId,
                int tilePengLayoutId, int tileGangLayoutId, int tileGangBlackLayoutId,
                int huedTileLayoutId, int tileOpenResId, int tileCloseResId,
                int tileIndicatorViewId) {
            this.locationResId = locationResId;
            this.activeIndicatorResId = activeIndicatorResId;
            this.nameResId = nameResId;
            this.iconResId = iconResId;
            this.ignoredResId = ignoredResId;
            this.bankerResId = bankerResId;
            this.bankerCountResId = bankerCountResId;
            this.tileListResId = tileListResId;
            this.newTileResId = newTileResId;
            this.tileBackgroundDrawableResId = tileBackgroundDrawableResId;
            this.tileBackDrawableResId = tileBackDrawableResId;
            this.tileListChiResId = tileListChiResId;
            this.tileListPengResId = tileListPengResId;
            this.tileListGangResId = tileListGangResId;
            this.tileListHuResId   = tileListHuResId;
            this.thrownTileListResId = thrownTilesViewResId;
            this.tileChiLayoutId  = tileChiLayoutId;
            this.tilePengLayoutId = tilePengLayoutId;
            this.tileGangLayoutId = tileGangLayoutId;
            this.tileGangBlackLayoutId = tileGangBlackLayoutId;
            this.huedTileLayoutId = huedTileLayoutId;
            this.tileOpenResId = tileOpenResId;
            this.tileCloseResId = tileCloseResId;
            this.tileIndicatorViewId = tileIndicatorViewId;
        }

        private void initView(Activity activity) {
            final Resources res = activity.getResources();

            TextView textViewName = (TextView)activity.findViewById(nameResId);
            textViewName.setText(mPlayer.name);

            ImageView iconView = (ImageView)activity.findViewById(iconResId);
            iconView.setImageBitmap(mPlayer.getIconBitmap());

            mTextIgnoredType = (TextView)activity.findViewById(ignoredResId);
            mTextIgnoredType.setVisibility(View.GONE);

            mTextBanker = (TextView)activity.findViewById(bankerResId);
            mTextBanker.setVisibility(View.INVISIBLE);
            mTextBankerCount = (TextView)activity.findViewById(bankerCountResId);
            mTextBankerCount.setVisibility(View.GONE);

            TextView locationLabelView = (TextView)activity.findViewById(locationResId);
            locationLabelView.setText(mPlayer.getLocation().labelResId);

            mCurPlayerIndicatorView = activity.findViewById(activeIndicatorResId);

            mTileListView = (LinearLayout)activity.findViewById(tileListResId);

            ImageView newTileView = (ImageView)activity.findViewById(newTileResId);

            // Resource.getDrawable(resId) has been deprecated...
            //tileBackgroundDrawable = res.getDrawable(tileBackgroundDrawableResId);
            //tileBackDrawable = res.getDrawable(tileBackDrawableResId);
            // Use Resource.getDrawable(resId, theme) instead...
            tileBackgroundDrawable = res.getDrawable(tileBackgroundDrawableResId, null);
            tileBackDrawable = res.getDrawable(tileBackDrawableResId, null);

            LinearLayout tileListChiView  = (LinearLayout)activity.findViewById(tileListChiResId);
            LinearLayout tileListPengView = (LinearLayout)activity.findViewById(tileListPengResId);
            LinearLayout tileListGangView = (LinearLayout)activity.findViewById(tileListGangResId);
            LinearLayout tileListHuView   = (LinearLayout)activity.findViewById(tileListHuResId);
            mThrownTilesView = (ViewGroup)activity.findViewById(thrownTileListResId);
            mPlayer.setViews(mTileListView, newTileView,
                    tileListChiView,  tileChiLayoutId,
                    tileListPengView, tilePengLayoutId,
                    tileListGangView, tileGangLayoutId, tileGangBlackLayoutId, huedTileLayoutId,
                    tileListHuView,
                    mThrownTilesView, tileOpenResId, tileCloseResId, tileIndicatorViewId);
        }

        public void setPlayer(Context context, Player player) {
            player.setPosition(this);
            mPlayer = player;
        }

        public String getPlayerName() {
            return mPlayer.name;
        }

        public boolean isIgnoredTypeDone() {
            return mPlayer.isIgnoredDetermined();
        }

        // 更新indicator显示状态
        // 只有正在摸牌/发牌的player indicator显示.
        public void updateWaiting(boolean isWaiting) {
            mCurPlayerIndicatorView.setVisibility(isWaiting ? View.VISIBLE : View.INVISIBLE);
        }

        public void updateIgnoredType() {
            mTextIgnoredType.setVisibility(View.VISIBLE);
            mTextIgnoredType.setText(mPlayer.getIgnoredType().labelResId);
        }

        public static void setBanker(BankerInfo bankerInfo) {
            for (Position position : values()) {
                if (bankerInfo.sameLocation(position.mPlayer.getLocation())) {
                    position.mTextBanker.setVisibility(View.VISIBLE);
                    int count = bankerInfo.getCount();
                    if (count > 0) {
                        position.mTextBankerCount.setText(Integer.toString(count + 1));
                        position.mTextBankerCount.setVisibility(View.VISIBLE);
                    } else {
                        position.mTextBankerCount.setVisibility(View.GONE);
                    }
                } else {
                    position.mTextBanker.setVisibility(View.INVISIBLE);
                    position.mTextBankerCount.setVisibility(View.GONE);
                }
            }
        }

        public static void initWaiting() {
            for (Position position : values()) {
                position.updateWaiting(false);
            }
        }

        public static void showPlayersIgnored() {
            for (Position position : values()) {
                position.updateIgnoredType();
            }
        }

        public static void initViews(Activity activity) {
            for (Position position : values()) {
                position.initView(activity);
            }
        }

        public static void updateWaiting(final Location curLocation) {
            for (Position position : values()) {
                position.updateWaiting(position.mPlayer.getLocation() == curLocation);
            }
        }

        public static Position getPosition(int ordinal) {
            Position[] values = values();
            for (Position position : values) {
                if (position.ordinal() == (ordinal % values.length)) {
                    return position;
                }
            }
            return Position.BOTTOM;
        }
    }

    private void clearLocations() {
        for (Player player : mPlayers) {
            if (player == null) continue;
            player.setLocation(null);
        }
    }

    private void setPositions(final Activity activity) {
        // mPlayers[0] is always the current user, in BOTTOM.
        Player player = mPlayers[0];
        Position position = Position.BOTTOM;
        do {
            position.setPlayer(activity, player);
            setPlayerViewChangeListener(activity, player);

            player = getNextPlayer(player);
            position = Position.getPosition(position.ordinal() + 1);
        } while (player != mPlayers[0]);
        Position.initViews(activity);
    }

    private synchronized void runInUIThread(final Runnable runnable) {
        if (mMainThreadHandler != null) {
            mMainThreadHandler.post(runnable);
        }
    }

    private void setPlayerViewChangeListener(final Activity activity, final Player curPlayer) {
        curPlayer.setViewChangeListener(new Player.PlayerCallback() {
            @Override
            public void showCallstack(final Throwable t, final String info) {
                runInUIThread(new Runnable() {
                    @Override
                    public void run() {
                        sendMessageToMainThread(Constants.UIMessage.MSG_GAME_END_NULL);
                        Utils.showInfo(activity, curPlayer.name,
                                        info + "\n" + Utils.getThrowableStackTrace(t));
                    }
                });
            }

            @Override
            public void send2RemotePlayer(final RemoteMessage.ConnMessage connMessage,
                            final String uiMessageContent) {
                PlayerInfo playerInfo = new PlayerInfo(MessageUtils.getPlayerIp(curPlayer),
                                curPlayer.name);
                sendMessage2RemotePlayer(connMessage, uiMessageContent, playerInfo);
            }

            @Override
            public void onViewChanged() {
                notifyMainThread(curPlayer, Constants.UIMessage.MSG_REFRESH_PLAYER);
            }

            @Override
            public void onIgnoredTypeDetermined() {
                sendMessageToMainThread(Constants.UIMessage.MSG_DETERMINE_IGNORED_TYPE_DONE);
            }

            @Override
            public void notifyActionsAvailableOnNewTile(TileInfo tileInfo) {
                notifyPlayerActions(curPlayer, tileInfo, curPlayer.getActions());
            }

            @Override
            public void notifyActionsAvailableOnThrownTile(TileInfo tileInfo) {
                checkPlayerActionsOnThrownTile(curPlayer, tileInfo);
            }

            @Override
            public void notifyActionsAvailableOnGotTile(TileInfo tileInfo) {
                notifyPlayerActions(curPlayer, tileInfo, curPlayer.getActions());
            }

            @Override
            public void notifyActionsAvailableOnGangedTile(TileInfo tileInfo,
                            boolean isBlackGang) {
                checkPlayerActionsOnGangedTile(curPlayer, tileInfo, isBlackGang);
            }

            @Override
            public void notifyThrowAvaiable() {
                // 通知当前player打一张牌
                notifyMainThread(curPlayer, Constants.UIMessage.MSG_NOTIFY_PLAYER_THROW_TILE);
            }

            @Override
            public void onTileThrown(TileInfo tileInfo) {
                // 扔出一张牌之后需要更新player显示.
                notifyMainThread(curPlayer, Constants.UIMessage.MSG_REFRESH_PLAYER);
                // 扔出一张牌后其他3 players可能有吃/碰/杠/胡的action;
                notifyPlayersCheckActionOnThrownTile(tileInfo);
            }

            @Override
            public void actionDoneOnNewTile(final Action action, final TileInfo tileInfo) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayerActionDoneOnNewTile(curPlayer, action, tileInfo);
                    }
                });
            }

            @Override
            public void actionDoneOnGotTile(final Action action, final TileInfo tileInfo) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayerActionDoneOnGotTile(curPlayer, action, tileInfo);
                    }
                });
            }

            @Override
            public void actionDoneOnThrownTile(final Action action, final TileInfo tileInfo) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayerActionDoneOnThrownTile(curPlayer, action, tileInfo);
                    }
                });
            }

            @Override
            public void gangFlowerDone(final TileInfo tileInfo) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        // 扔掉最后一张牌因为已经杠过了。
                        GameResource.getGame(mGameIndex).distributeTile(true,
                                        mDistributeTileListener);
                        onPlayerGangFlowered(curPlayer, tileInfo);
                    }
                });
            }

            @Override
            public void actionDoneOnGangedTile(final Action action, final TileInfo tileInfo) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayerActionDoneOnGangedTile(curPlayer, action, tileInfo);
                    }
                });
            }

            @Override
            public void actionsIgnoredOnNewTile(final TileInfo tileInfo, final Action... actions) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyMainThread(curPlayer,
                                        Constants.UIMessage.MSG_NOTIFY_PLAYER_THROW_TILE);
                    }
                });
            }

            @Override
            public void actionsIgnoredOnGotTile(final TileInfo tileInfo, final Action... actions) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyMainThread(curPlayer,
                                        Constants.UIMessage.MSG_NOTIFY_PLAYER_THROW_TILE);
                    }
                });
            }

            @Override
            public void actionsIgnoredOnThrownTile(final TileInfo tileInfo,
                            final Action... actions) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        updateWaitingPlayers(tileInfo);
                        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_ACTION_IGNORED,
                                        new Player.PlayerAction(curPlayer, tileInfo, actions));
                        checkPlayerActionsOnThrownTile(curPlayer, tileInfo);
                    }
                });
            }

            @Override
            public void actionsIgnoredOnGangedTile(final TileInfo tileInfo,
                            final boolean isBlackGang, final Action... actions) {
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        updateWaitingPlayers(tileInfo);
                        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_ACTION_IGNORED,
                                        new Player.PlayerAction(curPlayer, tileInfo, actions));
                        checkPlayerActionsOnGangedTile(curPlayer, tileInfo, isBlackGang);
                    }
                });
            }

            @Override
            public void promptDetermineIgnored() {
                notifyMainThread(curPlayer, Constants.UIMessage.MSG_SHOW_DETERMINE_IGNORED);
            }

            @Override
            public void promptChi(Player.PlayerCanChi playerCanChi) {
                notifyMainThread(Constants.UIMessage.MSG_SHOW_CAN_CHI, playerCanChi);
            }

            @Override
            public void showCanHuTiles(final HuTile[] canHuTiles) {
                notifyMainThread(Constants.UIMessage.MSG_SHOW_CAN_HU_TILES, canHuTiles);
            }

            @Override
            public void promptGang(final TileInfo tileInfo,
                            final Player.CanGangTile... canGangTiles) {
                notifyMainThread(Constants.UIMessage.MSG_SHOW_CAN_GANG_TILES,
                                new Player.PlayerCanGang(curPlayer, tileInfo, canGangTiles));
            }

            @Override
            public void maxGangCountReached() {
                checkGameEnd(Reason.MaxGangReached);
            }

            @Override
            public void whenNoTile() {
                checkGameEnd(Reason.NoTile);
            }
        });
    }

    private void checkPlayerActionsOnThrownTile(final Player actionCheckedPlayer,
                    final TileInfo tileInfo) {
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                mWaitingQueue.remove(actionCheckedPlayer);
                if (mWaitingQueue.size() <= 0) {
                    // 如果所有player都收集了action，
                    // 需要通知可以采取action的用户作决定.
                    checkActionOnThrownTile(tileInfo);
                    return;
                }
                // 还有player没有检查完action，继续等待.
                updateWaitingToast(tileInfo);
            }
        });
    }

    private void checkPlayerActionsOnGangedTile(final Player actionCheckedPlayer,
                    final TileInfo tileInfo, final boolean isBlackGang) {
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                mWaitingQueue.remove(actionCheckedPlayer);
                if (mWaitingQueue.size() <= 0) {
                    // 如果所有player都收集了action，
                    // 需要通知可以采取action的用户作决定.
                    checkActionOnGangedTile(tileInfo, isBlackGang);
                    return;
                }
                // 还有player没有检查完action，继续等待.
                updateWaitingToast(tileInfo);
            }
        });
    }

    public int getPlayerIndex(Player player) {
        for (int i = 0; i < mPlayers.length; i++) {
            if (player == mPlayers[i]) return i;
        }
        throw new RuntimeException("How come this happened?!");
    }

    private Player getPlayer(Location location) {
        for (Player player : mPlayers) {
            if (player.getLocation() == location) return player;
        }
        throw new RuntimeException("No player in location? " + location);
    }

    // 如果hostIp为空，说明当前就是host，或者没有remote players.
    private boolean isHost() {
        return TextUtils.isEmpty(mHostIp);
    }

    public synchronized boolean isBankerHere() {
        Player banker = getBankerPlayer();
        if (banker instanceof LocalPlayer) return true;
        if (banker instanceof WifiPlayer) return false;
        if (banker instanceof DummyPlayer) {
            return ((DummyPlayer)banker).ipv4 == null;
        }
        return false;
    }

    private synchronized void sendMessageToMainThread(Constants.UIMessage uiMessage) {
        if (mMainThreadHandler == null) return;
        mMainThreadHandler.sendEmptyMessage(uiMessage.ordinal());
    }

    private synchronized void sendMessageToMainThread(Constants.UIMessage uiMessage, int arg1) {
        if (mMainThreadHandler == null) return;
        Message msg = mMainThreadHandler.obtainMessage(uiMessage.ordinal());
        msg.arg1 = arg1;
        mMainThreadHandler.sendMessage(msg);
    }

    private synchronized void notifyMainThread(Player player, Constants.UIMessage uiMessage) {
        if (mMainThreadHandler == null) return;
        Message msg = Message.obtain();
        msg.what = uiMessage.ordinal();
        msg.obj = player;
        mMainThreadHandler.sendMessage(msg);
    }

    private synchronized void notifyMainThread(Constants.UIMessage uiMessage, Object msgObj) {
        if (mMainThreadHandler == null) return;
        Message msg = Message.obtain();
        msg.what = uiMessage.ordinal();
        msg.obj = msgObj;
        mMainThreadHandler.sendMessage(msg);
    }

    private void updateWaitingPlayers(final TileInfo tileInfo) {
        final Player tileOwner = getPlayer(tileInfo.fromWhere);
        StringBuilder sb = new StringBuilder();
        for (Player player : mPlayers) {
            if (player == tileOwner) continue;
            if (!player.actionCollected()) continue;
            if (!player.hasPendingAction()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(player.name).append(' ').append(tileInfo.toString());
        }
        if (sb.length() > 0) {
            notifyMainThread(Constants.UIMessage.MSG_SHOW_PROMPT, sb.toString());
        }
    }

    private void onPlayerActionDoneOnNewTile(Player curPlayer, Action action, TileInfo tileInfo) {
        // 更新UI, 出提示
        updateWaitingPlayers(tileInfo);
        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_ACTION_DONE_ON_NEW_TILE,
                        new Player.PlayerAction(curPlayer, tileInfo, action));
    }

    public void nextStepAfterActionDoneOnNewTile(Player player, Action action, TileInfo tileInfo) {
        Player nextPlayer = null;
        Constants.UIMessage nextUIMessage = null;
        switch (action) {
            case Hu:
            case GangFlower:
                // 如果接下来需要通知一个player拿牌，而且是在胡牌之后，
                // 需要找到最后一个胡牌的下一个player.
                nextPlayer = getNextPlayer(tileInfo.getLastHuedPlayer());
                nextUIMessage = getNextUIMessageForHu();
                if (mFirstWinnerInLastGame == null) {
                    mFirstWinnerInLastGame = player;
                }
                break;
            case Ting:
                nextPlayer = player; // 还是当前player打一张牌.
                nextUIMessage = Constants.UIMessage.MSG_NOTIFY_PLAYER_THROW_TILE;
                break;
            case Gang:
                nextPlayer = player; // 还是给当前player从尾部发一张牌.
                nextUIMessage = Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE_FROM_END;
                break;
            default:
                break;
        }
        if (nextUIMessage == null) return;
        if (nextUIMessage == Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE_FROM_END) {
            notifyMainThread(nextUIMessage, new Player.PlayerAction(nextPlayer, tileInfo,
                            action));
            return;
        }
        if (nextPlayer == null || nextUIMessage == Constants.UIMessage.MSG_GAME_END) {
            sendMessageToMainThread(nextUIMessage);
            return;
        }
        notifyMainThread(nextPlayer, nextUIMessage);
    }

    private void onPlayerActionDoneOnGotTile(Player curPlayer, Action action, TileInfo tileInfo) {
        // 更新UI, 出提示
        updateWaitingPlayers(tileInfo);
        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_ACTION_DONE_ON_GOT_TILE,
                        new Player.PlayerAction(curPlayer, tileInfo, action));
    }

    public void nextStepAfterActionDoneOnGotTile(Player player, Action action, TileInfo tileInfo) {
        Player nextPlayer = null;
        Constants.UIMessage nextUIMessage = null;
        switch (action) {
            case Gang:
                nextPlayer = player; // 还是给当前player从尾部发一张牌.
                nextUIMessage = Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE_FROM_END;
                break;
            case Ting:
                nextPlayer = player; // 还是当前player打一张牌.
                nextUIMessage = Constants.UIMessage.MSG_NOTIFY_PLAYER_THROW_TILE;
                break;
            default:
                break;
        }
        if (nextUIMessage == null) return;
        if (nextUIMessage == Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE_FROM_END) {
            notifyMainThread(nextUIMessage, new Player.PlayerAction(nextPlayer, tileInfo,
                            action));
            return;
        }
        notifyMainThread(nextPlayer, nextUIMessage);
    }

    private void onPlayerActionDoneOnThrownTile(final Player curPlayer, Action action, TileInfo tileInfo) {
        // 更新UI, 出提示
        updateWaitingPlayers(tileInfo);
        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_ACTION_DONE_ON_THROWN_TILE,
                        new Player.PlayerAction(curPlayer, tileInfo, action));
    }

    public void nextStepAfterActionDoneOnThrownTile(Player curPlayer, Action action, TileInfo tileInfo) {
        switch (action) {
            case Hu: // 需要检查还有没有别的player也胡此tile.
                for (Player player : mPlayers) {
                    if (player == curPlayer) continue;
                    player.clearAction(Action.Hu);
                }
                if (mFirstWinnerInLastGame == null) {
                    mFirstWinnerInLastGame = curPlayer;
                }
                break;
            case GangFlower:
                for (Player player : mPlayers) {
                    if (player == curPlayer) continue;
                    player.clearActionInfo();
                }
                if (mFirstWinnerInLastGame == null) {
                    mFirstWinnerInLastGame = curPlayer;
                }
                break;
            case Chi:
            case Peng:
            case Gang: // Gang3_1
                for (Player player : mPlayers) {
                    if (player == curPlayer) continue;
                    player.clearActionInfo();
                }
                break;
            default:
                return;
        }
        // 从打出的牌中删掉，因为已经被别人吃/碰/杠/胡了.
        getPlayer(tileInfo.fromWhere).removeTileFromThrown(tileInfo);
        checkPlayerActionsOnThrownTile(curPlayer, tileInfo);
    }

    private void onPlayerGangFlowered(final Player curPlayer, TileInfo tileInfo) {
        // 更新UI, 出提示
        updateWaitingPlayers(tileInfo);
        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_GANG_FLOWERED,
                        new Player.PlayerAction(curPlayer, tileInfo, Action.GangFlower));
        nextStepAfterActionDoneOnNewTile(curPlayer, Action.GangFlower, tileInfo);
    }

    private void onPlayerActionDoneOnGangedTile(final Player curPlayer, Action action, TileInfo tileInfo) {
        // 更新UI, 出提示
        updateWaitingPlayers(tileInfo);
        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_ACTION_DONE_ON_GANGED_TILE,
                        new Player.PlayerAction(curPlayer, tileInfo, action));
    }

    private boolean allPlayersCheckHuDone() {
        for (Player player : mPlayers) {
            if (player.isActionPending(Action.Hu)) return false;
        }
        return true;
    }

    private Constants.UIMessage getNextUIMessageForHu() {
        final HuConstants.HuedType huedType = GameResource.getGame(mGameIndex).getHuedType();
        switch (huedType) {
            case HuOnceAll:    // 一家胡牌后，则游戏结束.
                if (allPlayersCheckHuDone()) {
                    // 因为可能一炮多响，所以需要所有玩家都查过是否胡当前tile后，本局才可以结束.
                    return Constants.UIMessage.MSG_GAME_END;
                }
                break;
            case HuOncePlayer: // 一个玩家只能胡一次，胡过之后剩余玩家继续.
                if (allPlayersCheckHuDone()) {
                    // 所有玩家都查过是否胡当前tile后，最后一个胡牌的下家摸牌.
                    return Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE;
                }
                break;
            case HuMulti:      // 可以多家胡牌，每个玩家都可以胡任意次.
                if (allPlayersCheckHuDone()) {
                    // 所有玩家都查过是否可以胡当前tile后，最后一个胡牌的下家摸牌.
                    return Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE;
                }
            default:
                break;
        }
        return null;
    }

    // 对于扔出的tile, 需要通知主线程和其他players有thrown tile.
    // 需要更新UI focus on当前thrown tile.
    private void notifyPlayersCheckActionOnThrownTile(final TileInfo tileInfo) {
        notifyMainThread(Constants.UIMessage.MSG_NOTIFY_CHECK_ACTION_ON_THROWN_TILE, tileInfo);
    }

    private Player[] getActionAvailablePlayers(final Action action) {
        ArrayList<Player> tempList = new ArrayList<Player>(3);
        for (Player player : mPlayers) {
            if (player.isActionPending(action)) {
                tempList.add(player);
            }
        }
        return tempList.toArray(new Player[tempList.size()]);
    }

    // 获得有action(s)的player(s).
    private Player[] getActionPlayers(final TileInfo tileInfo) {
        ArrayList<Player> players = new ArrayList<Player>(3);
        for (Player player : mPlayers) {
            if (player.getLocation() == tileInfo.fromWhere) continue;
            if (player.hasPendingAction()) {
                players.add(player);
            }
        }
        return players.toArray(new Player[players.size()]);
    }

    // 获得有action done的第一个player.
    private Player getActionDonePlayer(final TileInfo tileInfo) {
        for (Player player : mPlayers) {
            if (player.getLocation() == tileInfo.fromWhere) continue;
            if (player.hasActionDone()) {
                return player;
            }
        }
        return null;
    }

    // 对于打出来的牌，只能有胡/碰/杠/吃操作，而且有优先级.
    private static final Action[] sActionsOnThrownTiles = {
                    Action.Hu, Action.Peng, Action.Gang, Action.Chi
    };
    private void checkActionOnThrownTile(TileInfo tileInfo) {
        //Constants.debug("checkActionOnThrownTile " + tileInfo);
        final Player tileOwner = getPlayer(tileInfo.fromWhere);
        // 先看看对该tile有几个player可以有action.
        Player[] actionPlayers = getActionPlayers(tileInfo);
        // 如果没有player可以对currently thrown tile有action.
        if (actionPlayers == null || actionPlayers.length <= 0) {
            // 先看看是不是已经有player做了action.
            Player actionDonePlayer = getActionDonePlayer(tileInfo);
            if (actionDonePlayer == null) {
                // 如果没有吃/杠/碰/胡等, 则下家摸牌.
                Player nextPlayer = getNextPlayer(tileOwner);
                notifyMainThread(nextPlayer, Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE);
                return;
            }
            Action doneAction = actionDonePlayer.getDoneAction();
            if (doneAction == Action.Hu) {
                Constants.UIMessage nextUIMessage = getNextUIMessageForHu();
                if (nextUIMessage == Constants.UIMessage.MSG_GAME_END) {
                    sendMessageToMainThread(nextUIMessage);
                    return;
                }
                // 如果有人胡了这张牌，则最后一个胡牌的下家摸牌.
                Player lastHuedPlayer = tileInfo.getLastHuedPlayer();
                Player nextPlayer = getNextPlayer(lastHuedPlayer);
                notifyMainThread(nextPlayer, nextUIMessage);
                return;
            }
            if (doneAction == Action.Gang) {
                notifyMainThread(Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE_FROM_END,
                                new Player.PlayerAction(actionDonePlayer, tileInfo, doneAction));
                return;
            }
            // 其他action，如吃/碰等，则需要做了该action的player继续打牌.
            notifyMainThread(Constants.UIMessage.MSG_NOTIFY_CHECK_ACTION_ON_GOT_TILE,
                            new Player.PlayerAction(actionDonePlayer, tileInfo, doneAction));
            return;
        }
        // 如果只有一个player有action(s)，
        if (actionPlayers.length == 1) {
            // 一次把所有action通知给该player.
            notifyPlayerActions(actionPlayers[0], tileInfo, actionPlayers[0].getActions());
            return;
        }
        // 如果有多个player对该tile有action，需要按照action优先级先请players判断.
        for (Action action : sActionsOnThrownTiles) {
            Player[] actionAvailablePlayers = getActionAvailablePlayers(action);
            if (actionAvailablePlayers == null || actionAvailablePlayers.length <= 0) {
                // 如果没有player可以对这张打出的牌take the current action，跳过去;
                continue;
            }
            for (Player player : actionAvailablePlayers) {
                notifyPlayerActions(player, tileInfo, action);
            }
            return;
        }
    }

    // 对于杠过的牌，只能有抢杠操作.
    private static final Action[] sActionsOnGangedTile = {
                    Action.GangGrab
    };
    // 看看有没有player(s)要抢杠.
    private void checkActionOnGangedTile(final TileInfo tileInfo, final boolean isBlackGang) {
        //Constants.debug("checkActionOnGangedTile " + tileInfo);
        final Player tileOwner = getPlayer(tileInfo.finalLocation);
        // 先看看对该tile有几个player可以有action.
        Player[] actionPlayers = getActionPlayers(tileInfo);
        // 如果没有player可以对杠的tile有action.
        if (actionPlayers == null || actionPlayers.length <= 0) {
            // 先看看是不是已经有player做了action.
            Player actionDonePlayer = getActionDonePlayer(tileInfo);
            if (actionDonePlayer == null) {
                // 如果没有player做action, 则杠家摸牌.
                Player nextPlayer = tileOwner;
                notifyMainThread(nextPlayer, Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE_FROM_END);
                return;
            }
            Action doneAction = actionDonePlayer.getDoneAction();
            if (doneAction == Action.GangGrab) {
                Constants.UIMessage nextUIMessage = getNextUIMessageForHu();
                if (nextUIMessage == Constants.UIMessage.MSG_GAME_END) {
                    sendMessageToMainThread(nextUIMessage);
                    return;
                }
                // 如果有人抢杠胡了这张牌，则最后一个胡牌的下家摸牌.
                Player lastHuedPlayer = tileInfo.getLastHuedPlayer();
                Player nextPlayer = getNextPlayer(lastHuedPlayer);
                notifyMainThread(nextPlayer, Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE);
                return;
            }
            // 暂时没有其他action.
            return;
        }
        // 如果只有一个player有action(s)，
        if (actionPlayers.length == 1) {
            // 一次把所有action通知给该player.
            notifyPlayerGangGrab(actionPlayers[0], tileInfo, isBlackGang);
            return;
        }
        // 如果有多个player对该tile有action，需要按照action优先级先请players判断.
        for (Action action : sActionsOnGangedTile) {
            Player[] actionAvailablePlayers = getActionAvailablePlayers(action);
            if (actionAvailablePlayers == null || actionAvailablePlayers.length <= 0) {
                // 如果没有player可以对这张打出的牌take the current action，跳过去;
                continue;
            }
            for (Player player : actionAvailablePlayers) {
                notifyPlayerGangGrab(player, tileInfo, isBlackGang);
            }
            return;
        }
    }

    // 准备通知player可以take的actions
    private void notifyPlayerActions(final Player player, final TileInfo tileInfo,
                    final Action...actions) {
        if (player.getPosition() == Position.BOTTOM) {
            player.disableThrow();
            notifyMainThread(Constants.UIMessage.MSG_SHOW_ACTIONS_TO_PLAYER,
                        new Player.PlayerAction(player, tileInfo, actions));
        } else if (player instanceof DummyPlayer) {
            player.selectAction(tileInfo, actions);
        } else if (player instanceof WifiPlayer) {
            // TODO: NOT implemented.
        } else if (player instanceof BluetoothPlayer){
            // TODO: NOT implemented.
        }
    }

    // 准备通知player可以抢杠.
    private void notifyPlayerGangGrab(final Player player, final TileInfo tileInfo,
                    final boolean isBlackGang) {
        if (player.getPosition() == Position.BOTTOM) {
            player.disableThrow();
            notifyMainThread(Constants.UIMessage.MSG_SHOW_ACTIONS_TO_PLAYER_ON_GANGED_TILE,
                        new Player.PlayerGrabGang(player, tileInfo, isBlackGang));
        } else if (player instanceof DummyPlayer) {
            // TODO: nothing so far. dummyPlayer不抢杠.
        } else if (player instanceof WifiPlayer) {
            // TODO: NOT implemented.
        } else if (player instanceof BluetoothPlayer){
            // TODO: NOT implemented.
        }
    }

    public void notifyPlayerThrowTile(Player player) {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("notifyPlayerThrowTile must be called in main thread!");
        }
        player.readyToThrow();
    }

    public boolean notifyPlayerGangedTile(final Player.PlayerAction playerAction) {
        final Game game = GameResource.getGame(mGameIndex);
        // 如果不支持抢杠，就直接返回.
        if (!game.gangGrabSupported()) return false;
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                final boolean isBlackGang = playerAction.isBlackGanged();
                mWaitingQueue.clear();
                for (Player player : mPlayers) {
                    if (player == playerAction.player) continue;
                    mWaitingQueue.add(player);
                    player.checkActionOnGangedTile(playerAction.tileInfo, isBlackGang);
                }
                //updateWaitingToast(tileInfo);
            }
        });
        return true;
    }

    // 当前player刚吃/碰过一张别人的牌，需要先check能不能有action，比如听牌等.
    // 然后再打一张牌.
    public void notifyPlayerCheckActionOnGotTile(final Player player, final TileInfo tileInfo) {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("notifyPlayerCheckActionOnGotTile must be called in main thread!");
        }
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                player.checkActionOnGotTile(tileInfo);
            }
        });
    }

    public Game getGame() {
        return GameResource.getGame(mGameIndex);
    }

    // 每一张扔出的tile，需要其他三家player检查是否需要.
    public void notifyThrownTileToPlayers(final TileInfo tileInfo) {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("notifyTileThrownToOtherPlayers must be called in main thread!");
        }
        playSound(null, null, tileInfo);

        runInGameThread(new Runnable() {
            @Override
            public void run() {
                mWaitingQueue.clear();
                for (Player player : mPlayers) {
                    player.initActionInfo();
                    if (player.getLocation() == tileInfo.fromWhere) {
                        continue;
                    }
                    mWaitingQueue.add(player);
                    player.checkActionOnThrownTile(tileInfo);
                }
                updateWaitingToast(tileInfo);
            }
        });
    }

    public void updatePlayerThrownTiles(final Location location) {
        Player player = getPlayer(location);
        notifyMainThread(player, Constants.UIMessage.MSG_REFRESH_PLAYER);
    }

    private void updateWaitingToast(TileInfo tileInfo) {
        notifyMainThread(Constants.UIMessage.MSG_SHOW_PROMPT, getWaitingPlayers(tileInfo));
    }

    private String getWaitingPlayers(final TileInfo tileInfo) {
        StringBuilder sb = new StringBuilder();
        for (Player player : mWaitingQueue) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(String.format(Constants.sFormatPlayerWaiting, player.name, tileInfo));
        }
        return sb.toString();
    }

    private boolean arePlayersLocationsSet() {
        for (Player player : mPlayers) {
            if (player.getLocation() == null) return false;
        }
        return true;
    }

    private void gameRestart(final Activity activity) {
        // init players.
        for (Player player : mPlayers) {
            player.init();
            //if (Constants.DEBUG) player.setTileOpen(true);
        }

        // init remote manager state
        for (int i = 0; i < mRemoteManagerStates.length; i++) {
            if (mPlayers[i] instanceof RemotePlayer) {
                mRemoteManagerStates[i].init(false);
            } else {
                mRemoteManagerStates[i].init(true);
            }
        }

        getGame().restart();
    }

    public void init(final Activity activity, final int gameIndex, final String hostIp,
                    final Player[] _4Players) {
        mGameIndex = gameIndex;
        mHostIp = hostIp;

        mBankerInfo.setLocation(null);

        for (int i = 0; i < mPlayers.length; i++) {
            mPlayers[i] = _4Players[i];
        }

        check4Players(activity, true);
    }

    private boolean are4PlayersOK() {
        for (int i = 0; i < mPlayers.length; i++) {
            if (mPlayers[i] == null) {
                return false;
            }
        }
        return true;
    }

    private void check4Players(final Activity activity, final boolean isNewLocation) {
        if (are4PlayersOK()) {
            initGame(activity, isNewLocation);
            if (!TextUtils.isEmpty(mHostIp)) {
                send2RemoteManager(mHostIp, ConnMessage.MSG_OK_TO_START, null);
            }
        } else if (TextUtils.isEmpty(mHostIp)) {
            Utils.showInfo(activity, activity.getString(R.string.label_error), "No host IP to get the 2 players?");
        } else {
            send2RemoteManager(mHostIp, ConnMessage.MSG_GET_2_PLAYERS, null);
        }
    }

    public void clear() {
        mBankerInfo.setLocation(null);
        clearLocations();
    }

    private void initGame(final Activity activity, final boolean newLocation) {
        if (newLocation) {
            if (isHost()) {
                clearLocations();
                randomlySetLocation(activity);
                // sendLocations();
            } else {
                requestLocations();
            }
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isHost()) {
                    setPositions(activity);
                    initBanker();
                }
            }

            private void initBanker() {
                // 刚开始，以东风为庄.
                mBankerInfo.setLocation(Location.East);
                Position.setBanker(mBankerInfo);
            }
        });
    }

    // This must be called in main thread.
    public synchronized void startGame(final Activity activity) {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("startGame must be called in main thread!");
        }
        doStartGame(activity);
    }

    private void doStartGame(final Activity activity) {
        stopGameThreads();

        gameRestart(activity);

        mGameThread = new GameThread();
        mGameThread.start();

        mSoundThread = new SoundThread();
        mSoundThread.start();

        mSoundThread.sendMessage(MSG_SOUND_THREAD_STARTED, activity);
    }

    // 获得除了当前player和已知的remotePlayer之外的另外两家player.
    public PlayerIndex[] get2PlayersForRemote(final String excludedPlayerIp) {
        ArrayList<PlayerIndex> new2Players = new ArrayList<PlayerIndex>(2);
        Player player;
        for (int i = 1; i < mPlayers.length; i++) {
            player = mPlayers[i];
            if (player instanceof DummyPlayer) {
                if (((DummyPlayer)player).ipv4 == null) {
                    new2Players.add(new PlayerIndex(player, i));
                }
            }
            if (player instanceof WifiPlayer) {
                WifiPlayer wifiPlayer = (WifiPlayer)player;
                if (wifiPlayer.ipv4.equals(excludedPlayerIp)) continue;
                new2Players.add(new PlayerIndex(player, i));
            }
        }
        return new2Players.toArray(new PlayerIndex[new2Players.size()]);
    }

    private void addPlayers(final Activity activity, final PlayerIndex[] playerIndexes) {
        synchronized (mPlayers) {
            int count = playerIndexes == null ? 0 : playerIndexes.length;
            for (int i = 0; i < count; i++) {
                mPlayers[playerIndexes[i].index] = playerIndexes[i].player;
            }
            check4Players(activity, !arePlayersLocationsSet());
        }
    }

    private synchronized void runInGameThread(final Runnable runnable) {
        if (mGameThread != null) {
            mGameThread.post(runnable);
        }
    }

    // 获得当前打法中最小剩余牌数. 有些打法有剩12张或者3杠荒庄等.
    public int getGameMinTileCount() {
        return getGame().getLeastRemainingTileNum();
    }

    public synchronized void whenGameThreadReady() {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("whenThreadReady must be called in main thread!");
        }
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                if (!isBankerHere()) return;
                mGameThread.sendEmptyMessage(MSG_WASH_TILES_FOR_NEW_GAME);
                localizeDummyPlayers();
            }
        });
    }

    private void localizeDummyPlayers() {
        synchronized(mPlayers) {
            boolean dummyPlayerIpChanged = false;
            for (Player player : mPlayers) {
                if (player instanceof DummyPlayer) {
                    DummyPlayer dummyPlayer = (DummyPlayer)player;
                    if (dummyPlayer.ipv4 != null) {
                        dummyPlayer.ipv4 = null;
                        dummyPlayerIpChanged = true;
                    }
                }
            }
            if (dummyPlayerIpChanged) {
                sendMessage2RemoteManager(RemoteMessage.ConnMessage.MSG_DUMMY_IP_CHANGED, null);
            }
        }
    }

    private void changeDummyPlayersIp(final String remoteIp) {
        synchronized(mPlayers) {
            for (Player player : mPlayers) {
                if (player instanceof DummyPlayer) {
                    DummyPlayer dummyPlayer = (DummyPlayer)player;
                    dummyPlayer.ipv4 = remoteIp;
                }
            }
        }
    }

    public synchronized void whenTilesReady() {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("whenTilesReady must be called in main thread!");
        }
        if (isBankerHere()) {
            whenTilesReadyInBanker();
        }
    }

    private void whenTilesReadyInBanker() {
        final Game game = getGame();
        final GameResource.ShowTile showTileMode = game.needShowTile();
        // 有些打法，如杠上花（亮最后一张）和北京麻将（亮第54张, 13*4+1+1=54）等，
        // 需要亮一张牌出来.
        if (showTileMode != null) {
            Tile shownTile = game.getShowTile();
            if (game instanceof GameResource.Beijing) {
                // 北京麻将需要确定huiEr牌
                final GameResource.Beijing beijing = (GameResource.Beijing)game;

                beijing.setShowTile(shownTile);
                runInGameThread(new Runnable() {
                    @Override
                    public void run() {
                        beijing.setSpecialTiles();
                    }
                });
            }
            notifyMainThread(Constants.UIMessage.MSG_SHOW_TILE, shownTile);
            sendMessage2RemoteManager(ConnMessage.MSG_WHEN_TILES_READY,
                            MessageUtils.messageWhenTilesReady(shownTile));
        }
        mGameThread.sendEmptyMessage(MSG_SEND_13_TILES);
    }

    private void whenTilesReadyInRemote(Tile shownTile) {
        final Game game = getGame();
        final GameResource.ShowTile showTileMode = game.needShowTile();
        // 有些打法，如杠上花（亮最后一张）和北京麻将（亮第54张, 13*4+1+1=54）等，
        // 需要亮一张牌出来.
        if (showTileMode != null) {
            if (game instanceof GameResource.Beijing) {
                // 北京麻将需要确定huiEr牌
                final GameResource.Beijing beijing = (GameResource.Beijing)game;
                beijing.setShowTile(shownTile);
                beijing.setSpecialTiles();
            }
            notifyMainThread(Constants.UIMessage.MSG_SHOW_TILE, shownTile);
        }
    }

    public void notifyNecessaryBeforeStartDone() {
        GameResource.getGame(mGameIndex).setNecessaryBeforeStartDone();
    }

    private void checkGameNecessary() {
        final Game game = GameResource.getGame(mGameIndex);
        // 比如血流成河/血战到底需要定缺，而且还没有定缺，需要通知players开始定缺门.
        if (isBankerHere() && !game.isNecessaryBeforeStartDone()) {
            if (game.isValidAction(Action.DetermineIgnored)) {
                notifyPlayersToDetermineIgnored();
            }
        }
    }

    private void notifyPlayersToDetermineIgnored() {
        //Constants.debug("notifyPlayerstoDetermineIgnored...");
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                for (Player player : mPlayers) {
                    player.determineIgnored(true);
                }
            }
        });
    }

    // 通知player摸牌，或者说发牌给player.
    // 如果是杠了之后，从尾部拿一张牌.
    public void notifyPlayerGetTile(final Player player, final TileInfo gangedTileInfo) {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("notifyPlayerGetTile must be called in main thread!");
        }
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                if (isBankerHere()) {
                    Constants.debug("notifyPlayerGetTile, player:" + player.name + ", gangedTile:"
                                + gangedTileInfo);
                    sendTileToPlayer(player, gangedTileInfo);
                }
            }
        });
        updateWaiting(player.getLocation());
    }

    private void updateWaiting(final Activity activity, final Location waitingLocation) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateWaiting(waitingLocation);
            }
        });
    }

    public void updateWaiting(final Location activeLocation) {
        Position.updateWaiting(activeLocation);
        if (isBankerHere()) {
            sendMessage2RemoteManager(ConnMessage.MSG_UPDATE_WAITING,
                            MessageUtils.messageWaitingLocation(activeLocation.ordinal()));
        }
    }

    public void checkIgnoredDetermined() {
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                for (Player player : mPlayers) {
                    if (!player.isIgnoredDetermined()) {
                        player.autoDetermineIgnored();
                    }
                }
            }
        });
    }

    private void randomlySetLocation(final Context context) {
        for (Player player : mPlayers) {
            // 测试：总是把mPlayers[0]设为东
            /*if (player == mPlayers[0]) {
                player.setLocation(Location.East);
            } else {
                player.setLocation(findLocationAvailable());
            }*/
            // 随机设置player location.
            // 注意location(东/南/西/北)和
            // position(bottom/right/top/left)的区别及联系.
            player.setLocation(findLocationAvailable());
        }
    }

    private Player getNextPlayer(Player player) {
        Location nextLocation;
        Player nextPlayer = player;
        do {
            nextLocation = Location.getNextLocation(nextPlayer.getLocation());
            nextPlayer = findPlayer(nextLocation);
            if (nextPlayer == player) {
                return null;
            }
        } while (!isActive(nextPlayer));
        return nextPlayer;
    }

    // 血战到底等game，player胡牌之后则退出战斗只观战，此时不再active.
    private boolean isActive(Player player) {
        if (GameResource.getGame(mGameIndex).getHuedType() == HuedType.HuOncePlayer) {
            return !player.isHued();
        }
        return true;
    }

    private Player findPlayer(Location location) {
        for (Player player : mPlayers) {
            if (player.getLocation() == location) return player;
        }
        throw new IllegalStateException("Why this happens!? location:" + location);
    }

    private Player findPlayer(PlayerInfo playerInfo) {
        return findPlayer(playerInfo.ip, playerInfo.playerName);
    }

    public Player findPlayer(final String ipv4, final String name) {
        String playerIp;
        for (Player player : mPlayers) {
            if (!TextUtils.equals(player.name, name)) continue;
            playerIp = MessageUtils.getPlayerIp(player);
            if (TextUtils.equals(playerIp, ipv4)) {
                return player;
            }
        }
        throw new IllegalStateException("Why player NOT found!?\nipv4:" + ipv4 + ", name:" + name);
    }

    private Location findLocationAvailable() {
        Location[] locations = Location.values();
        int index;
        do {
            index = Utils.getRandomInt(0, locations.length - 1);
        } while (isLocationOccupied(locations[index]));
        return locations[index];
    }

    private boolean isLocationOccupied(Location location) {
        for (Player player : mPlayers) {
            if (player.getLocation() == location) return true;
        }
        return false;
    }

    public synchronized boolean isPlaying() {
        return mGameThread != null && mGameThread.isAlive();
    }

    private synchronized void stopGameThreads() {
        if (mGameThread != null) {
            mGameThread.clear();
            mGameThread.quitSafely();
            mGameThread = null;
        }

        if (mSoundThread != null) {
            mSoundThread.clear();
            mSoundThread.sendEmptyMessage(MSG_SOUND_THREAD_QUIT);
            mSoundThread = null;
        }
    }

    public void openPlayerTiles() {
        for (Player player : mPlayers) {
            player.setTileOpen(true);
        }
    }

    public synchronized void endGame(final boolean isFinishingActivity) {
        if (mGameThread == null) {
            return;
        }

        for (Player player : mPlayers) {
            player.quitPlaying();
        }
        stopGameThreads();

        Constants.UIMessage uiMessage;
        Reason reason = null;
        if (isFinishingActivity) {
            uiMessage = Constants.UIMessage.MSG_GAME_OVER;
            reason = Reason.FinishActivity;
        } else {
            uiMessage = Constants.UIMessage.MSG_GAME_END;
        }
        sendMessageToMainThread(uiMessage, reason == null ? -1 : reason.ordinal());
    }

    private void sendLocations() {
        sendMessage2RemoteManager(RemoteMessage.ConnMessage.MSG_LOCATIONS,
                        MessageUtils.messagePlayersLocationInfo(mPlayers));
    }

    private void requestLocations() {
        sendMessage2RemoteManager(RemoteMessage.ConnMessage.MSG_GET_LOCATIONS, null);
    }

    private void sendBankerInfo(final String destIp) {
        getGame().isMasterGame = isBankerHere();
        if (!getGame().isMasterGame) return;
        sendMessage(RemoteMessage.constructStringMessage(
                        RemoteMessage.ConnMessage.MSG_BANKER_INFO, destIp, null,
                        mBankerInfo.infoString()));
    }

    private void requestBankerInfo() {
        sendMessage2RemoteManager(RemoteMessage.ConnMessage.MSG_GET_BANKER_INFO, null);
    }

    private void updateLocations(final Activity activity, final LocationInfo[] locationInfoArray) {
        if (locationInfoArray != null && locationInfoArray.length > 0) {
            for (LocationInfo info : locationInfoArray) {
                Player player = findPlayer(info.ipv4, info.name);
                if (player != null) {
                    player.setLocation(info.location);
                }
            }
            if (arePlayersLocationsSet()) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setPositions(activity);
                        requestBankerInfo();
                    }
                });
            } else {
                requestLocations();
            }
        }
    }

    private void uiMessageFromRemote(final Constants.UIMessage uiMessage) {
        sendMessageToMainThread(uiMessage);
    }

    private void uiMessageFromRemotePlayer(final Constants.UIMessage uiMessage, final Player player) {
        notifyMainThread(player, uiMessage);
    }

    public void uiMessageFromRemote(final Object msgObj, final Constants.UIMessage uiMessage) {
        notifyMainThread(uiMessage, msgObj);
    }

    private void sendMessage2RemotePlayer(final RemoteMessage.ConnMessage connMessage,
                    final String uiMessageContent, final PlayerInfo ipInfo) {
        for (Player player : mPlayers) {
            if (player instanceof WifiPlayer) {
                WifiPlayer wifiPlayer = (WifiPlayer)player;
                sendMessage(RemoteMessage.constructStringMessage(
                                connMessage, wifiPlayer.ipv4, ipInfo, uiMessageContent));
            }
        }
    }

    public void sendMessage2RemoteManager(final RemoteMessage.ConnMessage connMessage,
                    final String uiMessageContent) {
        for (Player player : mPlayers) {
            if (player instanceof WifiPlayer) {
                WifiPlayer wifiPlayer = (WifiPlayer)player;
                sendMessage(RemoteMessage.constructStringMessage(
                                connMessage, wifiPlayer.ipv4, null, uiMessageContent));
            }
        }
    }

    private void send2RemoteManager(final String remoteIp,
                    final RemoteMessage.ConnMessage connMessage, final String uiMessageContent) {
        sendMessage(RemoteMessage.constructStringMessage(connMessage, remoteIp, null,
                        uiMessageContent));
    }

    private void wifiSendMessage(final MessageInfo messageInfo) {
        if (Constants.sMahjongUseTcp) {
            RemoteConnector.getInstance().sendMessageTcp(messageInfo);
        } else {
            RemoteConnector.getInstance().sendMessageUdp1(messageInfo);
        }
    }

    private void sendMessage(MessageInfo messageInfo) {
        switch (Constants.sNetwork) {
            case Wifi:
                wifiSendMessage(messageInfo);
                break;
            case Bluetooth:
                break;
            case Hotspot:
                break;
        }
    }

    // 看看这个tile还有多少张活牌.
    public int getActiveTileNumber(final Tile tile, final int playerHoldCount) {
        final Game game = GameResource.getGame(mGameIndex);
        int count = game.getRemainedTileCount(tile);
        return count - playerHoldCount;
    }

    // 去掉所有打开(打掉/吃/碰/杠/听/胡)的牌，更新剩余的牌的信息.
    public void updateRemainedTiles(final Context context) {
        runInGameThread(new Runnable() {
            @Override
            public void run() {
                doUpdateRemainedTiles(context);
            }
        });
    }

    private void doUpdateRemainedTiles(final Context context) {
        if (!isBankerHere()) {
            //sendMessage2RemoteManager(ConnMessage.MSG_UPDATE_REMAINED_TILES, null);
            return;
        }
        for (Player player : mPlayers) {
            if (!player.isPlaying()) return;
        }

        final Game game = getGame();

        Tile[] availableTiles = game.getAvailableTiles();
        int tileCount, thrownedCount;
        for (Tile tile : availableTiles) {
            tileCount = Tile.MAX_TILE_COUNT;
            thrownedCount = 0;
            for (Player player : mPlayers) {
                if (player.isChiedTile(tile)) {
                    tileCount--;
                }
                if (player.isPengedTile(tile)) {
                    tileCount -= 3;
                } else if (player.isTileOpenGanged(tile)) {
                    tileCount = 0;
                }
                thrownedCount = player.getThrownedTileCount(tile);
                if (thrownedCount > 0) {
                    tileCount -= thrownedCount;
                }
            }
            game.setRemainedTileInfo(tile, tileCount);
        }
    }

    // 判断当前设备能不能startGame，需要满足如下之一：
    // 1. 如果没有remote player(s)，可以;
    // 2. 如果有remote player(s), 且庄家在当前设备上，可以;
    // 除以上之外，不可以.
    public boolean canStartGame() {
        if (!are4PlayersOK()) return false;
        if (!isBankerHere()) return false;
        return true;
    }

    private boolean hasPlayerHued() {
        for (Player player : mPlayers) {
            if (player.isHued()) return true;
        }
        return false;
    }

    private final GameResource.DistributeTileListener mDistributeTileListener = new GameResource.DistributeTileListener() {
        @Override
        public void refreshGame() {
            sendMessageToMainThread(Constants.UIMessage.MSG_GAME_REFRESH);
            if (isBankerHere()) {
                sendMessage2RemoteManager(ConnMessage.MSG_LIVE_TILE_NUM,
                                MessageUtils.messageLiveTileNum(getGame().getRemainingTileNum()));
            }
        }

        @Override
        public void needShowTile() {
            if (isBankerHere()) {
                // 杠后如果game还要求显示最后一张牌，send msg to the main thread.
                notifyMainThread(Constants.UIMessage.MSG_SHOW_TILE, getGame().getShowTile());
            } else {
                sendMessage2RemoteManager(ConnMessage.MSG_REQUEST_SHOWN_TILE, null);
            }
        }
    };

    private void checkGameEnd(final Reason reason) {
        Constants.UIMessage uiMessage = null;
        if (hasPlayerHued()) {
            uiMessage = Constants.UIMessage.MSG_GAME_END;
        } else { // 无人胡牌，则荒庄.
            uiMessage = Constants.UIMessage.MSG_GAME_END_NULL;
        }
        sendMessageToMainThread(uiMessage, reason == null ? -1 : reason.ordinal());
    }

    public boolean hasRemotePlayers() {
        synchronized (mPlayers) {
            for (Player player : mPlayers) {
                if (player instanceof RemotePlayer) return true;
            }
            return false;
        }
    }

    private void sendTileToPlayer(Player player, TileInfo gangedTileInfo) {
        final boolean fromEnd = gangedTileInfo != null;
        final Tile newTile = getGame().distributeTile(fromEnd, mDistributeTileListener);
        if (newTile == null) {
            checkGameEnd(Reason.TileNull);
            return;
        }
        sendMessage2RemoteManager(ConnMessage.MSG_LIVE_TILE_NUM,
                        MessageUtils.messageLiveTileNum(getGame().getRemainingTileNum()));
        player.addNewTile(newTile, gangedTileInfo, true);
    }

    public synchronized void updateBankerPosition() {
        final Game game = getGame();
        Player winnerPlayer = mFirstWinnerInLastGame;
        switch (game.getBankerSelect()) {
            case Winner:
                if (winnerPlayer == null) {// 如果上一局无人胡牌，继续保持当前庄.
                    mBankerInfo.keepLocation();
                } else {
                    Location winnerLocation = winnerPlayer.getLocation();
                    if (mBankerInfo.sameLocation(winnerLocation)) {
                        mBankerInfo.keepLocation(); // 如果是庄家胡牌，则连庄.
                    } else {
                        mBankerInfo.setLocation(winnerLocation); // 赢家上庄.
                    }
                }
                break;
            case Next:
                if (winnerPlayer == null) { // 无人胡牌，下家上庄.
                    Location nextLocation = Location.getNextLocation(mBankerInfo.getLocation());
                    mBankerInfo.setLocation(nextLocation);
                } else if (mBankerInfo.sameLocation(winnerPlayer.getLocation())) {
                    mBankerInfo.keepLocation();// 如果是庄家胡牌，则连庄.
                } else { // 下家上庄.
                    Location nextLocation = Location.getNextLocation(mBankerInfo.getLocation());
                    mBankerInfo.setLocation(nextLocation);
                }
                break;
        }
        Position.setBanker(mBankerInfo);
    }

    private Player getBankerPlayer() {
        if (mBankerInfo.getLocation() == null) {
            return findPlayer(Location.East);
        }
        for (Player player : mPlayers) {
            if (mBankerInfo.sameLocation(player.getLocation())) {
                return player;
            }
        }
        throw new RuntimeException("How comes here - getBankerPlayer?!");
    }

    private void send13TilesToPlayers() {
        mFirstWinnerInLastGame = null;
        final Player bankerPlayer = getBankerPlayer();

        Player player = null;
        // 开始，每人先发13张牌.
        for (int tileIndex = 0; tileIndex < Player.TILE_NUM_NORMAL; tileIndex++) {
            // 发一轮牌, 从庄家开始.
            for (int playerIndex = 0; playerIndex < mPlayers.length; playerIndex++) {
                if (player == null) {
                    player = bankerPlayer;
                } else {
                    player = getPlayer(Location.getNextLocation(player.getLocation()));
                }
                sendTileToPlayer(player, null);
            }
        }
        checkStartPlaying();
    }

    private void checkStartPlaying() {
        if (checkRemote13TilesOK()) {
            startPlaying();
        }
    }

    private void startPlaying() {
        // 发完第13张牌, 玩家开始分析手中牌.
        for (Player player : mPlayers) {
            if (player.isPlaying()) return;
            player.startPlaying(true);
        }
        // 给庄家发一张牌
        notifyMainThread(getBankerPlayer(), Constants.UIMessage.MSG_NOTIFY_PLAYER_GET_TILE);
        circleIncrease();

        // 如果game还有必须要在开始前完成的事，也需要现在开始做.
        checkGameNecessary();
    }

    private void circleIncrease() {
        getGame().circleIncrease();
        if (isBankerHere()) {
            sendMessage2RemoteManager(RemoteMessage.ConnMessage.MSG_CIRCLE_INCREASE, null);
        }
    }

    private static final int MSG_OFFSET_GAME_THREAD = 100;
    private static final int MSG_WASH_TILES_FOR_NEW_GAME = MSG_OFFSET_GAME_THREAD + 1;
    private static final int MSG_SEND_13_TILES = MSG_OFFSET_GAME_THREAD +2;

    private class GameThread extends HandlerThreadExt {
        public GameThread() {
            super("game_thread-" + System.currentTimeMillis());
            // TODO Auto-generated constructor stub
        }

        @Override
        protected boolean doHandleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WASH_TILES_FOR_NEW_GAME:
                    getGame().washTiles();
                    sendMessageToMainThread(Constants.UIMessage.MSG_TILES_READY);
                    return true;
                case MSG_SEND_13_TILES:
                    send13TilesToPlayers();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        protected void whenLooperPrepared() {
            final int remainingTileNum = getGame().getRemainingTileNum();
            sendMessageToMainThread(Constants.UIMessage.MSG_GAME_START, remainingTileNum);
            if (isBankerHere()) {
                sendMessage2RemoteManager(ConnMessage.MSG_GAME_START, null);
            }
        }
    }

    private static final int MSG_OFFSET_SOUND_THREAD = 200;
    private static final int MSG_SOUND_THREAD_STARTED = MSG_OFFSET_SOUND_THREAD + 1;
    private static final int MSG_SOUND_THREAD_QUIT = MSG_OFFSET_SOUND_THREAD + 2;
    private static final int MSG_SOUND_PLAY = MSG_OFFSET_SOUND_THREAD + 3;

    private class SoundThread extends HandlerThreadExt {
        private SoundPool mSoundPool;

        private AssetManager mAssetManager;

        private AudioManager mAudioManager;

        private final int mNumberOfSoundStreams = 2;

        public SoundThread() {
            super("sound_thread-" + System.currentTimeMillis());
            // TODO Auto-generated constructor stub
        }

        @Override
        protected boolean doHandleMessage(Message msg) {
            if (msg.what <= MSG_OFFSET_SOUND_THREAD) {
                throw new RuntimeException("Not correctly set the sound message id!?");
            }
            switch (msg.what) {
                case MSG_SOUND_THREAD_STARTED:
                    prepareSoundPool((Activity)msg.obj);
                    return true;
                case MSG_SOUND_THREAD_QUIT:
                    quitSoundThread();
                    return true;
                case MSG_SOUND_PLAY:
                    playSound(msg.arg1);
                    return true;
                default:
                    return false;
            }
        }

        private void prepareSoundPool(Activity activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final AudioAttributes attributes = new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .build();
                mSoundPool = new SoundPool.Builder()
                                .setAudioAttributes(attributes)
                                .setMaxStreams(mNumberOfSoundStreams)
                                .build();
            } else {
                mSoundPool = new SoundPool(mNumberOfSoundStreams, AudioManager.STREAM_MUSIC, 0);
            }
            mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId,
                        int status) {
                    sendMessageWithArg1(MSG_SOUND_PLAY, sampleId);
                }
            });
            mAssetManager = activity.getAssets();
            mAudioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        }

        private void quitSoundThread() {
            // 要退出sound thread，此处应该关闭资源等，比如清空sound pool.
            if (mSoundPool != null) {
                mSoundPool.setOnLoadCompleteListener(null);
                mSoundPool.release();
                mSoundPool = null;
            }

            // 最后退出handlerThread.
            quitSafely();
        }

        public void playSound(final String soundFilepathInAssets) {
            //final int sound_id;
            try {
                /*sound_id = */mSoundPool.load(mAssetManager.openFd(soundFilepathInAssets), 1);
            } catch (IOException ioe) {
                notifyMainThread(Constants.UIMessage.MSG_SHOW_PROMPT, soundFilepathInAssets + "\n" + Utils.getThrowableStackTrace(ioe));
            }
        }

        private void playSound(int soundId) {
            float streamVolumeCurrent = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            float streamVolumeMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            float volume = streamVolumeCurrent / streamVolumeMax;
            mSoundPool.play(soundId, volume, volume, 0, 0, 1.0f);
        }
    }

    public synchronized void handleReceivedMessage(final Activity activity,
                    final MessageInfo msgInfo) {
        if (msgInfo.playerInfo == null) {
            handleRemoteMessage(activity, msgInfo);
        } else {
            Player player = findPlayer(msgInfo.playerInfo);
            player.handleRemoteMessage(msgInfo);
        }
    }

    private void handleRemoteMessage(final Activity activity, final MessageInfo msgInfo) {
        MessageUtils.UIMessageInfo uiMessageInfo;

        final RemoteMessage remoteMessage = RemoteMessage.parse(msgInfo);
        Constants.debug("Received for MahJongActivity, " + remoteMessage);
        switch (remoteMessage.connMessage) {
            case MSG_CHECK_REMOTE_STATE:
                send2RemoteManager(remoteMessage.remoteIp, ConnMessage.MSG_REMOTE_STATE,
                                collectManagerState());
                break;
            case MSG_REMOTE_STATE:
                updateRemoteManagerState(remoteMessage.remoteIp, (String)remoteMessage.content);
                break;
            case MSG_OK_TO_START:
                if (checkRemoteManagerState()) {
                    sendMessageToMainThread(Constants.UIMessage.MSG_VIEW_INIT);
                }
                break;
            case MSG_CHECK_13_TILES_OK:
                send2RemoteManager(remoteMessage.remoteIp, ConnMessage.MSG_PLAYER_13_TILES_STATE,
                                allPlayers13TilesOK() ? Boolean.TRUE.toString()
                                                : Boolean.FALSE.toString());
                break;
            case MSG_PLAYER_13_TILES_STATE:
                update13TilesOK(remoteMessage.remoteIp, (String) remoteMessage.content);
                break;
            case MSG_GET_2_PLAYERS: // 收到remotePlayer发来的需要其他3家player的请求
                sendPlayersInfo(remoteMessage.remoteIp);
                break;
            case MSG_3_PLAYERS: // 收到host发来的其他3家player的信息.
                parsePlayersInfo(activity, remoteMessage.content);
                send2RemoteManager(remoteMessage.remoteIp, ConnMessage.MSG_REMOTE_STATE,
                                collectManagerState());
                break;
            case MSG_GET_LOCATIONS:// 收到需要locations的信息，则发出locations信息.
                sendLocations();
                break;
            case MSG_LOCATIONS: // 收到locations信息.
                updateLocations(activity, MessageUtils.parseLocations(remoteMessage.content));
                send2RemoteManager(remoteMessage.remoteIp, ConnMessage.MSG_REMOTE_STATE,
                                collectManagerState());
                break;
            case MSG_GET_BANKER_INFO:
                sendBankerInfo(remoteMessage.remoteIp);
                break;
            case MSG_BANKER_INFO:
                initBankerFromRemote(activity, (String)remoteMessage.content);
                send2RemoteManager(remoteMessage.remoteIp, ConnMessage.MSG_REMOTE_STATE,
                                collectManagerState());
                break;
            case MSG_REQUEST_LIVE_TILE_NUM:
                sendMessage2RemoteManager(ConnMessage.MSG_LIVE_TILE_NUM,
                                MessageUtils.messageLiveTileNum(getGame().getRemainingTileNum()));
                break;
            case MSG_LIVE_TILE_NUM:
                getGame().setLiveTileNum(MessageUtils.parseLiveTileNum(remoteMessage.content));
                sendMessageToMainThread(Constants.UIMessage.MSG_GAME_REFRESH);
                break;
            case MSG_DUMMY_IP_CHANGED:
                changeDummyPlayersIp(remoteMessage.remoteIp);
                break;
            case MSG_PLAYER_13_TILES:
                updatePlayers13Tiles((String)remoteMessage.content);
                send2RemoteManager(remoteMessage.remoteIp, ConnMessage.MSG_PLAYER_13_TILES_STATE,
                                allPlayers13TilesOK() ? Boolean.TRUE.toString()
                                                : Boolean.FALSE.toString());
                break;
            case MSG_GAME_START: // 远端发来game_start.
                doStartGame(activity);
                break;
            case MSG_CIRCLE_INCREASE:
                circleIncrease();
                break;
            case MSG_UPDATE_WAITING:
                updateWaiting(activity, MessageUtils.parseWaitingLocation(remoteMessage.content));
                break;
            case MSG_WHEN_TILES_READY:
                MessageUtils.WhenTilesReadyInfo whenTilesReadyInfo = MessageUtils
                                .parseWhenTilesReadyInfo(remoteMessage.content);
                whenTilesReadyInRemote(whenTilesReadyInfo.shownTile);
                break;
            case MSG_REQUEST_SHOWN_TILE:
                if (isBankerHere()) {
                    sendMessage2RemoteManager(ConnMessage.MSG_SHOWN_TILE,
                                    MessageUtils.messageWhenTilesReady(getGame().getShowTile()));
                }
                break;
            case MSG_SHOWN_TILE:
                MessageUtils.WhenTilesReadyInfo shownTileInfo = MessageUtils
                                .parseWhenTilesReadyInfo(remoteMessage.content);
                getGame().setShownTile(shownTileInfo.shownTile);
                notifyMainThread(UIMessage.MSG_SHOW_TILE, shownTileInfo.shownTile);
                break;
            case MSG_GAME_OVER: // 远端发来game_over.
            //case MSG_DISCONNECT:
                activity.finish();
                break;
            case MSG_UIMESSAGE_NULL:
                uiMessageFromRemote(MessageUtils.parseUIMessage(remoteMessage.content));
                break;
            case MSG_UIMESSAGE_PLAYER:
                uiMessageInfo = MessageUtils.parseUIMessagePlayer(remoteMessage.content);
                uiMessageFromRemotePlayer(uiMessageInfo.uiMessage, (Player) uiMessageInfo.obj);
                break;
            case MSG_UIMESSAGE_OBJ:
                try {uiMessageInfo = MessageUtils.parseUIMessageObj(remoteMessage.content);
                uiMessageFromRemote(uiMessageInfo.obj, uiMessageInfo.uiMessage);}
                catch (Exception e) {
                    throw new RuntimeException("Exception:" + e + "\nException msg:" + e.getMessage() + "\nRemoteMessage:" + remoteMessage.content);
                }
                break;
            default:
                throw new RuntimeException("Why comes to Manager?! " + remoteMessage.connMessage);
        }
    }

    private void sendPlayersInfo(final String remoteIp) {
        PlayerIndex[] playerIndexes = MahjongManager.getInstance().get2PlayersForRemote(remoteIp);
        RemoteMessage remoteMessage = new RemoteMessage(ConnMessage.MSG_3_PLAYERS, remoteIp,
                        DataType.String, MessageUtils.messagePlayersInfo(playerIndexes));
        sendMessage(remoteMessage.constructMessage());
    }

    private void parsePlayersInfo(final Activity activity, final Object messageContent) {
        PlayerIndex[] playerIndexes = MessageUtils.parsePlayers(messageContent);
        if (playerIndexes != null && playerIndexes.length > 0) {
            addPlayers(activity, playerIndexes);
        }
    }

    private void initBankerFromRemote(final Activity activity, final String bankerInfoString) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBankerInfo.init(BankerInfo.parse(bankerInfoString));
                Position.setBanker(mBankerInfo);
                if (checkRemoteManagerState()) {
                    sendMessageToMainThread(Constants.UIMessage.MSG_VIEW_INIT);
                }
                getGame().isMasterGame = isBankerHere();
                if (!getGame().isMasterGame) {
                    sendMessage2RemoteManager(ConnMessage.MSG_REQUEST_LIVE_TILE_NUM, null);
                }
            }
        });
    }

    private boolean checkRemoteManagerState() {
        if (isHost() || isBankerHere()) {
            boolean remoteOK = true;
            for (int i = 0; i < mPlayers.length; i++) {
                if (mPlayers[i] instanceof WifiPlayer) {
                    WifiPlayer wifiPlayer = (WifiPlayer) mPlayers[i];
                    if (mRemoteManagerStates[i].allOkToStart()) continue;
                    remoteOK = false;
                    if (!mRemoteManagerStates[i]._4PlayersOK) {
                        sendPlayersInfo(wifiPlayer.ipv4);
                    }
                    if (!mRemoteManagerStates[i]._4PlayersLocationOK) {
                        send2RemoteManager(wifiPlayer.ipv4, RemoteMessage.ConnMessage.MSG_LOCATIONS,
                                        MessageUtils.messagePlayersLocationInfo(mPlayers));
                    }
                    if (!mRemoteManagerStates[i].bankerInfoOK) {
                        sendBankerInfo(wifiPlayer.ipv4);
                    }
                    send2RemoteManager(wifiPlayer.ipv4, ConnMessage.MSG_CHECK_REMOTE_STATE, null);
                }
            }
            return remoteOK;
        }
        return true;
    }

    private boolean checkRemote13TilesOK() {
        if (isHost() || isBankerHere()) {
            boolean _13TilesOK = true;
            for (int i = 0; i < mPlayers.length; i++) {
                if (mPlayers[i] instanceof WifiPlayer) {
                    WifiPlayer wifiPlayer = (WifiPlayer) mPlayers[i];
                    if (!mRemoteManagerStates[i]._13TilesOK) {
                        _13TilesOK = false;
                        send2RemoteManager(wifiPlayer.ipv4, ConnMessage.MSG_CHECK_13_TILES_OK, null);
                    }
                }
            }
            return _13TilesOK;
        }
        return true;
    }

    // Format: playerOK,playerLocationOK,bankerInfoOK,
    private static final String FORMAT_REMOTE_MANAGER_STATE = "%d%s%d%s%d%s";
    private static final String SEPARATOR_MANAGER_STATE = ",";

    private String collectManagerState() {
        boolean _4PlayersOK = are4PlayersOK();
        boolean _4PlayersLocationOK = _4PlayersOK && arePlayersLocationsSet();
        boolean bankerInfoOK = _4PlayersLocationOK && mBankerInfo.getLocation() != null;
        return String.format(FORMAT_REMOTE_MANAGER_STATE,
                        _4PlayersOK ? 1 : 0, SEPARATOR_MANAGER_STATE,
                        _4PlayersLocationOK ? 1 : 0, SEPARATOR_MANAGER_STATE,
                        bankerInfoOK ? 1 : 0, SEPARATOR_MANAGER_STATE);
    }

    private int getRemoteManagerIndex(final String ip) {
        for (int i = 0; i < mPlayers.length; i++) {
            if (mPlayers[i] instanceof WifiPlayer) {
                WifiPlayer wifiPlayer = (WifiPlayer)(mPlayers[i]);
                if (wifiPlayer.ipv4.equals(ip)) return i;
            }
        }
        return -1;
    }

    private void updateRemoteManagerState(String remoteIp, String remoteManagerState) {
        int index = getRemoteManagerIndex(remoteIp);
        if (index < 0) return;
        String[] array = remoteManagerState.split(SEPARATOR_MANAGER_STATE);
        mRemoteManagerStates[index]._4PlayersOK = Integer.parseInt(array[0].trim()) == 1;
        mRemoteManagerStates[index]._4PlayersLocationOK = Integer.parseInt(array[1].trim()) == 1;
        mRemoteManagerStates[index].bankerInfoOK = Integer.parseInt(array[2].trim()) == 1;
    }

    private boolean allPlayers13TilesOK() {
        for (Player player : mPlayers) {
            if (!player.has13Tiles()) return false;
        }
        return true;
    }

    private void update13TilesOK(String remoteIp, String remoteOKString) {
        int index = getRemoteManagerIndex(remoteIp);
        if (index < 0) return;
        boolean _13TilesOK = Boolean.parseBoolean(remoteOKString.trim());
        mRemoteManagerStates[index]._13TilesOK = _13TilesOK;
        if (_13TilesOK) {
            checkStartPlaying();
        } else {
            send2RemoteManager(remoteIp, ConnMessage.MSG_PLAYER_13_TILES, players13TilesString());
        }
    }

    private static final String SEPARATOR_PLAYER_TILES = "X";
    private String players13TilesString() {
        StringBuilder sb = new StringBuilder();
        for (Player player : mPlayers) {
            if (sb.length() > 0) {
                sb.append(SEPARATOR_PLAYER_TILES);
            }
            sb.append(player.tilesString());
        }
        return sb.toString();
    }

    private void updatePlayers13Tiles(String players13Tiles) {
        String[] playerTilesArray = players13Tiles.split(SEPARATOR_PLAYER_TILES);
        Player.PlayerTiles playerTiles;
        Player player;
        for (int i = 0; i < mPlayers.length; i++) {
            playerTiles = Player.parsePlayerTiles(playerTilesArray[i].trim());
            player = findPlayer(playerTiles.ip, playerTiles.name);
            player.setTiles(playerTiles.tiles);
        }
    }
}
