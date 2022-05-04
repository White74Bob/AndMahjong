package wb.game.mahjong.constants;

import android.content.res.Resources;
import wb.game.mahjong.MahjongManager.Position;
import wb.game.mahjong.R;
import wb.game.mahjong.model.GameResource.Game;
import wb.game.utils.Utils;

public class TileResources {
    // 逆时针方向顺序： 逆时针出牌
    // INDEX_BOTTOM = 0;
    // INDEX_RIGHT  = 1;
    // INDEX_TOP    = 2;
    // INDEX_LEFT   = 3;
    private static final int[][] TiaoNumbers = {
        {R.drawable.tiao_1_bottom, R.drawable.tiao_1_right, R.drawable.tiao_1_top, R.drawable.tiao_1_left},
        {R.drawable.tiao_2_bottom, R.drawable.tiao_2_right, R.drawable.tiao_2_top, R.drawable.tiao_2_left},
        {R.drawable.tiao_3_bottom, R.drawable.tiao_3_right, R.drawable.tiao_3_top, R.drawable.tiao_3_left},
        {R.drawable.tiao_4_bottom, R.drawable.tiao_4_right, R.drawable.tiao_4_top, R.drawable.tiao_4_left},
        {R.drawable.tiao_5_bottom, R.drawable.tiao_5_right, R.drawable.tiao_5_top, R.drawable.tiao_5_left},
        {R.drawable.tiao_6_bottom, R.drawable.tiao_6_right, R.drawable.tiao_6_top, R.drawable.tiao_6_left},
        {R.drawable.tiao_7_bottom, R.drawable.tiao_7_right, R.drawable.tiao_7_top, R.drawable.tiao_7_left},
        {R.drawable.tiao_8_bottom, R.drawable.tiao_8_right, R.drawable.tiao_8_top, R.drawable.tiao_8_left},
        {R.drawable.tiao_9_bottom, R.drawable.tiao_9_right, R.drawable.tiao_9_top, R.drawable.tiao_9_left}
    };

    private static final int[][] TongNumbers = {
        {R.drawable.tong_1_bottom, R.drawable.tong_1_right, R.drawable.tong_1_top, R.drawable.tong_1_left},
        {R.drawable.tong_2_bottom, R.drawable.tong_2_right, R.drawable.tong_2_top, R.drawable.tong_2_left},
        {R.drawable.tong_3_bottom, R.drawable.tong_3_right, R.drawable.tong_3_top, R.drawable.tong_3_left},
        {R.drawable.tong_4_bottom, R.drawable.tong_4_right, R.drawable.tong_4_top, R.drawable.tong_4_left},
        {R.drawable.tong_5_bottom, R.drawable.tong_5_right, R.drawable.tong_5_top, R.drawable.tong_5_left},
        {R.drawable.tong_6_bottom, R.drawable.tong_6_right, R.drawable.tong_6_top, R.drawable.tong_6_left},
        {R.drawable.tong_7_bottom, R.drawable.tong_7_right, R.drawable.tong_7_top, R.drawable.tong_7_left},
        {R.drawable.tong_8_bottom, R.drawable.tong_8_right, R.drawable.tong_8_top, R.drawable.tong_8_left},
        {R.drawable.tong_9_bottom, R.drawable.tong_9_right, R.drawable.tong_9_top, R.drawable.tong_9_left}
    };

