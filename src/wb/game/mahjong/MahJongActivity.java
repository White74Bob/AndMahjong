package wb.game.mahjong;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import wb.conn.MessageInfo;
import wb.conn.RemoteMessage.ConnMessage;
import wb.conn.wifi.WifiUtils;
import wb.game.mahjong.MahjongManager.Position;
import wb.game.mahjong.constants.Constants;
import wb.game.mahjong.constants.Constants.Dummy;
import wb.game.mahjong.constants.Constants.Reason;
import wb.game.mahjong.constants.Constants.User;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.DummyPlayer;
import wb.game.mahjong.model.GameResource;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.GameResource.Game;
import wb.game.mahjong.model.LocalPlayer;
import wb.game.mahjong.model.Player;
import wb.game.mahjong.model.Player.CanGangTile;
import wb.game.mahjong.model.Tile;
import wb.game.mahjong.model.Tile.HuTile;
import wb.game.mahjong.model.Tile.TileInfo;
import wb.game.mahjong.model.Tile.TileState;
import wb.game.mahjong.model.WifiPlayer;
import wb.game.utils.Utils;

public class MahJongActivity extends Activity {
    private final String mSettingsName = "mahjong_settings";

    private static final String KEY_VOLUME_ON_OFF = "volume_on_off";

    private static enum SettingType {
        TypeString,
        TypeBoolean,
        TypeInt;
    }

    private static class Setting {
        public final String key;
        public final SettingType settingType;
        public final Object defaultValue;

        public Object value;

        public Setting(String key, SettingType type, Object defaultValue) {
            this.key = key;
            this.settingType = type;
            this.defaultValue = defaultValue;
        }
    }

    private final Setting[] mSettings = {
                    new Setting(KEY_VOLUME_ON_OFF, SettingType.TypeBoolean, false),
    };

    private Button mButtonVolumeOn;
    private Button mButtonVolumeOff;

    private LinearLayout mViewInfo;
    private TextView mTextViewRemainTiles;
    private TextView mTextViewGangCount;

    private Button mButtonGameStart;
    private Button mButtonGameEnd;

    private TileInfo mFocusedTileInfo; // 当前正在被关注的牌.

    private static enum GameEnd {
        NullHuer,  // 没牌了，也无人胡，荒庄.
        PlayerEnd, // 玩家主动结束.
        Normal,    // 正常结束.
        GameOver;  // 游戏结束，返回上一界面.

        public static Bitmap userIcon;
    }

    private boolean mIsHost;

