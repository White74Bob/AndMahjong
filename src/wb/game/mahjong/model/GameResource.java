package wb.game.mahjong.model;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import wb.game.mahjong.R;
import wb.game.mahjong.constants.HuConstants;
import wb.game.mahjong.constants.HuConstants.HuPattern;
import wb.game.mahjong.constants.HuConstants.HuType;
import wb.game.mahjong.constants.HuConstants.HuedType;
import wb.game.mahjong.constants.TileResources;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.Tile.HuTile;
import wb.game.mahjong.model.Tile.RemainedTileInfo;
import wb.game.mahjong.model.Tile.TileInfo;
import wb.game.utils.Utils;

public class GameResource {
    // 杠有以下几种：
    // 1. 4张都是自己摸来 ，                为暗杠； - GangedBlack
    // 2. 3张自己摸来，一张别人打出，为明杠； - Ganged3_1
    // 3. 碰出来的牌，自己摸来第4张，为明杠； - GangedPeng
    // 一人最多4杠;
    public static enum GangType {
        GangBlack,  // 暗杠
        GangPenged, // 碰杠
        Gang3_1;    // 明杠
    }

    public enum Action {
        GetTile(-1),                                   // 摸牌
        ThrowTile(-1),                                 // 打牌
        DetermineIgnored(R.string.action_determine_ignored), // 定缺,只存在于血战到底/血流成河...

        Gang(R.string.action_gang), // 杠, 暗杠/碰杠/明杠
        GangGrab(R.string.action_gang_grab, R.layout.prompt_action_item_2), // 抢杠.
        GangFlower(R.string.action_gang_flower, R.layout.prompt_action_item_4), // 杠上开花
        Peng(R.string.action_peng), // 碰
        Chi(R.string.action_chi), // 吃
        Ting(R.string.action_ting), // 听牌
        Hu(R.string.action_hu); // 胡

        public final int actionResId;

        public final int actionLayoutId;

        private Action(int labelResId) {
            this(labelResId, R.layout.prompt_action_item_1);
        }

        private Action(int labelResId, int actionLayoutId) {
            this.actionResId = labelResId;
            this.actionLayoutId = actionLayoutId;
        }

        // 需要用户判断的action为可见action
        // 摸牌/打牌，always invisible因为不由用户判断.
        public boolean isVisible() {
            return actionResId > 0;
        }

        public static Action getAction(final int actionOrder) {
            for (Action action : values()) {
                if (action.ordinal() == actionOrder) return action;
            }
            return null;
        }

        private static final String FORMAT_DEFAULT_ACTION_STRING = "%s %s";
        private static final String FORMAT_HU_ACTION_STRING = "%s %s\n%s";
        private static final String FORMAT_PLAYER_ACTION = "%s %s";

        public static String getActionString(Context context, Player player, Action action,
                        TileInfo tileInfo) {
            String actionString;
            if (action.isVisible() ) {
                if (player.name.equals(tileInfo.fromWhom)) {
                    actionString = String.format(FORMAT_DEFAULT_ACTION_STRING,
                                    context.getString(action.actionResId),
                                    tileInfo.tile.toString());
                } else {
                    actionString = String.format(FORMAT_DEFAULT_ACTION_STRING,
                                context.getString(action.actionResId), tileInfo.toString());
                }
            } else {
                actionString = String.format(FORMAT_DEFAULT_ACTION_STRING,
                                action.toString(), tileInfo.toString());
            }
            switch (action) {
                case Hu:
                    if (player.name.equals(tileInfo.fromWhom)) { // 自摸.
                        String strAction = null;
                        String strTile = tileInfo.tile.toString();
                        HuPattern huPattern = tileInfo.getHuPattern(player);

                        if (tileInfo.gangedTileInfo == null) {
                            strAction = context.getString(R.string.hu_self);
                        } else {
                            strAction = context.getString(R.string.action_gang_flower);
                        }

                        if (huPattern == null) {
                            HuTile[] huTiles = tileInfo.getHuTiles(player);
                            if (huTiles != null && huTiles.length > 0) {
                                actionString = String.format(FORMAT_HU_ACTION_STRING, strAction,
                                                strTile, getHuTilesInfo(huTiles));
                            }
                        } else {
                            actionString = String.format(FORMAT_HU_ACTION_STRING, strAction,
                                            strTile, huPattern.toString());
                        }
                    }
                    break;
                case GangFlower:
                    actionString = String.format(FORMAT_HU_ACTION_STRING,
                                    context.getString(R.string.action_gang_flower),
                                    tileInfo.toString(), tileInfo.getHuPattern(player).toString());
                    break;
                case Gang: // 暗杠不显示tile info.
                    if (player.isBlackGanged(tileInfo.tile)) {
                        actionString = context.getString(R.string.gang_black);
                    }
                    break;
                default:
                    break;
            }
            return String.format(FORMAT_PLAYER_ACTION, player.name, actionString);
        }

