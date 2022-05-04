package wb.game.mahjong.model;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import wb.conn.MessageUtils;
import wb.conn.RemoteMessage;
import wb.conn.RemoteMessage.ConnMessage;
import wb.game.mahjong.MahjongManager.Position;
import wb.game.mahjong.R;
import wb.game.mahjong.constants.TileResources;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.Tile.HuTile;
import wb.game.mahjong.model.Tile.TileInfo;

// The game always needs 4 players.
// 4 players might be in 3 kinds:
// DummyPlayer: manipulated by the app.
// LocalPlayer: always the current user locating in bottom.
// RemotePlayer: WifiPlayer or BluetoothPlayer.
public class LocalPlayer extends Player {
    private boolean mSoundCustomized; // TODO: 标记bottom player(user)声音是否定制，就是录制了自己的.

    private static final int sSelectedTileLayoutId = R.layout.tile_item_selected;

    private Tile mSelectedTile;

    public LocalPlayer(String name, Gender gender, String iconFilename) {
        super(name, gender, iconFilename);
        setTileOpen(true);
        mPosition = Position.BOTTOM;
    }

    @Override
    protected final void doInit() {
        super.doInit();
        mSelectedTile = null;
        setTileOpen(true);
    }

    @Override
    public void quitPlaying() {
        super.quitPlaying();
        if (mSelectedTile != null) {
            mSelectedTile = null;
            notifyViewChanged();
        }
    }

    @Override
    protected void whenReadyToThrow(final long startTime, final boolean fromLocalManager) {
        super.whenReadyToThrow(startTime, fromLocalManager);
        if (isHued() && mPlaying) {
            // 胡牌之后，如果游戏还没有结束，player也还能继续胡，则自动打牌，摸什么打什么.
            mSelectedTile = mNewTile;
            throwTile(mNewTile);
        } else {
            notifyViewChanged();
        }
        mPlayerCallback.send2RemotePlayer(ConnMessage.MSG_PLAYER_READY_TO_THROW, null);
    }

