package wb.game.mahjong.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import wb.game.mahjong.MahjongManager.Location;
import wb.game.mahjong.R;
import wb.game.mahjong.constants.HuConstants.HuPattern;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.GameResource.Game;

public class Tile {
    private static final ColorMatrixColorFilter sGrayColorMatrixFilter;

    private static final float[] sAlphaMatrixValues = {
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 1, 0
    };
    private static final ColorMatrixColorFilter sTileKeptAfterTingColorMatrixFilter;

    private static final ColorMatrixColorFilter sTileChiedColorMatrixFilter;

    private static final ColorMatrixColorFilter sTileGangBlackColorMatrixFilter;

    private static final ColorMatrixColorFilter sBlackColorMatrixFilter;

    private static final ColorMatrixColorFilter sHuSelfColorMatrixFilter;
    private static final ColorMatrixColorFilter sHuOtherColorMatrixFilter;
    private static final ColorMatrixColorFilter sHuOtherSharedColorMatrixFilter;

    public static void initResources(final Context context) {
        TileInfo.sFormatToString = context.getString(R.string.format_tile_info);
        TileInfo.sFormatGangToString = context.getString(R.string.format_tile_info_gang);
    }

    public static class TileCount {
        public final Tile tile;
        public int count;

        public TileCount(final Tile tile) {
            this.tile = tile;
            count = 1;
        }
    }

    public static class RemainedTileInfo {
        public final Tile tile;
        public int count;

        public RemainedTileInfo(final Tile tile) {
            this.tile = tile;
            count = MAX_TILE_COUNT;
        }
    }

    // 这个类记录player胡了什么牌型.
    public static class HuedInfo {
        public final Player player;
        public final HuPattern huPattern;
        public final HuTile[] huTiles;

        public HuedInfo(Player player, HuPattern huPattern) {
            this.player = player;
            this.huPattern = huPattern;
            this.huTiles = null;
        }

        public HuedInfo(Player player, HuTile[] huTiles) {
            this.player = player;
            this.huTiles = huTiles;
            this.huPattern = null;
        }
    }

    public static enum TileState {
        TileNew,
        TileThrown,
        TileGot;

        public static TileState getTileState(int ordinal) {
            for (TileState tileState : values()) {
                if (ordinal == tileState.ordinal()) return tileState;
            }
            return null;
        }
    }

    public static class TileInfo {
        public final Tile tile;

        public final String fromWhom;
        public final Location fromWhere;
        public Location finalLocation;

        public TileState tileState;

        public TileInfo gangedTileInfo; // 如果是杠后拿到的牌，需要记录那张杠牌.

        // 一张牌最多3家胡.
        private final ArrayList<HuedInfo> mHuedPlayers = new ArrayList<HuedInfo>(3);

        public TileInfo(Tile tile, Player player, boolean isThrown) {
            this(tile, player.name, player.getLocation(), isThrown);
        }

        public TileInfo(Tile tile, String fromWhom, Location fromWhere, boolean isThrown) {
            this(tile, fromWhom, fromWhere, isThrown ? TileState.TileThrown : TileState.TileNew);
        }

        public TileInfo(Tile tile, String fromWhom, Location fromWhere, TileState tileState) {
            this.tile = tile;
            this.fromWhom = fromWhom;
            this.fromWhere = fromWhere;
            this.tileState = tileState;
        }

        protected TileInfo(TileInfo inputTileInfo) {
            this(inputTileInfo.tile, inputTileInfo.fromWhom, inputTileInfo.fromWhere,
                            inputTileInfo.tileState);
        }

        private static String sFormatToString, sFormatGangToString;

        @Override
        public String toString() {
            if (gangedTileInfo == null) {
                if (sFormatToString == null) {
                    return super.toString();
                }
                return String.format(sFormatToString, tile.toString(), fromWhom);
            }
            if (sFormatGangToString == null) {
                return super.toString();
            }
            return String.format(sFormatGangToString, gangedTileInfo.tile.toString(),
                            tile.toString());
        }

