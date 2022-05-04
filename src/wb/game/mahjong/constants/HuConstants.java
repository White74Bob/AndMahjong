package wb.game.mahjong.constants;

import java.util.ArrayList;

import android.content.Context;
import wb.game.mahjong.R;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.Tile;
import wb.game.mahjong.model.Tile.TileCount;

public class HuConstants {
    public static enum HuedType {
        HuOnceAll,    // 一家胡牌后，则游戏结束，但可以一炮多响.
        HuOncePlayer, // 一个玩家只能胡一次，胡过之后剩余玩家继续. 比如血战到底.
        HuMulti;      // 每个玩家都可以胡任意次, 支持多次胡牌. 比如血流成河.
    }

    public static enum HuType {
        // 天胡：打牌的过程中，庄家在第一次模完牌后，就胡牌，叫天胡。
        TianHu(R.string.hu_tian,   6, 168),   // {天和} 168番 庄家起牌即和，任何胡牌型均可。
        // 地胡：在打牌过程中，非庄家在第一次摸完牌后就可以下叫，第一轮摸牌后就胡牌，叫地胡。
        DiHu(R.string.hu_di,       6, 158),   // {地和} 158番 庄家出第一张后，下家自摸即是地和，牌型不限。
        RenHu(R.string.hu_ren,     0, 108),   // {人和} 108番 第一圈内胡牌，牌型不限。
        Normal(R.string.action_hu, 0,   0);

        public final int labelResId;
        public final int fanNumber1;
        public final int fanNumber2;

        public String label;

        private HuType(int labelResId, int fan1, int fan2) {
            this.labelResId = labelResId;
            fanNumber1 = fan1;
            fanNumber2 = fan2;
        }

        public static void initLabels(Context context) {
            for (HuType type : values()) {
                type.label = context.getString(type.labelResId);
            }
        }
    }

    // 各种牌型名称，有些带番.
    public static enum HuPattern {
        HuNormal("平胡", 1), // （基本胡），一番，四坎牌加一对将（四坎牌可为刻子也可以是顺子）。
        HuPairs("对对胡", 2), // 除了一对对牌以外，剩下的都是三张一对的，一共四对。
        HuSameType("清一色", 3), // 玩家胡牌的手牌全部都是一门花色。
        HuOneNine("带幺九", 3), // 玩家手牌中，全部是用1的连牌或者9的连牌组成的牌。
        Hu7Pairs("七对", 3), // 玩家的手牌全部是两张一对的，没有碰过和杠过。
        HuPairsSameType("清对", 4), // 玩家手上的牌是清一色的对对胡。 如：将对：玩家手上的牌是带二、五、八的对对胡。
        HuDragon7Pairs("龙七对", 5), // 玩家手牌为暗七对牌型，没有碰过或者杠过，并且有四张牌是一样的，叫龙七对。不再计七对，同时减1根。
        Hu7PairsSameType("清七对", 5), // 玩家手上的牌是清一色的七对。
        HuOneNineSameType("清幺九", 5), // 清一色的幺九。
        HuDragon7PairsSameType("青龙七对", 6), // 玩家手牌是清一色的龙七对，叫青龙七对，算番时减 1根。
        Hu13_1("十三幺"),
        HuDasanyuan("大三元"), // 中、发、白称三元 ， 胡牌手中中中发发发白白白刻称大三元
        HuXiaosanyuan("小三元"), // 小三元就是缺一个刻字比发发发白白、发发发白白白、发发白白白三种情况
        Hu3anKe("三暗刻"), // 三暗刻就是三组一样的牌，都没杠。
        Hu4anKe("四暗刻"), // 四暗刻：四个三张，另外一对，没有碰对方的。
        Hu9lianBaodeng("九莲宝灯"), // 九莲宝灯：三个一万，二-八万各一个，三个九万。
        HuTrainWheels("火车轮"), // 火车轮：二饼--八饼各一对。
        HuOldMan("清老头"), // 清老头：四个三个幺、九饼、万、条加一对幺、九饼、万、条。
        Hu4Joy1("大四喜"), // 大四喜, 由4副风刻(杠)组成的和牌。不计圈风刻、门风刻、三风刻、碰碰和
        Hu4Joy0("小四喜"), // 小四喜：东南西北三个三张，另外一样一对，其它没要求。
        HuAllWan("百万石"), // 百万石：万一色，14张牌的总数加起来超过100.
        Hu18luoHan("十八罗汉"), // 十八罗汉：四杠开花。
        HuRedPeacock("红孔雀"), // 红孔雀：一条，五条、七条、九条、红中组合起来胡牌。
        HuAllGreen("绿一色"); // 绿一色：二、三、四、六、八、发财组合起来胡牌.

