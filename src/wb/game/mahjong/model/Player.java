package wb.game.mahjong.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import wb.conn.MessageInfo;
import wb.conn.MessageUtils;
import wb.conn.RemoteMessage;
import wb.conn.RemoteMessage.ConnMessage;
import wb.game.mahjong.MahjongManager;
import wb.game.mahjong.MahjongManager.Location;
import wb.game.mahjong.MahjongManager.Position;
import wb.game.mahjong.R;
import wb.game.mahjong.constants.Constants;
import wb.game.mahjong.constants.HuConstants;
import wb.game.mahjong.constants.HuConstants.HuPattern;
import wb.game.mahjong.constants.TileResources;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.GameResource.Game;
import wb.game.mahjong.model.GameResource.GangType;
import wb.game.mahjong.model.GameResource.ShowTile;
import wb.game.mahjong.model.Tile.GangFlower;
import wb.game.mahjong.model.Tile.HuTile;
import wb.game.mahjong.model.Tile.TileCount;
import wb.game.mahjong.model.Tile.TileInfo;
import wb.game.mahjong.model.Tile.TileState;
import wb.game.mahjong.view.FixedGridLayout;
import wb.game.utils.Utils;

// The game always needs 4 players.
// 4 players might be in 3 kinds:
// DummyPlayer: manipulated by the app.
// LocalPlayer: always the current user locating in bottom.
// RemotePlayer: WifiPlayer or BluetoothPlayer.
// Every player has a separate thread to handle messages/actions.
public class Player {
    // 一般每人13张牌.
    public static final int TILE_NUM_NORMAL = 13;
    // 最多张数：4杠（4*4） + 1张
    private static final int MAX_TILE_NUM = TILE_NUM_NORMAL / 3 * 4 + 1;

    // 记录当前手中握着的活牌, 已经吃/杠/碰等成牌的牌另外记录.
    protected final ArrayList<Tile> mTiles = new ArrayList<Tile>(MAX_TILE_NUM);

    // 记录所有打出去的牌.
    private final ArrayList<Tile> mThrownTiles = new ArrayList<Tile>();

    public static class CanChi {
        public final Tile missingTile;
        public final Tile[] twoTiles;
        public final int position;

        public CanChi(final Tile tile, final Tile[] twoTiles, final int position) {
            missingTile = tile;
            this.twoTiles = twoTiles;
            this.position = position;
        }
    }

    public static class PlayerCanAction {
        public final Player player;
        public final Action action;

        public PlayerCanAction(Player player, Action action) {
            this.player = player;
            this.action = action;
        }
    }

    public static class PlayerCanChi extends PlayerCanAction {
        public final TileInfo tileInfo;
        public final CanChi[] canChies;

        public PlayerCanChi(Player player, TileInfo tileInfo, CanChi[] canChies) {
            super(player, Action.Chi);
            this.tileInfo = tileInfo;
            this.canChies = canChies;
        }
    }

    // 记录可以吃的牌.
    protected final ArrayList<CanChi> mCanChiTiles = new ArrayList<CanChi>();
    // 记录可以碰的牌，一人最多4碰.
    private final ArrayList<Tile> mCanPengTiles = new ArrayList<Tile>(TILE_NUM_NORMAL / 3);

    public static class CanGangTile {
        public final Tile tile;
        public final GangType canGangType;

        public CanGangTile(Tile tile, GangType canGangType) {
            this.tile = tile;
            this.canGangType = canGangType;
        }
    }

    public static class PlayerCanGang extends PlayerCanAction {
        public final TileInfo tileInfo;
        public final CanGangTile[] canGangTiles;

        public PlayerCanGang(Player player, TileInfo tileInfo, CanGangTile[] canGangTiles) {
            super(player, Action.Gang);
            this.tileInfo = tileInfo;
            this.canGangTiles = canGangTiles;
        }
    }
    // 记录可以杠的牌，一人最多4杠; 包括暗杠, 碰杠, 明杠
    protected final ArrayList<CanGangTile> mCanGangTiles = new ArrayList<CanGangTile>(TILE_NUM_NORMAL / 3);

    // 记录可以胡的牌，最多能胡13张牌.
    private final ArrayList<HuTile> mCanHuTiles = new ArrayList<HuTile>(TILE_NUM_NORMAL);

    protected static class TingTileInfo {
        public final Tile tile; // 可以打出去听牌的牌.
        public final HuTile[] huTiles; // 打出去后可以胡的牌.

        public TingTileInfo(Tile tile, HuTile[] huTiles) {
            this.tile = tile;
            this.huTiles = huTiles;
        }
    }
    // 记录听牌时可以打掉的牌，最多13张任何一张都可以打掉。
    protected final ArrayList<TingTileInfo> mCanTingTiles = new ArrayList<TingTileInfo>(TILE_NUM_NORMAL);

    // true表示可以选择听牌,false表示不能听牌或者player选择不听牌.
    protected boolean mActionTingAvailable;
    // 表示player报听牌.
    protected boolean mActionTingReported;

    // 碰过的牌.
    protected static class Penged {
        public final Tile[] tiles;
        public final TileInfo externalTile;

        public Penged(Tile[] tiles, TileInfo externalTile) {
            this.tiles = tiles;
            this.externalTile = externalTile;
        }
    }
    // 记录碰过的牌，一人最多4碰.
    private final ArrayList<Penged> mPengs = new ArrayList<Penged>(TILE_NUM_NORMAL / 3);

    protected static class Ganged {
        public final GangType type;

        // 暗杠或明杠.
        public final Tile[] tiles;// = new Tile[3](明杠）或者Tile[4](暗杠）;
        public final TileInfo externalTile; // 暗杠这个为null.
        // 碰杠.
        public final Penged penged;
        public final Tile lastTile;

        // 暗杠
        public Ganged(Tile[] tiles) {
            this.type = GangType.GangBlack;

            this.tiles = tiles;

            externalTile = null;
            penged = null;
            lastTile = null;
        }

        // 明杠
        public Ganged(Tile[] tiles, TileInfo tileInfo) {
            type = GangType.Gang3_1;

            this.tiles = tiles;
            externalTile = tileInfo;

            penged = null;
            lastTile = null;
        }

        // 碰杠
        public Ganged(final Penged penged, final Tile tile) {
            type = GangType.GangPenged;

            this.penged = penged;
            this.lastTile = tile;

            tiles = null;
            externalTile = null;
        }

        public Tile getTile() {
            switch (type) {
                case GangBlack:
                    return tiles[0];
                case GangPenged:
                    return lastTile;
                case Gang3_1:
                    return externalTile.tile;
            }
            return null;
        }
    }

    // 记录杠过的牌，一人最多4杠.
    protected final ArrayList<Ganged> mGangs = new ArrayList<Ganged>(TILE_NUM_NORMAL / 3);

    // 记录已经胡过的牌
    private final ArrayList<TileInfo> mHuedTiles = new ArrayList<TileInfo>();

    // 吃过的牌.
    protected static class Chied {
        public final Tile[] tiles;
        public final TileInfo externalTile;
        public final int position;

        public Chied(Tile[] tiles, TileInfo externalTile, int position) {
            this.tiles = tiles;
            this.externalTile = externalTile;
            this.position = position;
        }
    }
    // 记录吃进的牌
    private final ArrayList<Chied> mChiedTiles = new ArrayList<Chied>();

    public String name;
    public Gender gender;

    public final String iconFilename; // Internal file.

    private Location mLocation;

    protected Position mPosition;

    private LinearLayout mTileListView;
    protected ImageView mNewTileView;
    private LinearLayout mChiView;
    private LinearLayout mPengView;
    private LinearLayout mGangView;
    private LinearLayout mHuView;
    private ViewGroup mThrownTilesView;

    private int mChiTileLayoutId;
    private int mGangTileLayoutId;
    private int mGangBlackTileLayoutId;
    private int mPengTileLayoutId;

    protected int mTileOpenLayoutId;
    private int mTileCloseLayoutId;

    private int mHuedTileLayoutId;

    private int mTileIndicatorViewId;

    protected TileType mIgnoredType; // 可以为null因为除了血流成河/血战到底，别的打法无缺一门...

    protected Tile mNewTile;

    // 显示牌正面还是背面/侧面.
    private boolean mOpenTile;

    protected volatile boolean mThrowAvailable; // 是否可以打牌

    protected volatile boolean mPlaying;

    private HandlerThreadExt mPlayerThread;

    public static class PlayerTiles {
        public final String ip;
        public final String name;
        public final Tile[] tiles;

        public PlayerTiles(String ip, String name, Tile[] tiles) {
            this.ip = ip;
            this.name = name;
            this.tiles = tiles;
        }
    }

    public static class PlayerActionInfo {
        public final String playerIp;
        public final String playerName;
        public final Action[] actions;
        public final TileInfo tileInfo;

        public PlayerActionInfo(String ip, String name, TileInfo tileInfo, Action...actions) {
            this.playerIp = ip;
            this.playerName = name;
            this.tileInfo = tileInfo;
            this.actions = actions;
        }
    }

    public static class PlayerAction {
        public final Player player;
        public final Action[] actions;
        public final TileInfo tileInfo;

        public PlayerAction(Player player, TileInfo tileInfo, Action...actions) {
            this.player = player;
            this.actions = actions;
            this.tileInfo = tileInfo;
        }

        private static final String FORMAT_TOSTRING = "%s %s %s";

        private static final String FORMAT_TOSTRING_NO_ACTIONS = "%s has no actions on %s.";

        private static final String FORMAT_TOSTRING_ACTIONS = "%s actions on %s:\n%s";

        @Override
        public String toString() {
            if (actions == null || actions.length < 1) {
                return String.format(FORMAT_TOSTRING_NO_ACTIONS, player.name, tileInfo.toString());
            }
            if (actions.length == 1) {
                return String.format(FORMAT_TOSTRING, player.name, actions[0].toString(),
                            tileInfo.toString());
            }
            StringBuilder sb = new StringBuilder();
            for (Action action : actions) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(' ').append(' ').append(action.toString());
            }
            return String.format(FORMAT_TOSTRING_ACTIONS, player.name, tileInfo.toString(),
                            sb.toString());
        }