        private static String getHuTilesInfo(final HuTile[] huTiles) {
            StringBuilder sb = new StringBuilder();
            for (HuTile huTile : huTiles) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(huTile.toString());
            }
            return sb.toString();
        }
    }

    public interface DistributeTileListener {
        void needShowTile();
        void refreshGame();
    }

    private static Tile[] sTiles; // 所有牌.

    public static void setTiles(Game game) {
        sTiles = Tile.getTiles(game); // 不同玩法的牌集不同.
    }

    public static enum BankerSelect {
        Winner, // 赢家坐庄
        Next;   // 下家坐庄

        public static BankerSelect getBankerSelect(final int index) {
            for (BankerSelect bankerSelect : values()) {
                if (bankerSelect.ordinal() == index) return bankerSelect;
            }
            return Winner;
        }
    }

    // 有些打法需要亮一张牌.
    public static enum ShowTile {
        LastTile(-1, true), // 亮最后一张
        No54Tile(Player.TILE_NUM_NORMAL * 4 + 1); // 庄家摸完后下一张

        // 表示亮出来的牌还能不能摸.
        // true表示还能摸. false则需要从活牌中去掉.
        public final boolean keepTile;

        public final int tileIndex; // 亮出来那张牌的位置。-1表示最后一张.

        private ShowTile(final int tileIndex) {
            this(tileIndex, false);
        }

        private ShowTile(final int tileIndex, final boolean keepTile) {
            this.tileIndex = tileIndex;
            this.keepTile = keepTile;
        }
    }

    public static abstract class Game {
        private static final String KEY_BANKER_SELECT = "banker_select";

        public final int labelResId;
        public final String introductionFilename;
        public final int settingsLayoutResId;

        private final String mSettingsName; // 每种打法的sharedPreferences名.

        private final HuedType mHuedType; // 默认只能胡一次.
        private final ShowTile mShowTile; // 是否需要亮一张牌出来;
        private final boolean mGameOnlyHuSelf; // 是否只能自摸. 这个是游戏规则决定的.

        private final Tile[] mAvailableTiles;
        private final RemainedTileInfo[] mRemainedTiles;

        private int mCircleCount;

        private int mGangCount;
        private int mMaxGangCount = -1; // -1表示不限制杠数.

        private BankerSelect mBankerSelect;

        private RadioGroup mRadioGroupBankerSelect;

        private int mLeastRemainingTileNum = 0; // 最少剩余牌数.

        // 北京麻将中有个huiEr牌（混儿，会儿，惠儿），可以当作任何牌用.
        private Tile mMatchAllTile;

        protected final Action[] sAlwaysValidActions = {
                        Action.GetTile, Action.ThrowTile, Action.Hu,
        };

        protected final ArrayList<Tile> mLiveTiles = new ArrayList<Tile>();

        public boolean isMasterGame = true;

        // 以下是为了remote Manager中的game服务的，在banker端不应起作用.
        private int mLiveTileNum;
        private Tile mShownTile;

        public Game(int labelResId, String introductionFilename, int settingsLayoutResId,
                        HuedType huedType, final String settingsName) {
            this(labelResId, introductionFilename, settingsLayoutResId, huedType, settingsName,
                            null, false);
        }

        public Game(int labelResId, String introductionFilename, int settingsLayoutResId,
                        HuedType huedType, final String settingsName, ShowTile showTile,
                        boolean onlyHuSelf) {
            this.labelResId = labelResId;
            this.introductionFilename = introductionFilename;
            this.settingsLayoutResId = settingsLayoutResId;

            mSettingsName = settingsName;

            mHuedType = huedType;
            mShowTile = showTile;
            mGameOnlyHuSelf = onlyHuSelf;

            mAvailableTiles = gameAvailableTiles();
            mRemainedTiles = new RemainedTileInfo[mAvailableTiles.length];
            initRemainedTilesCount();
        }

        public Tile distributeTile(final boolean fromEnd,
                        DistributeTileListener distributeTileListener) {
            Tile tile = null;
            synchronized(mLiveTiles) {
                final int count = mLiveTiles.size();
                if (count <= 0) return null;
                if (fromEnd) {
                    tile = mLiveTiles.remove(count - 1);
                    if (mShowTile == ShowTile.LastTile && distributeTileListener != null) {
                        // 杠后如果game还要求显示最后一张牌，send msg to the main thread.
                       // notifyMainThread(Constants.UIMessage.MSG_SHOW_LAST_TILE,
                       //         getLastTile());
                        distributeTileListener.needShowTile();
                    }
                } else {
                    tile = mLiveTiles.remove(0);
                }
            }
            //sendMessageToMainThread(Constants.UIMessage.MSG_GAME_REFRESH);
            if (distributeTileListener != null) {
                distributeTileListener.refreshGame();
            }

            return tile;
        }

        // 获得最小剩余牌数.
        public int getLeastRemainingTileNum() {
            return mLeastRemainingTileNum;
        }

        protected final void setLeastRemainingTileNum(int leastRemainingTileNum) {
            mLeastRemainingTileNum = leastRemainingTileNum;
        }

        public void washTiles() {
            synchronized (mLiveTiles) {
                mLiveTiles.clear();
                do {
                    int randomIndex = Utils.getRandomInt(0, sTiles.length - 1);
                    Tile tile = sTiles[randomIndex];
                    if (!mLiveTiles.contains(tile)) {
                        tile.init();
                        mLiveTiles.add(tile);
                    }
                } while (mLiveTiles.size() != sTiles.length);
            }
        }

        public int getRemainingTileNum() {
            if (isMasterGame) {
                synchronized (mLiveTiles) {
                    return mLiveTiles.size();
                }
            }
            return mLiveTileNum;
        }

        public void setLiveTileNum(int liveTileNum) {
            if (isMasterGame) {
                throw new RuntimeException("Invalid calling! Master game need NOT do this!");
            }
            mLiveTileNum = liveTileNum;
        }

        public void setShownTile(Tile shownTile) {
            if (isMasterGame) {
                throw new RuntimeException("Invalid calling! Master game need NOT do this!");
            }
            mShownTile = shownTile;
        }

        private void initRemainedTilesCount() {
            int i = 0;
            for (Tile tile : mAvailableTiles) {
                if (mRemainedTiles[i] == null) {
                    mRemainedTiles[i] = new RemainedTileInfo(tile);
                } else {
                    mRemainedTiles[i].count = Tile.MAX_TILE_COUNT;
                }
                i++;
            }
        }

        // 新game开始时要对一些设置初始化.
        public void restart() {
            // 初始化剩余牌(不包括拿在players手中的牌，因为这些牌看不到)的信息.
            initRemainedTilesCount();

            mCircleCount = 0; // 重新开始数圈.
            mGangCount = 0;
            setMatchAllTile(null);
        }

        public boolean pengActionAvailable() {
            return true;
        }

        public final boolean isFirstCircle() {
            return mCircleCount == 1;
        }

        // 怎么计算圈数....决定什么时候调用这个method.
        public final void circleIncrease() {
            mCircleCount++;
        }

        public void gangIncrease() {
            mGangCount++;
        }

        public final int getGangCount() {
            return mGangCount;
        }

        public final int getMaxGangCount() {
            return mMaxGangCount;
        }

        public final boolean isMaxGangCountReached() {
            return mMaxGangCount >= 3 && mGangCount == mMaxGangCount;
        }

        protected final void setMaxGangCount(final int maxGangCount) {
            mMaxGangCount = maxGangCount;
        }

        // 看看是不是没牌了.
        public boolean hasNoTile() {
            return getRemainingTileNum() <= mLeastRemainingTileNum;
        }

        public final BankerSelect getBankerSelect() {
            if (mBankerSelect == null) {
                mBankerSelect = BankerSelect.Winner;
            }
            return mBankerSelect;
        }

        // 检查是否players必须要做的都做完了.
        // 需要子类重载，比如血流成河/血战到底都需要定缺门.
        // 默认返回true，说明没有什么需要在start之前做的.
        public boolean isNecessaryBeforeStartDone() {
            return true;
        }

        public void setNecessaryBeforeStartDone() {
            // TODO: Empty in the parent. Implemented in the child if necessary.
        }

        public void loadSettings(final Context context) {
            final SharedPreferences pref = context.getSharedPreferences(mSettingsName,
                            Context.MODE_PRIVATE);
            loadPreferences(context, pref);

            mBankerSelect = BankerSelect.getBankerSelect(pref.getInt(KEY_BANKER_SELECT, 0));
        }

        protected abstract void loadPreferences(final Context context, final SharedPreferences pref);

        public final void showSettings(final Context context) {
            Utils.showViewDialog(context, settingsLayoutResId, context.getString(labelResId),
                            new Utils.ViewInit() {
                                @Override
                                public void initViews(View rootView) {
                                    loadSettings(context);
                                    initSettingViews(context, rootView);
                                }
            }, new DialogInterface.OnClickListener() { // OK button listener
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    saveSettings(context);
                }
            }, new DialogInterface.OnClickListener() { // Cancel button listener
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        }

        private void initSettingViews(final Context context, final View rootView) {
            mRadioGroupBankerSelect = (RadioGroup)rootView.findViewById(R.id.radio_group_banker_select);
            switch (mBankerSelect) {
                case Next:
                    mRadioGroupBankerSelect.check(R.id.radio_banker_next);
                    break;
                case Winner:
                default:
                    mRadioGroupBankerSelect.check(R.id.radio_banker_winner);
                    break;
            }

            initChildSettingViews(context, rootView);
        }

        protected abstract void initChildSettingViews(final Context context, final View rootView);

        protected final void saveSettings(final Context context) {
            saveValues();

            SharedPreferences pref = context.getSharedPreferences(mSettingsName, Context.MODE_PRIVATE);
            Editor editor = pref.edit();
            savePreferences(context, editor);
            editor.apply();
        }

        private void saveValues() {
            switch (mRadioGroupBankerSelect.getCheckedRadioButtonId()) {
                case R.id.radio_banker_next:
                    mBankerSelect = BankerSelect.Next;
                    break;
                case R.id.radio_banker_winner:
                    mBankerSelect = BankerSelect.Winner;
                    break;
                default:
                    mBankerSelect = BankerSelect.Winner;
                    break;
            }
            saveChildValues();
        }

        protected abstract void saveChildValues();

        private void savePreferences(final Context context, final Editor editor) {
            editor.putInt(KEY_BANKER_SELECT, mBankerSelect.ordinal());

            saveChildPreferences(context, editor);
        }

        protected abstract void saveChildPreferences(final Context context, final Editor editor);

        public void setRemainedTileInfo(final Tile tile, final int count) {
            for (RemainedTileInfo remained : mRemainedTiles) {
                if (remained.tile.isSameTile(tile)) {
                    remained.count = count;
                }
            }
        }

        public int getRemainedTileCount(final Tile tile) {
            for (RemainedTileInfo remained : mRemainedTiles) {
                if (remained.tile.isSameTile(tile)) {
                    return remained.count;
                }
            }
            return 0;
        }

        public Tile[] getAvailableTiles() {
            return mAvailableTiles;
        }

        private Tile[] gameAvailableTiles() {
            ArrayList<Tile> tiles = new ArrayList<Tile>();
            for (TileType type : TileType.values()) {
                if (!isValidTileType(type)) continue;
                int typeTileNum = type.getCount();
                for (int i = 0; i < typeTileNum; i++) {
                    tiles.add(new Tile(type, i));
                }
            }
            return tiles.toArray(new Tile[tiles.size()]);
        }

        protected Action[] getValidActions() {
            Action[] gameValidActions = getGameValidActions();
            ArrayList<Action> validActions = new ArrayList<Action>(gameValidActions.length +
                            sAlwaysValidActions.length);
            for (Action action : sAlwaysValidActions) {
                validActions.add(action);
            }
            for (Action action : gameValidActions) {
                validActions.add(action);
            }
            return validActions.toArray(new Action[validActions.size()]);
        }

        public HuedType getHuedType() {
            return mHuedType;
        }

        // 是否需要显示一张牌.
        public ShowTile needShowTile() {
            return mShowTile;
        }

        public final Tile getShowTile() {
            if (mShowTile == null) return null;
            if (!isMasterGame) return mShownTile;
            synchronized (mLiveTiles) {
                Tile shownTile = null;
                if (mShowTile.tileIndex < 0) {
                    if (mShowTile.tileIndex == -1) {
                        shownTile = getLastTile();
                    }
                } else {
                    shownTile = mLiveTiles.get(mShowTile.tileIndex);
                }
                if (!mShowTile.keepTile && shownTile != null) {
                    mLiveTiles.remove(shownTile);
                }
                return shownTile;
            }
        }

        // 是否只能胡自摸.
        public boolean onlyHuSelf() {
            return mGameOnlyHuSelf;
        }
        // 是否支持抢杠.
        public boolean gangGrabSupported() {
            return false;
        }

        protected abstract Action[] getGameValidActions();

        public Tile getLastTile() {
            synchronized (mLiveTiles) {
                final int count = mLiveTiles.size();
                if (count <= 0) return null; // 没牌了.
                return mLiveTiles.get(count - 1);
            }
        }

        public Action[] getVisibleActions() {
            Action[] validActions = getValidActions();
            ArrayList<Action> visibleActions = new ArrayList<Action>(validActions.length);
            for (Action action : validActions) {
                if (action.isVisible()) {
                    visibleActions.add(action);
                }
            }
            return visibleActions.toArray(new Action[visibleActions.size()]);
        }

        public boolean isValidAction(Action action) {
            final Action[] validActions = getValidActions();
            for (Action validAction : validActions) {
                if (action == validAction) return true;
            }
            return false;
        }

        // 有些打法必须先报听才能胡牌.
        public final boolean mustReportTing() {
            Action[] visibleActions = getVisibleActions();
            for (Action action : visibleActions) {
                if (action == Action.Ting) return true;
            }
            return false;
        }

        public void setMatchAllTile(Tile inputTile) {
            mMatchAllTile = inputTile;
        }

        public final Tile getMatchAllTile() {
            return mMatchAllTile;
        }

        public final boolean isMatchAllTile(final Tile tile) {
            return mMatchAllTile != null && tile.isSameTile(mMatchAllTile);
        }

        public abstract boolean isValidTileType(TileType tileType);

        protected final int getTileCount(ArrayList<Tile> tiles, final TileType tileType) {
            int count = 0;
            for (Tile tile : tiles) {
                if (tile.tileType == tileType) {
                    count++;
                }
            }
            return count;
        }

        protected boolean isValidPattern(ArrayList<Tile> aliveTiles, ArrayList<Tile> gangTiles,
                        ArrayList<Tile> pengTiles, ArrayList<Tile> chiTiles,
                        final TileType ignoredTileType) {
            int chiNum = chiTiles.size();
            int gangNum = gangTiles.size() / 4 * 3;
            int pengNum = pengTiles.size();
            int aliveNum = aliveTiles.size();

            // 如果吃/碰/杠+活牌不是13张，一定不能胡.
            if (chiNum + pengNum + gangNum + aliveNum != 13) { return false; }
            if (ignoredTileType != null) {
                return getTileCount(aliveTiles, ignoredTileType) == 0;
            }
            return true;
        }

        private static void addTilesExclude(final ArrayList<Tile> destTiles,
                        final ArrayList<Tile> sourceTiles, final Tile excludedTile) {
            destTiles.clear();
            for (Tile tile : sourceTiles) {
                if (tile.isSameTile(excludedTile)) continue;
                destTiles.add(tile);
            }
        }

        // Refer to
        // http://www.cnblogs.com/kuangbin/archive/2012/10/27/2742985.html
        public void getHuTiles(final ArrayList<Tile> aliveTiles, final int setNum,
                        final ArrayList<Tile> huTiles, final Tile matchAllTile,
                        final int matchAllTileCount, final Tile[] availableTiles) {
            int aliveNum = aliveTiles.size();
            if (aliveNum <= 0 || aliveNum > 13) return;

            huTiles.clear();

            ArrayList<Tile> tiles = new ArrayList<Tile>(aliveNum + 1);
            if (matchAllTileCount <= 0) {
                tiles.addAll(aliveTiles);
                Tile.sort(tiles);
                checkHu(tiles, setNum, huTiles);
                return;
            }
            switch (matchAllTileCount) {
                case 1:
                    matchAllTile1(aliveTiles, setNum, huTiles, matchAllTile, availableTiles, tiles);
                    break;
                case 2:
                    matchAllTile2(aliveTiles, setNum, huTiles, matchAllTile, availableTiles, tiles);
                    break;
                case 3:
                    matchAllTile3(aliveTiles, setNum, huTiles, matchAllTile, availableTiles, tiles);
                    break;
                case 4: // 4个混儿牌都在手，北京麻将直接就胡了. 不知道其他打法是不是...
                    matchAllTile4(aliveTiles, setNum, huTiles, matchAllTile, availableTiles, tiles);
                    break;
                default:
                    return;
            }
        }

        private void matchAllTile1(final ArrayList<Tile> aliveTiles, final int setNum,
                        final ArrayList<Tile> huTiles, final Tile matchAllTile,
                        final Tile[] availableTiles, final ArrayList<Tile> tiles) {
            for (Tile tile : availableTiles) {
                addTilesExclude(tiles, aliveTiles, matchAllTile);
                tiles.add(tile);

                Tile.sort(tiles);
                checkHu(tiles, setNum, huTiles);
            }
        }

        private void matchAllTile2(final ArrayList<Tile> aliveTiles, final int setNum,
                        final ArrayList<Tile> huTiles, final Tile matchAllTile,
                        final Tile[] availableTiles, final ArrayList<Tile> tiles) {
            for (Tile tile1 : availableTiles) {
                for (Tile tile2 : availableTiles) {
                    addTilesExclude(tiles, aliveTiles, matchAllTile);
                    tiles.add(tile1);
                    tiles.add(tile2);

                    Tile.sort(tiles);
                    checkHu(tiles, setNum, huTiles);
                }
            }
        }

        private void matchAllTile3(final ArrayList<Tile> aliveTiles, final int setNum,
                        final ArrayList<Tile> huTiles, final Tile matchAllTile,
                        final Tile[] availableTiles, final ArrayList<Tile> tiles) {
            for (Tile tile1 : availableTiles) {
                for (Tile tile2 : availableTiles) {
                    for (Tile tile3 : availableTiles) {
                        addTilesExclude(tiles, aliveTiles, matchAllTile);
                        tiles.add(tile1);
                        tiles.add(tile2);
                        tiles.add(tile3);

                        Tile.sort(tiles);
                        checkHu(tiles, setNum, huTiles);
                    }
                }
            }
        }

        private void matchAllTile4(final ArrayList<Tile> aliveTiles, final int setNum,
                        final ArrayList<Tile> huTiles, final Tile matchAllTile,
                        final Tile[] availableTiles, final ArrayList<Tile> tiles) {
            for (Tile tile1 : availableTiles) {
                for (Tile tile2 : availableTiles) {
                    for (Tile tile3 : availableTiles) {
                        for (Tile tile4 : availableTiles) {
                            addTilesExclude(tiles, aliveTiles, matchAllTile);
                            tiles.add(tile1);
                            tiles.add(tile2);
                            tiles.add(tile3);
                            tiles.add(tile4);

                            Tile.sort(tiles);
                            checkHu(tiles, setNum, huTiles);
                        }
                    }
                }
            }
        }

        // 以下算法自己写的，因为已上getHuTiles不能判断正确所有case.
        public static void getHuTiles0(Game game, ArrayList<Tile> aliveTiles,
                        ArrayList<Tile> chiedTiles, ArrayList<Tile> pengedTiles,
                        ArrayList<Tile> gangedTiles, Tile[] publicGangedTiles,
                        final Tile[] availableTiles, final ArrayList<HuTile> canHuTiles) {
            canHuTiles.clear();

            int chiNum  = chiedTiles.size();
            int gangNum = gangedTiles.size() / 4 * 3;
            int pengNum = pengedTiles.size();
            int aliveNum = aliveTiles.size();
            // 如果吃/碰/杠+活牌不是13张，一定不能胡.
            if (aliveNum <= 0 || chiNum + pengNum + gangNum + aliveNum != 13) {
                return;
            }

            // 看看还需要成几副牌.
            //final int setNum = 4 - gangNum / 3 - pengNum / 3 - chiNum / 3;

            // liveTiles = 现在的活牌 + 每一张availableTile，然后检查能不能胡.
            final Tile[] liveTiles = new Tile[aliveTiles.size() + 1];
            for (int i = 0; i < liveTiles.length - 1; i++) {
                liveTiles[i] = aliveTiles.get(i);
            }

            final Tile[] chiTiles = chiNum > 0 ? chiedTiles.toArray(new Tile[chiNum]) : null;
            final Tile[] pengTiles = pengNum > 0 ? pengedTiles.toArray(new Tile[pengNum]) : null;
            final Tile[] gangTiles = gangNum > 0 ? gangedTiles.toArray(new Tile[gangNum]) : null;

            // 以下用枚举算法分别遍历available tiles.
            // 没有字时是34张
            // 血流成河等，没有风，所以是27张.
            HuPattern huPattern;
            for (Tile tile : availableTiles) {
                // 杠牌不能再胡...
                if (isTileGanged(tile, gangedTiles, publicGangedTiles)) continue;
                liveTiles[liveTiles.length - 1] = tile;
                huPattern = HuConstants.getHuPattern(liveTiles, chiTiles, pengTiles, gangTiles);
                if (huPattern != null) {
                    canHuTiles.add(new HuTile(tile, huPattern));
                }
            }
        }

        private static boolean isTileGanged(Tile tile, ArrayList<Tile> tiles,
                        Tile[] publicGangedTiles) {
            if (tile == null || tiles == null || tiles.size() <= 0) return false;
            for (Tile tileInList : tiles) {
                if (tileInList.isSameTile(tile)) return true;
            }
            for (Tile gangedTile : publicGangedTiles) {
                if (tile.isSameTile(gangedTile)) return true;
            }
            return false;
        }
    }

    public static final int sShowLatestGameCount = 3;

    private static final Game[] sGames = {
        new BloodRiver(),
        new FlowerAfterGang(),
        new Beijing(),
        new BloodBattle(),
        new TuidaoHu(),
    };

    public static int getGameIndex(final String settingsName) {
        for (int i = 0; i < sGames.length; i++) {
            if (sGames[i].mSettingsName.equals(settingsName)) {
                return i;
            }
        }
        throw new RuntimeException("Invalid game settingsName?!");
    }

    public static Game getGame(int index) {
        return sGames[index];
    }

    public static int getGameNum() {
        return sGames.length;
    }

    public static String getGameSettingsName(int index) {
        return sGames[index].mSettingsName;
    }

    // 血流成河
    public static class BloodRiver extends Game {
        protected static final Action[] sValidActions = {
                        Action.DetermineIgnored,
                        Action.Gang, Action.Peng, Action.Ting,
        };

        private boolean mIgnoredTypeDetermined; // 是否players都定了缺门.

        private boolean mAutoThrowAfterTing; // 听牌后自动打牌
        private boolean mAutoHuAfterTing; // 听牌后自动胡牌
        private boolean mAutoHuSelfAfterTing; // 听牌后自动胡自摸

        private CheckBox mCheckBoxAutoThrowAfterTing;
        private CheckBox mCheckBoxAutoHuAfterTing;
        private CheckBox mCheckBoxAutoHuSelfAfterTing;

        private static final String KEY_AUTO_THROW_TILE_AFTER_TING = "auto_throw_tile_after_ting";
        private static final String KEY_AUTO_HU_AFTER_TING = "auto_hu_after_tile";
        private static final String KEY_AUTO_HU_SELF_AFTER_TING = "auto_hu_after_tile";

        public BloodRiver() {
            super(R.string.game_blood_river,
                    "game_blood_river.txt",
                    R.layout.game_settings_blood_river, HuedType.HuMulti,
                    "game_blood_river");
        }

        protected BloodRiver(int labelResId, String introductionFilename, int settingsLayoutResId,
                        HuedType huedType, final String settingsName) {
            super(labelResId, introductionFilename, settingsLayoutResId, huedType, settingsName);
        }

        @Override
        public void restart() {
            super.restart();
            mIgnoredTypeDetermined = false;
        }

        @Override
        public boolean isNecessaryBeforeStartDone() {
            return mIgnoredTypeDetermined;
        }

        @Override
        public void setNecessaryBeforeStartDone() {
            mIgnoredTypeDetermined = true;
        }

        @Override
        protected void initChildSettingViews(final Context context, final View rootView) {
            mCheckBoxAutoThrowAfterTing = (CheckBox)rootView.findViewById(R.id.check_auto_throw_after_ting);
            mCheckBoxAutoThrowAfterTing.setChecked(mAutoThrowAfterTing);

            mCheckBoxAutoHuAfterTing = (CheckBox)rootView.findViewById(R.id.check_auto_hu_after_ting);
            mCheckBoxAutoHuAfterTing.setChecked(mAutoHuAfterTing);

            mCheckBoxAutoHuSelfAfterTing = (CheckBox)rootView.findViewById(R.id.check_auto_hu_self_after_ting);
            mCheckBoxAutoHuSelfAfterTing.setChecked(mAutoHuSelfAfterTing);
        }

        @Override
        protected void loadPreferences(final Context context, final SharedPreferences pref) {
            mAutoThrowAfterTing  =  pref.getBoolean(KEY_AUTO_THROW_TILE_AFTER_TING, false);
            mAutoHuAfterTing     =  pref.getBoolean(KEY_AUTO_HU_AFTER_TING, false);
            mAutoHuSelfAfterTing =  pref.getBoolean(KEY_AUTO_HU_SELF_AFTER_TING, false);
        }

        @Override
        protected void saveChildValues() {
            mAutoThrowAfterTing = mCheckBoxAutoThrowAfterTing.isChecked();
            mAutoHuAfterTing = mCheckBoxAutoHuAfterTing.isChecked();
            mAutoHuSelfAfterTing = mCheckBoxAutoHuSelfAfterTing.isChecked();
        }

        @Override
        protected void saveChildPreferences(final Context context, final Editor editor) {
            editor.putBoolean(KEY_AUTO_THROW_TILE_AFTER_TING, mAutoThrowAfterTing);
            editor.putBoolean(KEY_AUTO_HU_AFTER_TING, mAutoHuAfterTing);
            editor.putBoolean(KEY_AUTO_HU_SELF_AFTER_TING, mAutoHuSelfAfterTing);
        }

        @Override
        protected Action[] getGameValidActions() {
            return sValidActions;
        }

        @Override
        public boolean isValidTileType(TileType tileType) {
            return tileType != TileResources.TileType.Feng;
        }

        public boolean isAutoThrowAfterTing() {
            return mAutoThrowAfterTing; // 听牌后自动打牌
        }

        public boolean isAutoHuAfterTing() {
            return mAutoHuAfterTing; // 听牌后自动胡牌
        }

        public boolean isAutoHuSelfAfterTing() {
            return mAutoHuSelfAfterTing;
        }
    }

    // 血战到底
    public static class BloodBattle extends BloodRiver {
        public BloodBattle() {
            super(R.string.game_blood_battle,
                    "game_blood_battle.txt",
                    R.layout.game_settings_blood_battle, HuedType.HuOncePlayer,
                    "game_blood_battle");
        }
    }

    // 杠后花
    public static class FlowerAfterGang extends Game {
        private static final Action[] sValidActions = {
                        Action.Gang, Action.Peng, Action.Ting,
                        Action.GangFlower,
        };

        private boolean mAutoThrowAfterTing; // 听牌后自动打牌.
        private boolean mAutoHuAfterTing; // 听牌后自动胡牌.
        private boolean mAutoGangFlower; // 自动杠开.
        private boolean mMaxGang3; // 最多3杠.

        private CheckBox mCheckBoxAutoThrowAfterTing;
        private CheckBox mCheckBoxAutoHuAfterTing;
        private CheckBox mCheckBoxAutoGangFlower;
        private CheckBox mCheckBoxMaxGang3;

        private static final String KEY_AUTO_THROW_AFTER_TING = "auto_throw_after_ting";
        private static final String KEY_AUTO_HU_AFTER_TING = "auto_hu_after_ting";
        private static final String KEY_AUTO_GANG_FLOWER = "auto_gang_flower";
        private static final String KEY_MAX_GANG_3 = "max_gang_3";

        public FlowerAfterGang() {
            super(R.string.game_flower_after_gang,
                    "game_flower_on_gang.txt",
                    R.layout.game_settings_flower_on_gang, HuedType.HuOnceAll,
                    "game_flower_on_gang", ShowTile.LastTile, true);
        }

        @Override
        protected Action[] getGameValidActions() {
            return sValidActions;
        }

        // 自动翻出最后一张牌为明牌。
        // ·明牌为玩家杠牌所获得的那张。
        // ·明牌杠掉后，系统会自动再翻一张明牌。
        // ·明牌不能为东、南、西、北、中、发、白。翻牌为字牌，系统会自动与普通牌交换。
        @Override
        public Tile getLastTile() {
            synchronized (mLiveTiles) {
                final int count = mLiveTiles.size();
                if (count <= 0) return null; // 没牌了.
                int lastNotFengTileIndex = count - 1;
                Tile lastTile, lastNotFengTile;
                lastTile = lastNotFengTile = mLiveTiles.get(lastNotFengTileIndex);
                do {
                    if (lastNotFengTile.tileType != TileType.Feng) {
                        break;
                    }
                    if (lastNotFengTileIndex <= 0) {
                        lastTile = null;
                        break;
                    }
                    lastNotFengTile = mLiveTiles.get(--lastNotFengTileIndex);
                } while (true);
                // 交换两张牌的位置.
                if (lastNotFengTileIndex != count - 1) {
                    if (lastTile == null) return null; // 说明没有符合要求的牌了.
                    mLiveTiles.set(lastNotFengTileIndex, lastTile);
                    mLiveTiles.set(count - 1, lastNotFengTile);
                }
                return lastNotFengTile;
            }
        }

        @Override
        public boolean isValidTileType(TileType tileType) {
            return true;
        }

        @Override
        protected void initChildSettingViews(final Context context, final View rootView) {
            mCheckBoxAutoThrowAfterTing = (CheckBox)rootView.findViewById(R.id.check_auto_throw_after_ting);
            mCheckBoxAutoThrowAfterTing.setChecked(mAutoThrowAfterTing);

            mCheckBoxAutoHuAfterTing = (CheckBox)rootView.findViewById(R.id.check_auto_hu_after_ting);
            mCheckBoxAutoHuAfterTing.setChecked(mAutoHuAfterTing);

            mCheckBoxAutoGangFlower = (CheckBox)rootView.findViewById(R.id.check_auto_gang_flower);
            mCheckBoxAutoGangFlower.setChecked(mAutoGangFlower);

            mCheckBoxMaxGang3 = (CheckBox)rootView.findViewById(R.id.check_max_gang_3);
            mCheckBoxMaxGang3.setChecked(mMaxGang3);
        }

        @Override
        protected void loadPreferences(final Context context, final SharedPreferences pref) {
            mAutoThrowAfterTing = pref.getBoolean(KEY_AUTO_THROW_AFTER_TING, false);
            mAutoHuAfterTing    = pref.getBoolean(KEY_AUTO_HU_AFTER_TING,    false);
            mAutoGangFlower     = pref.getBoolean(KEY_AUTO_GANG_FLOWER,      false);

            mMaxGang3           = pref.getBoolean(KEY_MAX_GANG_3,            false);
            setMaxGangCount(mMaxGang3 ? 3 : -1);
        }

        @Override
        protected void saveChildValues() {
            mAutoThrowAfterTing = mCheckBoxAutoThrowAfterTing.isChecked();
            mAutoHuAfterTing = mCheckBoxAutoHuAfterTing.isChecked();
            mAutoGangFlower = mCheckBoxAutoGangFlower.isChecked();

            mMaxGang3 = mCheckBoxMaxGang3.isChecked();
            setMaxGangCount(mMaxGang3 ? 3 : -1);
        }

        @Override
        protected void saveChildPreferences(final Context context, final Editor editor) {
            editor.putBoolean(KEY_AUTO_THROW_AFTER_TING, mAutoThrowAfterTing);
            editor.putBoolean(KEY_AUTO_HU_AFTER_TING, mAutoHuAfterTing);
            editor.putBoolean(KEY_AUTO_GANG_FLOWER, mAutoGangFlower);
            editor.putBoolean(KEY_MAX_GANG_3, mMaxGang3);
        }

        public boolean isAutoThrowAfterTing() {
            return mAutoThrowAfterTing; // 听牌后自动打牌
        }

        public boolean isAutoHuAfterTing() {
            return mAutoHuAfterTing; // 听牌后自动胡牌
        }

        public boolean isAutoGangFlower() {
            return mAutoGangFlower; // 是否自动开杠.
        }
    }

    // 推倒胡
    public static class TuidaoHu extends Game {
        // 不带吃.
        private static final Action[] sAvailableActions0 = {
                        Action.Gang, Action.Peng,
        };
        // 支持吃牌.
        private static final Action[] sAvailableActions1 = {
                        Action.Gang, Action.Peng, Action.Chi
        };

        protected boolean mFengAvailable; // 是否支持风（东西南北中发白）
        private boolean mPlayerHuOnlySelf; // 只胡自摸的牌. 这个是user设定的.
        protected boolean mChiSupported; // 是否支持吃上家的牌.

        private CheckBox mCheckBoxFengAvailable;
        private CheckBox mCheckBoxHuOnlySelf;
        protected CheckBox mCheckBoxChiSupported;

        private static final String KEY_FENG_AVAILABLE = "feng_available";
        private static final String KEY_HU_ONLY_SELF = "hu_only_self";
        protected static final String KEY_CHI_SUPPORTED = "chi_supported";

        public TuidaoHu() {
            super(R.string.game_tuidaohu,
                    "game_tuidaohu.txt",
                    R.layout.game_settings_tuidaohu, HuedType.HuOnceAll,
                    "game_tuidaohu");
        }

        protected TuidaoHu(int labelResId, String introductionFilename, int settingsLayoutResId,
                        HuedType huedType, final String settingsName, ShowTile showTile,
                        boolean onlyHuSelf) {
            super(labelResId, introductionFilename, settingsLayoutResId, huedType, settingsName,
                            showTile, onlyHuSelf);
        }

        @Override
        protected Action[] getGameValidActions() {
            return mChiSupported ? sAvailableActions1 : sAvailableActions0;
        }

        @Override
        public boolean isValidTileType(TileType tileType) {
            if (mFengAvailable) return true;
            return tileType != TileResources.TileType.Feng;
        }

        @Override
        protected void initChildSettingViews(final Context context, final View rootView) {
            mCheckBoxFengAvailable = (CheckBox)rootView.findViewById(R.id.check_feng_available);
            mCheckBoxFengAvailable.setChecked(mFengAvailable);

            mCheckBoxHuOnlySelf = (CheckBox)rootView.findViewById(R.id.check_only_hu_self);
            mCheckBoxHuOnlySelf.setChecked(mPlayerHuOnlySelf);

            mCheckBoxChiSupported = (CheckBox)rootView.findViewById(R.id.check_chi_supported);
            mCheckBoxChiSupported.setChecked(mChiSupported);
        }

        @Override
        public void loadPreferences(final Context context, final SharedPreferences pref) {
            mFengAvailable = pref.getBoolean(KEY_FENG_AVAILABLE, false);
            mPlayerHuOnlySelf    = pref.getBoolean(KEY_HU_ONLY_SELF,   false);
            mChiSupported  = pref.getBoolean(KEY_CHI_SUPPORTED,  false);
        }

        @Override
        protected void saveChildValues() {
            mFengAvailable = mCheckBoxFengAvailable.isChecked();
            mPlayerHuOnlySelf = mCheckBoxHuOnlySelf.isChecked();
            mChiSupported = mCheckBoxChiSupported.isChecked();
        }

        @Override
        protected void saveChildPreferences(final Context context, final Editor editor) {
            editor.putBoolean(KEY_FENG_AVAILABLE, mFengAvailable);
            editor.putBoolean(KEY_HU_ONLY_SELF, mPlayerHuOnlySelf);
            editor.putBoolean(KEY_CHI_SUPPORTED, mChiSupported);
        }

        public boolean isFengAvailable() {
            return isValidTileType(TileType.Feng);
        }

        @Override
        public boolean onlyHuSelf() {
            return mPlayerHuOnlySelf || super.onlyHuSelf();
        }
    }

    // 北京麻将
    public static class Beijing extends TuidaoHu {
        private Tile mShownTile; // 混而坯.

        private boolean mPengAvailable;

        private CheckBox mCheckBoxPengAvailable;

        private static final String KEY_PENG_AVAILABLE = "peng_available";

        public Beijing() {
            super(R.string.game_beijing,
                    "game_beijing.txt",
                    R.layout.game_settings_beijing, HuedType.HuOnceAll,
                    "game_beijing", ShowTile.No54Tile, false);
            mFengAvailable = true;
        }

        @Override
        public void restart() {
            super.restart();

            setMaxGangCount(3); // 最多3杠.
            setLeastRemainingTileNum(6 * 2);
            mShownTile = null;
            mFengAvailable = true;
        }

        @Override
        public boolean pengActionAvailable() {
            return mPengAvailable;
        }

        @Override
        public boolean isValidTileType(TileType tileType) {
            return true;
        }

        @Override
        public void gangIncrease() {
            super.gangIncrease();
            setLeastRemainingTileNum(getLeastRemainingTileNum() - 2 + 1);
        }

        public void setShowTile(final Tile tile) {
            mShownTile = tile;
        }

        @Override
        protected void initChildSettingViews(final Context context, final View rootView) {
            mCheckBoxPengAvailable = (CheckBox)rootView.findViewById(R.id.check_peng_available);
            mCheckBoxPengAvailable.setChecked(mPengAvailable);

            mCheckBoxChiSupported = (CheckBox)rootView.findViewById(R.id.check_chi_supported);
            mCheckBoxChiSupported.setChecked(mChiSupported);
        }

        @Override
        public void loadPreferences(final Context context, final SharedPreferences pref) {
            mPengAvailable = pref.getBoolean(KEY_PENG_AVAILABLE, false);
            mChiSupported  = pref.getBoolean(KEY_CHI_SUPPORTED,  false);
        }

        @Override
        protected void saveChildValues() {
            mPengAvailable = mCheckBoxPengAvailable.isChecked();
            mChiSupported = mCheckBoxChiSupported.isChecked();
        }

        @Override
        protected void saveChildPreferences(final Context context, final Editor editor) {
            editor.putBoolean(KEY_PENG_AVAILABLE, mPengAvailable);
            editor.putBoolean(KEY_CHI_SUPPORTED, mChiSupported);
        }

        public void setSpecialTiles() {
            switch (mShownTile.tileType) {
                case Tiao:
                case Tong:
                case Wan:
                    setMatchAllTile(new Tile(mShownTile.tileType, (mShownTile.tileIndex + 1) % 9));
                    break;
                case Feng:
                    if (mShownTile.tileIndex <= 3) {
                        setMatchAllTile(new Tile(mShownTile.tileType, (mShownTile.tileIndex + 1) % 4));
                    } else {
                        int newTileIndex = (mShownTile.tileIndex + 1) % 7;
                        if (newTileIndex == 0) newTileIndex = 4;
                        setMatchAllTile(new Tile(mShownTile.tileType, newTileIndex));
                    }
                    break;
            }

            synchronized(mLiveTiles) {
                for (Tile tile : mLiveTiles) {
                    tile.isMatchAll = isMatchAllTile(tile);
                    if (tile.tileIndex == 4 && tile.tileType == TileType.Wan) {
                        tile.specialTile = Tile.SpecialTile.WuKui;
                    } else {
                        tile.specialTile = null;
                    }
                }
            }
        }
    }

    public static void init(Context context) {
        HuType.initLabels(context);
    }

    public static void checkHu(final ArrayList<Tile> tiles, final int setNum,
                    final ArrayList<Tile> huTiles) {
        (new HuCheckUtils()).huCheck(tiles, huTiles, setNum);
    }

    public static boolean isHued(final Tile[] tiles, final int setNum) {
        return (new HuCheckUtils()).isHued(tiles, setNum);
    }

    // 检测是否胡牌
    // Refer to
    // http://www.cnblogs.com/kuangbin/archive/2012/10/27/2742985.html
    static class HuCheckUtils {
        // 35 = 9(Tiao) + 9(Tong) + 9(Wan) + 7(Feng) + 1.
        private final int[] mTileCountArray = new int[35];

        private final int[] mTmp = new int[mTileCountArray.length];

        //  3个相同的牌或者顺子是一付牌. 看看有几组成牌，也就是计算有几付牌.
        private int judge4X3() {
            int setNum = 0;

            for (int i = 0; i < mTmp.length; i++) {
                mTmp[i] = mTileCountArray[i];
            }

            for (int i = 0; i <= 18; i += 9) {
                for (int j = 0; j < 9; j++) {
                    // 三张相同的算一付牌.
                    if (mTmp[i + j] >= 3) {
                        mTmp[i + j] -= 3;
                        setNum++;
                    }
                    // 三张连牌/顺子,是一付牌.
                    while (j + 2 < 9 && mTmp[i + j] > 0 && mTmp[i + j + 1] > 0
                                    && mTmp[i + j + 2] > 0) {
                        mTmp[i + j]--;
                        mTmp[i + j + 1]--;
                        mTmp[i + j + 2]--;
                        setNum++;
                    }
                }
            }
            for (int j = 0; j < 7; j++) {
                // 三张相同的风是一付牌.
                if (mTmp[27 + j] >= 3) {
                    mTmp[27 + j] -= 3;
                    setNum++;
                }
            }
            return setNum;
        }

        // 胡牌由一个对子 + 4组 3个相同的牌或者顺子组成.
        private boolean judge1(final int setNum) {
            for (int i = 0; i < 34; i++) {
                if (mTileCountArray[i] >= 2) {
                    // 枚举对子, 去掉这一对牌，看看剩下的牌能不能成n套牌.
                    mTileCountArray[i] -= 2;
                    if (judge4X3() == setNum) {
                        mTileCountArray[i] += 2;
                        return true;
                    }
                    mTileCountArray[i] += 2;
                }
            }
            return false;
        }

        // 判断是不是7对.
        // 每一种牌的数量要么是0，要么是2或4（两对），一定要是7个对子才可以.
        private boolean judge2() {
            int pairCount = 0;
            for (int i = 0; i < 34; i++) {
                if (mTileCountArray[i] == 2) {
                    pairCount++;
                    continue;
                }
                if (mTileCountArray[i] == 4) {
                    pairCount += 2;
                    continue;
                }
                if (mTileCountArray[i] != 0) {
                    return false;
                }
            }
            return pairCount == 7;
        }

        // 13幺, 而且仅有这13种牌。肯定是有一种2张。其他的1张。
        private boolean judge3() {
            for (int j = 0; j < 7; j++) {
                if (mTileCountArray[j + 27] == 0) {
                    return false;
                }
            }
            for (int i = 0; i <= 18; i += 9) {
                if (mTileCountArray[i] == 0 || mTileCountArray[i + 8] == 0) {
                    return false;
                }
                for (int j = 1; j < 8; j++) {
                    if (mTileCountArray[i + j] != 0) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean judge(final int setNum) {
            return (judge1(setNum) || judge2() || judge3());
        }

        private void huCheck(final ArrayList<Tile> tiles, final ArrayList<Tile> huTiles,
                        final int setNum) {
            int[] answers = new int[mTileCountArray.length];
            Arrays.fill(mTileCountArray, 0);

            int countIndex;
            for (Tile tile : tiles) {
                countIndex = tile.tileIndex;
                countIndex += getTileTypeIndexOffset(tile.tileType);
                mTileCountArray[countIndex]++;
            }
            int foundHuTileCount = 0;
            for (int i = 0; i < 34; i++) {
                mTileCountArray[i]++;
                if (mTileCountArray[i] <= Tile.MAX_TILE_COUNT && judge(setNum)) {
                    answers[foundHuTileCount++] = i;
                }
                mTileCountArray[i]--;
            }
            if (foundHuTileCount == 0) return;
            int index;
            TileType tileType;
            Tile newTile;
            for (int i = 0; i < foundHuTileCount; i++) {
                tileType = TileType.getTileType(answers[i] / 9);
                index = (answers[i] % 9);
                newTile = new Tile(tileType, index);
                if (!existingInList(huTiles, newTile)) {
                    huTiles.add(newTile);
                }
            }
        }

        // 判断是否是已知的牌，现在只是胡牌用到.
        private static boolean existingInList(ArrayList<Tile> tiles, Tile inputTile) {
            for (Tile tile : tiles) {
                if (tile.isSameTile(inputTile)) return true;
            }
            return false;
        }

        public boolean isHued(final Tile[] tiles, final int setNum) {
            Arrays.fill(mTileCountArray, 0);

            int countIndex;
            for (Tile tile : tiles) {
                countIndex = tile.tileIndex;
                countIndex += getTileTypeIndexOffset(tile.tileType);
                mTileCountArray[countIndex]++;
            }
            return judge(setNum);
        }
    }

    // Tiao - 0, Tong - 9, Wan - 18, Feng - 27.
    private static int getTileTypeIndexOffset(final TileType tileType) {
        int typeOrdinal = tileType.ordinal();
        int offset = 0;
        for (int i = 0; i < typeOrdinal; i++) {
            offset += TileType.getTileType(i).getCount();
        }
        return offset;
    }

    static class HuCheckUtils2 {
        // 胡牌的基础牌型：
        // (1)11、123、123、123、123
        // (2)11、123、123、123、111(1111，下同)
        // (3)11、123、123、111、111
        // (4)11、123、111、111、111
        // (5)11、111、111、111、111
        // 胡牌的特殊牌型：
        // (1) （七对）11、11、11、11、11、11、11
        // (2) 十三幺
        // 其中：1=单张　11=将、对子　111=刻子　1111=杠　123=顺子
    }
}