    private static final int[][] WanNumbers = {
        {R.drawable.w_1_bottom, R.drawable.w_1_right, R.drawable.w_1_top, R.drawable.w_1_left},
        {R.drawable.w_2_bottom, R.drawable.w_2_right, R.drawable.w_2_top, R.drawable.w_2_left},
        {R.drawable.w_3_bottom, R.drawable.w_3_right, R.drawable.w_3_top, R.drawable.w_3_left},
        {R.drawable.w_4_bottom, R.drawable.w_4_right, R.drawable.w_4_top, R.drawable.w_4_left},
        {R.drawable.w_5_bottom, R.drawable.w_5_right, R.drawable.w_5_top, R.drawable.w_5_left},
        {R.drawable.w_6_bottom, R.drawable.w_6_right, R.drawable.w_6_top, R.drawable.w_6_left},
        {R.drawable.w_7_bottom, R.drawable.w_7_right, R.drawable.w_7_top, R.drawable.w_7_left},
        {R.drawable.w_8_bottom, R.drawable.w_8_right, R.drawable.w_8_top, R.drawable.w_8_left},
        {R.drawable.w_9_bottom, R.drawable.w_9_right, R.drawable.w_9_top, R.drawable.w_9_left}
    };

    private static final int[][] Fengs = {
        {R.drawable.dong_bottom,  R.drawable.dong_right,  R.drawable.dong_top,  R.drawable.dong_left },
        {R.drawable.nan_bottom,   R.drawable.nan_right,   R.drawable.nan_top,   R.drawable.nan_left  },
        {R.drawable.xi_bottom,    R.drawable.xi_right,    R.drawable.xi_top,    R.drawable.xi_left   },
        {R.drawable.bei_bottom,   R.drawable.bei_right,   R.drawable.bei_top,   R.drawable.bei_left  },
        {R.drawable.zhong_bottom, R.drawable.zhong_right, R.drawable.zhong_top, R.drawable.zhong_left},
        {R.drawable.fa_bottom,    R.drawable.fa_right,    R.drawable.fa_top,    R.drawable.fa_left   },
        {R.drawable.bai_bottom,   R.drawable.bai_right,   R.drawable.bai_top,   R.drawable.bai_left  }
    };

    private static final int[] sFengLabelResources = {
                    R.string.feng_east,  R.string.feng_west, R.string.feng_south, R.string.feng_north,
                    R.string.feng_zhong, R.string.feng_fa,   R.string.feng_bai
    };

    public enum TileType {
        Tiao(R.string.tile_tiao, TiaoNumbers),
        Tong(R.string.tile_tong, TongNumbers),
         Wan(R.string.tile_wan,  WanNumbers),
        Feng(R.string.tile_feng, Fengs, sFengLabelResources);

        public final int labelResId;
        public final int[] tileLabelResources;

        private String mTypeLabel = toString();
        private final String[] mTileLabels;

        private final int[][] resArray;

        private TileType(int labelId, int[][] resArray) {
            this(labelId, resArray, null);
        }

        private TileType(int labelId, int[][] resArray, int[] tileLabelResources) {
            this.labelResId = labelId;
            this.resArray = resArray;
            this.tileLabelResources = tileLabelResources;
            if (tileLabelResources == null || tileLabelResources.length <= 0) {
                mTileLabels = null;
            } else {
                mTileLabels = new String[tileLabelResources.length];
            }
        }

        public int getCount() {
            return resArray.length;
        }

        private void initTileLabel(Resources res) {
            mTypeLabel = res.getString(labelResId);
            if (mTileLabels != null && mTileLabels.length > 0) {
                for (int i = 0; i < mTileLabels.length; i++) {
                    mTileLabels[i] = res.getString(tileLabelResources[i]);
                }
            }
        }

        public String getLabel() {
            return mTypeLabel;
        }

        public int getResourceId(int tileIndex, int positionIndex) {
            return resArray[tileIndex][positionIndex];
        }

        private static final String FORMAT_TILE_LABEL = "%d%s";

        public String getTileLabel(final int tileIndex) {
            if (mTileLabels != null && mTileLabels.length > 0) {
                return mTileLabels[tileIndex];
            }
            return String.format(FORMAT_TILE_LABEL, tileIndex + 1, mTypeLabel);
        }

        public static int getTileTotal(Game game) {
            int total = 0;
            for (TileType type : values()) {
                if (!game.isValidTileType(type)) continue;
                total += type.resArray.length * 4;
            }
            return total;
        }