        public String getActionString(final Context context) {
            if (actions == null || actions.length < 1) {
                return String.format(FORMAT_TOSTRING_NO_ACTIONS, player.name, tileInfo.toString());
            }
            if (actions.length == 1) {
                return Action.getActionString(context, player, actions[0], tileInfo);
            }
            StringBuilder sb = new StringBuilder();
            for (Action action : actions) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(' ').append(' ').append(context.getString(action.actionResId));
            }
            return String.format(FORMAT_TOSTRING_ACTIONS, player.name, tileInfo.toString(),
                            sb.toString());
        }

        public boolean isBlackGanged() {
            if (actions[0] != Action.Gang) return false;
            return player.isBlackGanged(tileInfo.tile);
        }

        // Format: ip=name=actionOrdinal+...=tileInfo
        private static final String SEPARATOR = "_";
        private static final String SEPARATOR_ACTION = "+";
        public String infoString() {
            StringBuilder sb = new StringBuilder();
            sb.append(MessageUtils.getPlayerIp(player)).append(SEPARATOR).append(player.name);
            sb.append(SEPARATOR);
            if (actions != null) {
                for (int i = 0; i < actions.length; i++) {
                    sb.append(actions[i].ordinal());
                    if (i < actions.length - 1) {
                        sb.append(SEPARATOR_ACTION);
                    }
                }
            }
            sb.append(SEPARATOR).append(tileInfo.tileInfoString());
            return sb.toString();
        }

        public static PlayerActionInfo parseInfoString(final String infoString) {
            String[] array = infoString.split(SEPARATOR);
            String playerIp = array[0].trim();
            String playerName = array[1].trim();

            Action[] actions = parseActions(array[2].trim());
            TileInfo tileInfo = TileInfo.parseTileInfoString(array[3].trim());
            return new PlayerActionInfo(playerIp, playerName, tileInfo, actions);
        }

        private static Action[] parseActions(final String actionsString) {
            if (TextUtils.isEmpty(actionsString)) return null;
            String[] array = actionsString.split(SEPARATOR_ACTION);
            ArrayList<Action> actionList = new ArrayList<Action>(array.length);
            for (String element : array) {
                if (TextUtils.isEmpty(element)) continue;
                actionList.add(Action.getAction(Integer.parseInt(element.trim())));
            }
            if (actionList.size() <= 0) return null;
            return actionList.toArray(new Action[actionList.size()]);
        }
    }

    public static class PlayerGrabGang {
        public final Player player;
        public final boolean isBlackGang;
        public final TileInfo tileInfo;

        public PlayerGrabGang(Player player, TileInfo tileInfo, boolean isBlackGang) {
            this.player = player;
            this.isBlackGang = isBlackGang;
            this.tileInfo = tileInfo;
        }

        private static final String FORMAT_TOSTRING = "%s %s %s.";

        private static final String FORMAT_TOSTRING_BLACK_GANG = "%s %s.";

        @Override
        public String toString() {
            if (isBlackGang) {
                return String.format(FORMAT_TOSTRING_BLACK_GANG, player.name,
                                Action.GangGrab.toString());
            }
            return String.format(FORMAT_TOSTRING, player.name, Action.GangGrab.toString(),
                            tileInfo.toString());
        }
    }

    public interface PlayerCallback {
        void onViewChanged();
        void send2RemotePlayer(RemoteMessage.ConnMessage connMessage, String uiMessageContent);
        void promptDetermineIgnored();
        void promptChi(PlayerCanChi  playerCanChi);
        void onIgnoredTypeDetermined();

        void notifyActionsAvailableOnNewTile(TileInfo tileInfo);
        void notifyActionsAvailableOnThrownTile(TileInfo tileInfo);
        void notifyActionsAvailableOnGotTile(TileInfo tileInfo);
        void notifyActionsAvailableOnGangedTile(TileInfo tileInfo, boolean isBlackGang);
        void notifyThrowAvaiable();
        void onTileThrown(TileInfo tileInfo);

        void actionDoneOnNewTile(Action action, TileInfo tileInfo);
        void actionDoneOnGotTile(Action action, TileInfo tileInfo);
        void actionDoneOnThrownTile(Action action, TileInfo tileInfo);
        void actionDoneOnGangedTile(Action action, TileInfo tileInfo);
        void gangFlowerDone(TileInfo tileInfo);
        void actionsIgnoredOnNewTile(TileInfo tileInfo, Action...actions);
        void actionsIgnoredOnGotTile(TileInfo tileInfo, Action...actions);
        void actionsIgnoredOnThrownTile(TileInfo tileInfo, Action...actions);
        void actionsIgnoredOnGangedTile(TileInfo tileInfo, boolean isBlackGang, Action...actions);
        void showCanHuTiles(final HuTile[] canHuTiles);
        void promptGang(final TileInfo tileInfo, final CanGangTile...canGangTiles);
        void maxGangCountReached();
        void whenNoTile();

        void showCallstack(Throwable t, String info);
    }

    protected PlayerCallback mPlayerCallback;

    private final ActionInfo actionInfo = new ActionInfo();

    public static enum Gender {
        Male,
        Female;

        public static Gender getGender(int order) {
            return order == 1 ? Female : Male;
        }
    }

    public Player(String name, Gender gender, String iconFilename) {
        this.name = name;
        this.gender = gender;
        this.iconFilename = iconFilename;
    }

    public boolean actionCollected() {
        return actionInfo.actionCollected;
    }

    public boolean hasPendingAction() {
        return actionInfo.hasPendingAction();
    }

    public boolean isActionPending(final Action action) {
        return actionInfo.isActionPending(action);
    }

    public boolean hasActionDone() {
        return actionInfo.hasActionDone();
    }

    public Action getDoneAction() {
        return actionInfo.getDoneAction();
    }

    public void initActionInfo() {
        actionInfo.initActionInfo();
    }

    public void clearActionInfo() {
        actionInfo.clear();
    }

    public void clearAction(final Action action) {
        actionInfo.clear(action);
    }

    public Bitmap getIconBitmap() {
        return Constants.getIcon(iconFilename);
    }

    protected void doInit() {
        synchronized(mTiles) {
            mTiles.clear();
        }
        synchronized(mThrownTiles) {
            mThrownTiles.clear();
        }

        synchronized(mHuedTiles) {
            mHuedTiles.clear();
        }

        synchronized (this) {
            mCanChiTiles.clear();
            mCanPengTiles.clear();
            mCanGangTiles.clear();
            mCanHuTiles.clear();

            mChiedTiles.clear();
            mPengs.clear();
            mGangs.clear();

            mPlaying = false;
            mActionTingAvailable = false;
            mActionTingReported = false;
            mNewTile = null;
            mOpenTile = false;
            mIgnoredType = null;

            actionInfo.initActionInfo();
        }
    }

    public void init() {
        doInit();
        startPlayerThread(true);
    }

    private synchronized void startPlayerThread(boolean fromLocal) {
        if (mPlayerThread != null) {
            mPlayerThread.quitSafely();
            mPlayerThread = null;
        }
        mPlayerThread = new HandlerThreadExt(name);
        mPlayerThread.start();
        playerThreadWait();
    }

    private void playerThreadWait() {
        synchronized(mPlayerThread) {
            try {
                mPlayerThread.wait();
            } catch (Exception e) {
            }
        }
    }

    public void setTileOpen(boolean isOpen) {
        if (mOpenTile != isOpen) {
            mOpenTile = isOpen;
            notifyViewChanged();
        }
    }

    public void startPlaying(final boolean fromLocalManager) {
        mPlaying = true;
        disableThrow();
        updateTilesInfo();
        if (fromLocalManager) {
            mPlayerCallback.send2RemotePlayer(RemoteMessage.ConnMessage.MSG_START_PLAYING, null);
        }
    }

    public synchronized void quitPlaying() {
        setTileOpen(true);
        mPlaying = false;

        if (mPlayerThread != null) {
            mPlayerThread.clear();
            mPlayerThread.quitSafely();
            mPlayerThread = null;
        }
    }

    public void setViewChangeListener(PlayerCallback viewChangeListener) {
        mPlayerCallback = viewChangeListener;
    }

    public void addNewTile(final Tile tile, final TileInfo gangedTileInfo,
                    final boolean fromManager) {
        if (mPlaying) {Constants.debug(name + " got " + tile);
            setNewTile(tile);
            TileInfo tileInfo = new TileInfo(tile, this, false);
            tileInfo.gangedTileInfo = gangedTileInfo;
            checkActionOnNewTile(tileInfo);
        } else {
            addTile(tile);
        }
        // 收到新牌，需要更新player显示.
        mPlayerCallback.onViewChanged();
        if (fromManager) {
            mPlayerCallback.send2RemotePlayer(RemoteMessage.ConnMessage.MSG_PLAYER_ADD_TILE,
                            messageAddTile(tile, gangedTileInfo));
        }
    }

    protected void runInPlayerThread(final Runnable runnable) {
        if (mPlayerThread == null) return;
        mPlayerThread.post(runnable);
    }

    // 对新tile，需要检查能做的actions：胡/听/杠
    private void checkActionOnNewTile(final TileInfo tileInfo) {
        if (mPlayerThread != null) {
            synchronized (mPlayerThread) {
                if (!isIgnoredDetermined()) {// 没有定缺之前要等待action check.
                    playerThreadWait();
                }
            }
        }
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                actionInfo.initActionInfo();
                final Game game = MahjongManager.getInstance().getGame();

                // 如果必须要报听，就先看看能不能听.
                if (game.mustReportTing()) {
                    getCanTingTiles(tileInfo.tile);
                    if (mCanTingTiles.size() > 0) {
                        actionInfo.addAction(Action.Ting);
                        mActionTingAvailable = true;
                    }
                }

                boolean isFromGang = tileInfo.gangedTileInfo != null;
                if (canHuTile(tileInfo.tile, isFromGang, true)) {
                    actionInfo.addAction(Action.Hu);
                } else {
                    if (isFromGang) { // 看看是不是限制到一定杠数不能胡牌就荒庄
                        if (game.isMaxGangCountReached()) {
                            // 到了最多杠数不胡牌，则荒庄.
                            mPlayerCallback.maxGangCountReached();
                            return;
                        }
                    }
                    if (game.hasNoTile()) {// 没牌了还不胡牌，则荒庄.
                        mPlayerCallback.whenNoTile();
                        return;
                    }
                    // 北京麻将中4个混儿都摸到了就胡牌.
                    if (game instanceof GameResource.Beijing &&
                                    getTileCount(game.getMatchAllTile()) == Tile.MAX_TILE_COUNT) {
                        actionInfo.addAction(Action.Hu);
                    }
                }

                if (canGangNewTile(tileInfo.tile)) {
                    actionInfo.addAction(Action.Gang);
                    checkActionGangFlower(game, tileInfo);
                }

                actionInfo.actionCollected = true;
                if (actionInfo.hasPendingAction()) {
                    mPlayerCallback.notifyActionsAvailableOnNewTile(tileInfo);
                } else {
                    mPlayerCallback.notifyThrowAvaiable();
                }
            }
        });
    }

    // 检查要不要为当前tile加上Action.GangFlower.
    // 如果加上此action，则需要扩展TileInfo为GangFlower对象.
    private void checkActionGangFlower(final Game game, final TileInfo tileInfo) {
        if (game.needShowTile() == ShowTile.LastTile) {
            Tile lastTile = game.getLastTile();
            if (canHuTile(lastTile, true, true)) {
                //tileInfo = new GangFlower(inputTileInfo, lastTile);
                actionInfo.addAction(Action.GangFlower);
            }
        }
    }

    // 对吃/碰来的tile，需要检查能做的actions：听/杠等
    public final void checkActionOnGotTile(final TileInfo tileInfo) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                actionInfo.initActionInfo();
                final Game game = MahjongManager.getInstance().getGame();
                // 如果必须要报听，就先看看能不能听.
                if (game.mustReportTing()) {
                    getCanTingTiles(null);
                    if (mCanTingTiles.size() > 0) {
                        actionInfo.addAction(Action.Ting);
                        mActionTingAvailable = true;
                    }
                }

                // 检查暗杠， 因为没有新摸牌.
                // 检查碰杠，因为有可能第四张牌已经摸在手里.
                if (hasGangAfterGotTile(GangType.GangBlack, GangType.GangPenged)) {
                    actionInfo.addAction(Action.Gang);
                    checkActionGangFlower(game, tileInfo);
                }

                actionInfo.actionCollected = true;
                if (actionInfo.hasPendingAction()) {
                    mPlayerCallback.notifyActionsAvailableOnGotTile(tileInfo);
                } else {
                    mPlayerCallback.notifyThrowAvaiable();
                }
            }
        });
    }

    private void addTile(final Tile tile) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                if (tile == null) {
                    Constants.debug("Why null tile for " + name + "!?\n" + Utils.getThrowableStackTrace(new Throwable()));
                    return;
                }
                synchronized (mTiles) {
                    mTiles.add(tile);
                    if (mPlaying || mTiles.size() >= TILE_NUM_NORMAL) {
                        sortTiles();
                    }
                }
            }
        });
    }

    private final Comparator<Tile> mTileComparator = new Comparator<Tile>() {
        @Override
        public int compare(Tile tile1, Tile tile2) {
            int result = 0;
            if (tile1.tileType != tile2.tileType) {
                if (tile1.tileType == mIgnoredType) {
                    result = 1;
                } else if (tile2.tileType == mIgnoredType) {
                    result = -1;
                } else {
                    result = tile1.tileType.ordinal() - tile2.tileType.ordinal();
                }
            } else {
                final int tile1Index = tile1.tileIndex;
                final int tile2Index = tile2.tileIndex;
                result = tile1Index - tile2Index;
            }
            if (mPosition == Position.TOP || mPosition == Position.RIGHT) {
                result = -result;
            }
            return result;
        }

    };

    public void sortTiles() {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                synchronized(mTiles) {
                    Collections.sort(mTiles, mTileComparator);
                    notifyViewChanged();
                }
            }
        });
    }

    public boolean isBlackGanged(final Tile tile) {
        return getGangType(tile) == GangType.GangBlack;
    }

    private GangType getGangType(final Tile tile) {
        synchronized (mGangs) {
            for (Ganged ganged : mGangs) {
                if (tile.isSameTile(ganged.getTile()))
                    return ganged.type;
            }
            return null;
        }
    }

    protected void throwSelected(final Tile tile, final boolean fromRemote) {Constants.debug(name + " throws " + tile);
        synchronized(mTiles) {
            if (!tile.isSameTile(mNewTile)) {
                removeTileFromAlive(tile);
                if (mNewTile != null) {
                    addTile(mNewTile);
                }
            }
        }
        setNewTile(null);
        mThrowAvailable = false;
        synchronized(mThrownTiles) {
            mThrownTiles.add(tile);
            mPlayerCallback.onTileThrown(new TileInfo(tile, this, true));
        }

        // 扔完牌之后需要重新判断胡牌以及能碰/杠/胡的牌.
        updateTilesInfo();
        // 听牌之后，扔掉一张牌之后，需要清空canTingTiles list.
        if (mActionTingReported) {
            mCanTingTiles.clear();
        }

        if (!fromRemote) {
            mPlayerCallback.send2RemotePlayer(ConnMessage.MSG_PLAYER_THREW_TILE, tile.toString());
        }
    }

    private void updateTilesInfo() {
        final Game game = MahjongManager.getInstance().getGame();
        runInPlayerThread(new Runnable() {
            private ArrayList<TileCount> getTileCountList() {
                synchronized (mTiles) {
                    final ArrayList<TileCount> tileCountList = new ArrayList<TileCount>(mTiles.size());
                    TileCount tileCount;
                    for (Tile tile : mTiles) {
                        if (tile.tileType == mIgnoredType) continue; // 忽略不要的牌.
                        tileCount = getTileCount(tileCountList, tile);
                        if (tileCount == null) {
                            tileCountList.add(new TileCount(tile));
                        } else {
                            tileCount.count++;
                        }
                    }
                    return tileCountList;
                }
            }

            @Override
            public void run() {
                if (!isIgnoredDetermined()) {
                    return;
                }
                final ArrayList<TileCount> tileCountList = getTileCountList();
                getCanHuTiles();
                getCanPengTiles(tileCountList);
                getCanGangTiles(tileCountList);
            }

            // 计算出可以碰的牌; 只能在player thread中运行;
            private void getCanPengTiles(final ArrayList<TileCount> tileCountList) {
                if (!isInPlayerThread()) {
                    throw new RuntimeException("The method must run in player thread!");
                }
                mCanPengTiles.clear();
                if (isHued() || isTinged()) return; // 胡牌/停牌之后不能再碰.
                for (TileCount tileCount : tileCountList) {
                    if (tileCount.count >= 2 && tileCount.count < Tile.MAX_TILE_COUNT) {
                        mCanPengTiles.add(tileCount.tile);
                    }
                }
            }

            // 计算出可以杠的牌; 只能在player thread中运行;
            private void getCanGangTiles(final ArrayList<TileCount> tileCountList) {
                if (!isInPlayerThread()) {
                    throw new RuntimeException("The method must run in player thread!");
                }
                mCanGangTiles.clear();
                for (TileCount tileCount : tileCountList) {
                    // 计算出可以暗杠的牌; 4张牌全自己摸来为暗杠.
                    if (tileCount.count == 4) {
                        mCanGangTiles.add(new CanGangTile(tileCount.tile, GangType.GangBlack));
                    } else if (tileCount.count == 3) {
                        // 已经摸到3张的牌，可能明杠，摸到第4张就是暗杠了;
                        mCanGangTiles.add(new CanGangTile(tileCount.tile, GangType.Gang3_1));
                    }
                }
                // 计算出可以碰杠的牌;
                for (Penged penged : mPengs) {
                    mCanGangTiles.add(new CanGangTile(penged.externalTile.tile, GangType.GangPenged));
                }
            }

            // 计算出可以胡的牌; 只能在player thread中运行;
            private void getCanHuTiles() {
                if (!isInPlayerThread()) {
                    throw new RuntimeException("The method must run in player thread!");
                }
                final int chiNum  = getChiedSetNum();
                final int gangNum = getGangedSetNum();
                final int pengNum = getPengedSetNum();
                final Tile matchAllTile = game.getMatchAllTile();
                final Tile[] availables = (matchAllTile == null ? null : game.getAvailableTiles());

                synchronized(mTiles) {
                    mCanHuTiles.clear();

                    if (getTileCount(mIgnoredType) > 0) {
                        return;
                    }
                    int aliveNum = mTiles.size();
                    // 如果吃/碰/杠+活牌不是13张，一定不能胡.
                    if (aliveNum <= 0 || (chiNum + pengNum + gangNum) * 3 + aliveNum != 13) {
                        return;
                    }

                    // 看看还需要成几副牌.
                    final int setNum = 4 - gangNum - pengNum - chiNum;

                    ArrayList<Tile> canHuTiles = new ArrayList<Tile>(TILE_NUM_NORMAL);

                    final int matchAllTileCount = getTileCount(matchAllTile);
                    game.getHuTiles(mTiles, setNum, canHuTiles, matchAllTile, matchAllTileCount,
                                    availables);

                    if (canHuTiles.size() > 0) {
                        constructCanHuTiles(canHuTiles);
                    }
                }
            }

            private void constructCanHuTiles(final ArrayList<Tile> canHuTiles) {
                synchronized (mTiles) {
                    // liveTiles = 现在的活牌 + 每一张availableTile，然后检查能不能胡.
                    final Tile[] liveTiles = new Tile[mTiles.size() + 1];
                    for (int i = 0; i < liveTiles.length - 1; i++) {
                        liveTiles[i] = mTiles.get(i);
                    }

                    final Tile[] chiTiles = getChiedTiles();
                    final Tile[] pengTiles = getPengedTiles();
                    final Tile[] gangTiles = getGangedTiles();

                    for (Tile canHuTile : canHuTiles) {
                        liveTiles[liveTiles.length - 1] = canHuTile;
                        HuPattern huPattern = HuConstants.getHuPattern(liveTiles, chiTiles, pengTiles,
                                        gangTiles);
                        mCanHuTiles.add(new HuTile(canHuTile, huPattern));
                    }
                }
            }
        });
    }

    private boolean isInPlayerThread() {
        return Thread.currentThread().getId() == mPlayerThread.getId();
    }

    private int getChiedSetNum() {
        synchronized(mChiedTiles) {
            return mChiedTiles.size();
        }
    }

    private int getGangedSetNum() {
        synchronized(mGangs) {
            return mGangs.size();
        }
    }

    private int getPengedSetNum() {
        synchronized(mPengs) {
            return mPengs.size();
        }
    }

    private Tile[] getChiedTiles() {
        synchronized(mChiedTiles) {
            int count = mChiedTiles.size();
            if (count <= 0) return null;
            Tile[] tiles = new Tile[count * 3];
            int i = 0;
            for (Chied chied : mChiedTiles) {
                switch (chied.position) {
                    case 0:
                        tiles[i] = chied.externalTile.tile;
                        tiles[i + 1] = chied.tiles[0];
                        tiles[i + 2] = chied.tiles[1];
                        break;
                    case 1:
                        tiles[i] = chied.tiles[0];
                        tiles[i + 1] = chied.externalTile.tile;
                        tiles[i + 2] = chied.tiles[1];
                        break;
                    case 2:
                        tiles[i] = chied.tiles[0];
                        tiles[i + 1] = chied.tiles[1];
                        tiles[i + 2] = chied.externalTile.tile;
                        break;
                }
                i += 3;
            }
            return tiles;
        }
    }

    private Tile[] getGangedTiles() {
        ArrayList<Tile> tiles = new ArrayList<Tile>(mGangs.size() * Tile.MAX_TILE_COUNT);
        synchronized(mGangs) {
            for (Ganged ganged : mGangs) {
                switch (ganged.type) {
                    case GangPenged:
                        tiles.add(ganged.lastTile);
                        tiles.add(ganged.penged.externalTile.tile);
                        for (Tile tile : ganged.penged.tiles) {
                            tiles.add(tile);
                        }
                        break;
                    case GangBlack:
                        for (Tile tile : ganged.tiles) {
                            tiles.add(tile);
                        }
                        break;
                    case Gang3_1:
                        tiles.add(ganged.externalTile.tile);
                        for (Tile tile : ganged.tiles) {
                            tiles.add(tile);
                        }
                        break;
                }
            }
            return tiles.toArray(new Tile[tiles.size()]);
        }
    }

    private Tile[] getPengedTiles() {
        synchronized (mPengs) {
            int count = mPengs.size();
            if (count <= 0) return null;
            Tile[] tiles = new Tile[count * 3];
            int i = 0;
            for (Penged penged : mPengs) {
                tiles[i++] = penged.externalTile.tile;
                for (Tile tile : penged.tiles) {
                    tiles[i++] = tile;
                }
            }
            return tiles;
        }
    }

    // 计算出听牌时可以打掉的牌; 只能在player thread中运行;
    // 意思是打掉这张牌，剩余13张牌就差一张胡牌了.
    // 这里的参数tile有2种情况：
    // 1. 别人打出的牌，已经碰到当前player手中;
    // 2. 自己摸到的牌.
    private void getCanTingTiles(final Tile newTile) {
        mCanTingTiles.clear();
        if (mActionTingReported) return; // 如果已经报听则不用再判断.
        int ignoredTypeTileCount = getTileCount(mIgnoredType);
        if (newTile != null && newTile.tileType == mIgnoredType) {
            ignoredTypeTileCount++;
        }
        if (ignoredTypeTileCount > 1) return; // 缺门没有打完(多于一张牌时)不能报听.

        final int chiNum  = getChiedSetNum();
        final int gangNum = getGangedSetNum();
        final int pengNum = getPengedSetNum();

        final Tile[] chiTiles = getChiedTiles();
        final Tile[] pengTiles = getPengedTiles();
        final Tile[] gangTiles = getGangedTiles();

        synchronized(mTiles) {
            int tileNum = mTiles.size() + (chiNum + pengNum + gangNum) * 3;
            // 如果吃/碰/杠+活牌不是13/14张，一定不能胡.
            if (newTile == null) {
                if (tileNum != 14) return;
            } else if (tileNum != 13) return;

            // 看看还需要成几副牌.
            final int setNum = 4 - gangNum - pengNum - chiNum;
            ArrayList<Tile> canHuTiles = new ArrayList<Tile>(TILE_NUM_NORMAL);
            ArrayList<Tile> liveTiles = new ArrayList<Tile>(mTiles.size());

            // 首先看看当前手上的牌能不能听牌.
            if (newTile != null) {
                liveTiles.addAll(mTiles);
                checkTing(liveTiles, canHuTiles, setNum, newTile, chiTiles, pengTiles, gangTiles);
            }
            // 如果new tile为空，则表示检查现有牌打掉哪一张然后可以停下来等胡牌.
            // 否则，检查现有牌加上new tile后打掉哪一张可以停下来等胡牌.
            for (Tile tile : mTiles) {
                canHuTiles.clear();
                liveTiles.clear();

                liveTiles.addAll(mTiles);
                liveTiles.remove(tile);
                if (newTile != null) {
                    liveTiles.add(newTile);
                }
                checkTing(liveTiles, canHuTiles, setNum, tile, chiTiles, pengTiles, gangTiles);
            }
        }
    }

    private void checkTing(final ArrayList<Tile> liveTiles, final ArrayList<Tile> canHuTiles,
                    final int setNum, final Tile tile, final Tile[] chiTiles,
                    final Tile[] pengTiles, final Tile[] gangTiles) {
        final Game game = MahjongManager.getInstance().getGame();
        final Tile matchAllTile = game.getMatchAllTile();
        final int matchAllTileCount = getTileCount(matchAllTile);
        final Tile[] availableTiles = matchAllTile == null ? null : game.getAvailableTiles();
        game.getHuTiles(liveTiles, setNum, canHuTiles, matchAllTile, matchAllTileCount,
                        availableTiles);
        if (canHuTiles.size() > 0) {
            ArrayList<HuTile> huTiles = new ArrayList<HuTile>(canHuTiles.size());
            constructCanHuTiles(liveTiles, chiTiles, pengTiles, gangTiles, canHuTiles,
                            huTiles);
            mCanTingTiles.add(new TingTileInfo(tile,
                            huTiles.toArray(new HuTile[huTiles.size()])));
        }
    }

    private void constructCanHuTiles(final ArrayList<Tile> liveTileList, final Tile[] chiTiles,
                    final Tile[] pengTiles, final Tile[] gangTiles,
                    final ArrayList<Tile> huTiles, final ArrayList<HuTile> canHuTiles) {
        final Tile[] liveTiles = new Tile[liveTileList.size() + 1];
        for (int i = 0; i < liveTiles.length - 1; i++) {
            liveTiles[i] = liveTileList.get(i);
        }
        for (Tile canHuTile : huTiles) {
            liveTiles[liveTiles.length - 1] = canHuTile;
            HuPattern huPattern = HuConstants.getHuPattern(liveTiles, chiTiles, pengTiles,
                            gangTiles);
            canHuTiles.add(new HuTile(canHuTile, huPattern));
        }
    }

    private boolean hasTile(final Tile inputTile) {
        synchronized(mTiles) {
            for (Tile tile : mTiles) {
                if (tile.isSameTile(inputTile)) return true;
            }
            return false;
        }
    }

    // 计算出可以吃的牌; 只能在player thread中运行;
    private void getCanChiTiles(final TileInfo tileInfo) {
        mCanChiTiles.clear();
        if (!MahjongManager.getInstance().getGame().isValidAction(Action.Chi)) {
            return;
        }
        if (!isFromUpperPlayer(tileInfo)) return; // 只能吃上家的牌.
        // 只有数字牌（条/筒/万）才可以吃.
        if (!Tile.isNumberTile(tileInfo.tile)) return;
        if (mActionTingReported) return; // 听牌后不能再吃.

        synchronized(mTiles) {
            // 看看左1左2右1右2这4张牌是不是都在.
            // 大小顺序：left_2 < left_1 < self < right_1 < right_2.
            Tile left_1 = Tile.getTile(tileInfo.tile.tileIndex - 1, tileInfo.tile.tileType);
            Tile left_2 = Tile.getTile(tileInfo.tile.tileIndex - 2, tileInfo.tile.tileType);
            Tile right_1 = Tile.getTile(tileInfo.tile.tileIndex + 1, tileInfo.tile.tileType);
            Tile right_2 = Tile.getTile(tileInfo.tile.tileIndex + 2, tileInfo.tile.tileType);
            // 如果是左边张, 可以吃.
            if (left_1 != null && left_2 != null && hasTile(left_1) && hasTile(left_2)) {
                mCanChiTiles.add(new CanChi(tileInfo.tile, new Tile[] {left_2, left_1}, 2));
            }
            // 如果是中间张, 可以吃.
            if (left_1 != null && right_1 != null && hasTile(left_1) && hasTile(right_1)) {
                mCanChiTiles.add(new CanChi(tileInfo.tile, new Tile[] {left_1, right_1}, 1));
            }
            // 如果是右边张, 可以吃.
            if (right_1 != null && right_2 != null && hasTile(right_1) && hasTile(right_2)) {
                mCanChiTiles.add(new CanChi(tileInfo.tile, new Tile[] {right_1, right_2}, 0));
            }
        }
    }

    // 获得list中某个牌的个数.
    private TileCount getTileCount(final ArrayList<TileCount> tileCountList, final Tile tile) {
        for (TileCount tileCount : tileCountList) {
            if (tileCount.tile.isSameTile(tile)) return tileCount;
        }
        return null;
    }

    // 获得某种类型的牌的个数.
    private int getTileCount(final TileType tileType) {
        if (tileType == null) return 0;
        synchronized(mTiles) {
            int count = 0;
            for (Tile tile : mTiles) {
                if (tile.tileType == tileType) count++;
            }
            return count;
        }
    }

    // 检查player是否明杠/碰杠此牌。暗杠的牌不在此public函数检查。
    public boolean isTileOpenGanged(final Tile tile) {
        synchronized(mGangs) {
            for (Ganged ganged : mGangs) {
                switch (ganged.type) {
                    case GangBlack:
                        break;
                    case Gang3_1:
                        if (tile.isSameTile(ganged.externalTile.tile)) {
                            return true;
                        }
                        break;
                    case GangPenged:
                        if (tile.isSameTile(ganged.lastTile)) {
                            return true;
                        }
                        break;
                }
            }
            return false;
        }
    }

    public boolean isChiedTile(final Tile inputTile) {
        synchronized(mChiedTiles) {
            for (Chied chied : mChiedTiles) {
                if (inputTile == chied.externalTile.tile) {
                    return true;
                }
                for (Tile tile : chied.tiles) {
                    if (inputTile == tile) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public boolean isPengedTile(final Tile tile) {
        synchronized(mPengs) {
            for (Penged penged : mPengs) {
                if (tile.isSameTile(penged.externalTile.tile)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 看看player打过几张此tile.
    public int getThrownedTileCount(final Tile inputTile) {
        synchronized(mThrownTiles) {
            int count = 0;
            for (Tile tile : mThrownTiles) {
                if (tile.isSameTile(inputTile)) {
                    count++;
                }
            }
            return count;
        }
    }

    private HuPattern getHuPattern(final Tile huedTile) {
        for (HuTile huTile : mCanHuTiles) {
            if (huedTile.isSameTile(huTile.tile)) {
                return huTile.huPattern;
            }
        }
        return null;
    }

    // 摸到混儿牌，返回所有能胡的牌型信息.
    private HuTile[] getHuTiles() {
        if (mCanHuTiles.size() > 0) {
            return mCanHuTiles.toArray(new HuTile[mCanHuTiles.size()]);
        }
        return null;
    }

    private boolean canHuTile(final Tile tile, final boolean isFromGang, final boolean isNew) {
        if (tile == null) return false;
        final Game game = MahjongManager.getInstance().getGame();
        if (isHued()) {
            HuConstants.HuedType gameHuedType = game.getHuedType();
            switch (gameHuedType) {
                case HuOnceAll:    // 一家胡牌后，则游戏结束.
                    return false;
                case HuOncePlayer: // 一个玩家只能胡一次，胡过之后剩余玩家继续.
                    return false;
                case HuMulti:      // 可以多家胡牌，每个玩家都可以胡任意次.
                    break;
            }
        }
        if (game.onlyHuSelf() && !isNew) {
            return false;
        }
        // 先判断是不是胡牌.
        boolean canHu = false;
        if (mCanHuTiles.size() > 0) {
            if (tile != null && isNew && isMatchAllTile(tile)) {
                canHu = true;
            } else {
                for (HuTile huTile : mCanHuTiles) {
                    if (huTile.tile.isSameTile(tile)) {
                        canHu = true;
                        break;
                    }
                }
            }
        }

        if (canHu) {// 如果是胡牌，
            // 杠牌可以直接胡.
            if (isFromGang) {
                return true;
            }
            // 检查是否必须要报听才能胡牌.
            if (game.mustReportTing()) {
                return mActionTingReported;
            }
            return true;
        }
        return false;
    }

    private boolean hasGangAfterGotTile(final GangType...gangTypes) {
        final boolean checkBlackGang = isGangTypeSupported(GangType.GangBlack, gangTypes);
        final boolean checkPengedGang = isGangTypeSupported(GangType.GangPenged, gangTypes);
        for (CanGangTile canGangTile : mCanGangTiles) {
            // 已经有一个暗杠.4张牌都是自己摸来的.
            if (checkBlackGang) {
                if (canGangTile.canGangType == GangType.GangBlack) {
                    return true;
                }
            }
            if (checkPengedGang) {
                if (canGangTile.canGangType == GangType.GangPenged) {
                    // 有个碰杠，第4张已经在手上.
                    if (getTileCount(canGangTile.tile) == 1) return true;
                }
            }
        }
        return false;
    }

    private boolean hasGangForThrownTile(final Tile tile) {
        for (CanGangTile canGangTile : mCanGangTiles) {
            if (canGangTile.canGangType != GangType.Gang3_1) continue;
            // 别人打出来的牌，手上已经有3张了.
            if (canGangTile.tile.isSameTile(tile)) return true;
        }
        return false;
    }

    // gangTypes为null表示支持所有gangType.
    private static boolean isGangTypeSupported(final GangType inputGangType, final GangType...gangTypes) {
        if (gangTypes == null) return true;
        for (GangType gangType : gangTypes) {
            if (gangType == inputGangType) return true;
        }
        return false;
    }

    private boolean canGangNewTile(final Tile inputTile) {
        /*// 如果已经胡牌了，...
        if (isHued()) {
            return false;
        }*/
        for (CanGangTile canGangTile : mCanGangTiles) {
            switch (canGangTile.canGangType) {
                case GangBlack:
                    if (mActionTingReported) {
                        return false; // 听牌后这种牌不让杠了.
                    }
                    return true;
                case GangPenged:
                    // 3张碰牌，第4张已经在手里.
                    if (getTileCount(canGangTile.tile) == 1) {
                        if (mActionTingReported) {
                            return false; // 听牌后这种牌不让杠了.
                        }
                        return true;
                    }
                    // 3张碰牌，第4张刚摸到.
                    if (canGangTile.tile.isSameTile(inputTile)) {
                        return true;
                    }
                    continue;
                case Gang3_1:
                    // 之前已经摸到了3张，现在摸到了第4张.
                    if (canGangTile.tile.isSameTile(inputTile)) {
                        return true;
                    }
                    continue;
            }
        }
        return false;
    }

    protected int getTileCount(final Tile inputTile) {
        synchronized (mTiles) {
            if (inputTile == null || mTiles.size() <= 0) return 0;
            int count = 0;
            for (Tile tile : mTiles) {
                if (tile.isSameTile(inputTile)) count++;
            }
            return count;
        }
    }

    // 检查是否可以碰此tile.
    private boolean canPengTile(TileInfo inputTileInfo) {
        // 如果已经胡牌了，不能碰牌...
        if (isHued() || inputTileInfo == null) {
            return false;
        }
        if (!MahjongManager.getInstance().getGame().pengActionAvailable()) {
            return false;
        }
        if (mActionTingReported) return false; // 报听后不能再碰牌.
        if (!inputTileInfo.isThrown()) return false; // 只能碰别人打出来的牌.
        if (inputTileInfo.fromWhere.equals(mLocation)) return false; // 自己的牌不能碰.
        for (Tile tile : mCanPengTiles) {
            if (tile.isSameTile(inputTileInfo.tile)) return true;
        }
        return false;
    }

    // 对于别人打出来的牌，看看能不能吃/碰/杠/胡等.
    public final void checkActionOnThrownTile(final TileInfo tileInfo) {
        if (mPlayerThread != null) {
            synchronized (mPlayerThread) {
                if (!isIgnoredDetermined()) {// 没有定缺之前要等待action check.
                    playerThreadWait();
                }
            }
        }
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                actionInfo.initActionInfo();
                final Game game = MahjongManager.getInstance().getGame();

                if (canHuTile(tileInfo.tile, false, false)) {
                    if (game instanceof GameResource.Beijing) {
                        // 北京麻将要求3个混儿只能自摸了.
                        GameResource.Beijing beijing = (GameResource.Beijing)game;
                        if (getTileCount(beijing.getMatchAllTile()) < 3) {
                            actionInfo.addAction(Action.Hu);
                        }
                    } else {
                        actionInfo.addAction(Action.Hu);
                    }
                }
                // 只用检查明杠，因为是别人打出来的牌.
                if (hasGangForThrownTile(tileInfo.tile)) {
                    actionInfo.addAction(Action.Gang);
                    checkActionGangFlower(game, tileInfo);
                }
                if (canPengTile(tileInfo)) {
                    actionInfo.addAction(Action.Peng);
                }
                // 检查是否可以吃此tile. 别人的牌，而且还没拿到手，需要判断能不能吃.
                getCanChiTiles(tileInfo);
                if (mCanChiTiles.size() > 0) {
                    actionInfo.addAction(Action.Chi);
                }

                actionInfo.actionCollected = true;
                // 对该tile，action收集完毕，需要通知manager.
                mPlayerCallback.notifyActionsAvailableOnThrownTile(tileInfo);
            }
        });
    }

    // 对于别人已经杠了的牌，看看能不能抢杠.
    public final void checkActionOnGangedTile(final TileInfo tileInfo, final boolean isBlackGang) {
        Constants.debug(name + ".checkActionOnGangedTile(" + tileInfo + ")");
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                actionInfo.initActionInfo();
                whenTileGanged(tileInfo, isBlackGang);
                actionInfo.actionCollected = true;
                // 对该tile，action收集完毕，需要通知manager.
                mPlayerCallback.notifyActionsAvailableOnGangedTile(tileInfo, isBlackGang);
            }
        });
    }

    protected void whenTileGanged(final TileInfo tileInfo, final boolean isBlackGang) {
        final Game game = MahjongManager.getInstance().getGame();

        // 检查是否可以抢杠.只有听牌之后，可以胡的牌才可以抢杠?
        if (game.mustReportTing()) {
            if (mActionTingReported) {
                if (isBlackGang) {
                    actionInfo.addAction(Action.GangGrab);
                } else if (canHuTile(tileInfo.tile, false, false)) {
                    actionInfo.addAction(Action.GangGrab);
                }
            }
        } else if (canHuTile(tileInfo.tile, false, false)) {
            actionInfo.addAction(Action.GangGrab);
        }
    }

    public final void readyToThrow() {
        final long startTime = System.currentTimeMillis();
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                whenReadyToThrow(startTime, true);
            }
        });
    }

    protected void whenReadyToThrow(final long startTime, final boolean fromLocalManager) {
        mThrowAvailable = true;
    }

    public final void disableThrow() {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                mThrowAvailable = false;
            }
        });
    }

    public void selectAction(final TileInfo tileInfo, final Action...actions) {
        disableThrow();
    }

    protected final void takeAction(Action action, TileInfo tileInfo, boolean fromRemote) {
        switch (action) {
            case Hu:
                actionHu(tileInfo);
                break;
            case GangFlower:
                actionGangFlower(tileInfo);
                break;
            case Ting:
                actionTing(tileInfo);
                break;
            case Gang:
                actionGang(tileInfo);
                break;
            case GangGrab:
                actionGangGrab(tileInfo);
                break;
            case Peng:
                actionPeng(tileInfo);
                break;
            case Chi:
                actionChi(tileInfo);
                break;
            default:
                return;
        }
        if (!fromRemote) {
            mPlayerCallback.send2RemotePlayer(ConnMessage.MSG_PLAYER_TAKE_ACTION,
                            MessageUtils.messageActionInfo(action, tileInfo));
        }
    }

    protected void actionDone(Action action, TileInfo tileInfo) {
        actionInfo.doneAction(action);
        notifyViewChanged();
        if (action == Action.GangFlower) {
            updateTilesInfo();
            mPlayerCallback.gangFlowerDone(tileInfo);
            return;
        }
        if (tileInfo.isThrown()) {
            switch (action) {
                case Chi:
                case Peng:
                case Hu:
                    mPlayerCallback.actionDoneOnThrownTile(action, tileInfo);
                    break;
                case GangGrab:
                    mPlayerCallback.actionDoneOnGangedTile(action, tileInfo);
                    break;
                case Ting:
                    mActionTingAvailable = false;
                    mPlayerCallback.actionDoneOnGotTile(action, tileInfo);
                    break;
                case Gang:
                    GangType gangType = getGangType(tileInfo.tile);
                    if (gangType == GangType.Gang3_1) { // Gang3_1
                         mPlayerCallback.actionDoneOnThrownTile(action, tileInfo);
                    } else { // GangBlack or GangPeng
                        mPlayerCallback.actionDoneOnGotTile(action, tileInfo);
                    }
                    break;
                default:
                    break;
            }
        } else {
            mActionTingAvailable = false;
            mPlayerCallback.actionDoneOnNewTile(action, tileInfo);
        }
        updateTilesInfo();
    }

    public void actionsIgnored(final TileInfo tileInfo, final Action...actions) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                actionInfo.ignoreActions(actions);
                mActionTingAvailable = false;

                notifyViewChanged();

                if (tileInfo.isThrown()) {
                    if (tileInfo.finalLocation == mLocation) {
                        mPlayerCallback.actionsIgnoredOnGotTile(tileInfo, actions);
                    } else {
                        mPlayerCallback.actionsIgnoredOnThrownTile(tileInfo, actions);
                    }
                } else {
                    mPlayerCallback.actionsIgnoredOnNewTile(tileInfo, actions);
                }
            }
        });
    }

    public void gangGrabIgnored(final TileInfo tileInfo, final boolean isBlackGang,
                    final Action...actions) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                actionInfo.ignoreActions(actions);
                notifyViewChanged();
                mPlayerCallback.actionsIgnoredOnGangedTile(tileInfo, isBlackGang, actions);
            }
        });
    }

    protected void notifyViewChanged() {
        if (mPlayerCallback != null) {
            mPlayerCallback.onViewChanged();
        }
    }

    public synchronized void setLocation(Location location) {
        mLocation = location;
    }

    public synchronized Location getLocation() {
        return mLocation;
    }

    public synchronized void setPosition(Position position) {
        mPosition = position;
    }

    public synchronized Position getPosition() {
        return mPosition;
    }

    public void setViews(LinearLayout tileLayout, ImageView newTileView,
            LinearLayout tileListChiView,  int chiTileLayoutId,
            LinearLayout tileListPengView, int pengTileLayoutId,
            LinearLayout tileListGangView, int gangTileLayoutId, int gangBlackTileLayoutId,
            int huedTileLayoutId, LinearLayout tileListHuView, ViewGroup thrownTilesLayout,
            int tileOpenResId, int tileCloseResId, int tileIndicatorViewId) {
        mTileListView = tileLayout;

        mChiView = tileListChiView;
        mChiTileLayoutId = chiTileLayoutId;
        mPengView = tileListPengView;
        mPengTileLayoutId = pengTileLayoutId;
        mGangView = tileListGangView;
        mGangTileLayoutId = gangTileLayoutId;
        mGangBlackTileLayoutId = gangBlackTileLayoutId;

        mHuedTileLayoutId = huedTileLayoutId;

        mHuView = tileListHuView;
        mThrownTilesView = thrownTilesLayout;
        mNewTileView = newTileView;

        mTileOpenLayoutId = tileOpenResId;
        mTileCloseLayoutId = tileCloseResId;

        mTileIndicatorViewId = tileIndicatorViewId;
    }

    // This must be called in main thread.
    public void viewChanged(final Context context) {
        if (!Utils.isMainThread()) {
            throw new RuntimeException("Player.viewChanged() must be called in main thread!");
        }
        final int positionIndex = mPosition.ordinal();
        updateTilesView(context, positionIndex);
        updateThrownTilesView(context);
        updateHuedTilesView(context, positionIndex);
    }

    // 这张牌已经被其他player 吃/碰/杠了，需要从扔出的牌中删掉.
    public void removeTileFromThrown(final TileInfo tileInfo) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                synchronized(mThrownTiles) {
                    mThrownTiles.remove(tileInfo.tile);
                    notifyViewChanged();
                }
            }
        });
    }

    private void updateChiView(final Context context, final int positionIndex) {
        synchronized (mChiedTiles) {
            if (mChiView == null) return;
            mChiView.removeAllViews();
            View externalTileView, tileView0, tileView1;
            for (Chied chied : mChiedTiles) {
                externalTileView = chied.externalTile.tile.inflateChi(context, mChiTileLayoutId,
                                positionIndex);
                final String infoString = chied.externalTile.info();
                externalTileView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        Utils.showInfo(context, context.getString(R.string.action_chi),
                                        infoString);
                    }
                });
                tileView0 = chied.tiles[0].inflate(context, mChiTileLayoutId, positionIndex);
                tileView1 = chied.tiles[1].inflate(context, mChiTileLayoutId, positionIndex);
                switch (chied.position) {
                    case 0:
                        mChiView.addView(externalTileView);
                        mChiView.addView(tileView0);
                        mChiView.addView(tileView1);
                        break;
                    case 1:
                        mChiView.addView(tileView0);
                        mChiView.addView(externalTileView);
                        mChiView.addView(tileView1);
                        break;
                    case 2:
                        mChiView.addView(tileView0);
                        mChiView.addView(tileView1);
                        mChiView.addView(externalTileView);
                        break;
                }
            }
        }
    }

    private void updatePengView(final Context context, final int positionIndex) {
        synchronized(mPengs) {
            mPengView.removeAllViews();
            View tileView;
            for (Penged penged : mPengs) {
                for (Tile tile : penged.tiles) {
                    tileView = tile.inflate(context, mPengTileLayoutId, positionIndex);
                    mPengView.addView(tileView);
                }
                tileView = penged.externalTile.tile.inflate(context, mPengTileLayoutId,
                                positionIndex);
                final String infoString = penged.externalTile.info();
                tileView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        Utils.showInfo(context, context.getString(R.string.action_peng),
                                        infoString);
                    }
                });
                mPengView.addView(tileView);
            }
        }
    }

    private void updateGangView(final Context context, final int positionIndex) {
        synchronized (mGangs) {
            mGangView.removeAllViews();
            for (Ganged ganged : mGangs) {
                inflateGangView(context, positionIndex, ganged);
            }
        }
    }

    private void inflateGangView(final Context context, final int positionIndex,
                    final Ganged ganged) {
        View tileView;
        final String infoString;
        switch (ganged.type) {
            case GangBlack:
                for (Tile tile : ganged.tiles) {
                    tileView = tile.inflateGangBlack(context,
                                    mPosition == Position.BOTTOM || mOpenTile ? mGangTileLayoutId
                                                    : mGangBlackTileLayoutId,
                                    positionIndex);
                    mGangView.addView(tileView);
                }
                break;
            case Gang3_1:
                for (Tile tile : ganged.tiles) {
                    tileView = tile.inflate(context, mGangTileLayoutId, positionIndex);
                    mGangView.addView(tileView);
                }
                tileView = ganged.externalTile.tile.inflate(context, mGangTileLayoutId,
                                positionIndex);
                infoString = ganged.externalTile.info();
                tileView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        Utils.showInfo(context, context.getString(R.string.action_gang),
                                        infoString);
                    }
                });
                mGangView.addView(tileView);
                break;
            case GangPenged:
                for (Tile tile : ganged.penged.tiles) {
                    tileView = tile.inflate(context, mGangTileLayoutId, positionIndex);
                    mGangView.addView(tileView);
                }

                tileView = ganged.penged.externalTile.tile.inflate(context, mPengTileLayoutId,
                                positionIndex);
                infoString = Action.getActionString(context, Player.this, Action.Peng,
                                ganged.penged.externalTile);
                tileView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        Utils.showInfo(context, context.getString(R.string.action_gang),
                                        infoString);
                    }
                });
                mGangView.addView(tileView);

                tileView = ganged.lastTile.inflate(context, mGangTileLayoutId, positionIndex);
                mGangView.addView(tileView);
                break;
        }
    }

    private void updateTilesView(final Context context, final int positionIndex) {
        synchronized(mTiles) {
            mTileListView.removeAllViews();
            mTileListView.setGravity(Gravity.BOTTOM);
            View tileView;
            for (Tile tile : mTiles) {
                tileView = inflateTileView(context, tile, positionIndex);
                mTileListView.addView(tileView);
            }
        }

        setNewTileView();

        updateChiView(context,  positionIndex);
        updatePengView(context, positionIndex);
        updateGangView(context, positionIndex);
    }

    private synchronized void setNewTileView() {
        final View rootView = mNewTileView.getRootView();

        TextView tileMatchAllTextView = (TextView) rootView.findViewById(R.id.tile_match_all_bottom_new);
        if (tileMatchAllTextView != null) {
            if (mNewTile != null && mNewTile.isMatchAll && mOpenTile) {
                tileMatchAllTextView.setVisibility(View.VISIBLE);
            } else {
                tileMatchAllTextView.setVisibility(View.GONE);
            }
        }

        TextView tileSpecialTextView = (TextView)rootView.findViewById(R.id.tile_special_bottom_new);
        if (tileSpecialTextView != null) {
            if (mNewTile != null && mNewTile.specialTile != null && mOpenTile) {
                tileSpecialTextView.setVisibility(View.VISIBLE);
            } else {
                tileSpecialTextView.setVisibility(View.GONE);
            }
        }

        if (mNewTile == null) {
            mNewTileView.setVisibility(View.INVISIBLE);
            mNewTileView.setOnClickListener(null);
            return;
        }

        mNewTileView.setVisibility(View.VISIBLE);
        mNewTileView.setColorFilter(null);
        final boolean isSelectable = isSelectableTile(mNewTile);
        if (mOpenTile) {
            mNewTileView.setBackground(mPosition.tileBackgroundDrawable);
            mNewTileView.setImageResource(mNewTile.getResourceId(mPosition.ordinal()));
            if (mNewTile.tileType == mIgnoredType) {
                Tile.grayImageView(mNewTileView);
            } else if (!isSelectable) {
                Tile.tingImageView(mNewTileView);
            }
        } else {
            mNewTileView.setBackground(mPosition.tileBackDrawable);
            mNewTileView.setImageBitmap(null);
        }
        doSetNewTileView(isSelectable);
    }

    protected void doSetNewTileView(final boolean isSelectable) {
        mNewTileView.setOnClickListener(null);
    }

    // 判断是不是可以被选择的牌.
    // 但是可以被选择并不代表可以随后扔掉。
    // 没有报听时，选择的牌可以接下来再被选中扔掉.
    protected final boolean isSelectableTile(final Tile tile) {
        // 没报听之前以及不能报听的时候，每一张牌都可以打掉.
        if (!mActionTingReported && !mActionTingAvailable) return true;

        if (isMatchAllTile(tile)) {
            return false;
        }

        synchronized (mCanTingTiles) {
            if (tile == mNewTile && mCanTingTiles.size() <= 0) {
                return true;// 报听之后只有新摸来的牌可以打掉.
            }
            // 刚报听时每一张可以听的牌可以打掉.
            for (TingTileInfo tileInfo : mCanTingTiles) {
                if (tileInfo.tile.isSameTile(tile)) return true;
            }
            return false;
        }
    }

    protected final boolean isMatchAllTile(final Tile tile) {
        Tile matchAllTile = MahjongManager.getInstance().getGame().getMatchAllTile();
        if (matchAllTile != null && tile.isSameTile(matchAllTile)) {
            return true;
        }
        return false;
    }

    protected View inflateTileView(Context context, Tile tile, final int positionIndex) {
        View tileView = tile.inflate(context, mOpenTile || tile.isTingedTile ? mTileOpenLayoutId
                : mTileCloseLayoutId, positionIndex);
        if (tile.tileType == mIgnoredType && mOpenTile) {
            tile.grayTile(tileView);
        }
        return tileView;
    }

    private void updateThrownTilesView(final Context context) {
        synchronized(mThrownTiles) {
            final int positionIndex = mPosition.ordinal();
            mThrownTilesView.removeAllViews();
            View tileView;
            LayoutParams params = null;
            for (Tile tile : mThrownTiles) {
                tileView = tile.inflateThrownTile(context, mTileOpenLayoutId, positionIndex,
                                mTileIndicatorViewId);

                if (mThrownTilesView instanceof LinearLayout) {
                    mThrownTilesView.addView(tileView);
                } else if (mThrownTilesView instanceof FixedGridLayout) {
                    ((FixedGridLayout) mThrownTilesView).addCell(tileView);
                }

                if (params == null) {
                    params = tileView.getLayoutParams();
                }
                boolean isHorizontal = mPosition == Position.BOTTOM || mPosition == Position.TOP;
                if (tile.isFocused) {
                    params.width = TileResources.getFocusedTileWidth(isHorizontal);
                    params.height = TileResources.getFocusedTileHeight(isHorizontal);
                } else {
                    params.width = TileResources.getThrownTileWidth(isHorizontal);
                    params.height = TileResources.getThrownTileHeight(isHorizontal);
                }
                tileView.setLayoutParams(params);
            }
        }
    }

    private void updateHuedTilesView(final Context context, final int positionIndex) {
        synchronized(mHuedTiles) {
            mHuView.removeAllViews();
            View tileView;
            boolean isGangFlower;
            for (TileInfo huedTile : mHuedTiles) {
                isGangFlower = huedTile instanceof GangFlower;
                tileView = huedTile.tile.inflateHuedTile(context, mHuedTileLayoutId,
                        positionIndex, huedTile, name, isGangFlower);
                if (isFromSelf(huedTile)) {
                    if (isGangFlower) {
                        final String infoString = ((GangFlower)huedTile).info(context);
                        tileView.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View arg0) {
                                Utils.showInfo(context, context.getString(R.string.action_gang_flower),
                                                infoString);
                            }
                        });
                    } else {
                        tileView.setOnClickListener(null);
                    }
                } else {
                    final String infoString = huedTile.info();
                    tileView.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View arg0) {
                            Utils.showInfo(context, context.getString(R.string.action_hu),
                                            infoString);
                        }
                    });
                }
                mHuView.addView(tileView);
            }
        }
    }

    protected void playerThreadNotify(boolean fromRemote) {
        synchronized(mPlayerThread) {
            mPlayerThread.notify();
            if (!fromRemote) {
                mPlayerCallback.send2RemotePlayer(ConnMessage.MSG_PLAYER_THREAD_NOTIFY, null);
            }
        }
    }

    public void setIgnoredType(final TileType ignoredType, boolean fromRemote) {
        mIgnoredType = ignoredType;
        playerThreadNotify(fromRemote);
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                sortTiles();
                mPlayerCallback.onIgnoredTypeDetermined();
                updateTilesInfo();
            }
        });
        if (!fromRemote) {
            mPlayerCallback.send2RemotePlayer(ConnMessage.MSG_SET_IGNORED_TYPE,
                            MessageUtils.messageIgnoredType(ignoredType));
        }
    }

    public final void determineIgnored(final boolean fromLocalManager) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                doDetermineIgnored(fromLocalManager);
            }
        });
    }

    protected void doDetermineIgnored(final boolean fromLocalManager) {
        mPlayerCallback.promptDetermineIgnored();
    }

    public void autoDetermineIgnored() {
        setIgnoredType(findWorstTileType(), false);
    }

    private TileType findWorstTileType() {
        int tiaoNum = getTilesNum(TileType.Tiao);
        int tongNum = getTilesNum(TileType.Tong);
        int wanNum = getTilesNum(TileType.Wan);
        if (tiaoNum != tongNum && tiaoNum != wanNum && tongNum != wanNum) {
            int min = Utils.min(tiaoNum, tongNum, wanNum);
            if (min == tiaoNum) {
                return TileType.Tiao;
            }
            if (min == tongNum) {
                return TileType.Tong;
            }
            return TileType.Wan;
        }
        if (tiaoNum == tongNum) {
            if (tiaoNum > wanNum) return TileType.Wan;
        }
        if (tiaoNum == wanNum) {
            if (tiaoNum > tongNum) return TileType.Tong;
        }
        if (wanNum == tongNum) {
            if (wanNum > tiaoNum) return TileType.Tiao;
        }
        return TileType.getRandomType(MahjongManager.getInstance().getGame());
    }

    protected int getTilesNum(TileType type) {
        synchronized(mTiles) {
            if (type == null) {
                return mTiles.size();
            }
            int num = 0;
            for (Tile tile : mTiles) {
                if (tile.tileType == type) {
                    num++;
                }
            }
            return num;
        }
    }

    // So far只有血流成河/血战到底需要定缺.
    public boolean isIgnoredDetermined() {
        final Game game = MahjongManager.getInstance().getGame();
        if (game.isValidAction(Action.DetermineIgnored)) {
            return mIgnoredType != null;
        }
        return true;
    }

    public TileType getIgnoredType() {
        return mIgnoredType;
    }

    public Action[] getActions() {
        return actionInfo.getActions();
    }

    // 判断是否已经胡了或者胡过. 胡过之后牌不能再改变.
    public boolean isHued() {
        synchronized(mHuedTiles) {
            return mHuedTiles.size() > 0;
        }
    }

    // 判断是否player胡了该tile.
    public boolean huedTile(final TileInfo inputTileInfo) {
        synchronized(mHuedTiles) {
            for (TileInfo tileInfo : mHuedTiles) {
                if (tileInfo == inputTileInfo) return true;
            }
            return false;
        }
    }

    // 判断是否已经听牌. 听牌之后不能再改变.
    protected boolean isTinged() {
        // TODO: Action.Ting is not implemented so far.
        return false;
    }

    public boolean isPlaying() {
        return mPlaying;
    }

    // 判断是不是自己摸的牌.
    private boolean isFromSelf(final TileInfo tileInfo) {
        return tileInfo.fromWhom == null || name.equals(tileInfo.fromWhom);
    }

    // 判断是不是上家打的牌.
    private boolean isFromUpperPlayer(final TileInfo tileInfo) {
        Location upper = Location.getPreviousLocation(mLocation);
        return upper.equals(tileInfo.fromWhere);
    }

    private void actionHu(final TileInfo tileInfo) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                synchronized (mHuedTiles) {
                    final Tile matchAllTile = MahjongManager.getInstance().getGame().getMatchAllTile();
                    if (matchAllTile != null && tileInfo.tile.isSameTile(matchAllTile)) {
                        tileInfo.addHuedPlayer(new Tile.HuedInfo(Player.this,
                                getHuTiles()));
                    } else {
                        tileInfo.addHuedPlayer(new Tile.HuedInfo(Player.this,
                                getHuPattern(tileInfo.tile)));
                    }
                    mHuedTiles.add(tileInfo);
                }
                // 自摸的牌，需要更新new tile view.
                if (tileInfo.tileState == TileState.TileNew) {
                    setNewTile(null);
                }
                actionDone(Action.Hu, tileInfo);
            }
        });
    }

    private synchronized void setNewTile(final Tile tile) {
        mNewTile = tile;
    }

    // Action.Gang + GotTileFromEnd + Action.Hu
    private void actionGangFlower(final TileInfo tileInfo) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                final Game game = MahjongManager.getInstance().getGame();
                final GangFlower gangFlower = new GangFlower(tileInfo, game.getLastTile());
                CanGangTile[] canGangTiles = getCanGangTiles(tileInfo);
                CanGangTile canGangTile = canGangTiles[0];
                gang(canGangTile, gangFlower, false);

                final Tile matchAllTile = MahjongManager.getInstance().getGame().getMatchAllTile();
                if (matchAllTile != null && gangFlower.huTile.isSameTile(matchAllTile)) {
                    gangFlower.addHuedPlayer(new Tile.HuedInfo(Player.this,
                            getHuTiles()));
                } else {
                    gangFlower.addHuedPlayer(new Tile.HuedInfo(Player.this,
                            getHuPattern(gangFlower.huTile)));
                }

                setNewTile(gangFlower.huTile);
                synchronized(mHuedTiles) {
                    mHuedTiles.add(gangFlower);
                }
                actionDone(Action.GangFlower, gangFlower);
            }
        });
    }

    private void actionTing(final TileInfo tileInfo) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                mActionTingReported = true;
                tileInfo.tile.isTingedTile = true;
                if (mNewTile != null) {
                    addTile(mNewTile); // 新tile还没有加入liveTiles.
                    setNewTile(null);
                }
                actionDone(Action.Ting, tileInfo);
            }
        });
    }

    protected void actionChi(final TileInfo tileInfo) {
        if (mCanChiTiles.size() > 1) {
            // 说明不只一种吃法, 需要弹出让user选择.
            mPlayerCallback.promptChi(new PlayerCanChi(Player.this, tileInfo,
                            mCanChiTiles.toArray(new CanChi[mCanChiTiles.size()])));
            return;
        }
        chi(mCanChiTiles.get(0), tileInfo);
    }

    public final void chi(final CanChi canChi, final TileInfo tileInfo) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                mCanChiTiles.clear();
                // 从活牌中删掉这些牌.
                removeFromAliveTiles(canChi.twoTiles);

                tileInfo.tileState = TileState.TileGot;
                // 把这些牌加入吃牌
                mChiedTiles.add(new Chied(canChi.twoTiles, tileInfo, canChi.position));
                tileInfo.finalLocation = mLocation;
                actionDone(Action.Chi, tileInfo);
            }
        });
    }

    // 从活牌中删掉一张牌. 并返回这张牌， 当吃牌时.
    private Tile removeTileFromAlive(Tile inputTile) {
        synchronized(mTiles) {
            Tile foundTile = null;
            for (Tile tile : mTiles) {
                if (tile.isSameTile(inputTile)) {
                    foundTile = tile;
                    break;
                }
            }
            if (foundTile != null) {
                mTiles.remove(foundTile);
            }
            return foundTile;
        }
    }

    // 从活牌中删掉这些牌. 当吃/碰/杠牌时.
    private void removeFromAliveTiles(Tile...tiles) {
        synchronized(mTiles) {
            for (Tile tile : tiles) {
                removeTileFromAlive(tile);
            }
        }
    }

    protected final CanGangTile[] getCanGangTiles(final TileInfo tileInfo) {
        ArrayList<CanGangTile> tempList = new ArrayList<CanGangTile>(mCanGangTiles.size());
        boolean isSameTile;
        for (CanGangTile canGangTile : mCanGangTiles) {
            isSameTile = canGangTile.tile.isSameTile(tileInfo.tile);
            switch (canGangTile.canGangType) {
                case GangBlack:
                    // 自己碰过之后可以暗杠. 自己摸的牌也可以选择暗杠.
                    if (isFromSelf(tileInfo) || tileInfo.tileState == TileState.TileGot) {
                        tempList.add(canGangTile);
                    }
                    break;
                case Gang3_1:
                    if (isSameTile) {// 之前已经摸到了3张，现在摸到了第4张.
                        tempList.add(canGangTile);
                    }
                    break;
                case GangPenged:
                    if (isSameTile && isFromSelf(tileInfo)) {
                        tempList.add(canGangTile);// 3张碰牌，第4张刚摸到.
                    } else if (getTileCount(canGangTile.tile) == 1) {
                        tempList.add(canGangTile);// 3张碰牌，第4张已经在手里.
                    }
                    break;
            }
        }
        return tempList.toArray(new CanGangTile[tempList.size()]);
    }

    protected void actionGang(final TileInfo tileInfo) {
        CanGangTile[] canGangTiles = getCanGangTiles(tileInfo);
        if (canGangTiles.length > 1) {
            // 说明不只一个杠, 需要弹出让user选择.
            mPlayerCallback.promptGang(tileInfo, canGangTiles);
            return;
        }
        CanGangTile canGangTile = canGangTiles[0];
        gang(canGangTile, tileInfo);
    }

    private void actionGangGrab(final TileInfo tileInfo) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                // TODO: Not implemented.
                actionDone(Action.GangGrab, tileInfo);
            }
        });
    }

    public void gang(final CanGangTile canGangTile, final TileInfo tileInfo) {
        gang(canGangTile, tileInfo, true);
    }

    private void gang(final CanGangTile canGangTile, final TileInfo tileInfo,
                    final boolean notifyActionDone) {
        final Tile tile = canGangTile.tile;
        final Game game = MahjongManager.getInstance().getGame();
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                if (mNewTile != null && !mNewTile.isSameTile(tile)) {
                    addTile(mNewTile); // 新tile还没有加入liveTiles.
                    setNewTile(null);
                }
                switch (canGangTile.canGangType) {
                    case GangBlack:
                        gangBlackTile();
                        break;
                    case Gang3_1:
                        gang3_1Tile();
                        break;
                    case GangPenged:
                        gangPengedTile();
                        break;
                    default:
                        return;
                }
                mCanGangTiles.remove(canGangTile);

                game.gangIncrease();

                if (notifyActionDone) {
                    switch (canGangTile.canGangType) {
                        case GangBlack:
                        case Gang3_1:
                            actionDone(Action.Gang, tileInfo);
                            break;
                        case GangPenged:
                            actionDone(Action.Gang, new TileInfo(tile, Player.this, false));
                            break;
                        default:
                            return;
                    }
                }
            }

            private void gangBlackTile() {
                final Tile[] tiles = findSameTiles(tile);
                // 从活牌中删掉这些牌, 如果有的话.
                removeFromAliveTiles(tiles);
                if (tiles.length == 4) {
                    // 已经存在的暗杠, 把这些牌加入杠牌
                    mGangs.add(new Ganged(tiles));
                    return;
                }
                if (tiles.length == 3) {
                    gangBlack1_3(tiles);
                }
            }

            private void gangPengedTile() {
                final Penged penged;
                synchronized(mPengs) {
                    penged = findPenged(tile);
                    if (penged == null) {
                        mPlayerCallback.showCallstack(new RuntimeException(Action.Gang.toString() + "," + GangType.GangPenged), tile.toString() + "," + tileInfo.toString());
                        return;
                    }
                    // 从碰牌中删掉这些牌.
                    mPengs.remove(penged);
                }
                // 从活牌中删掉这张牌, 如果有的话.
                removeFromAliveTiles(tile);
                synchronized (mGangs) {
                    // 把这些牌加入杠牌.
                    mGangs.add(new Ganged(penged, tile));
                }
            }

            private void gang3_1Tile() {
                final Tile[] tiles = findSameTiles(tile, 3);
                // 从活牌中删掉这些牌, 如果有的话.
                removeFromAliveTiles(tiles);

                if (tileInfo.isThrown() && tileInfo.tile.isSameTile(tile)) {
                    tileInfo.tileState = TileState.TileGot;
                    tileInfo.finalLocation = mLocation;
                    // 明杠，把这些牌加入杠牌
                    mGangs.add(new Ganged(tiles, tileInfo));
                } else {
                    gangBlack1_3(tiles);
                }
            }

            private void gangBlack1_3(final Tile[] tiles) {
                final Tile[] tiles4 = new Tile[4];
                for (int i = 0; i < tiles.length; i++) {
                    tiles4[i] = tiles[i];
                }
                tiles4[3] = tile;
                // 新摸到的杠牌, 把这些牌加入杠牌
                mGangs.add(new Ganged(tiles4));
            }
        });
    }

    private Penged findPenged(final Tile tile) {
        synchronized(mPengs) {
            for (Penged penged : mPengs) {
                if (penged.externalTile.tile.isSameTile(tile)) return penged;
            }
        }
        return null;
    }

    // 从活牌中找出n张同样的牌
    private Tile[] findSameTiles(final Tile tile, final int n) {
        synchronized (mTiles) {
            if (n <= 0 || n > mTiles.size()) {
                throw new IllegalArgumentException("Invalid n:" + n);
            }
            Tile[] tiles = new Tile[n];
            int i = 0;
            for (Tile liveTile : mTiles) {
                if (liveTile.isSameTile(tile)) {
                    tiles[i++] = liveTile;
                }
                if (i == n) break;
            }
            if (i < n) return null;
            return tiles;
        }
    }

    // 从活牌中找出所有同样的牌
    private Tile[] findSameTiles(final Tile tile) {
        synchronized (mTiles) {
            if (mTiles.size() <= 0) {
                throw new IllegalArgumentException("No tile(s)?!");
            }
            final ArrayList<Tile> tileList = new ArrayList<Tile>(Tile.MAX_TILE_COUNT);
            for (Tile liveTile : mTiles) {
                if (liveTile.isSameTile(tile)) {
                    tileList.add(liveTile);
                }
            }
            return tileList.toArray(new Tile[tileList.size()]);
        }
    }

    private void actionPeng(final TileInfo tileInfo) {
        final int liveSameCount = 2;
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                final Tile[] tiles = findSameTiles(tileInfo.tile, liveSameCount);
                if (tiles == null) {
                    mPlayerCallback.showCallstack(new RuntimeException("Failed to Peng tile!"), tileInfo.toString());
                    return;
                }
                // 从活牌中删掉这些牌.
                synchronized(mTiles) {
                    removeFromAliveTiles(tiles);
                }
                // 把这些牌加入碰牌中.
                synchronized(mPengs) {
                    mPengs.add(new Penged(tiles, tileInfo));
                }
                tileInfo.finalLocation = mLocation;
                tileInfo.tileState = TileState.TileGot;

                // 从canPengTiles中删掉这张牌.
                synchronized(mCanPengTiles) {
                    for (Tile tile : mCanPengTiles) {
                        if (tile.isSameTile(tileInfo.tile)) {
                            mCanPengTiles.remove(tile);
                            break;
                        }
                    }
                }

                actionDone(Action.Peng, tileInfo);
            }
        });
    }

    public boolean has13Tiles() {
        synchronized (mTiles) {
            return mTiles.size() == 13;
        }
    }

    public void setTiles(final Tile[] tiles) {
        synchronized (mTiles) {
            mTiles.clear();
            for (Tile tile : tiles) {
                if (tile != null) {
                    mTiles.add(tile);
                }
            }
        }
    }

    public void handleRemoteMessage(MessageInfo messageInfo) {
        final RemoteMessage remoteMessage = RemoteMessage.parse(messageInfo);
        Constants.debug("Received for WifiPlayer[" + name + "]," + remoteMessage);
        switch (remoteMessage.connMessage) {
            case MSG_PLAYER_ADD_TILE:
                MsgPlayerAddTile playerAddTile = parsePlayerAddTile(remoteMessage.content);
                addNewTile(playerAddTile.newTile, playerAddTile.gangedTileInfo, false);
                break;
            case MSG_REQUEST_START_PLAYING: // 收到从slave player发来的请求.
            case MSG_START_PLAYING: // 收到master player发来的命令.
                startPlaying(false);
                break;
            case MSG_SET_IGNORED_TYPE:
                setIgnoredType(MessageUtils.parseIgnoredType(remoteMessage.content), true);
                break;
            case MSG_PLAYER_THREAD_NOTIFY:
                playerThreadNotify(true);
                break;
            case MSG_DETERMINE_IGNORED_TYPE:
                doDetermineIgnored(false);
                break;
            case MSG_PLAYER_READY_TO_THROW:
                whenReadyToThrow(System.currentTimeMillis(), false);
                break;
            case MSG_PLAYER_THREW_TILE:
                throwSelected(Tile.parse((String)remoteMessage.content), true);
                break;
            case MSG_PLAYER_TAKE_ACTION:
                MessageUtils.ActionInfo remoteActionInfo = MessageUtils.parseActionInfo(
                                remoteMessage.content);
                takeAction(remoteActionInfo.action, remoteActionInfo.tileInfo, true);
                break;
            case MSG_PLAYER_ACTIONS_IGNORED:
                MessageUtils.ActionsIgnoredInfo actionsIgnored = MessageUtils
                                .parsePlayerActionsIgnored(remoteMessage.content);
                actionsIgnored(actionsIgnored.tileInfo, actionsIgnored.actions);
                break;
            default:
                Constants.debug("Unknown MSG for WifiPlayer[" + name + "]," + remoteMessage.connMessage);
                break;
        }
    }

    // Format: newTile x gangedTileInfo
    private static final String SEPARATOR_ADD_TILE = "x";
    protected static String messageAddTile(Tile newTile, TileInfo gangedTileInfo) {
        StringBuilder sb = new StringBuilder(newTile.toString());
        sb.append(SEPARATOR_ADD_TILE);
        if (gangedTileInfo != null) {
            sb.append(gangedTileInfo.tileInfoString());
        }
        return sb.toString();
    }

    private static class MsgPlayerAddTile {
        public final Tile newTile;
        public final TileInfo gangedTileInfo;

        public MsgPlayerAddTile(Tile newTile, TileInfo gangedTileInfo) {
            this.newTile = newTile;
            this.gangedTileInfo = gangedTileInfo;
        }
    }

    private static MsgPlayerAddTile parsePlayerAddTile(final Object messageContent) {
        if (messageContent == null || !(messageContent instanceof String)) {
            throw new RuntimeException("Invalid argument!Content NOT string:\n" + messageContent);
        }
        String[] array = ((String)messageContent).split(SEPARATOR_ADD_TILE);

        Tile newTile = Tile.parse(array[0].trim());
        TileInfo gangedTileInfo = null;
        if (array.length >= 2) {
            gangedTileInfo = TileInfo.parseTileInfoString(array[1].trim());
        }

        return new MsgPlayerAddTile(newTile, gangedTileInfo);
    }

    private static final String SEPARATOR_TILES_STRING = "%";
    private static final String SEPARATOR_TILES = ";";
    public String tilesString() {
        synchronized(mTiles) {
            StringBuilder sb = new StringBuilder();
            sb.append(MessageUtils.getPlayerIp(this)).append(SEPARATOR_TILES_STRING);
            sb.append(name).append(SEPARATOR_TILES_STRING);
            for (Tile tile : mTiles) {
                sb.append(tile.toString()).append(SEPARATOR_TILES);
            }
            return sb.toString();
        }
    }

    public static PlayerTiles parsePlayerTiles(String playerTilesString) {
        String[] array = playerTilesString.split(SEPARATOR_TILES_STRING);
        String ip = array[0].trim();
        String name = array[1].trim();
        String[] tileStringArray = array[2].trim().split(SEPARATOR_TILES);
        Tile[] tiles = new Tile[tileStringArray.length];
        for (int i = 0; i < tiles.length; i++) {
            try {
                tiles[i] = Tile.parse(tileStringArray[i].trim());
            } catch (Exception e) {
                tiles[i] = null;
            }
        }
        return new PlayerTiles(ip, name, tiles);
    }

    // 记录对获得的或者扔出的tile， 当前player可以有的action(s)以及action(s)的处理情况.
    public static class ActionInfo {
        private final ArrayList<Action> mActionList = new ArrayList<Action>();

        private volatile Action mActionDone; // 记录已经执行的action，以便决定下一步行动.

        public volatile boolean actionCollected;

        public void initActionInfo() {
            actionCollected = false;
            mActionDone = null;
            //tileInfo = null;
            clear();
        }

        public void clear() {
            synchronized(mActionList) {
                mActionList.clear();
            }
        }

        public void clear(final Action excludedAction) {
            synchronized(mActionList) {
                boolean containsExcluded = mActionList.contains(excludedAction);
                mActionList.clear();
                if (containsExcluded) {
                    mActionList.add(excludedAction);
                }
            }
        }

        public Action getDoneAction() {
            return mActionDone;
        }

        public void doneAction(Action action) {
            mActionDone = action;
            // 执行一个action后其他action都不能做了.
            clear();
        }

        public void ignoreActions(final Action...actions) {
            if (actions == null || actions.length <= 0) return;
            synchronized(mActionList) {
                for (Action action : actions) {
                    mActionList.remove(action);
                }
            }
        }

        public boolean hasActionDone() {
            return mActionDone != null;
        }

        public boolean hasPendingAction() {
            synchronized(mActionList) {
                return mActionList.size() > 0;
            }
        }

        private Action[] getActions() {
            synchronized(mActionList) {
                return mActionList.toArray(new Action[mActionList.size()]);
            }
        }

        public boolean isActionPending(final Action action) {
            synchronized(mActionList) {
                return mActionList.contains(action);
            }
        }

        public void addAction(final Action...actions) {
            synchronized (mActionList) {
                if (actions == null || actions.length <= 0) return;
                for (Action action : actions) {
                    mActionList.add(action);
                }
            }
        }
    }
}