        // Format: tile, fromWhom, fromWhere, tileState, finalLocation, gangedTileInfo
        //          %s      %s        %d        %d          %d,           %s
        private static final String FORMAT_INFO_STRING = "%s,%s,%d,%d";
        private static final String SEPARATOR_INFO_ARG = ",";
        private static final String SEPARATOR_GANGED_INFO_ARG = "_";
        public String tileInfoString() {
            StringBuilder sb = new StringBuilder(String.format(FORMAT_INFO_STRING, tile, fromWhom,
                            fromWhere.ordinal(), tileState.ordinal()));
            sb.append(SEPARATOR_INFO_ARG);
            if (finalLocation != null) {
                sb.append(finalLocation.ordinal());
            }
            sb.append(SEPARATOR_INFO_ARG);
            if (gangedTileInfo != null) {
                sb.append(gangedTileInfo.tileInfoString().replace(SEPARATOR_INFO_ARG,
                                SEPARATOR_GANGED_INFO_ARG));
            }
            return sb.toString();
        }

        public static TileInfo parseTileInfoString(final String tileInfoString) {
            String[] array = tileInfoString.split(SEPARATOR_INFO_ARG);
            Tile tile = Tile.parse(array[0].trim());
            String fromWhom = array[1].trim();
            Location fromWhere = Location.getLocation(Integer.parseInt(array[2].trim()));
            TileState tileState = TileState.getTileState(Integer.parseInt(array[3].trim()));
            Location finalLocation = null;
            if (array.length >= 5 && !TextUtils.isEmpty(array[4])) {
                finalLocation = Location.getLocation(Integer.parseInt(array[4]));
            }
            TileInfo gangedTileInfo = null;
            if (array.length >= 6 && !TextUtils.isEmpty(array[5])) {
                String gangedTileInfoString = array[5].trim().replace(SEPARATOR_GANGED_INFO_ARG,
                                SEPARATOR_INFO_ARG);
                gangedTileInfo = TileInfo.parseTileInfoString(gangedTileInfoString);
            }

            TileInfo tileInfo = new TileInfo(tile, fromWhom, fromWhere, tileState);
            if (finalLocation != null) {
                tileInfo.finalLocation = finalLocation;
            }
            if (gangedTileInfo != null) {
                tileInfo.gangedTileInfo = gangedTileInfo;
            }
            return tileInfo;
        }

        public void addHuedPlayer(HuedInfo huedInfo) {
            synchronized(mHuedPlayers) {
                mHuedPlayers.add(huedInfo);
            }
        }

        // 看看当前player用这张牌胡了什么.
        public HuPattern getHuPattern(final Player player) {
            synchronized(mHuedPlayers) {
                for (HuedInfo huedInfo : mHuedPlayers) {
                    if (huedInfo.player == player) return huedInfo.huPattern;
                }
                return null;
            }
        }

        public HuTile[] getHuTiles(final Player player) {
            synchronized(mHuedPlayers) {
                for (HuedInfo huedInfo : mHuedPlayers) {
                    if (huedInfo.player == player) return huedInfo.huTiles;
                }
                return null;
            }
        }

        public boolean isThrown() {
            return tileState == TileState.TileThrown || tileState == TileState.TileGot;
        }

        public String info() {
            synchronized (mHuedPlayers) {
                if (mHuedPlayers.size() <= 0) {
                    return toString();
                }
                StringBuilder sb = new StringBuilder(toString());
                for (HuedInfo huedInfo : mHuedPlayers) {
                    sb.append('\n');
                    sb.append(huedInfo.player.name);
                    if (huedInfo.huPattern != null) {
                        sb.append(' ').append(huedInfo.huPattern);
                    }
                }
                return sb.toString();
            }
        }

        public Player getFirstHuedPlayer() {
            Player firstPlayer = null;
            for (HuedInfo huedInfo : mHuedPlayers) {
                if (firstPlayer == null) {
                    firstPlayer = huedInfo.player;
                    continue;
                }
                if (huedInfo.player.getLocation().ordinal() < firstPlayer.getLocation().ordinal()) {
                    firstPlayer = huedInfo.player;
                }
            }
            return firstPlayer;
        }

        public Player getLastHuedPlayer() {
            Player lastPlayer = null;
            for (HuedInfo huedInfo : mHuedPlayers) {
                if (lastPlayer == null) {
                    lastPlayer = huedInfo.player;
                    continue;
                }
                if (huedInfo.player.getLocation().ordinal() > lastPlayer.getLocation().ordinal()) {
                    lastPlayer = huedInfo.player;
                }
            }
            return lastPlayer;
        }

        public Hued getHued(final String playerName) {
            synchronized (mHuedPlayers) {
                int playerCount = mHuedPlayers.size();
                if (playerCount < 1) {
                    throw new RuntimeException("Why player count is 0 for " + this + "?!");
                }
                if (playerName.equals(fromWhom)) {
                    return Hued.HuedSelf;
                }
                if (playerCount == 1) {
                    return Hued.HuedOther;
                }
                return Hued.HuedOtherShare;// 一炮多响
            }
        }
    }

