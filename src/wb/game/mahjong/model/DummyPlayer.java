package wb.game.mahjong.model;

import wb.game.mahjong.MahjongManager;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.Tile.TileInfo;
import wb.game.utils.Utils;

// The game always needs 4 players.
// 4 players might be in 3 kinds:
// DummyPlayer: manipulated by the app.
// LocalPlayer: always the current user locating in bottom.
// RemotePlayer: WifiPlayer or BluetoothPlayer.
public class DummyPlayer extends Player {
    public String ipv4; // 如果这个member非空，说明是remote dummy.

    public DummyPlayer(String name, Gender gender) {
        this(name, gender, null);
    }

    public DummyPlayer(String name, Gender gender, String ipv4) {
        super(name, gender, null);
        this.ipv4 = ipv4;
    }

    @Override
    protected void doDetermineIgnored(boolean fromLocalManager) {
        if (ipv4 != null) return;
        autoDetermineIgnored();
    }

    @Override
    public void selectAction(final TileInfo tileInfo, final Action...actions) {
        if (ipv4 != null) return;
        if (actions == null || actions.length <= 0) return;
        if (hasAction(actions, Action.Hu)) {
            takeAction(Action.Hu, tileInfo, false);
            return;
        }
        if (hasAction(actions, Action.Ting)) {
            takeAction(Action.Ting, tileInfo, false);
            return;
        }
        int index = Utils.getRandomInt(0, actions.length - 1);
        takeAction(actions[index], tileInfo, false);
    }

    private static boolean hasAction(final Action[] actions, final Action specifiedAction) {
        if (actions == null || actions.length <= 0) return false;
        for (Action action : actions) {
            if (action == specifiedAction) return true;
        }
        return false;
    }

    @Override
    protected void actionGang(final TileInfo tileInfo) {
        if (ipv4 != null) return;
        final CanGangTile[] canGangTiles = getCanGangTiles(tileInfo);
        CanGangTile canGangTile;
        if (canGangTiles.length > 1) {// 说明不只一个杠, 随机选择一个.
            int index = Utils.getRandomInt(0, canGangTiles.length - 1);
            canGangTile = canGangTiles[index];
        } else {
            canGangTile = canGangTiles[0];
        }
        gang(canGangTile, tileInfo);
    }

    @Override
    protected void actionChi(final TileInfo tileInfo) {
        if (ipv4 != null) return;
        final int chiCount = mCanChiTiles.size();
        CanChi canChi;
        if (chiCount > 1) {// 说明不只一种吃法, 随机选择一种.
            canChi = mCanChiTiles.get(Utils.getRandomInt(0, chiCount - 1));
        } else {
            canChi = mCanChiTiles.get(0);
        }
        chi(canChi, tileInfo);
    }

    @Override
    protected void whenTileGanged(final TileInfo tileInfo, final boolean isBlackGang) {
        // TODO: empty in dummy player.
        return;
    }

    // 每个dummyPlayer最少占用MIN_TIMEOUT扔牌。可以让audio播放完整清楚。
    private static final long MIN_TIMEOUT = 800L;

    @Override
    protected void whenReadyToThrow(final long startTime, final boolean fromLocalManager) {
        if (ipv4 != null) return;
        autoThrowTile(startTime);
    }

    private void autoThrowTile(final long startTime) {
        Tile tile = null;
        if (isHued()) {
            tile = mNewTile;
        } else {
            tile = autoFindTileToThrow();
        }

        long timePassed = System.currentTimeMillis() - startTime;
        if (timePassed < MIN_TIMEOUT) {
            long delay = MIN_TIMEOUT - timePassed;
            if (delay < 100L) delay += 200L;
            // 延迟打牌以便播放之前的声音.
            try {
                Thread.sleep(delay);
            } catch (Exception e) {
                // TODO: nothing needs to be done?
            }
        }
        throwSelected(tile, false);
    }

    private Tile autoFindTileToThrow() {
        final TileType ignoredType = getIgnoredType();
        int ignoredNum = ignoredType == null ? 0 : getTilesNum(ignoredType);
        if (ignoredNum > 0) {
            // 如果有缺还没打完，优先打缺门的牌，随机选.
            return getRandomTile(ignoredType);
        }
        // 对于dummy player,现在没有支持智能选牌，只是随机选一张打出去.
        return getRandomTile(null);
    }

    private Tile getRandomTile(TileType selectedType) {
        synchronized(mTiles) {
            final int num = mTiles.size();
            if (num <= 0) return null;
            final int canTingNum = mCanTingTiles.size();
            int randomIndex;
            if (selectedType == null) {
                if (mActionTingReported && canTingNum == 0) {
                    return mNewTile;
                }
                if (canTingNum > 0) {
                    randomIndex = Utils.getRandomInt(0, canTingNum - 1);
                    return mCanTingTiles.get(randomIndex).tile;
                }
                Tile matchAllTile = MahjongManager.getInstance().getGame().getMatchAllTile();
                final int tileCount = getTileCount(matchAllTile);
                if (tileCount <= 0) {
                    randomIndex = Utils.getRandomInt(0, num - 1);
                    return mTiles.get(randomIndex);
                }
                if (tileCount == mTiles.size()) {
                    return mTiles.get(0); // 应该不会发生这种情况啊.到这时候应该早都胡牌了...
                }
                // 对于matchAllTile，什么时候都不扔掉.
                Tile tile;
                do {
                    randomIndex = Utils.getRandomInt(0, num - 1);
                    tile = mTiles.get(randomIndex);
                } while (tile.isSameTile(matchAllTile));
                return tile;
            }
            Tile tile;
            do {
                randomIndex = Utils.getRandomInt(0, num - 1);
                tile = mTiles.get(randomIndex);
            } while (tile.tileType != selectedType);
            return tile;
        }
    }
}