        public final String name;
        public final int fanNumber;

        private HuPattern(String name, int fan) {
            this.name = name;
            this.fanNumber = fan;
        }

        private HuPattern(String name) {
            this.name = name;
            this.fanNumber = 0;
        }

        private static final String FORMAT_PATTERN = "%s %d番";
        @Override
        public String toString() {
            if (fanNumber > 1) {
                return String.format(FORMAT_PATTERN, name, fanNumber);
            }
            return name;
        }
    }

    public static enum Fan1 {
        GangFlower("杠上花"),  // 1番，杠后自模胡牌（杠了之后补牌而胡）。如果某个玩家放杠给另一个玩家，此时被杠上开花胡牌后，需要包掉另外两家所输的分。
        GangGun("杠上炮"),     // 1番，玩家在杠牌时，先杠一张牌，再打掉一张牌，而打出的这张牌正好是其他玩家胡牌所需要的叫牌时，这种情况叫杠上炮。即玩家杠了后补牌，打出，然后给其他玩家胡了。
        GangGrabbed("抢杠"),  // 加1番,
        Gang("杠"),           // 加1番。
        Gen("根");            // 1番，四张同样的牌不作杠算1根，胡牌时1根加1番。

        public final String name;

        private Fan1(String name) {
            this.name = name;
        }
    }

    // 胡牌有以下3种情况：
    // 1、一个对子 +4组 3个相同的牌或者顺子。
    //    只有条/饼/万可以构成顺子。东西南北中发白没有顺子。
    // 2、7对。
    // 3、13幺:1条,9条,1饼,9饼,1万,9万,东,西,南,北,中,发,白.
    //    这13种牌每种都有，而且仅有这13种牌。肯定是有一种2张。其他的1张)。
    public static HuPattern getHuPattern(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        final ArrayList<TileCount> tileCountList = getTileCountList(liveTiles);

        if (isHuPairs(liveTiles, tileCountList, chiTiles, pengTiles, gangTiles)) {
            if (isHuSameType(liveTiles, chiTiles, pengTiles, gangTiles)) {
                return HuPattern.HuPairsSameType;
            }
            return HuPattern.HuPairs;
        }
        if (isHuOneNine(liveTiles, chiTiles, pengTiles, gangTiles)) {
            if (isHuSameType(liveTiles, chiTiles, pengTiles, gangTiles)) {
                return HuPattern.HuOneNineSameType;
            }
            return HuPattern.HuOneNine;
        }
        // 是否各种7对.
        if (isHu7Pairs(liveTiles, chiTiles, pengTiles, gangTiles)) {
            boolean isSameType = isSameType(liveTiles, liveTiles[0].tileType);
            boolean hasDragon = hasDragon(liveTiles);
            if (isSameType) {
                if (hasDragon) return HuPattern.HuDragon7PairsSameType;
                return HuPattern.Hu7PairsSameType;
            }
            if (hasDragon) return HuPattern.HuDragon7Pairs;
            return HuPattern.Hu7Pairs;
        }
        // 是否十三幺.
        if (is13_1(liveTiles, null, null, null)) {
            return HuConstants.HuPattern.Hu13_1;
        }
        return checkNormalHu(liveTiles, chiTiles, pengTiles, gangTiles);
    }

    // 四张牌没有杠过，而是成两对，称为龙.
    private static boolean hasDragon(final Tile[] tiles) {
        ArrayList<TileCount> tileCountList = getTileCountList(tiles);

        for (TileCount tileCount : tileCountList) {
            if (tileCount.count == 4) return true;
        }
        return false;
    }

    private static TileCount getTileCount(final ArrayList<TileCount> tileCountList, final Tile tile) {
        for (TileCount tileCount : tileCountList) {
            if (tileCount.tile.isSameTile(tile)) return tileCount;
        }
        return null;
    }