    public static class GangFlower extends TileInfo {
        public final Tile huTile;

        public GangFlower(TileInfo gangTileInfo, Tile huTile) {
            super(gangTileInfo);
            this.huTile = huTile;
        }

        private static final String FORMAT_GANG_FLOWER = "%s %s\n%s %s\n%s";

        public String info(final Context context) {
            return String.format(FORMAT_GANG_FLOWER,
                            context.getString(Action.Gang.actionResId), tile.toString(),
                            context.getString(Action.Hu.actionResId), huTile.toString(), info());
        }
    }

    // 这个类是指可以胡的牌的信息： 胡什么牌，胡了之后的牌型叫什么.
    public static class HuTile {
        public final Tile tile;
        public final HuPattern huPattern;
        public int playerHoldCount;

        public HuTile(Tile tile, HuPattern huPattern) {
            this.tile = tile;
            this.huPattern = huPattern;
        }

        private static final String FORMAT_TOSTRING = "%s(%s)";
        @Override
        public String toString() {
            if (huPattern == null || huPattern == HuPattern.HuNormal) {
                return tile.toString();
            }
            return String.format(FORMAT_TOSTRING, tile.toString(), huPattern.toString());
        }
    }

    private static enum TileShow {
        Black,
        GangBlack,     // 暗杠
        Ting,
        Chi,
        HuSelf,        // 自摸
        HuOther,       // 别人点炮
        HuOtherShared, // 一家点炮多家胡
        Live;
    }

    private static enum Hued {
        HuedSelf(Color.RED, TileShow.HuSelf),
        HuedOther(Color.GREEN, TileShow.HuOther),
        HuedOtherShare(Color.DKGRAY, TileShow.HuOtherShared);

        public final int color;

        public final TileShow tileShow;

        private Hued(int color, TileShow tileShow) {
            this.color = color;
            this.tileShow = tileShow;
        }
    }

    public static enum SpecialTile {
        WuKui; // 北京麻将中，五万为五魁
    }

    static {
        sGrayColorMatrixFilter = getGrayColorMatrixFilter();
        sTileKeptAfterTingColorMatrixFilter = getTingedColorMatrixFilter();
        sTileChiedColorMatrixFilter = getChiedColorMatrixFilter();
        sTileGangBlackColorMatrixFilter = getGangBlackColorMatrixFilter();
        sBlackColorMatrixFilter = getBlackColorMatrixFilter();
        sHuSelfColorMatrixFilter = getHuedColorMatrixFilter(Hued.HuedSelf);
        sHuOtherColorMatrixFilter = getHuedColorMatrixFilter(Hued.HuedOther);
        sHuOtherSharedColorMatrixFilter = getHuedColorMatrixFilter(Hued.HuedOtherShare);
    }

    private static ColorMatrixColorFilter getGrayColorMatrixFilter() {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        return new ColorMatrixColorFilter(matrix);
    }

    private static ColorMatrixColorFilter getBlackColorMatrixFilter() {
        return getColorMatrixFilter(Color.BLACK, 0.5f);
    }

    private static ColorMatrixColorFilter getTingedColorMatrixFilter() {
        return getColorMatrixFilter(Color.WHITE, 0.5f);
    }

    private static ColorMatrixColorFilter getHuedColorMatrixFilter(Hued hued) {
        return getColorMatrixFilter(hued.color, 0.5f);
    }