    private void setTileClickListener(final View tileView, final Tile tile) {
        // 胡牌之后不能再选牌打,只能扔掉新摸的牌.
        // 如果支持混儿牌，混儿牌也不能打.
        // 游戏结束后也不再响应click.
        if (isHued() || !mPlaying || isMatchAllTile(tile)) {
            tileView.setOnClickListener(null);
            return;
        }
        tileView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mThrowAvailable) {
                    throwTile(tile);
                } else if (mPlaying && mActionTingAvailable) {
                    mSelectedTile = tile;
                    TingTileInfo tingTileInfo = getTingTileInfo(tile);
                    if (tingTileInfo != null) {
                        showCanHuTiles(tingTileInfo.huTiles);
                    }
                    notifyViewChanged();
                }
            }
        });
    }

    private void throwTile(final Tile tile) {
        runInPlayerThread(new Runnable() {
            @Override
            public void run() {
                if (mSelectedTile == tile) {
                    if (!mActionTingAvailable && mThrowAvailable) {
                        throwSelected(mSelectedTile, false);
                        mSelectedTile = null;
                    }
                } else if (mPlaying) {
                    mSelectedTile = tile;
                    TingTileInfo tingTileInfo = getTingTileInfo(tile);
                    if (tingTileInfo != null) {
                        showCanHuTiles(tingTileInfo.huTiles);
                    }
                    notifyViewChanged();
                }
            }
        });
    }

    private TingTileInfo getTingTileInfo(final Tile tile) {
        for (TingTileInfo ting : mCanTingTiles) {
            if (ting.tile.isSameTile(tile)) {
                return ting;
            }
        }
        return null;
    }

    private void showCanHuTiles(final HuTile[] canHuTiles) {
        for (HuTile huTile : canHuTiles) {
            huTile.playerHoldCount = getHiddenCount(huTile.tile);
        }
        if (mPlayerCallback != null) {
            mPlayerCallback.showCanHuTiles(canHuTiles);
        }
    }

    // 吃/碰/明杠的牌，大家都可以看到.
    // 暗杠的牌，以及live tiles中的牌，数量player自己知道，其他人看不到.
    private int getHiddenCount(final Tile inputTile) {
        synchronized (mGangs) {
            for (Ganged ganged : mGangs) {
                switch (ganged.type) {
                    case GangBlack:
                        if (ganged.tiles[0].isSameTile(inputTile)) return 4;
                        break;
                    default:
                        break;
                }
            }
        }
        int count = 0;
        synchronized (mTiles) {
            for (Tile tile : mTiles) {
                if (tile.isSameTile(inputTile)) count++;
            }
        }
        return count;
    }

    @Override
    protected void doSetNewTileView(final boolean isSelectable) {
        if (isSelectable) {
            setTileClickListener(mNewTileView, mNewTile);
        } else {
            mNewTileView.setOnClickListener(null);
        }

        if (mPlaying) {
            LayoutParams params = mNewTileView.getLayoutParams();
            if (mSelectedTile == mNewTile) {
                params.width = TileResources.getSelectedTileWidth();
                params.height = TileResources.getSelectedTileHeight();
            } else {
                params.width = TileResources.getTileWidth(mPosition);
                params.height = TileResources.getTileHeight(mPosition);
            }
            mNewTileView.setLayoutParams(params);
        }
    }

    public interface ActionListener {
        void onActionSelected();
    }

    public View inflateActionView(final Context context, final ActionListener actionListener,
                    final TileInfo tileInfo, final Action...actions) {
        View actionsView = View.inflate(context, R.layout.prompt_actions, null);
        LinearLayout promptActionLayout = (LinearLayout)actionsView.findViewById(R.id.prompt_actions);
        promptActionLayout.removeAllViews();

        addActionViews(context, promptActionLayout, actionListener, tileInfo, actions);

        // Add Action.Null actionView
        addIgnoreActionView(context, promptActionLayout, actionListener, tileInfo, actions);

        return actionsView;
    }

    private void addActionViews(final Context context, final LinearLayout promptActionLayout,
                    final ActionListener actionListener, final TileInfo tileInfo,
                    final Action...actions) {
        for (final Action action : actions) {
            addActionView(context, promptActionLayout, actionListener, action, tileInfo);
        }
    }

    private void addActionView(final Context context, final LinearLayout promptActionLayout,
            final ActionListener actionListener, final Action action, final TileInfo tileInfo) {
        View actionView = View.inflate(context, action.actionLayoutId, null);
        TextView actionLabelView = (TextView)actionView.findViewById(R.id.label_action);
        actionLabelView.setText(action.actionResId);
        actionLabelView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (actionListener != null) {
                    actionListener.onActionSelected();
                }
                takeAction(action, tileInfo, false);
            }
        });
        promptActionLayout.addView(actionView);
    }

    private void addIgnoreActionView(final Context context, final LinearLayout promptActionLayout,
                    final ActionListener actionListener, final TileInfo tileInfo,
                    final Action... actions) {
        View actionView = View.inflate(context, R.layout.prompt_action_item_1, null);
        TextView actionLabelView = (TextView) actionView.findViewById(R.id.label_action);
        actionLabelView.setText(R.string.action_ignore);
        actionLabelView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (actionListener != null) {
                    actionListener.onActionSelected();
                }
                actionsIgnored(tileInfo, actions);
            }
        });
        promptActionLayout.addView(actionView);
    }

    @Override
    public void actionsIgnored(final TileInfo tileInfo, final Action...actions) {
        super.actionsIgnored(tileInfo, actions);
        mPlayerCallback.send2RemotePlayer(RemoteMessage.ConnMessage.MSG_PLAYER_ACTIONS_IGNORED,
                        MessageUtils.messagePlayerActionsIgnored(tileInfo, actions));
    }

    @Override
    protected View inflateTileView(Context context, Tile tile, final int positionIndex) {
        View tileView;
        if (mSelectedTile == tile && mPlaying) {
            tileView = tile.inflate(context, sSelectedTileLayoutId, positionIndex);
        } else {
            tileView = tile.inflate(context, mTileOpenLayoutId, positionIndex);
        }
        final boolean isSelectable = isSelectableTile(tile);
        if (tile.tileType == mIgnoredType) {
            tile.grayTile(tileView);
        } else if (!isSelectable) {
            tile.tingTile(tileView);
        }
        if (!isSelectable) {
            tileView.setOnClickListener(null);
        } else {
            setTileClickListener(tileView, tile);
        }
        return tileView;
    }

    public boolean soundCustomized() {
        if (mPosition != Position.BOTTOM) return false;
        return mSoundCustomized;
    }

    public void customizeActionSound(Action action) {
        // TODO:...
    }

    public void customizeTileSound(Tile tile) {
        // TODO:...
    }
}