    private static ArrayList<TileCount> getTileCountList(final Tile[] tiles) {
        final ArrayList<TileCount> tileCountList = new ArrayList<TileCount>(tiles.length);
        TileCount tileCount;
        for (Tile tile : tiles) {
            tileCount = getTileCount(tileCountList, tile);
            if (tileCount == null) {
                tileCountList.add(new TileCount(tile));
            } else {
                tileCount.count++;
            }
        }
        return tileCountList;
    }

    // （基本胡），一番，四坎牌加一对将（四坎牌可为刻子也可以是顺子）。
    // 胡牌情况1： 一个对子 + 4组3个相同的牌或者顺子。
    private static HuPattern checkNormalHu(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        final int chiSetNum = chiTiles == null ? 0 : chiTiles.length / 3;
        final int pengSetNum = pengTiles == null ? 0 : pengTiles.length /3;
        final int gangSetNum = gangTiles == null ? 0 : gangTiles.length / 4;

        if (!isNormalHu(liveTiles, chiSetNum, pengSetNum, gangSetNum)) return null;

        // 检查是不是暗刻.
        HuPattern anke = checkAnke(liveTiles, chiTiles, pengTiles, gangTiles);
        if (anke != null) return anke;

        if (isHuSameType(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuSameType;
        if (isBigSanyuan(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuDasanyuan;
        if (isSmallSanyuan(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuXiaosanyuan;
        if (is9lianBaodeng(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.Hu9lianBaodeng;
        if (isTrainWheels(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuTrainWheels;
        if (isOldMan(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuOldMan;
        if (is4Joy1(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.Hu4Joy1;
        if (is4Joy0(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.Hu4Joy0;
        if (isAllWan(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuAllWan;
        if (is18luoHan(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.Hu18luoHan;
        if (isRedPeacock(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuRedPeacock;
        if (isAllGreen(liveTiles, chiTiles, pengTiles, gangTiles)) return HuPattern.HuAllGreen;

        return HuPattern.HuNormal;
    }

    private static boolean isNormalHu(final Tile[] liveTiles,
                    final int chiSetNum, final int pengSetNum, final int gangSetNum) {
        // 因为之前已经检查过是胡牌了，这里直接返回true。
        return true; /*
        int liveNum = liveTiles == null ? 0 : liveTiles.length;
        if (liveNum <= 0) return false;

        if (liveNum + chiSetNum * 3 + pengSetNum * 3 + gangSetNum * 3 != 14) {
            return false;
        }

        final Tile[] sortedTiles = getSortedTiles(liveTiles);
        final int requiredSetNum = 4 - chiSetNum - pengSetNum - gangSetNum;

        // 以下只检查liveTiles. 胡牌情况1： 一个对子 + 4组3个相同的牌或者顺子。
        return GameResource.isHued(sortedTiles, requiredSetNum);
        */
    }

    // 玩家手牌除了一对对牌以外，剩下的都是三张一对的，一共四对。
    private static boolean isHuPairs(final Tile[] liveTiles, final ArrayList<TileCount> tileCountList,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        int chiNum = (chiTiles == null ? 0 : chiTiles.length);
        int liveNum = liveTiles == null ? 0 : liveTiles.length;
        if (chiNum > 0 || liveNum <= 0) return false;

        int pengNum = (pengTiles == null ? 0 : pengTiles.length);
        int gangNum = (gangTiles == null ? 0 : gangTiles.length) / 4 * 3;

        if (liveNum + pengNum + gangNum != 14) return false;

        boolean pair2Found = false;
        // 活牌中应该是n*3+2的形式
        for (TileCount tileCount : tileCountList) {
            if (tileCount.count == 1 || tileCount.count == 4) return false;
            if (tileCount.count == 2) {
                if (pair2Found) return false; // 找到了第2对牌, 不符合要求.
                pair2Found = true;
            }
        }
        return true;
    }

    private static boolean isSameType(final Tile[] tiles, final TileType tileType) {
        if (tiles == null || tiles.length <= 0) return true;
        for (Tile tile : tiles) {
            if (tile.tileType != tileType) return false;
        }
        return true;
    }

    // 玩家胡牌的手牌全部都是一门花色。
    private static boolean isHuSameType(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        if (liveTiles == null || liveTiles.length <= 0) return false;
        TileType tileType = null;
        for (Tile tile : liveTiles) {
            if (tileType == null) {
                tileType = tile.tileType;
            } else if (tile.tileType != tileType) {
                return false;
            }
        }
        return isSameType(chiTiles, tileType)
                        && isSameType(pengTiles, tileType)
                        && isSameType(gangTiles, tileType);
    }

    // 带幺九, 玩家手牌中，全部是用1的连牌或者9的连牌组成的牌。
    private static boolean isHuOneNine(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        if (pengTiles != null && pengTiles.length > 0) return false;
        if (gangTiles != null && gangTiles.length > 0) return false;
        return false;
    }

    private static Tile[] getSortedTiles(final Tile[] tiles) {
        ArrayList<Tile> tileList = new ArrayList<Tile>(tiles.length);
        for (Tile tile : tiles) {
            tileList.add(tile);
        }
        Tile.sort(tileList);
        return tileList.toArray(new Tile[tileList.size()]);
    }

    private static boolean isHu7Pairs(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        if (chiTiles != null && chiTiles.length > 0) return false;
        if (pengTiles != null && pengTiles.length > 0) return false;
        if (gangTiles != null && gangTiles.length > 0) return false;
        if (liveTiles == null || liveTiles.length != 14) return false;

        Tile[] tile14 = getSortedTiles(liveTiles);
        for (int i = 0; i < tile14.length; i += 2) {
            if (!tile14[i].isSameTile(tile14[i + 1])) return false;
        }
        return true;
    }

    private static final Tile[] s13Tiles = {
                    new Tile(TileType.Tiao, 0), // 1条
                    new Tile(TileType.Tiao, 8), // 9条
                    new Tile(TileType.Tong, 0), // 1筒
                    new Tile(TileType.Tong, 8), // 9筒
                    new Tile(TileType.Wan,  0), // 1万
                    new Tile(TileType.Wan,  8), // 9万
                    new Tile(TileType.Feng, 0),
                    new Tile(TileType.Feng, 1),
                    new Tile(TileType.Feng, 2),
                    new Tile(TileType.Feng, 3),
                    new Tile(TileType.Feng, 4),
                    new Tile(TileType.Feng, 5),
                    new Tile(TileType.Feng, 6),
    };

    private static int getTileNum(Tile inputTile, Tile[] tiles) {
        if (tiles == null || tiles.length <= 0) return 0;
        int count = 0;
        for (Tile tile : tiles) {
            if (tile.isSameTile(inputTile)) {
                count++;
            }
        }
        return count;
    }

    // 胡牌情况3, 13幺：
    // 1条,9条,1饼,9饼,1万,9万,东,西,南,北,中,发,白.
    // 这13种牌每种都有，而且仅有这13种牌。
    // 肯定是有一种2张。其他的1张。
    public static boolean is13_1(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        if (chiTiles != null && chiTiles.length != 0) return false;
        if (pengTiles != null && pengTiles.length != 0) return false;
        if (gangTiles != null && gangTiles.length != 0) return false;
        int[] counts = new int[s13Tiles.length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = getTileNum(s13Tiles[i], liveTiles);
            // 每一张幺牌，必须有一张，最多有两张
            if (counts[i] != 1 || counts[i] != 2) {
                return false;
            }
        }
        int count2 = 0;
        for (int count : counts) {
            if (count == 2) {
                count2++;
            }
        }
        return count2 == 1; // 只能有一对
    }

    // 中中中发发发白白白刻称大三元
    private static boolean isBigSanyuan(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        Tile zhong = new Tile(TileType.Feng, 4);
        Tile fa    = new Tile(TileType.Feng, 5);
        Tile bai   = new Tile(TileType.Feng, 6);

        boolean has3zhong = getTileNum(zhong, liveTiles) == 3 || getTileNum(zhong, pengTiles) == 3;
        boolean has3fa    = getTileNum(fa,    liveTiles) == 3 || getTileNum(fa,    pengTiles) == 3;
        boolean has3bai   = getTileNum(bai,   liveTiles) == 3 || getTileNum(bai,   pengTiles) == 3;

        return has3zhong && has3fa && has3bai;
    }

    private static boolean isSmallSanyuan(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        Tile zhong = new Tile(TileType.Feng, 4);
        Tile fa    = new Tile(TileType.Feng, 5);
        Tile bai   = new Tile(TileType.Feng, 6);

        int zhongCount = getTileNum(zhong, liveTiles) + getTileNum(zhong, pengTiles);
        int faCount    = getTileNum(fa,    liveTiles) + getTileNum(fa,    pengTiles);
        int baiCount   = getTileNum(bai,   liveTiles) + getTileNum(bai,   pengTiles);

        if (zhongCount == 2) return faCount == 3 && baiCount == 3;
        if (faCount == 2) return zhongCount == 3 && baiCount == 3;
        if (baiCount == 2) return zhongCount == 3 && faCount == 3;

        return false;
    }

    // 三暗刻就是三组一样的牌，都没杠。
    // 四暗刻：四个三张，另外一对，没有碰对方的。
    private static HuPattern checkAnke(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        if (liveTiles == null || liveTiles.length < 9) return null;

        ArrayList<TileCount> tileCountList = getTileCountList(liveTiles);
        int setCount = 0;
        for (TileCount tileCount : tileCountList) {
            if (tileCount.count == 3) setCount++;
        }
        switch (setCount) {
            case 3: return HuPattern.Hu3anKe;
            case 4: return HuPattern.Hu4anKe;
            default: return null;
        }
    }

    // 九莲宝灯：三个一万，二-八万各一个，三个九万
    private static boolean is9lianBaodeng(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        Tile wan1 = new Tile(TileType.Wan, 0);
        if (getTileNum(wan1, liveTiles) != 3 && getTileNum(wan1, pengTiles) != 3) return false;
        Tile wan9 = new Tile(TileType.Wan, 8);
        if (getTileNum(wan9, liveTiles) != 3 && getTileNum(wan9, pengTiles) != 3) return false;

        Tile wan2 = new Tile(TileType.Wan, 1);
        Tile wan8 = new Tile(TileType.Wan, 7);

        return getTileNum(wan2, liveTiles) == 1 && getTileNum(wan8, liveTiles) == 1;
    }

    private static boolean isTrainWheels(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        // TODO: ...
        return false;
    }

    private static boolean isOldMan(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        // TODO: ...
        return false;
    }

    private static boolean is4Joy1(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        // TODO: ...
        return false;
    }

    private static boolean is4Joy0(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        // TODO: ...
        return false;
    }

    // 百万石：万一色，14张牌的总数加起来超过100.
    private static boolean isAllWan(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        if (!isSameType(liveTiles, TileType.Wan)) return false;
        if (!isSameType(chiTiles,  TileType.Wan)) return false;
        if (!isSameType(pengTiles, TileType.Wan)) return false;
        if (!isSameType(gangTiles, TileType.Wan)) return false;

        int sum = 0;
        for (Tile tile : liveTiles) {
            sum += tile.tileIndex + 1;
        }
        if (chiTiles != null && chiTiles.length > 0) {
            for (Tile tile : chiTiles) {
                sum += tile.tileIndex + 1;
            }
        }
        if (pengTiles != null && pengTiles.length > 0) {
            for (Tile tile : pengTiles) {
                sum += tile.tileIndex + 1;
            }
        }
        if (gangTiles != null && gangTiles.length > 0) {
            for (Tile tile : gangTiles) {
                sum += tile.tileIndex + 1;
            }
        }
        return sum > 100;
    }

    private static boolean is18luoHan(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        if (chiTiles != null && chiTiles.length > 0) return false;
        if (pengTiles != null && pengTiles.length > 0) return false;
        if (gangTiles == null || gangTiles.length != 16) return false;
        if (liveTiles == null || liveTiles.length != 2) return false;
        return liveTiles[0].isSameTile(liveTiles[1]);
    }

    // 红孔雀：一条，五条、七条、九条、红中组合起来胡牌。
    private static boolean isRedPeacock(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        // TODO: ...
        return false;
    }

    // 绿一色：二、三、四、六、八、发财组合起来胡牌.
    private static boolean isAllGreen(final Tile[] liveTiles,
                    final Tile[] chiTiles, final Tile[] pengTiles, final Tile[] gangTiles) {
        // TODO: ...
        return false;
    }
}