    private static ColorMatrixColorFilter getColorMatrixFilter(final int color, final float alpha) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(alpha);
        matrix.postConcat(alphaMatrix(alpha, color));
        return new ColorMatrixColorFilter(matrix);
    }

    private static ColorMatrix alphaMatrix(float alpha, int color) {
        sAlphaMatrixValues[0] = Color.red(color) * alpha / 255;
        sAlphaMatrixValues[6] = Color.green(color) * alpha / 255;
        sAlphaMatrixValues[12] = Color.blue(color) * alpha / 255;
        sAlphaMatrixValues[4] = 255 * (1 - alpha);
        sAlphaMatrixValues[9] = 255 * (1 - alpha);
        sAlphaMatrixValues[14] = 255 * (1 - alpha);
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.set(sAlphaMatrixValues);
        return colorMatrix;
    }

    private static ColorMatrixColorFilter getChiedColorMatrixFilter() {
        return getColorMatrixFilter(Color.RED, 0.6f);
    }

    private static ColorMatrixColorFilter getGangBlackColorMatrixFilter() {
        return getColorMatrixFilter(Color.DKGRAY, 0.7f);
    }

    public static void grayImageView(ImageView view) {
        view.setColorFilter(sGrayColorMatrixFilter);
    }

    public static void tingImageView(ImageView view) {
        view.setColorFilter(sTileKeptAfterTingColorMatrixFilter);
    }

    public static boolean isNumberTile(final Tile tile) {
        switch (tile.tileType) {
            case Tiao:
            case Tong:
            case Wan:
                return true;
            default:
                return false;
        }
    }

    public static Tile getTile(final int tileIndex, final TileType tileType) {
        if (tileIndex < 0 || tileIndex >= tileType.getCount()) {
            return null;
        }
        return new Tile(tileType, tileIndex);
    }

    public final TileType tileType;
    public final int tileIndex;

    public boolean isTingedTile; // 是否是报听的那张牌.
    public boolean isFocused; // 是否是刚打出的那张牌.
    public boolean isMatchAll; // 是否是一张百搭牌.

    public SpecialTile specialTile; // 标记特殊牌，比如五万为五魁等.

    public Tile(TileType tileType, int tileIndex) {
        this.tileType = tileType;
        this.tileIndex = tileIndex;
    }

    public void init() {
        isTingedTile = false;
        isMatchAll = false;
        specialTile = null;
    }

    private static void applyTileShow(final TileShow tileShow, final ImageView tileFrontView) {
        switch (tileShow) {
            case Black:
                tileFrontView.setColorFilter(sBlackColorMatrixFilter);
                return;
            case Ting:
                tileFrontView.setColorFilter(sTileKeptAfterTingColorMatrixFilter);
                return;
            case Chi:
                tileFrontView.setColorFilter(sTileChiedColorMatrixFilter);
                return;
            case GangBlack:
                tileFrontView.setColorFilter(sTileGangBlackColorMatrixFilter);
                return;
            case HuSelf:
                tileFrontView.setColorFilter(sHuSelfColorMatrixFilter);
                return;
            case HuOther:
                tileFrontView.setColorFilter(sHuOtherColorMatrixFilter);
                return;
            case HuOtherShared:
                tileFrontView.setColorFilter(sHuOtherSharedColorMatrixFilter);
                return;
            case Live:
            default:
                return;
        }
    }

    public View inflate(Context context, int tileItemLayoutId, int positionIndex) {
        return inflate(context, tileItemLayoutId, positionIndex, null);
    }

    public View inflateGangBlack(Context context, int tileItemLayoutId, int positionIndex) {
        return inflate(context, tileItemLayoutId, positionIndex, TileShow.GangBlack);
    }

    private View inflate(Context context, int tileItemLayoutId, int positionIndex,
                    final TileShow tileShow) {
        View view = View.inflate(context, tileItemLayoutId, null);

        ImageView tileFrontView = (ImageView)view.findViewById(R.id.tile_front);
        if (tileFrontView != null) {
            tileFrontView.setImageResource(getResourceId(positionIndex));
            if (tileShow != null) applyTileShow(tileShow, tileFrontView);
        }
        TextView tileTextView = (TextView)view.findViewById(R.id.tile_text);
        if (tileTextView != null) {
            if (isTingedTile) {
                applyTileText(tileTextView, TileShow.Ting, R.string.action_ting);
            }
        }

        TextView tileMatchAllTextView = (TextView)view.findViewById(R.id.tile_match_all_text);
        if (tileMatchAllTextView != null) {
            tileMatchAllTextView.setVisibility(isMatchAll && tileFrontView != null ? View.VISIBLE : View.GONE);
        }

        TextView tileSpecialTextView = (TextView)view.findViewById(R.id.tile_special);
        if (tileSpecialTextView != null) {
            tileSpecialTextView.setVisibility(specialTile == null ? View.GONE : View.VISIBLE);
        }
        return view;
    }

    private void applyTileText(final TextView tileTextView, final TileShow tileShow,
                    final int textResId) {
        switch (tileShow) {
            case Ting:
                tileTextView.setVisibility(View.VISIBLE);
                tileTextView.setText(textResId);
                break;
            case HuSelf:
                tileTextView.setVisibility(View.GONE);
                break;
            case HuOther:
                tileTextView.setVisibility(View.VISIBLE);
                tileTextView.setText(textResId);
                break;
            case HuOtherShared:
                tileTextView.setVisibility(View.VISIBLE);
                tileTextView.setText(textResId);
                break;
            default:
                tileTextView.setVisibility(View.GONE);
        }
    }

    public View inflateBlack(Context context, int tileItemLayoutId, int positionIndex) {
        return inflate(context, tileItemLayoutId, positionIndex, TileShow.Black);
    }

    public View inflateChi(Context context, int tileItemLayoutId, int positionIndex) {
        return inflate(context, tileItemLayoutId, positionIndex, TileShow.Chi);
    }

    public View inflateThrownTile(Context context, int tileItemLayoutId, int positionIndex,
            int tileIndicatorViewId) {
        View view = inflate(context, tileItemLayoutId, positionIndex);
        View tileIndicatorView = view.findViewById(tileIndicatorViewId);
        tileIndicatorView.setVisibility(isFocused ? View.VISIBLE : View.GONE);
        return view;
    }

    // 胡牌使用不同的layout.
    public View inflateHuedTile(Context context, final int huedTileLayoutId,
            final int positionIndex, final TileInfo huedTileInfo, final String playerName,
            final boolean isGangFlower) {
        final TileShow tileShow = huedTileInfo.getHued(playerName).tileShow;

        View view = inflate(context, huedTileLayoutId, positionIndex, tileShow);
        //View backgroundView = view.findViewById(R.id.hued_tile_background);
        //backgroundView.setBackgroundColor(huedTileInfo.getTileBackgroundColor(playerName));

        TextView tileTextView = (TextView)view.findViewById(R.id.tile_text);
        if (isGangFlower && tileTextView != null) {
            tileTextView.setVisibility(View.VISIBLE);
            tileTextView.setText(R.string.action_gang);
        } else {
            applyTileText(tileTextView, tileShow, huedTileInfo.fromWhere.labelResId);
        }
        return view;
    }

    public void grayTile(View tileView) {
        ImageView tileFrontView = (ImageView)tileView.findViewById(R.id.tile_front);
        if (tileFrontView != null) {
            tileFrontView.setColorFilter(sGrayColorMatrixFilter);
        }
    }

    // 听牌后，对不可以打出的tile设置不同的显示.
    public void tingTile(View tileView) {
        ImageView tileFrontView = (ImageView)tileView.findViewById(R.id.tile_front);
        if (tileFrontView != null) {
            tileFrontView.setColorFilter(sTileKeptAfterTingColorMatrixFilter);
        }
    }

    public int getResourceId(int resIndex) {
        return tileType.getResourceId(tileIndex, resIndex);
    }

    public void setFocused(boolean isFocused) {
        this.isFocused = isFocused;
    }

    // Attention: 这里不能重载equals!
    // 因为两张牌相同内容的牌，是不同对象，
    public boolean isSameTile(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof Tile)) return false;
        Tile inputTile = (Tile)obj;
        return (tileType == inputTile.tileType) && tileIndex == inputTile.tileIndex;
    }

    @Override
    public String toString() {
        return tileType.getTileLabel(tileIndex);
    }

    public static Tile parse(final String tileString) {
        for (TileType tileType : TileType.values()) {
            int typeTileCount = tileType.getCount();
            for (int i = 0; i < typeTileCount; i++) {
                if (tileType.getTileLabel(i).equals(tileString)) {
                    return new Tile(tileType, i);
                }
            }
        }
        throw new RuntimeException("Why not found?! " + tileString);
    }

    public static final int MAX_TILE_COUNT = 4; // 每个牌都有4张.

    public static Tile[] getTiles(Game game) {
        Tile[] tiles = new Tile[TileType.getTileTotal(game)];
        int i = 0;
        for (TileType type : TileType.values()) {
            if (!game.isValidTileType(type)) continue;
            int typeTileNum = type.getCount();
            for (int index = 0; index < typeTileNum; index++) {
                for (int j = 0; j < MAX_TILE_COUNT; j++) {
                    tiles[i++] = new Tile(type, index);
                }
            }
        }
        return tiles;
    }

    private static final Comparator<Tile> sTileComparator = new Comparator<Tile>() {
        @Override
        public int compare(Tile tile1, Tile tile2) {
            int result = 0;
            if (tile1.tileType != tile2.tileType) {
                result = tile1.tileType.ordinal() - tile2.tileType.ordinal();
            } else {
                final int tile1Index = tile1.tileIndex;
                final int tile2Index = tile2.tileIndex;
                result = tile1Index - tile2Index;
            }
            return result;
        }

    };

    public static void sort(ArrayList<Tile> tileList) {
        Collections.sort(tileList, sTileComparator);
    }
}