        public static void initLabels(Resources res) {
            for (TileType type : values()) {
                type.initTileLabel(res);
            }
        }

        public static TileType getRandomType(Game game) {
            TileType[] types = values();
            int randomIndex;
            do {
                randomIndex = Utils.getRandomInt(0, types.length - 1);
            } while (!game.isValidTileType(types[randomIndex]));
            return types[randomIndex];
        }

        public static TileType getTileType(int ordinal) {
            for (TileType tileType : values()) {
                if (tileType.ordinal() == ordinal) return tileType;
            }
            return null;
        }
    }

    public static int getNumberDrawable(TileType tileType, final int tileIndex, Position position) {
        return tileType.resArray[tileIndex][position.ordinal()];
    }

    private static int sSelectedTileWidth;
    private static int sSelectedTileHeight;
    private static int sTileWidth;
    private static int sTileHeight;
    private static int sTileWidthBottom, sTileHeightBottom;
    private static int sThrownTileWidthHor, sThrownTileHeightHor;
    private static int sThrownTileWidthVer, sThrownTileHeightVer;
    private static int sFocusedTileWidthHor, sFocusedTileHeightHor;
    private static int sFocusedTileWidthVer, sFocusedTileHeightVer;

    public static int getSelectedTileWidth() {
        return sSelectedTileWidth;
    }

    public static int getSelectedTileHeight() {
        return sSelectedTileHeight;
    }

    public static int getThrownTileWidth(final boolean isHorizontal) {
        return isHorizontal ? sThrownTileWidthHor : sThrownTileWidthVer;
    }

    public static int getThrownTileHeight(final boolean isHorizontal) {
        return isHorizontal ? sThrownTileHeightHor : sThrownTileHeightVer;
    }

    public static int getFocusedTileWidth(final boolean isHorizontal) {
        return isHorizontal ? sFocusedTileWidthHor : sFocusedTileWidthVer;
    }

    public static int getFocusedTileHeight(final boolean isHorizontal) {
        return isHorizontal ? sFocusedTileHeightHor : sFocusedTileHeightVer;
    }

    public static int getTileWidth(Position position) {
        switch (position) {
            case BOTTOM:
                return sTileWidthBottom;
            default:
                return sTileWidth;
        }
    }

    public static int getTileHeight(Position position) {
        switch (position) {
            case BOTTOM:
                return sTileHeightBottom;
            default:
                return sTileHeight;
        }
    }

    public static void init(Resources res) {
        sTileWidth = (int)res.getDimension(R.dimen.tile_width_ver);
        sTileHeight = (int)res.getDimension(R.dimen.tile_height_ver);
        sTileWidthBottom = (int)res.getDimension(R.dimen.tile_width_bottom);
        sTileHeightBottom = (int)res.getDimension(R.dimen.tile_height_bottom);

        sSelectedTileWidth = (int)res.getDimension(R.dimen.tile_width_selected);
        sSelectedTileHeight = (int)res.getDimension(R.dimen.tile_height_selected);

        sThrownTileWidthHor  = (int)res.getDimension(R.dimen.thrown_tile_width_hor);
        sThrownTileWidthVer  = (int)res.getDimension(R.dimen.thrown_tile_width_ver);
        sThrownTileHeightHor = (int)res.getDimension(R.dimen.thrown_tile_height_hor);
        sThrownTileHeightHor = (int)res.getDimension(R.dimen.thrown_tile_height_ver);

        sFocusedTileWidthHor  = (int)res.getDimension(R.dimen.tile_width_focused_hor);
        sFocusedTileWidthVer  = (int)res.getDimension(R.dimen.tile_width_focused_ver);
        sFocusedTileHeightHor = (int)res.getDimension(R.dimen.tile_height_focused_hor);
        sFocusedTileHeightHor = (int)res.getDimension(R.dimen.tile_height_focused_ver);

        TileType.initLabels(res);
    }
}