    private final RemoteConnector.RemoteListener mRemoteListener = new RemoteConnector.RemoteListener() {
        @Override
        public void onException(final Exception e, final String log) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Utils.showInfo(MahJongActivity.this, getString(R.string.label_error),
                                    Utils.getExceptionInfo(e) + "\n" + log);
                }
            });
        }

        @Override
        public void onError(final String errorInfo) {
            final Context context = MahJongActivity.this;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.isEmpty(errorInfo)) {
                        Utils.showInfo(context, getString(android.R.string.dialog_alert_title),
                                        "Why no errofInfo?!");
                        return;
                    }
                    Utils.showInfo(context, getString(R.string.label_error), errorInfo);
                }
            });
        }

        @Override
        public void handleReceivedMessage(MessageInfo messageInfo) {
            MahjongManager.getInstance().handleReceivedMessage(MahJongActivity.this, messageInfo);
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final Context context = MahJongActivity.this;
            Player player = null;
            Constants.UIMessage uiMsg = Constants.UIMessage.getMessage(msg.what);
            switch (uiMsg) {
                case MSG_VIEW_INIT:
                    initViewsVisibility();
                    break;
                case MSG_GAME_START:
                    onGameStart(msg.arg1);
                    break;
                case MSG_TILES_READY:
                    onTilesReady();
                    break;
                case MSG_SHOW_TILE:
                    showTile((Tile)msg.obj);
                    break;
                case MSG_GAME_END:
                    onGameEnd(GameEnd.Normal, msg.arg1);
                    break;
                case MSG_GAME_END_NULL:
                    onGameEnd(GameEnd.NullHuer, msg.arg1);
                    break;
                case MSG_GAME_OVER:
                    onGameEnd(GameEnd.GameOver, msg.arg1);
                    break;
                case MSG_GAME_REFRESH:
                    onGameRefresh();
                    break;
                case MSG_REFRESH_PLAYER:
                    player = (Player)msg.obj;
                    player.viewChanged(context);
                    MahjongManager.getInstance().updateRemainedTiles(context);
                    break;
                case MSG_SHOW_DETERMINE_IGNORED:
                    player = (Player)msg.obj;
                    showDetermineIgnored(player);
                    break;
                case MSG_SHOW_CAN_CHI:
                    showSelectChi((Player.PlayerCanChi)msg.obj);
                    break;
                case MSG_SHOW_CAN_GANG_TILES:
                    showSelectGang((Player.PlayerCanGang)msg.obj);
                    break;
                case MSG_DETERMINE_IGNORED_TYPE_DONE:
                    checkIgnoredTypeDone();
                    break;
                case MSG_NOTIFY_PLAYER_GET_TILE:
                    player = (Player)msg.obj;
                    MahjongManager.getInstance().notifyPlayerGetTile(player, null);
                    break;
                case MSG_NOTIFY_PLAYER_GET_TILE_FROM_END:
                    notifyPlayerGetTileFromEnd((Player.PlayerAction)msg.obj);
                    break;
                case MSG_NOTIFY_PLAYER_ACTION_DONE_ON_NEW_TILE:
                    whenPlayerActionDone((Player.PlayerAction)msg.obj, TileState.TileNew);
                    break;
                case MSG_NOTIFY_PLAYER_THROW_TILE:
                    player = (Player)msg.obj;
                    MahjongManager.getInstance().notifyPlayerThrowTile(player);
                    break;
                case MSG_NOTIFY_CHECK_ACTION_ON_THROWN_TILE:
                    notifyCheckActionOnThrownTile((TileInfo)msg.obj);
                    break;
                case MSG_NOTIFY_PLAYER_ACTION_DONE_ON_THROWN_TILE:
                    whenPlayerActionDone((Player.PlayerAction)msg.obj, TileState.TileThrown);
                    break;
                case MSG_NOTIFY_CHECK_ACTION_ON_GOT_TILE:
                    notifyCheckActionOnGotTile((Player.PlayerAction)msg.obj);
                    break;
                case MSG_NOTIFY_PLAYER_ACTION_DONE_ON_GOT_TILE:
                    whenPlayerActionDone((Player.PlayerAction)msg.obj, TileState.TileGot);
                    break;
                case MSG_NOTIFY_PLAYER_ACTION_IGNORED:
                    playerIgnoredActions((Player.PlayerAction)msg.obj);
                    break;
                case MSG_NOTIFY_PLAYER_GANG_FLOWERED:
                    playerGangFlowered((Player.PlayerAction)msg.obj);
                    break;
                case MSG_SHOW_PROMPT:Constants.debug((String)msg.obj);
                    //Utils.showInfo(context, uiMsg.toString(), (String)msg.obj);
                    break;
                case MSG_SHOW_ACTIONS_TO_PLAYER:
                    showActionsToUser((Player.PlayerAction)msg.obj);
                    break;
                case MSG_SHOW_CAN_HU_TILES:
                    showCanHuTiles((HuTile[])msg.obj);
                    break;
                default:
                    Utils.showInfo(context,
                                context.getString(android.R.string.dialog_alert_title),
                                "Unknown msg received:" + uiMsg);
                    super.handleMessage(msg);
            }
        }
    };

    private void whenPlayerActionDone(final Player.PlayerAction playerAction, TileState tileState) {
        final Context context = this;
        Utils.showToast(context, playerAction.getActionString(context));

        final Player player = playerAction.player;
        final Action action = playerAction.actions[0];
        final TileInfo tileInfo = playerAction.tileInfo;
        MahjongManager.getInstance().playSound(player, action, tileInfo);

        boolean done = false;
        if (playerAction.actions != null && playerAction.actions[0] == Action.Gang) {
            done = MahjongManager.getInstance().notifyPlayerGangedTile(playerAction);
            updateGangCountView();
        }
        if (!done) {
            switch (tileState) {
                case TileNew:
                    MahjongManager.getInstance().nextStepAfterActionDoneOnNewTile(player, action, tileInfo);
                    break;
                case TileGot:
                    MahjongManager.getInstance().nextStepAfterActionDoneOnGotTile(player, action, tileInfo);
                    break;
                case TileThrown:
                    MahjongManager.getInstance().nextStepAfterActionDoneOnThrownTile(player, action, tileInfo);
                    break;
            }
        }
    }

    private void showCanHuTiles(HuTile[] canHuTiles) {
        final String formatTileInfo = "%s(%d)";
        StringBuilder sb = new StringBuilder();
        int tileNumber;
        for (HuTile huTile : canHuTiles) {
            if (sb.length() > 0) sb.append('\n');
            tileNumber = MahjongManager.getInstance().getActiveTileNumber(huTile.tile,
                            huTile.playerHoldCount);
            sb.append(String.format(formatTileInfo, huTile.toString(), tileNumber));
        }
        Utils.showToast(this, sb.toString());
    }

    private void notifyCheckActionOnThrownTile(final TileInfo tileInfo) {
        updateFocused(tileInfo);
        MahjongManager.getInstance().notifyThrownTileToPlayers(tileInfo);
    }

    private void notifyCheckActionOnGotTile(final Player.PlayerAction playerAction) {
        Player player = playerAction.player;
        // 聚焦于当前正在busy的player location.
        MahjongManager.getInstance().updateWaiting(player.getLocation());
        // player吃/碰过后需要检查能不能有什么action.
        MahjongManager.getInstance().notifyPlayerCheckActionOnGotTile(player, playerAction.tileInfo);
    }

    // 聚焦于当前刚打出来的牌;
    private void updateFocused(TileInfo tileInfo) {
        if (mFocusedTileInfo != null) {
            mFocusedTileInfo.tile.setFocused(false);
            MahjongManager.getInstance().updatePlayerThrownTiles(mFocusedTileInfo.fromWhere);
        }
        if (tileInfo != null && tileInfo.tileState == TileState.TileThrown) {
            mFocusedTileInfo = tileInfo;
            mFocusedTileInfo.tile.setFocused(true);
        }
    }

    private void notifyPlayerGetTileFromEnd(Player.PlayerAction playerAction) {
        Player player = playerAction.player;
        TileInfo gangedTileInfo = playerAction.tileInfo;
        MahjongManager.getInstance().notifyPlayerGetTile(player, gangedTileInfo);
    }

    private void showActionsToUser(final Player.PlayerAction playerAction) {
        final Context context = this;

        final LocalPlayer player = (LocalPlayer)playerAction.player;
        final Action[] actions = playerAction.actions;
        final TileInfo tileInfo = playerAction.tileInfo;

        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        final int promptActionsViewId = R.id.prompt_actions;
        View actionsView = player.inflateActionView(context, new LocalPlayer.ActionListener() {
            @Override
            public void onActionSelected() {
                removeViews(mahjongView, promptActionsViewId);
            }
        }, tileInfo, actions);
        LinearLayout promptActionsLayout = (LinearLayout)actionsView.findViewById(promptActionsViewId);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.ALIGN_TOP, R.id.bottom_player_view);

        mahjongView.addView(promptActionsLayout, params);
    }

    private void playerIgnoredActions(Player.PlayerAction playerAction) {
        // TODO: So far when actions ignored, 没有需要UI做的...
    }

    private void playerGangFlowered(Player.PlayerAction playerAction) {
        whenPlayerActionDone(playerAction, TileState.TileNew);
    }

    private void checkIgnoredTypeDone() {
        Position[] positions = Position.values();
        boolean allDone = true;
        for (Position position : positions) {
            boolean isIgnoredTypeSet = position.isIgnoredTypeDone();
            position.updateWaiting(!isIgnoredTypeSet);
            if (!isIgnoredTypeSet && allDone) {
                allDone = false;
            }
        }
        if (allDone) {
            if (sDetermingIgnoredTimerUsed) {
                stopDeterminingTimer();
            }
            afterIgnoredDetermined();
            Position.showPlayersIgnored();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置无标题
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 设置全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.mahjong_main);

        Constants.clearUsedNames();

        Intent intent = getIntent();
        Bundle bundle = intent == null ? null : intent.getExtras();

        User user = (User)(bundle == null ? null : bundle.getParcelable(Constants.EXTRA_USER_INFO));
        final int gameIndex = (bundle == null ? 0 : bundle.getInt(Constants.EXTRA_GAME_INDEX));

        final String hostIp = bundle == null ? null : bundle.getString(Constants.EXTRA_HOST_IP);
        mIsHost = TextUtils.isEmpty(hostIp);

        Parcelable[] playerParcelables = bundle.getParcelableArray(Constants.EXTRA_PLAYERS_INFO);
        Player[] fourPlayers = getPlayers(user, playerParcelables);

        try {
            startNetwork(hostIp);
        } catch (Exception e) {
            Utils.showInfo(this, "Failed in startNetwork!", Utils.getExceptionInfo(e));
        }

        loadSettings(this);
        GameResource.getGame(gameIndex).loadSettings(this);
        initMahjongManager(gameIndex, hostIp, fourPlayers);

        initViews(user);
        GameEnd.userIcon = Constants.getIcon(user.user_icon_filepath);
    }

    private void startWifiCommunicate(final String serverIp) throws Exception {
        if (Constants.sMahjongUseTcp) {
            RemoteConnector.getInstance().startWifiTcp(serverIp, mRemoteListener);
        } else {
            RemoteConnector.getInstance().startWifiUdp1(mRemoteListener);
        }
    }

    private void startNetwork(final String serverIp) throws Exception {
        switch (Constants.sNetwork) {
            case Wifi:
                if (WifiUtils.isWifiConnected(this)) {
                    startWifiCommunicate(serverIp);
                }
                break;
            case Hotspot:
                break;
            case Bluetooth:
                break;
            default:
                break;
        }
    }

    private void loadSettings(final Context context) {
        final SharedPreferences pref = context.getSharedPreferences(mSettingsName,
                        Context.MODE_PRIVATE);
        // load Preferences.
        for (Setting setting : mSettings) {
            switch (setting.settingType) {
                case TypeBoolean:
                    setting.value = pref.getBoolean(setting.key, (Boolean)setting.defaultValue);
                    break;
                default:
                    break;
            }
        }
    }

    private void saveSettings(final Setting...settings) {
        SharedPreferences pref = getSharedPreferences(mSettingsName, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        // save Preferences.
        for (Setting setting : settings) {
            switch (setting.settingType) {
                case TypeBoolean:
                    editor.putBoolean(setting.key, (Boolean)setting.value);
                    break;
                default:
                    break;
            }
        }

        editor.apply();
    }

    private Player[] getPlayers(final User user, final Parcelable[] playerParcelables) {
        Player[] players = new Player[4];
        players[0] = new LocalPlayer(user.user_name, user.user_gender, user.user_icon_filepath);
        int parcelableCount = playerParcelables == null ? 0 : playerParcelables.length;
        Parcelable parcelable;
        for (int i = 1; i < players.length; i++) {
            parcelable = (i - 1 < parcelableCount ? playerParcelables[i - 1] : null);
            players[i] = getPlayer(parcelable);
        }
        return players;
    }

    private Player getPlayer(Parcelable playerParcelable) {
        DummyPlayer dummyPlayer = null;
        if (playerParcelable == null && mIsHost) {
            Dummy dummy = Constants.getDummyPlayerInfo();
            dummyPlayer = new DummyPlayer(dummy.name, dummy.gender);
        }
        if (playerParcelable instanceof WifiPlayer) {
            return (WifiPlayer)playerParcelable;
        }
        return dummyPlayer;
    }

    private void initMahjongManager(int gameIndex, String hostIp, Player[] players) {
        Game game = GameResource.getGame(gameIndex);

        TextView gameLabelTextView = (TextView)findViewById(R.id.game_label);
        gameLabelTextView.setText(game.labelResId);

        GameResource.setTiles(game);
        MahjongManager.getInstance().setMainThreadHandler(mHandler);
        MahjongManager.getInstance().init(this, gameIndex, hostIp, players);
    }

    private void initViews(final User user) {
        mViewInfo = (LinearLayout)findViewById(R.id.info_view);
        mTextViewRemainTiles = (TextView)findViewById(R.id.tile_remaining);
        mTextViewGangCount = (TextView)findViewById(R.id.gang_count);

        mButtonGameStart = (Button)findViewById(R.id.button_start_game);
        mButtonGameStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                MahjongManager.getInstance().startGame(MahJongActivity.this);
                mButtonGameStart.setVisibility(View.INVISIBLE);
            }
        });
        mButtonGameEnd = (Button)findViewById(R.id.button_end_game);
        mButtonGameEnd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showEndGameConfirm(false);
            }
        });

        initVolumeButtons();
        initViewsVisibility();
    }

    private void initVolumeButtons() {
        mButtonVolumeOn = (Button) findViewById(R.id.button_volume_on);
        mButtonVolumeOn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switchVolume(false);
            }
        });

        mButtonVolumeOff = (Button) findViewById(R.id.button_volume_off);
        mButtonVolumeOff.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switchVolume(true);
            }
        });
        boolean isVolumeOn = getSettingValue(KEY_VOLUME_ON_OFF) == Boolean.TRUE;
        MahjongManager.getInstance().setMute(!isVolumeOn);
        updateVolumeButtons(isVolumeOn);
    }

    private void updateVolumeButtons(boolean isVolumeOn) {
        mButtonVolumeOn.setVisibility(isVolumeOn  ? View.VISIBLE : View.GONE   );
        mButtonVolumeOff.setVisibility(isVolumeOn ? View.GONE    : View.VISIBLE);
    }

    private void switchVolume(boolean isOn) {
        if (setSettingValue(KEY_VOLUME_ON_OFF, isOn)) {
            MahjongManager.getInstance().setMute(!isOn);
            updateVolumeButtons(isOn);
        }
    }

    private Object getSettingValue(final String key) {
        for (Setting setting : mSettings) {
            if (setting.key.equals(key)) {
                return setting.value;
            }
        }
        return null;
    }

    private boolean setSettingValue(final String key, final Object value) {
        for (Setting setting : mSettings) {
            if (setting.key.equals(key)) {
                setting.value = value;
                saveSettings(setting);
                return true;
            }
        }
        return false;
    }

    private void initMahjongView() {
        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        removeViews(mahjongView, R.id.timer_layout, R.id.prompt_tile_type);
        removeViews(mahjongView, R.id.prompt_chi_select);
        removeViews(mahjongView, R.id.prompt_gang_select);
        removeViews(mahjongView, R.id.prompt_tile_type);
        removeViews(mahjongView, R.id.prompt_actions);
    }

    private void showEndGameConfirm(final boolean isFinishingActivity) {
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle(R.string.label_prompt);
        builder.setMessage(R.string.prompt_confirm_quit_game);
        builder.setPositiveButton(android.R.string.ok,
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        initMahjongView();
                        MahjongManager.getInstance().endGame(isFinishingActivity);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private void initViewsVisibility() {
        mViewInfo.setVisibility(View.GONE);
        mTextViewRemainTiles.setVisibility(View.GONE);
        mTextViewGangCount.setVisibility(View.GONE);

        if (MahjongManager.getInstance().canStartGame()) {
            mButtonGameStart.setVisibility(View.VISIBLE);
        } else {
            mButtonGameStart.setVisibility(View.GONE);
        }
        mButtonGameEnd.setVisibility(View.GONE);
    }

    private void initInfoView() {
        mViewInfo.setVisibility(View.VISIBLE);
        mTextViewRemainTiles.setVisibility(View.VISIBLE);
        updateGangCountView();
        int i = 0;
        View childView;
        while (i < mViewInfo.getChildCount()) {
           childView = mViewInfo.getChildAt(i);
           // 以下判断是否是已知的固定views. 现在只有一个textView用来显示剩余牌数.
           if (childView == mTextViewRemainTiles || childView == mTextViewGangCount) {
               i++;
               continue;
           }
           mViewInfo.removeViewAt(i);
        }
    }

    private void onGameStart(final int remainingTileNum) {
        initInfoView();
        updateRemainingTileNum(remainingTileNum);
        MahjongManager.getInstance().whenGameThreadReady();
    }

    private void onTilesReady() {
        mButtonGameStart.setVisibility(View.GONE);
        mButtonGameEnd.setVisibility(View.VISIBLE);
        MahjongManager.getInstance().whenTilesReady();
    }

    private void showTile(final Tile tile) {
        initInfoView();
        if (tile == null) return;
        int tileOpenLayoutId = Position.TOP.tileOpenResId;
        int positionIndex = Position.BOTTOM.ordinal();
        View tileView = tile.inflate(this, tileOpenLayoutId, positionIndex);
        mViewInfo.addView(tileView);
    }

    // 吃牌可能有几种吃法，需要显示出来让用户选择.
    private void showSelectChi(final Player.PlayerCanChi playerCanChi) {
        final Context context = this;
        View chiOptionView = View.inflate(context, R.layout.prompt_can_chi_tile, null);
        LinearLayout promptChiSelectLayout = (LinearLayout)chiOptionView.findViewById(R.id.prompt_chi_select);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.ALIGN_TOP, R.id.bottom_player_view);

        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        mahjongView.addView(promptChiSelectLayout, params);

        LinearLayout promptTileSetLayout = (LinearLayout)chiOptionView.findViewById(R.id.prompt_chi_tile);
        final int tileItemLayoutId = R.layout.tile_item_bottom_open;
        final int positionIndex = Position.BOTTOM.ordinal();
        for (final Player.CanChi canChi: playerCanChi.canChies) {
            View promptChiView = View.inflate(context, R.layout.prompt_chi_item, null);
            LinearLayout promptTileLayout = (LinearLayout)promptChiView.findViewById(R.id.tile_layout);
            switch (canChi.position) {
                case 0:
                    promptTileLayout.addView(canChi.missingTile.inflateBlack(context, tileItemLayoutId, positionIndex));
                    promptTileLayout.addView(canChi.twoTiles[0].inflate(context, tileItemLayoutId, positionIndex));
                    promptTileLayout.addView(canChi.twoTiles[1].inflate(context, tileItemLayoutId, positionIndex));
                    break;
                case 1:
                    promptTileLayout.addView(canChi.twoTiles[0].inflate(context, tileItemLayoutId, positionIndex));
                    promptTileLayout.addView(canChi.missingTile.inflateBlack(context, tileItemLayoutId, positionIndex));
                    promptTileLayout.addView(canChi.twoTiles[1].inflate(context, tileItemLayoutId, positionIndex));
                    break;
                case 2:
                    promptTileLayout.addView(canChi.twoTiles[0].inflate(context, tileItemLayoutId, positionIndex));
                    promptTileLayout.addView(canChi.twoTiles[1].inflate(context, tileItemLayoutId, positionIndex));
                    promptTileLayout.addView(canChi.missingTile.inflateBlack(context, tileItemLayoutId, positionIndex));
                    break;
            }
            promptChiView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    playerChiedTile(playerCanChi.player, canChi, playerCanChi.tileInfo);
                }
            });
            promptTileSetLayout.addView(promptChiView);
        }

        View cancelView = chiOptionView.findViewById(R.id.cancel_chi);
        cancelView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                playerChiedTile(playerCanChi.player, null, playerCanChi.tileInfo);
            }
        });
    }

    private void playerChiedTile(final Player player, final Player.CanChi canChi,
                    final TileInfo tileInfo) {
        if (canChi == null) {
            player.actionsIgnored(tileInfo, Action.Chi);
        } else {
            player.chi(canChi, tileInfo);
        }
        // Remove the tile chi prompt view.
        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        removeViews(mahjongView, R.id.prompt_chi_select);
    }

    // 可能有几个暗杠，需要显示出来让用户选择.
    private void showSelectGang(final Player.PlayerCanGang playerCanGang) {
        final Context context = this;
        View gangBlackOptionView = View.inflate(context, R.layout.prompt_can_gang_tile, null);
        LinearLayout promptGangSelectLayout = (LinearLayout)gangBlackOptionView.findViewById(R.id.prompt_gang_select);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.ALIGN_TOP, R.id.bottom_player_view);

        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        mahjongView.addView(promptGangSelectLayout, params);

        LinearLayout promptTileSetLayout = (LinearLayout)gangBlackOptionView.findViewById(R.id.prompt_gang_tile);
        final int tileItemLayoutId = R.layout.tile_item_bottom_open;
        final int positionIndex = Position.BOTTOM.ordinal();
        for (final CanGangTile canGangTile : playerCanGang.canGangTiles) {
            View promptGangView = View.inflate(context, R.layout.prompt_gang_item, null);
            LinearLayout promptTileLayout = (LinearLayout)promptGangView.findViewById(R.id.tile_layout);
            View tileView = canGangTile.tile.inflate(context, tileItemLayoutId, positionIndex);
            promptTileLayout.addView(tileView);
            promptGangView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    playerGangTile(playerCanGang.player, canGangTile, playerCanGang.tileInfo);
                }
            });
            promptTileSetLayout.addView(promptGangView);
        }

        View cancelView = gangBlackOptionView.findViewById(R.id.cancel_gang);
        cancelView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                playerGangTile(playerCanGang.player, null, playerCanGang.tileInfo);
            }
        });
    }

    private void playerGangTile(final Player player, final Player.CanGangTile canGangTile,
                    final TileInfo tileInfo) {
        if (canGangTile == null) {
            player.actionsIgnored(tileInfo, Action.Gang); // GangBlack
        } else {
            player.gang(canGangTile, tileInfo);
        }
        // Remove the tile gang black prompt view.
        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        removeViews(mahjongView, R.id.prompt_gang_select);
    }

    private static final boolean sDetermingIgnoredTimerUsed = false;

    // 牌已经排好序，接下来定缺.
    // 显示牌类型以便选择
    private void showDetermineIgnored(final Player player) {
        addTileTypesPromptView(player);
        if (sDetermingIgnoredTimerUsed) {
            startDeterminingTimer();
        }
    }

    private void addTileTypesPromptView(final Player player) {
        //LayoutInflater inflater = LayoutInflater.from(this);
        //View tileTypeView = inflater.inflate(R.layout.prompt_tile_type, null, false);
        View tileTypeView = View.inflate(this, R.layout.prompt_tile_type, null);
        LinearLayout promptTileTypeLayout = (LinearLayout)tileTypeView.findViewById(R.id.prompt_tile_type);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.ALIGN_TOP, R.id.bottom_player_view);

        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        mahjongView.addView(promptTileTypeLayout, params);

        TextView promptTiao = (TextView)tileTypeView.findViewById(R.id.label_tiao);
        promptTiao.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                playerSetIgnoredTileType(player, TileType.Tiao);
            }
        });
        TextView promptTong = (TextView)tileTypeView.findViewById(R.id.label_tong);
        promptTong.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                playerSetIgnoredTileType(player, TileType.Tong);
            }
        });
        TextView promptWan  = (TextView)tileTypeView.findViewById(R.id.label_wan);
        promptWan.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                playerSetIgnoredTileType(player, TileType.Wan);
            }
        });
    }

    private void playerSetIgnoredTileType(final Player player, final TileType type) {
        player.setIgnoredType(type, false);
        // Remove the tile type prompt view.
        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        removeViews(mahjongView, R.id.prompt_tile_type);
    }

    private final Timer mTimer = new Timer();
    private TimerTask mDetermineTimerTask;

    private void startDeterminingTimer() {
        //LayoutInflater inflater = LayoutInflater.from(this);
        //final View timerView = inflater.inflate(R.layout.prompt_timer, null, false);
        final View timerView = View.inflate(this, R.layout.prompt_timer, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        mahjongView.addView(timerView, params);

        final TextView timerText = (TextView)timerView.findViewById(R.id.text_timer);
        if (mDetermineTimerTask != null) {
            mDetermineTimerTask.cancel();
        }
        mDetermineTimerTask = new TimerTask() {
            @Override
            public void run() {
                int i = 10;
                while (i > 0) {
                    final int time = i;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            timerText.setText(Integer.toString(time));
                        }
                    });
                    try {
                        Thread.sleep(1000L);
                    } catch (Exception e) {
                    }
                    i--;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopDeterminingTimer();
                    }
                });
            }
        };
        mTimer.schedule(mDetermineTimerTask, 0L);
    }

    private void stopDeterminingTimer() {
        if (mDetermineTimerTask == null) return;

        mDetermineTimerTask.cancel();
        mDetermineTimerTask = null;
    }

    private void afterIgnoredDetermined() {
        if (!isFinishing()) {
            MahjongManager.getInstance().checkIgnoredDetermined();
        }
        // Remove sub views, including:
        //     the determining timer display;
        //     the tile type prompt view.
        final RelativeLayout mahjongView = (RelativeLayout)findViewById(R.id.mahjong_view);
        removeViews(mahjongView, R.id.timer_layout, R.id.prompt_tile_type);

        MahjongManager.getInstance().notifyNecessaryBeforeStartDone();
    }

    private void removeViews(RelativeLayout rootView, int...subViewIds) {
        if (subViewIds == null || subViewIds.length <= 0) return;
        for (int viewId : subViewIds) {
            View subView = rootView.findViewById(viewId);
            if (subView != null) {
                rootView.removeView(subView);
            }
        }
    }

    private void onGameRefresh() {
        updateRemainingTileNum(MahjongManager.getInstance().getGame().getRemainingTileNum());
    }

    private void onGameEnd(final GameEnd gameEnd, final int reasonOrdinal) {
        final Reason reason = Reason.getReason(reasonOrdinal);

        Position.initWaiting();
        if (mFocusedTileInfo != null) {
            mFocusedTileInfo.tile.setFocused(false);
        }
        mHandler.removeCallbacksAndMessages(null);
        MahjongManager.getInstance().openPlayerTiles();

        boolean gameEnded = !MahjongManager.getInstance().isPlaying();
        switch (gameEnd) {
            case NullHuer:
                if (!gameEnded) {
                    String text = getString(R.string.prompt_game_ended_null);
                    if (reason != null) text += " " + reason;
                    Utils.showToast(this, text, GameEnd.userIcon);
                    notifyGameEndedNull();
                }
                break;
            case Normal:
                if (!gameEnded) {
                    Utils.showInfo(this, getString(R.string.label_prompt),
                                    getString(R.string.prompt_game_ended));
                    notifyGameEnded();
                }
                break;
            case PlayerEnd:
                if (!gameEnded) {
                    notifyPlayerLeft();
                }
                break;
            case GameOver:
                finish();
                notifyGameOverTo3Players();
                break;
        }
        if (gameEnd != GameEnd.GameOver) {
            MahjongManager.getInstance().updateBankerPosition();
            initViewsVisibility();
        }
    }

    private void notifyGameEndedNull() {
        //MahjongManager.getInstance().sendRemoteMessage();
    }

    private void notifyGameEnded() {
        //MahjongManager.getInstance().sendRemoteMessage();
    }

    private void notifyPlayerLeft() {
        //MahjongManager.getInstance().sendRemoteMessage(uiMessageContent);
    }

    private void notifyGameOverTo3Players() {
        MahjongManager.getInstance().sendMessage2RemoteManager(ConnMessage.MSG_GAME_OVER, null);
    }

    private void updateRemainingTileNum(int remainingNum) {
        mTextViewRemainTiles.setText(getString(R.string.format_remaining_tile_num, remainingNum));
        if (remainingNum <= MahjongManager.getInstance().getGameMinTileCount()) {
            initInfoView();
        }
    }

    private void updateGangCountView() {
        int gangCount = MahjongManager.getInstance().getGame().getGangCount();
        if (gangCount > 0) {
            mTextViewGangCount.setVisibility(View.VISIBLE);
            mTextViewGangCount.setText(getString(R.string.format_gang_count, gangCount));
        } else {
            mTextViewGangCount.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int repeatCount = event.getRepeatCount();
        if (repeatCount == 0) {
            switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (MahjongManager.getInstance().isPlaying()) {
                    showEndGameConfirm(true);
                    return false;
                }
                notifyGameOverTo3Players();
                MahjongManager.getInstance().clear();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        stopNetwork();

        if (sDetermingIgnoredTimerUsed) {
            stopDeterminingTimer();
        }
        if (isFinishing()) {
            MahjongManager.getInstance().clear();
            MahjongManager.getInstance().setMainThreadHandler(null);
            mHandler.removeCallbacksAndMessages(null);
        }

        super.onDestroy();
    }

    private void stopWifiCommunicate() {
        if (Constants.sMahjongUseTcp) {
            RemoteConnector.getInstance().stopWifiTcp();
        } else {
            RemoteConnector.getInstance().stopWifiUdp1(null);
        }
    }

    private void stopNetwork() {
        switch (Constants.sNetwork) {
            case Wifi:
                if (WifiUtils.isWifiConnected(this)) {
                    stopWifiCommunicate();
                }
                break;
            case Hotspot:
                break;
            case Bluetooth:
                break;
            default:
                break;
        }
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }*/

}
