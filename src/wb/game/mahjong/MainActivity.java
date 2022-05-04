package wb.game.mahjong;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import wb.conn.MessageInfo;
import wb.conn.MessageInfo.MessageType;
import wb.conn.MessageUtils;
import wb.conn.RemoteMessage;
import wb.conn.RemoteMessage.ConnMessage;
import wb.conn.RemoteMessage.DataType;
import wb.conn.bluetooth.BluetoothUtils;
import wb.conn.bluetooth.BluetoothUtils.ScanActionListener;
import wb.conn.bluetooth.BluetoothUtils.StateChangeListener;
import wb.conn.wifi.HotspotUtils;
import wb.conn.wifi.WifiUtils;
import wb.game.mahjong.constants.Constants;
import wb.game.mahjong.constants.Constants.User;
import wb.game.mahjong.constants.TileResources;
import wb.game.mahjong.model.BluetoothPlayer;
import wb.game.mahjong.model.GameResource;
import wb.game.mahjong.model.GameResource.Game;
import wb.game.mahjong.model.Player.Gender;
import wb.game.mahjong.model.RemotePlayer;
import wb.game.mahjong.model.RemotePlayer.PlayerConnectState;
import wb.game.mahjong.model.Tile;
import wb.game.mahjong.model.WifiPlayer;
import wb.game.utils.BitmapUtils;
import wb.game.utils.NetworkUtils;
import wb.game.utils.Utils;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_SELECT_IMAGE_FILE = 1;
    private static final int REQUEST_ENABLE_WIFI = 2;
    private static final int REQUEST_ENABLE_HOTSPOT = 3;
    private static final int REQUEST_ENABLE_BT = 4;

    private static final String PREFERENCES_SETTINGS = "settings";

    private static final String KEY_USERS = "users";
    private static final String KEY_LATEST_GAMES = "latest_games";

    private static final String SEPARATOR_LATEST_GAMES = ";";

    private final ArrayList<User> mUsers = new ArrayList<User>();

    private UserListAdapter mUserListAdapter;

    private final ArrayList<BluetoothPlayer> mBluetoothPlayers = new ArrayList<BluetoothPlayer>();

    private BluetoothPlayerListAdapter mBluetoothPlayerListAdapter;

    private final ArrayList<WifiPlayer> mWifiPlayers = new ArrayList<WifiPlayer>();

    // 最多需要3个remotePlayer.
    private final ArrayList<RemotePlayer> mSelectedPlayers = new ArrayList<RemotePlayer>(3);

    private RemotePlayerListAdapter mWifiPlayerListAdapter;

    public static class LatestGame {
        private final String mGameSettingsName;

        public final Game game;

        public final int gameIndex;

        public Date latestUsedTime;

        public LatestGame(final String gameSettingsName) {
            this(gameSettingsName, new Date(System.currentTimeMillis()));
        }

        public LatestGame(final String gameSettingsName, final Date latestUsedTime) {
            mGameSettingsName = gameSettingsName;

            gameIndex = GameResource.getGameIndex(gameSettingsName);
            game = GameResource.getGame(gameIndex);
            this.latestUsedTime = latestUsedTime;
        }

        public boolean isSameGame(LatestGame latestGame) {
            if (gameIndex == latestGame.gameIndex) return true;
            if (mGameSettingsName.equals(latestGame.mGameSettingsName)) return true;
            return false;
        }

        private static final String FORMAT_TOSTRING = "%s%s%s";
        private static final String SEPARATOR_LATEST_GAME_INFO = ",";

        @Override
        public String toString() {
            return String.format(FORMAT_TOSTRING, mGameSettingsName, SEPARATOR_LATEST_GAME_INFO,
                            Long.toString(latestUsedTime.getTime()));
        }

        public static String parseGameSettingsName(String latestGameInfo) {
            String[] array = latestGameInfo.split(SEPARATOR_LATEST_GAME_INFO);
            if (array.length < 2) {
                throw new RuntimeException("Invalid latest game info format?!\n" + latestGameInfo);
            }
            String gameSettingsName = array[0];
            //Date date = new Date(Long.parseLong(array[1]));
            return gameSettingsName;
        }
    }
    private final ArrayList<LatestGame> mLatestGames = new ArrayList<LatestGame>(GameResource.sShowLatestGameCount + 1);

    private LatestGameArrayAdapter mLatestGameListAdapter;

    private final RemoteConnector.RemoteListener mRemoteListener = new RemoteConnector.RemoteListener() {
        @Override
        public void onException(final Exception e, final String log) {
            final Context context = MainActivity.this;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.isEmpty(log)) {
                        Utils.showInfo(context, getString(R.string.label_error),
                                        Utils.getExceptionInfo(e));
                    } else {
                        Utils.showInfo(context, getString(R.string.label_error),
                                        log + "\n" + Utils.getExceptionInfo(e));
                    }
                }
            });
        }

        @Override
        public void onError(final String errorInfo) {
            final Context context = MainActivity.this;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.isEmpty(errorInfo)) {
                        Utils.showInfo(context, getString(android.R.string.dialog_alert_title),
                                        "Why no errorInfo?!");
                    } else {
                        Utils.showInfo(context, getString(R.string.label_error), errorInfo);
                    }
                }
            });
        }

        @Override
        public void handleReceivedMessage(final MessageInfo messageInfo) {
            final RemoteMessage remoteMessage = RemoteMessage.parse(messageInfo);
            Constants.debug("Received in MainActivity, " + remoteMessage);
            handleRemoteMessage(remoteMessage);
        }
    };

    /* The method is running in UI thread.
     *
     * To connect to remote:
     * Local --  MSG_CONNECT_0(name+gender) ->   Remote
     *       <-- MSG_CONNECT_1(name+gender) --
     *       <-- MSG_SEND_ICON --
     * connected...
     *       --  MSG_SEND_ICON  -------------->
     *                                         .connected...
     *
     * To disconnect with the remote:
     * Local --  MSG_DISCONNECT ->    Remote
     * disconnected
     */
    private void handleRemoteMessage(final RemoteMessage remoteMessage) {
        switch (remoteMessage.connMessage) {
            case MSG_UDP_SCAN:
                addRemote(remoteMessage.remoteIp);
                break;
            case MSG_UDP_OK:
                connectRemote(remoteMessage.remoteIp);
                break;
            case MSG_UDP_CONNECT_0: // 远端发来connect_0.
                updateRemotePlayer(remoteMessage);
                sendNameGender(remoteMessage.remoteIp, ConnMessage.MSG_UDP_CONNECT_1); // 收到后发出connect_1.
                sendIcon(remoteMessage.remoteIp);// 再发出send_icon.
                break;
            case MSG_UDP_CONNECT_1: // 已经连上远端，远端收到connect_0，发回connect_1
                updateRemotePlayer(remoteMessage);
                sendIcon(remoteMessage.remoteIp);// 再发出send_icon.
                break;
            case MSG_UDP_SEND_ICON: // 收到远端send_icon
                updateRemotePlayerIcon(remoteMessage);
                break;
            case MSG_UDP_PLAYER_UPDATE:
                updateRemotePlayer(remoteMessage);
                break;
            case MSG_UDP_GOTO_GAME_REQUEST:
                showStartGameDialog(remoteMessage);
                break;
            case MSG_UDP_GOTO_GAME_OK:
                break;
            case MSG_UDP_GOTO_GAME_NO:
                break;
            case MSG_UDP_GOTO_GAME:
                gotoGame(remoteMessage);
                break;
            case MSG_DISCONNECT:
                removeRemotePlayer(remoteMessage.remoteIp);
                break;
            default:
                throw new RuntimeException("Why comes to MainActivity?! " + remoteMessage.connMessage);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clearFiles();

        initResources();

        initViewsForUser();

        initNetwork();
    }

    private void clearFiles() {
        final File filesDir = getFilesDir();
        if (filesDir == null || !filesDir.isDirectory()) return;

        final long curTime = System.currentTimeMillis();
        (new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                WifiPlayer.clearFiles(filesDir, curTime);
                return null;
            }}).execute();
    }

    private void initNetwork() {
        switch (Constants.sNetwork) {
            case Wifi:
                if (WifiUtils.isWifiConnected(this)) {
                    RemoteConnector.getInstance().startWifiUdp(mRemoteListener);
                }
                initViewsForWifi();
                break;
            case Hotspot:
                RemoteConnector.getInstance().start(mRemoteListener);
                initViewsForHotspot();
                break;
            case Bluetooth:
                RemoteConnector.getInstance().start(mRemoteListener);
                initViewsForBluetooth();
                break;
            default:
                break;
        }
    }

    private void initResources() {
        createDefaultIcon();
        Constants.initStrings(this);
        GameResource.init(this);
        TileResources.init(getResources());
        Tile.initResources(this);
    }

    private void initViewsForUser() {
        Button buttonAddUser = (Button)findViewById(R.id.button_add_user);
        buttonAddUser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showUserInfoDialog(null);
            }
        });

        final ListView userList = (ListView)findViewById(R.id.user_list);
        loadUsers(userList);

        mLatestGameListAdapter = new LatestGameArrayAdapter(this, R.layout.game_item);

        ListView latestGameList = (ListView)findViewById(R.id.latest_game_list);
        latestGameList.setAdapter(mLatestGameListAdapter);
        latestGameList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                User user = mUserListAdapter.getSelectedUser();
                if (user == null) {
                    Utils.showInfo(MainActivity.this, getString(R.string.label_prompt),
                            getString(R.string.prompt_user_info_not_set));
                } else {
                    LatestGame selected = mLatestGames.get(position);
                    gameSelected(user, selected.mGameSettingsName, selected.gameIndex);
                }
            }
        });
        loadLatestGames();

        View moreGamesView = findViewById(R.id.text_more_games);
        moreGamesView.setVisibility(GameResource.getGameNum() > GameResource.sShowLatestGameCount ?
                        View.VISIBLE : View.GONE);
    }

    // If renaming available, the new name is returned;
    // Otherwise null is returned.
    private String renameBluetoothAvailable() {
        if (!BluetoothUtils.isBluetoothEnabled()) return null;

        String deviceName = BluetoothUtils.getDeviceName();
        User user = mUserListAdapter.getSelectedUser();
        if (TextUtils.equals(deviceName, user.user_name)) {
            return null;
        }
        return user.user_name;
    }

    private void initViewsForBluetooth() {
        findViewById(R.id.for_bluetooth).setVisibility(View.VISIBLE);

        final boolean btSupported = BluetoothUtils.isBluetoothSupported();

        final Button buttonOpenBt = (Button)findViewById(R.id.button_open_bluetooth);
        final Button buttonCloseBt = (Button)findViewById(R.id.button_close_bluetooth);
        final Button buttonScanPlayers = (Button)findViewById(R.id.button_scan_bluetooth_players);
        final View   viewPromptScanning = findViewById(R.id.text_scanning_players);

        if (!btSupported) {
            final TextView textViewBtInfo = (TextView)findViewById(R.id.text_bt_info);
            textViewBtInfo.setVisibility(View.VISIBLE);
            textViewBtInfo.setText(R.string.prompt_bluetooth_not_supported);

            buttonOpenBt.setVisibility(View.GONE);
            buttonCloseBt.setVisibility(View.GONE);
            buttonScanPlayers.setVisibility(View.GONE);
            viewPromptScanning.setVisibility(View.GONE);

            final Button buttonRenameBt = (Button)findViewById(R.id.button_rename_bluetooth);
            buttonRenameBt.setVisibility(View.GONE);
            return;
        }

        BluetoothUtils.registerStateChangeReceiver(this, new StateChangeListener() {
            @Override
            public void onStateOff() {
                initRemotePlayers(false);
                refreshBtViews(buttonOpenBt, buttonCloseBt, buttonScanPlayers, viewPromptScanning);
            }

            @Override
            public void onStateOn() {
                refreshBtViews(buttonOpenBt, buttonCloseBt, buttonScanPlayers, viewPromptScanning);
            }
        });

        ListView playerListView = (ListView)findViewById(R.id.player_list);
        mBluetoothPlayerListAdapter = new BluetoothPlayerListAdapter(this, R.layout.bluetooth_player_item);
        playerListView.setAdapter(mBluetoothPlayerListAdapter);
        playerListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                BluetoothPlayer player = mBluetoothPlayers.get(position);
                if (player.isNotConnected()) {
                    connectRemotePlayerAsync(player);
                }
            }
        });

        refreshBtViews(buttonOpenBt, buttonCloseBt, buttonScanPlayers, viewPromptScanning);
    }

    private void initViewsForWifi() {
        findViewById(R.id.for_wifi).setVisibility(View.VISIBLE);

        final Button   buttonOpenWifi = (Button)findViewById(R.id.button_open_wifi);
        final Button   buttonScanPlayers = (Button)findViewById(R.id.button_scan_wifi_players);
        final Button   buttonConnectPlayer = (Button)findViewById(R.id.button_connect_wifi_player);
        final TextView textViewWifiInfo = (TextView)findViewById(R.id.text_wifi_info);

        final TextView viewPromptScanning = (TextView)findViewById(R.id.text_scanning_players);

        buttonConnectPlayer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showConnectDialog();
            }
        });

        WifiUtils.registerConnectionStateChangeReceiver(this, new WifiUtils.ConnectionStateChangeListener() {
            @Override
            public void onConnected() {
                refreshWifiViews(buttonOpenWifi, buttonScanPlayers, buttonConnectPlayer,
                                viewPromptScanning, textViewWifiInfo);
            }

            @Override
            public void onDisconnected() {;
                initRemotePlayers(false);
                refreshWifiViews(buttonOpenWifi, buttonScanPlayers, buttonConnectPlayer,
                                viewPromptScanning, textViewWifiInfo);
            }
        });

        ListView playerListView = (ListView)findViewById(R.id.player_list);
        mWifiPlayerListAdapter = new RemotePlayerListAdapter(this, R.layout.remote_player_item);
        playerListView.setAdapter(mWifiPlayerListAdapter);
        /*playerListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                WifiPlayer player = mWifiPlayers.get(position);
                if (player.isNotConnected()) {
                    connectRemotePlayerAsync(player);
                }
            }
        });*/

        refreshWifiViews(buttonOpenWifi, buttonScanPlayers, buttonConnectPlayer, viewPromptScanning,
                        textViewWifiInfo);
    }

    private void initViewsForHotspot() {
        findViewById(R.id.for_hotspot).setVisibility(View.VISIBLE);

        final boolean isHotspot = HotspotUtils.isHotspot();

        final Button buttonSetAsHotSpot = (Button)findViewById(R.id.button_set_as_hotspot);
        final Button buttonCloseHotSpot = (Button)findViewById(R.id.button_close_hotspot);
        final Button buttonScanPlayers = (Button)findViewById(R.id.button_scan_wifi_players);
        final View   viewPromptScanning = findViewById(R.id.text_scanning_players);

        if (!isHotspot) {
            final TextView textViewHotspotInfo = (TextView)findViewById(R.id.text_hotspot_info);
            textViewHotspotInfo.setVisibility(View.GONE/*View.VISIBLE*/);
            textViewHotspotInfo.setText("hotspotInfo");

            buttonSetAsHotSpot.setVisibility(View.GONE);
            buttonCloseHotSpot.setVisibility(View.GONE);
            buttonScanPlayers.setVisibility(View.GONE);
            viewPromptScanning.setVisibility(View.GONE);

            final Button buttonRenameHotspot = (Button)findViewById(R.id.button_rename_hotspot);
            buttonRenameHotspot.setVisibility(View.GONE);
            return;
        }

        ListView playerListView = (ListView)findViewById(R.id.player_list);
        mWifiPlayerListAdapter = new RemotePlayerListAdapter(this, R.layout.remote_player_item);
        playerListView.setAdapter(mWifiPlayerListAdapter);
        playerListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                WifiPlayer player = mWifiPlayers.get(position);
                if (player.isNotConnected()) {
                    connectRemotePlayerAsync(player);
                }
            }
        });

        refreshHotspotViews(buttonSetAsHotSpot, buttonCloseHotSpot, buttonScanPlayers, viewPromptScanning);
    }

    private void refreshWifiViews(final Button buttonOpenWifi, final Button buttonScanPlayers,
                    final Button buttonConnectPlayer, final TextView viewPromptScanning,
                    final TextView textViewWifiInfo) {
        final boolean wifiConnected = NetworkUtils.isWifiConnected(this);

        viewPromptScanning.setVisibility(View.GONE);

        if (wifiConnected) {
            buttonOpenWifi.setVisibility(View.GONE);
            buttonOpenWifi.setOnClickListener(null);

            buttonScanPlayers.setVisibility(View.VISIBLE);
            buttonScanPlayers.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    final User user = mUserListAdapter.getSelectedUser();
                    if (user == null) {
                        Utils.showInfo(MainActivity.this, getString(R.string.label_prompt),
                                getString(R.string.prompt_user_info_not_set));
                        return;
                    }

                    initRemotePlayers(false);

                    setVisibilityForProgressBar(true);

                    scanWifiPlayersAsync(buttonScanPlayers, viewPromptScanning);
                }
            });

            buttonConnectPlayer.setVisibility(View.VISIBLE);

            textViewWifiInfo.setText(WifiUtils.getWifiInfo(this));
            textViewWifiInfo.setVisibility(View.VISIBLE);
        } else {
            buttonOpenWifi.setVisibility(View.VISIBLE);
            buttonOpenWifi.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent enableIntent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_WIFI);
                }
            });

            buttonScanPlayers.setVisibility(View.GONE);
            buttonScanPlayers.setOnClickListener(null);

            buttonConnectPlayer.setVisibility(View.GONE);

            textViewWifiInfo.setText(R.string.prompt_wifi_not_enabled);
            textViewWifiInfo.setVisibility(View.VISIBLE);
        }
    }

    private void scanWifiPlayersAsync(final Button buttonScanPlayers,
                    final TextView viewPromptScanning) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                buttonScanPlayers.setVisibility(View.INVISIBLE);

                viewPromptScanning.setVisibility(View.VISIBLE);
                viewPromptScanning.setText(R.string.scanning_wifi_players);

                super.onPreExecute();
            }

            private void scanWifiNeighbors() {
                final String localIp = WifiUtils.getIpInWifi();
                final MessageType messageType = MessageType.Unknown;
                final byte[] messageData = RemoteMessage.constructMessageData(
                                ConnMessage.MSG_UDP_SCAN, DataType.NoContent, null);

                final String dot = ".";
                int secondDotIndex = localIp.substring(0, localIp.lastIndexOf(dot)).lastIndexOf(dot);
                final String ipPrefix = localIp.substring(0, secondDotIndex);
                final String formatDestIp = "%s.%d.%d";
                String destIp;

                ArrayList<String> destIps = new ArrayList<String>(256);
                for (int i = 0; i < 255; i++) {
                    destIps.clear();
                    for (int j = 0; j < 255; j++) {
                        destIp = String.format(formatDestIp, ipPrefix, i, j);
                        if (TextUtils.equals(localIp, destIp)) continue;
                        destIps.add(destIp);
                    }
                    RemoteConnector.getInstance().sendMessageUdp(messageType, messageData,
                                    destIps.toArray(new String[destIps.size()]));
                }
            }

            // 使用UDP发广播, ***现在不成功，待搞定...先使用ip列表挨个发吧.
            private void scanWifiNeighbors1() {
                final String localIp = WifiUtils.getIpInWifi();
                final MessageType messageType = MessageType.Unknown;
                final byte[] messageData = RemoteMessage.constructMessageData(
                                ConnMessage.MSG_UDP_SCAN, DataType.NoContent, null);

                final String dot = ".";
                int secondDotIndex = localIp.substring(0, localIp.lastIndexOf(dot)).lastIndexOf(dot);
                final String ipPrefix = localIp.substring(0, secondDotIndex);
                final String formatDestIp = "%s.%d.%d";
                String[] destIps = new String[] {
                                String.format(formatDestIp, ipPrefix,   0, 255),
                                String.format(formatDestIp, ipPrefix, 255, 255),
                };
                RemoteConnector.getInstance().sendMessageUdp(messageType, messageData, destIps);
            }

            @Override
            protected Void doInBackground(Void... params) {
                scanWifiNeighbors(); // - send to IPs.
                //scanWifiNeighbors1(); // - send udp broadcast.
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                updateWifiPlayerList(true);

                super.onPostExecute(result);
            }
        }.execute();
    }

    private String getRecentlyUsedIp() {
        int wifiPlayerCount = mWifiPlayers.size();
        if (wifiPlayerCount > 0) {
            return mWifiPlayers.get(wifiPlayerCount - 1).ipv4;
        }
        return WifiUtils.getIpInWifi();
    }

    private static boolean isLocalIp(final String destIp) {
        return destIp != null && destIp.equals(WifiUtils.getIpInWifi());
    }

    private void showConnectDialog() {
        final Context context = this;
        final String recentlyUsedIp = getRecentlyUsedIp();
        Utils.ViewInitWithPositiveNegative viewInit = new Utils.ViewInitWithPositiveNegative() {
            private EditText inputIpEdit;
            @Override
            public void initViews(View rootView) {
                inputIpEdit = (EditText)rootView.findViewById(R.id.edit_ip);
                inputIpEdit.setText(recentlyUsedIp);
            }

            @Override
            public void onPositiveClick(View rootView) {
                String destIp = Utils.getEditTextInput(inputIpEdit);
                if (TextUtils.isEmpty(destIp)) {
                    Utils.showInfo(context, context.getString(R.string.label_prompt),
                                    context.getString(R.string.prompt_empty_ip));
                    return;
                }
                if (isLocalIp(destIp)) {
                    Utils.showInfo(context, context.getString(R.string.label_prompt),
                                    context.getString(R.string.prompt_same_local_ip));
                    return;
                }
                connectRemote(destIp);
            }

            @Override
            public void onNegativeClick(View rootView) {
                // TODO Nothing to be done?
            }
        };
        Utils.showViewDialog(context, R.layout.dialog_connect, getString(R.string.connect),
                        viewInit);
    }

    private void addRemote(final String remoteIp) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addWifiPlayer(null, null, remoteIp); // 在回msg_udp_ok之前，先加到列表.
                sendEmptyConnMessage(remoteIp, ConnMessage.MSG_UDP_OK);
            }
        });
    }

    private void connectRemote(final String remoteIp) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addWifiPlayer(null, null, remoteIp); // 在回msg_connect_0之前，先加到列表.
                sendNameGender(remoteIp, ConnMessage.MSG_UDP_CONNECT_0);
            }
        });
    }

    private void sendEmptyConnMessage(final String destIp, final ConnMessage connMessage) {
        RemoteMessage remoteMessage = new RemoteMessage(connMessage, destIp);
        sendMessage(remoteMessage.constructMessage());
    }

    // connMessage有3种case:
    // MSG_CONNECT_0, 起始连接;
    // MSG_CONNECT_1, 响应MSG_CONNECT_0;
    // MSG_PLAYER_UPDATE, 更新player信息.
    private void sendNameGender(final String destIp, final ConnMessage connMessage) {
        RemoteMessage remoteMessage = new RemoteMessage(connMessage, destIp, DataType.String,
                        MessageUtils.messageNameGender(mUserListAdapter.getSelectedUser()));
        sendMessage(remoteMessage.constructMessage());
    }

    private void sendIcon(final String destIp) {
        byte[] iconBytes = mUserListAdapter.getSelectedUser().getIconBytes();
        if (iconBytes == null || iconBytes.length <= 0) return;//Constants.debug("iconBytes len:" + iconBytes.length);
        RemoteMessage remoteMessage = new RemoteMessage(ConnMessage.MSG_UDP_SEND_ICON, destIp,
                        DataType.Bitmap, iconBytes);
        sendMessage(remoteMessage.constructMessage());
    }

    private void sendDisconnect(final String destIp) {
        RemoteMessage remoteMessage = new RemoteMessage(ConnMessage.MSG_DISCONNECT, destIp);
        sendMessage(remoteMessage.constructMessage());
    }

    private void addWifiPlayer(final String playerName, final Gender gender, final String playerIp) {
        RemotePlayer found = null;
        switch (Constants.sNetwork) {
            case Wifi:
                synchronized (mWifiPlayers) {
                    found = findWifiPlayer(playerIp);
                    if (found == null) {
                        found = new WifiPlayer(playerIp);
                        mWifiPlayers.add((WifiPlayer) found);
                    } else {
                        ((WifiPlayer) found).update(playerName, gender);
                    }
                    found.updateConnectState(RemotePlayer.PlayerConnectState.StateConnecting);
                }
                break;
            case Hotspot:
                initViewsForHotspot();
                break;
            case Bluetooth:
                initViewsForBluetooth();
                break;
            default:
                return;
        }
        if (found == null) return;
        updateRemotePlayerConnectState(found, PlayerConnectState.StateConnecting);
        updatePlayerList(true);
    }

    private void updateRemotePlayer(final RemoteMessage remoteMessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WifiPlayer found = findWifiPlayer(remoteMessage.remoteIp);
                if (found == null) {
                    found = MessageUtils.parseNameGender(remoteMessage.remoteIp,
                                    remoteMessage.content);
                    mWifiPlayers.add(found);
                } else {
                    MessageUtils.parseNameGender(found, remoteMessage.content);
                }
                updateRemotePlayerConnectState(found, PlayerConnectState.StateConnected);
                updatePlayerList(true);
            }
        });
    }

    private void updateRemotePlayerIcon(final RemoteMessage remoteMessage) {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WifiPlayer found = findWifiPlayer(remoteMessage.remoteIp);
                if (found == null) {
                    Utils.showInfo(context, getString(R.string.label_error),
                                    "No player " + remoteMessage.remoteIp + "?!");
                    return;
                }
                byte[] bitmapData = MessageUtils.parseIcon(remoteMessage.content);
                found.updateIcon(bitmapData);
                updateRemotePlayerConnectState(found, PlayerConnectState.StateConnected);
                updatePlayerList(true);
            }
        });
    }

    private void showStartGameDialog(final RemoteMessage remoteMessage) {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WifiPlayer found = findWifiPlayer(remoteMessage.remoteIp);
                if (found == null) {
                    Utils.showInfo(context, getString(R.string.label_error), "Why no player ?! - " + remoteMessage.remoteIp);
                    return;
                }
                int gameIndex = MessageUtils.parseGameIndex(remoteMessage.content);
                showStartGameDialog(found,
                                context.getString(GameResource.getGame(gameIndex).labelResId));
            }

            private void showStartGameDialog(final WifiPlayer remotePlayer, final String gameName) {
                final String startGamePrompt = getString(R.string.prompt_start_game,
                                remotePlayer.name, gameName);
                Utils.showConfirmDialog(context, startGamePrompt,
                                context.getString(R.string.agree_play_game),
                                /* positive button listener */
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        RemoteMessage remoteMessage = new RemoteMessage(
                                                        ConnMessage.MSG_UDP_GOTO_GAME_OK,
                                                        remotePlayer.ipv4);
                                        sendMessage(remoteMessage.constructMessage());
                                    }
                                }, context.getString(R.string.decline_play_game),
                                /* negative button listener */
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        RemoteMessage remoteMessage = new RemoteMessage(
                                                        ConnMessage.MSG_UDP_GOTO_GAME_NO,
                                                        remotePlayer.ipv4);
                                        sendMessage(remoteMessage.constructMessage());
                                    }
                                });
            }
        });
    }

    private WifiPlayer findWifiPlayer(final String ipv4) {
        for (WifiPlayer player : mWifiPlayers) {
            if (player.ipv4.equals(ipv4)) {
                return player;
            }
        }
        return null;
    }

    private void removeRemotePlayer(final String ipv4) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WifiPlayer found = findWifiPlayer(ipv4);
                if (found != null) {
                    mWifiPlayers.remove(found);
                    updatePlayerList(true);
                }
            }
        });
    }

    private void refreshHotspotViews(final Button buttonSetAsHotSpot, final Button buttonCloseHotSpot,
                    final Button buttonScanPlayers, final View viewPromptScanning) {
        refreshHotspotButtons(buttonSetAsHotSpot, buttonCloseHotSpot, buttonScanPlayers,
                        viewPromptScanning);
    }

    private boolean refreshHotspotInfo() {
        final boolean hotspotConnected = HotspotUtils.isHotspot();
        final TextView textViewWifiInfo = (TextView)findViewById(R.id.text_hotspot_info);
        if (hotspotConnected) {
            textViewWifiInfo.setVisibility(View.VISIBLE);
            textViewWifiInfo.setText(BluetoothUtils.getInfo(this));
        } else {
            textViewWifiInfo.setVisibility(View.GONE);
            textViewWifiInfo.setText(null);
        }
        return hotspotConnected;
    }

    private void refreshHotspotButtons(final Button buttonSetAsHotSpot, final Button buttonCloseHotSpot,
                    final Button buttonScanPlayers, final View viewPromptScanning) {
        final boolean hotspotConnected = refreshHotspotInfo();
        if (hotspotConnected) {
            buttonSetAsHotSpot.setVisibility(View.GONE);
            buttonSetAsHotSpot.setOnClickListener(null);

            buttonCloseHotSpot.setVisibility(View.VISIBLE);
            buttonCloseHotSpot.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConfirmDisableHotspot();
                }
            });

            viewPromptScanning.setVisibility(View.GONE);
            buttonScanPlayers.setVisibility(View.VISIBLE);
            buttonScanPlayers.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    initRemotePlayers(false);

                    viewPromptScanning.setVisibility(View.VISIBLE);
                    buttonScanPlayers.setVisibility(View.GONE);
                    setVisibilityForProgressBar(true);
                }
            });
        } else {
            buttonSetAsHotSpot.setVisibility(View.VISIBLE);
            buttonSetAsHotSpot.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_HOTSPOT);
                }
            });

            buttonCloseHotSpot.setVisibility(View.GONE);
            buttonCloseHotSpot.setOnClickListener(null);

            viewPromptScanning.setVisibility(View.GONE);
            buttonScanPlayers.setVisibility(View.GONE);
            buttonScanPlayers.setOnClickListener(null);
        }
    }

    private void updateRemotePlayerConnectState(RemotePlayer remotePlayer,
                    PlayerConnectState connectState) {
        remotePlayer.updateConnectState(connectState);
        switch (Constants.sNetwork) {
            case Wifi:
                mWifiPlayerListAdapter.notifyDataSetInvalidated();
                break;
            case Hotspot:
                Utils.showInfo(this, getString(R.string.label_error), "updateRemotePlayerConnectState - Not implemented!");
                break;
            case Bluetooth:
                mBluetoothPlayerListAdapter.notifyDataSetInvalidated();
                break;
            default:
                break;
        }
    }

    private void connectRemotePlayerAsync(final RemotePlayer remotePlayer) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                updateRemotePlayerConnectState(remotePlayer, PlayerConnectState.StateConnecting);
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Thread.sleep(3000L);
                } catch (Exception e) {
                }
                return Boolean.FALSE;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                PlayerConnectState state = (result ? PlayerConnectState.StateConnected
                                : PlayerConnectState.StateConnectFailed);
                updateRemotePlayerConnectState(remotePlayer, state);
                super.onPostExecute(result);
            }

        }.execute();
    }

    private boolean refreshBluetoothInfo() {
        final boolean btEnabled = BluetoothUtils.isBluetoothEnabled();
        final TextView textViewBtInfo = (TextView)findViewById(R.id.text_bt_info);
        if (btEnabled) {
            textViewBtInfo.setVisibility(View.VISIBLE);
            textViewBtInfo.setText(BluetoothUtils.getInfo(this));
        } else {
            textViewBtInfo.setVisibility(View.GONE);
            textViewBtInfo.setText(null);
        }
        return btEnabled;
    }

    private void updateRenameBluetoothStatus() {
        final Button buttonRenameBluetooth = (Button) findViewById(R.id.button_rename_bluetooth);
        final String newName = BluetoothUtils.isBluetoothEnabled() ? renameBluetoothAvailable()
                : null;
        if (TextUtils.isEmpty(newName)) {
            buttonRenameBluetooth.setVisibility(View.GONE);
            buttonRenameBluetooth.setOnClickListener(null);
            return;
        }
        buttonRenameBluetooth.setVisibility(View.VISIBLE);
        buttonRenameBluetooth.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                renameBluetoothAsync(buttonRenameBluetooth, newName);
            }
        });
    }

    private void renameBluetoothAsync(final Button buttonRenameBluetooth, final String newName) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                buttonRenameBluetooth.setVisibility(View.GONE);
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                if (BluetoothUtils.renameBluetooth(newName)) {
                    return Boolean.TRUE;
                }
                return Boolean.FALSE;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                refreshBluetoothInfo();
                updateRenameBluetoothStatus();
                super.onPostExecute(result);
            }
        }.execute();
    }

    private void initRemotePlayers(boolean isScanFinished) {
        switch (Constants.sNetwork) {
            case Wifi:
            case Hotspot:
                clearWifiPlayers();
                break;
            case Bluetooth:
                mBluetoothPlayers.clear();
                mBluetoothPlayerListAdapter.notifyDataSetChanged();
                break;
            default:
                return;
        }
        updatePlayerList(isScanFinished);
    }

    private void refreshBtViews(final Button buttonOpenBt, final Button buttonCloseBt,
            final Button buttonScanPlayers, final View viewPromptScanning) {
        refreshBluetoothButtons(buttonOpenBt, buttonCloseBt, buttonScanPlayers, viewPromptScanning);
        updateRenameBluetoothStatus();
    }

    private void refreshBluetoothButtons(final Button buttonOpenBt, final Button buttonCloseBt,
            final Button buttonScanPlayers, final View viewPromptScanning) {
        final boolean btEnabled = refreshBluetoothInfo();
        if (btEnabled) {
            buttonOpenBt.setVisibility(View.GONE);
            buttonOpenBt.setOnClickListener(null);

            buttonCloseBt.setVisibility(View.VISIBLE);
            buttonCloseBt.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConfirmDisableBluetooth();
                }
            });

            viewPromptScanning.setVisibility(View.GONE);
            buttonScanPlayers.setVisibility(View.VISIBLE);
            buttonScanPlayers.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    initRemotePlayers(false);
                    if (!BluetoothUtils.startDiscovery()) return;

                    viewPromptScanning.setVisibility(View.VISIBLE);
                    buttonScanPlayers.setVisibility(View.GONE);
                    setVisibilityForProgressBar(true);

                    BluetoothUtils.registerScanActionReceiver(MainActivity.this,
                            new ScanActionListener() {
                                @Override
                                public void onDeviceFound(BluetoothDevice device) {
                                    checkDevice(device);
                                }

                                @Override
                                public void onDiscoveryFinished() {
                                    BluetoothUtils.unregisterScanActionReceiver(MainActivity.this);
                                    setVisibilityForProgressBar(false);
                                    if (!isFinishing()) {
                                        viewPromptScanning.setVisibility(View.GONE);
                                        buttonScanPlayers.setVisibility(View.VISIBLE);
                                        if (mBluetoothPlayers.size() <= 0) {
                                            updatePlayerList(true);
                                        }
                                    }
                                }
                    });
                }
            });
        } else {
            buttonOpenBt.setVisibility(View.VISIBLE);
            buttonOpenBt.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
            });

            buttonCloseBt.setVisibility(View.GONE);
            buttonCloseBt.setOnClickListener(null);

            viewPromptScanning.setVisibility(View.GONE);
            buttonScanPlayers.setVisibility(View.GONE);
            buttonScanPlayers.setOnClickListener(null);
        }
    }

    // setVisibilityForProgressBar is No longer supported starting in Lollipop(API 21).
    private void setVisibilityForProgressBar(final boolean visible) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setProgressBarIndeterminateVisibility(visible);
        } else {
        }
    }

    private void checkDevice(BluetoothDevice device) {
        String bluetoothName = device.getName();
        Gender gender = Gender.Male;
        String bluetoothAddr = device.getAddress();
        BluetoothPlayer player = new BluetoothPlayer(bluetoothName + "-" + bluetoothAddr, gender,
                        null, bluetoothName, bluetoothAddr);
        mBluetoothPlayers.add(player);
        updatePlayerList(false);
        mBluetoothPlayerListAdapter.notifyDataSetChanged();
    }

    // 响应id/text_more_games onClick.
    public void onMoreGames(View view) {
        final Context context = this;
        Utils.showListDialog(context, getString(R.string.more),
                        new GameArrayAdapter(context, R.layout.game_item),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                User user = mUserListAdapter.getSelectedUser();
                                if (user == null) {
                                    Utils.showInfo(context, getString(R.string.label_prompt),
                                            getString(R.string.prompt_user_info_not_set));
                                    addLatestGame(GameResource.getGameSettingsName(which));
                                } else {
                                    gameSelected(user, GameResource.getGameSettingsName(which), which);
                                }
                                dialog.dismiss();
                            }
                        });
    }

    private void gameSelected(final User user, final String gameSettingsName, final int gameIndex) {
        switch (Constants.sNetwork) {
            case Wifi:
                if (!hasConnectedWifiPlayers(gameIndex) || sDirectGameStart) {
                    gotoGame(user, gameSettingsName, gameIndex, null);
                }
                break;
            case Hotspot:
            case Bluetooth:
            default:
                gotoGame(user, gameSettingsName, gameIndex, null);
                break;
        }
    }

    // 是否不经询问直接开始打。为true时直接开始打。false则先发送request询问是否同意开打.
    private static final boolean sDirectGameStart = true;

    private boolean hasConnectedWifiPlayers(final int gameIndex) {
        if (mWifiPlayers.size() <= 0) return false;
        boolean hasConnectedWifiPlayers = false;
        for (WifiPlayer player : mWifiPlayers) {
            if (!player.isConnected()) continue;
            hasConnectedWifiPlayers = true;
            if (sDirectGameStart) {
                notifyPlayerGotoGame(player, gameIndex);
            } else {
                sendGotoGameRequest(player, gameIndex);
            }
        }
        return hasConnectedWifiPlayers;
    }

    private void notifyPlayerGotoGame(final WifiPlayer wifiPlayer, final int gameIndex) {
        RemoteMessage remoteMessage = new RemoteMessage(ConnMessage.MSG_UDP_GOTO_GAME,
                        wifiPlayer.ipv4, DataType.String, MessageUtils.messageGameIndex(gameIndex));
        sendMessage(remoteMessage.constructMessage());
    }

    private void sendGotoGameRequest(final WifiPlayer wifiPlayer, final int gameIndex) {
        RemoteMessage remoteMessage = new RemoteMessage(ConnMessage.MSG_UDP_GOTO_GAME_REQUEST,
                        wifiPlayer.ipv4, DataType.String, MessageUtils.messageGameIndex(gameIndex));
        sendMessage(remoteMessage.constructMessage());
    }

    private void sendMessage(MessageInfo messageInfo) {
        switch (Constants.sNetwork) {
            case Wifi:
                RemoteConnector.getInstance().sendMessageUdp(messageInfo);
                break;
            case Bluetooth:
                break;
            case Hotspot:
                break;
        }
    }

    private void gotoGame(final User user, final String gameSettingsName, final int gameIndex,
                    final String serverIp) {
        addLatestGame(gameSettingsName);
        startActivity(user, gameIndex, serverIp);
    }

    private void gotoGame(final RemoteMessage remoteMessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                User user = mUserListAdapter.getSelectedUser();
                String serverIp = remoteMessage.remoteIp;
                int gameIndex = MessageUtils.parseGameIndex(remoteMessage.content);
                gotoGame(user, GameResource.getGameSettingsName(gameIndex), gameIndex, serverIp);
            }
        });
    }

    private String[] getConnectedIps() {
        int count = mWifiPlayers.size();
        if (count <= 0) return null;
        ArrayList<String> connectedIps = new ArrayList<String>(count);
        for (WifiPlayer player : mWifiPlayers) {
            connectedIps.add(player.ipv4);
        }
        return connectedIps.toArray(new String[connectedIps.size()]);
    }

    @Override
    protected void onDestroy() {
        Constants.setDefaultIconBitmap(null);

        stopNetwork();

        super.onDestroy();
    }

    private void stopNetwork() {
        switch (Constants.sNetwork) {
            case Wifi:
                clearWifiPlayers();
                WifiUtils.unregisterConnectionStateChangeReceiver(this);
                if (WifiUtils.isWifiConnected(this)) {
                    RemoteConnector.getInstance().stopWifiUdp(getConnectedIps());
                }
                break;
            case Hotspot:
                RemoteConnector.getInstance().stop();
                break;
            case Bluetooth:
                BluetoothUtils.unegisterStateChangeReceiver(this);
                BluetoothUtils.unregisterScanActionReceiver(this);
                RemoteConnector.getInstance().stop();
                break;
            default:
                break;
        }
    }

    private void clearWifiPlayers() {
        for (WifiPlayer player : mWifiPlayers) {
            if (player.isConnected()) {
                sendDisconnect(player.ipv4);
            }
            mSelectedPlayers.remove(player);

            File file = new File(player.iconFilename);
            if (file.exists()) {
                file.delete();
            }
        }
        mWifiPlayers.clear();
        mWifiPlayerListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_SELECT_IMAGE_FILE:
                if (mUserInfoConfigDialog != null) {
                    mUserInfoConfigDialog.onActivityResult(requestCode, resultCode, data);
                }
                break;
            case REQUEST_ENABLE_WIFI:
                // When the request to enable wifi returns
                if (resultCode == Activity.RESULT_OK) {
                    // wifi is now enabled.
                    initViewsForWifi();
                } else {
                    // User did not enable wifi or an error occurred
                    Utils.showInfo(this, getString(R.string.label_prompt),
                                    getString(R.string.prompt_wifi_not_enabled));
                }
                break;
            case REQUEST_ENABLE_HOTSPOT:
                // When the request to enable hotspot returns
                if (resultCode == Activity.RESULT_OK) {
                    // Hotspot is now enabled.
                    initViewsForHotspot();
                } else {
                    // User did not enable hotspot or an error occurred
                    Utils.showInfo(this, getString(R.string.label_prompt),
                                    getString(R.string.prompt_hotspot_not_enabled));
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled.
                    initViewsForBluetooth();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Utils.showInfo(this, getString(R.string.label_prompt),
                                    getString(R.string.prompt_bluetooth_not_enabled));
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void createDefaultIcon() {
        String defaultFilepath = Constants.getInternalFilepath(Constants.DEFAULT_ICON_FILENAME);
        File file = new File(defaultFilepath);

        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
        Constants.setDefaultIconBitmap(bm);

        if (file.exists()) return;

        //file.createNewFile();
        BitmapUtils.saveBitmapToPngFile(bm, file);
    }

    private static final String FILE_TYPE_IMG = "image/*";

    private void gotoFileBrowser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(FILE_TYPE_IMG);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Intent chooserIntent = Intent.createChooser(intent, "File chooser");
        Intent finalIntent = chooserIntent;
        try {
            startActivityForResult(finalIntent, REQUEST_CODE_SELECT_IMAGE_FILE);
        } catch (android.content.ActivityNotFoundException ex) {
            Utils.showInfo(this, "No file explorer", ex.toString());
        }
    }

    private UserInfoDialog mUserInfoConfigDialog;

    private void showUserInfoDialog(final User user) {
        mUserInfoConfigDialog = new UserInfoDialog(user);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(mUserInfoConfigDialog, getString(R.string.user_info_config));
        fragmentTransaction.commit();
    }

    private User getUser(final String username) {
        for (User user : mUsers) {
            if (user.user_name.equals(username)) return user;
        }
        return null;
    }

    private void showDeleteConfirm(final User user) {
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle(R.string.label_prompt);
        builder.setMessage(getString(R.string.prompt_delete_user, user.user_name));
        builder.setPositiveButton(android.R.string.ok,
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeUser(user);
                        dialog.dismiss();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private void showConfirmDisableBluetooth() {
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle(R.string.label_prompt);
        builder.setMessage(R.string.prompt_disable_bluetooth);
        builder.setPositiveButton(android.R.string.ok,
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        BluetoothUtils.disableBluetooth();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private void showConfirmDisableHotspot() {
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle(R.string.label_prompt);
        builder.setMessage(R.string.prompt_close_hotspot);
        builder.setPositiveButton(android.R.string.ok,
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        HotspotUtils.disableHotspot();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private void removeUser(User user) {
        if (mUsers.remove(user)) {
            mUserListAdapter.updateSelected();
            usersChanged();
        }
        // Delete the user icon file
        File file = TextUtils.isEmpty(user.user_icon_filepath) ? null : new File(user.user_icon_filepath);
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private void usersChanged() {
        saveUsers();
        mUserListAdapter.notifyDataSetChanged();
        updateUserPromptView();
        updateRenameBluetoothStatus();
    }

    private void loadUsers(final ListView userList) {
        mUsers.clear();

        SharedPreferences settings = getSharedPreferences(PREFERENCES_SETTINGS, MODE_PRIVATE);
        Set<String> userSet = settings.getStringSet(KEY_USERS, new HashSet<String>());
        String[] array = userSet.toArray(new String[userSet.size()]);
        for (String userInfo : array) {
            mUsers.add(User.parseString(userInfo));
        }
        if (mUserListAdapter == null) {
            mUserListAdapter = new UserListAdapter(this, R.layout.user_item);
            userList.setAdapter(mUserListAdapter);
            userList.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                    setSelectedUser(position);
                }
            });
            setSelectedUser(0);
        } else {
            mUserListAdapter.notifyDataSetChanged();
        }
        updateUserPromptView();
    }

    private void updateUserPromptView() {
        View userPromptView = findViewById(R.id.text_users);
        userPromptView.setVisibility(mUsers.size() > 0 ? View.VISIBLE : View.GONE);
    }

    private void updatePlayerList(boolean isScanFinished) {
        int playerNum = 0;
        switch (Constants.sNetwork) {
            case Wifi:
                playerNum = updateWifiPlayerList(isScanFinished);
                if (playerNum > 0 && isScanFinished) {
                    Button   buttonScanPlayers = (Button)findViewById(R.id.button_scan_wifi_players);
                    buttonScanPlayers.setVisibility(View.VISIBLE);
                    TextView viewPromptScanning = (TextView)findViewById(R.id.text_scanning_players);
                    viewPromptScanning.setVisibility(View.GONE);
                    viewPromptScanning.setText(null);
                }
                mWifiPlayerListAdapter.notifyDataSetChanged();
                break;
            case Hotspot:
                break;
            case Bluetooth:
                updateBluetoothPlayerList(isScanFinished);
                mBluetoothPlayerListAdapter.notifyDataSetChanged();
                break;
            default:
                break;
        }
    }

    private int getConnectedPlayerNum() {
        int count = 0;
        for (WifiPlayer player : mWifiPlayers) {
            if (player.isConnected()) count++;
        }
        return count;
    }

    private int updateWifiPlayerList(boolean isScanFinished) {
        TextView playerPromptView = (TextView)findViewById(R.id.text_players);

        int playerNum = mWifiPlayers.size();

        playerPromptView.setVisibility(playerNum > 0 ? View.VISIBLE : View.GONE);

        if (playerNum > 0) {
            playerPromptView.setVisibility(View.VISIBLE);
            String playerNumString;
            int connectedPlayerNum = getConnectedPlayerNum();
            if (connectedPlayerNum > 0) {
                playerNumString = getString(R.string.format_connected_players_num,
                                connectedPlayerNum, playerNum);
            } else {
                playerNumString = getString(R.string.format_players_num, playerNum);
            }
            playerPromptView.setText(playerNumString);
        } else if (isScanFinished) {
            //playerPromptView.setText(R.string.prompt_after_scan_no_wifi_players);
            //playerPromptView.setVisibility(View.VISIBLE);
        } else {
            playerPromptView.setText(null);
            playerPromptView.setVisibility(View.GONE);
        }
        return playerNum;
    }

    private void updateBluetoothPlayerList(boolean isScanFinished) {
        TextView playerPromptView = (TextView)findViewById(R.id.text_players);
        int playerNum = mBluetoothPlayers.size();
        if (playerNum > 0) {
            playerPromptView.setVisibility(View.VISIBLE);
            String playerNumString = getString(R.string.format_players_num, playerNum);
            playerPromptView.setText(playerNumString);
        } else if (isScanFinished) {
            playerPromptView.setText(R.string.prompt_after_scan_no_bluetooth_players);
            playerPromptView.setVisibility(View.VISIBLE);
        } else {
            playerPromptView.setText(null);
            playerPromptView.setVisibility(View.GONE);
        }
    }

    private void setSelectedUser(final int index) {
        mUserListAdapter.setSelected(index);
        mUserListAdapter.notifyDataSetInvalidated();

        switch (Constants.sNetwork) {
            case Wifi:
                updateUserForWifiPlayers();
                break;
            case Hotspot:
                break;
            case Bluetooth:
                updateRenameBluetoothStatus();
                break;
            default:
                break;
        }
    }

    private void updateUserForWifiPlayers() {
        for (WifiPlayer player : mWifiPlayers) {
            if (!player.isConnected()) continue;
            sendNameGender(player.ipv4, ConnMessage.MSG_UDP_PLAYER_UPDATE);
            sendIcon(player.ipv4);// 再发出send_icon.
        }
    }

    private void saveUsers() {
        SharedPreferences settings = getSharedPreferences(PREFERENCES_SETTINGS, MODE_PRIVATE);
        Editor editor = settings.edit();

        Set<String> userSet = new HashSet<String>();
        for (User user : mUsers) {
            userSet.add(user.toString());
        }
        editor.putStringSet(KEY_USERS, userSet);

        editor.apply();
    }

    private void initLatestGames() {
        mLatestGames.clear();

        for (int i = 0; i < GameResource.sShowLatestGameCount; i++) {
            mLatestGames.add(new LatestGame(GameResource.getGameSettingsName(i)));
        }
    }

    private void loadLatestGames() {
        initLatestGames();

        SharedPreferences settings = getSharedPreferences(PREFERENCES_SETTINGS, MODE_PRIVATE);
        String[] latestGames = Utils.readStringArray(settings, KEY_LATEST_GAMES, null,
                        SEPARATOR_LATEST_GAMES);
        if (latestGames == null || latestGames.length <= 0) return;
        for (int i = latestGames.length - 1; i >= 0; i--) {// 倒着添加.
            addLatestGame(LatestGame.parseGameSettingsName(latestGames[i]));
        }
    }

    private void addLatestGame(final String gameSettingsName) {
        LatestGame found = null;
        for (LatestGame latest : mLatestGames) {
            if (latest.mGameSettingsName.equals(gameSettingsName)) {
                found = latest;
                mLatestGames.remove(latest);
                break;
            }
        }
        if (found == null) {
            found = new LatestGame(gameSettingsName);
        } else {
            found.latestUsedTime = new Date(System.currentTimeMillis());
        }
        mLatestGames.add(0, found);
        while (mLatestGames.size() > GameResource.sShowLatestGameCount) {
            mLatestGames.remove(mLatestGames.size() - 1);
        }
        saveLatestGames();
        if (!isFinishing()) {
            mLatestGameListAdapter.notifyDataSetChanged();
        }
    }

    private String getLatestGamesString() {
        StringBuilder sb = new StringBuilder();
        for (LatestGame latestGame : mLatestGames) {
            if (sb.length() > 0) sb.append(SEPARATOR_LATEST_GAMES);
            sb.append(latestGame.toString());
        }
        return sb.toString();
    }

    private void saveLatestGames() {
        SharedPreferences settings = getSharedPreferences(PREFERENCES_SETTINGS, MODE_PRIVATE);
        Editor editor = settings.edit();

        editor.putString(KEY_LATEST_GAMES, getLatestGamesString());

        editor.apply();
    }

    // 参数hostIp表示发起game的player ip。 hostIp为空时，也就是host.
    // host收到其他players的消息后，需要通知其他players都有哪些player参与.
    private void startActivity(final User user, final int gameIndex, final String hostIp) {
        final Class<?> clazz = MahJongActivity.class;
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.EXTRA_USER_INFO, user);

        if (TextUtils.isEmpty(hostIp)) {// 自己作host.
            checkSelectedPlayers();
            putSelectedPlayers(bundle);
        } else {// 收到host player要求start game.
            putHostPlayer(bundle, hostIp);
            bundle.putString(Constants.EXTRA_HOST_IP, hostIp);
        }

        bundle.putInt(Constants.EXTRA_GAME_INDEX, gameIndex);

        Intent intent = new Intent(this, clazz);
        intent.putExtras(bundle);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Utils.showInfo(this, e.toString(), String.format(
                    "Failed to start activity [%s]!%n%s\n%s", clazz.getName(), e,
                    Utils.getExceptionInfo(e)));
        }
    }

    private void checkSelectedPlayers() {
        int connectedPlayerCount = getConnectedPlayerNum();
        if (connectedPlayerCount <= 3 && mSelectedPlayers.size() == 0) {
            for (WifiPlayer player : mWifiPlayers) {
                if (player.isConnected()) {
                    mSelectedPlayers.add(player);
                }
            }
        }
    }

    private void putSelectedPlayers(final Bundle bundle) {
        final int remoteCount = mSelectedPlayers.size();
        if (remoteCount == 0) return;
        Parcelable[] parcelables = new Parcelable[remoteCount];
        for (int i = 0; i < remoteCount; i++) {
            RemotePlayer player = mSelectedPlayers.get(i);
            parcelables[i] = (Parcelable)player;
        }
        bundle.putParcelableArray(Constants.EXTRA_PLAYERS_INFO, parcelables);
    }

    private void putHostPlayer(final Bundle bundle, final String hostIp) {
        RemotePlayer found = null;
        switch (Constants.sNetwork) {
            case Wifi:
                found = findWifiPlayer(hostIp);
                break;
            case Hotspot:
                break;
            case Bluetooth:
                break;
            default:
                return;
        }
        if (found == null) return;
        if (found instanceof Parcelable) {
            bundle.putParcelableArray(Constants.EXTRA_PLAYERS_INFO,
                            new Parcelable[] { (Parcelable) found });
        }
    }

    private void startSoundCustomizeActivity(final User user) {
        final Class<?> clazz = CustomizeSoundActivity.class;
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.EXTRA_USER_INFO, user);

        Intent intent = new Intent(this, clazz);
        intent.putExtras(bundle);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Utils.showInfo(this, e.toString(), String.format(
                    "Failed to start activity [%s]!%n%s\n%s", clazz.getName(), e,
                    Utils.getExceptionInfo(e)));
        }
    }

    private void startActivity(final int gameIndex) {
        final Class<?> clazz = GameIntroductionActivity.class;
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.EXTRA_GAME_INDEX, gameIndex);

        Intent intent = new Intent(this, clazz);
        intent.putExtras(bundle);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Utils.showInfo(this, e.toString(), String.format(
                    "Failed to start activity [%s]!%n%s\n%s", clazz.getName(), e,
                    Utils.getExceptionInfo(e)));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_about, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                Utils.showInfo(this, getString(R.string.about), getString(R.string.about_text));
                return true;
            default:
                return super.onMenuItemSelected(featureId, item);
        }
    }

    private class UserInfoDialog extends DialogFragment {
        private final User mUser;

        private boolean mBitmapRotated;
        private Bitmap mBitmap;

        private ImageView mUserIconView;

        private TextView mIconFilepathTextView;

        public UserInfoDialog(User user) {
            mUser = user;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getContext();

            final String userName = mUser == null ? null : mUser.user_name;
            final Gender userGender = (mUser == null ? Gender.Male : mUser.user_gender);
            final String userIconFilepath = mUser == null ? null : mUser.user_icon_filepath;
            final String defaultUsername = getString(R.string.default_username);

            mBitmapRotated = false;

            AlertDialog.Builder builder = new Builder(context);
            builder.setTitle(R.string.user_info_config);

            View view = View.inflate(context, R.layout.user_info_config, null);

            Button buttonRotate90 = (Button)view.findViewById(R.id.button_rotate);
            buttonRotate90.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mBitmap == null) {
                        mBitmap = Constants.sDefaultIconBitmap;
                    }
                    mBitmap = BitmapUtils.rotateBitmap90(mBitmap);
                    mUserIconView.setImageBitmap(mBitmap);
                    mBitmapRotated = true;
                }
            });

            final EditText userNameEdit = (EditText)view.findViewById(R.id.edit_user_name);
            userNameEdit.setText(TextUtils.isEmpty(userName) ?  defaultUsername : userName);

            final RadioGroup genderRadioGroup = (RadioGroup)view.findViewById(R.id.user_gender);
            genderRadioGroup.check(userGender == Gender.Male ? R.id.radio_male : R.id.radio_female);

            mIconFilepathTextView = (TextView)view.findViewById(R.id.text_user_icon_filepath);
            mUserIconView = (ImageView) view.findViewById(R.id.user_icon_view);
            setIconFilepath(userIconFilepath);

            mUserIconView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    gotoFileBrowser();
                }
            });

            Button buttonCustomizeSound = (Button)view.findViewById(R.id.button_customize_sound);
            buttonCustomizeSound.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    startSoundCustomizeActivity(mUser);
                }
            });

            builder.setView(view);

            builder.setPositiveButton(android.R.string.ok,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    userChanged(userNameEdit,
                                                    userName,
                                                    genderRadioGroup.getCheckedRadioButtonId(),
                                                    userIconFilepath);
                                }
                            });
            builder.setNegativeButton(android.R.string.cancel, null);
            return builder.create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            clean();
            super.onDismiss(dialog);
        }

        private void userChanged(final EditText userNameEdit, final String userName,
                        final int userGenderRadioId, final String userIconFilepath) {
            String newUsername = Utils.getEditTextInput(userNameEdit);
            if (TextUtils.isEmpty(newUsername)) {
                Utils.showInfo(MainActivity.this, getString(R.string.label_prompt),
                        getString(R.string.prompt_invalid_user_name));
                clean();
                return;
            }
            final Context context = userNameEdit.getContext();
            CharSequence inputIconFilepath = mIconFilepathTextView.getText();
            String newIconFilepath = inputIconFilepath == null ? null : inputIconFilepath.toString();
            if (TextUtils.equals(newUsername, userName)) {
                if (mBitmapRotated || !TextUtils.equals(userIconFilepath, newIconFilepath)) {
                    newIconFilepath = Constants.getInternalFilepath(User.getIconFilename(newUsername));
                    BitmapUtils.copyToPngFile(mBitmap, newIconFilepath);
                    usersChanged();
                }
                return;
            }
            User findUser = getUser(newUsername);
            if (findUser != null && findUser != mUser) {
                Utils.showInfo(context, getString(R.string.label_prompt),
                        getString(R.string.prompt_user_name_exists, newUsername));
                return;
            }
            if (!TextUtils.equals(userIconFilepath, newIconFilepath)) {
                newIconFilepath = Constants.getInternalFilepath(User.getIconFilename(newUsername));
                // newIconFilepath是外部文件,
                // 需要copy到app files目录下.
                BitmapUtils.copyToPngFile(mBitmap, newIconFilepath);
            }
            if (!TextUtils.equals(newUsername, userName) &&
                    TextUtils.equals(userIconFilepath, newIconFilepath)) {
                if (mBitmapRotated) {
                    BitmapUtils.copyToPngFile(mBitmap, userIconFilepath);
                }
                newIconFilepath = Constants.getInternalFilepath(User.getIconFilename(newUsername));
                renameFile(userIconFilepath, newIconFilepath);
            }
            User newUser = new User(newUsername,
                            userGenderRadioId == R.id.radio_female ? Gender.Female : Gender.Male,
                            newIconFilepath);
            if (mUser != null) {
                mUsers.set(mUsers.indexOf(mUser), newUser);
                removeUser(mUser);
            } else {
                mUsers.add(newUser);
                setSelectedUser(mUsers.size() - 1);
            }
            usersChanged();
        }

        private boolean renameFile(String oldPath, String newPath) {
            File file = new File(oldPath);
            if (file != null && file.exists()) {
                return file.renameTo(new File(newPath));
            }
            return false;
        }

        private void clean() {
            if (mBitmap != Constants.sDefaultIconBitmap) {
                mBitmap.recycle();
            }
            mBitmap = null;
        }

        private void setIconFilepath(String iconFilepath) {
            if (iconFilepath == null) {
                mIconFilepathTextView.setText(null);
                mIconFilepathTextView.setVisibility(View.GONE);
            } else {
                mIconFilepathTextView.setVisibility(View.VISIBLE);
                mIconFilepathTextView.setText(iconFilepath);
            }
            mBitmap = Constants.getIcon(iconFilepath);
            if (mBitmap.getWidth() != Constants.sIconWidth
                            || mBitmap.getHeight() != Constants.sIconHeight) {
                mBitmap = BitmapUtils.zoomBitmap(mBitmap, Constants.sIconWidth,
                                Constants.sIconHeight);
            }
            mUserIconView.setImageBitmap(mBitmap);
        }

        private void handleOpenFileIntent(final Intent intent) {
            Uri uri = intent.getData();
            String filepath = Utils.getFilePath(getActivity(), uri);
            if (TextUtils.isEmpty(filepath)) {
                Utils.showInfo(getActivity(), getString(R.string.label_error),
                        getString(R.string.fail_to_get_filepath_from_uri, uri.toString(),
                                        uri.getScheme()));
            } else {
                setIconFilepath(filepath);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_CODE_SELECT_IMAGE_FILE && resultCode == RESULT_OK) {
                handleOpenFileIntent(data);
            } else {
                Utils.showInfo(getActivity(), getString(R.string.label_prompt), "No image file is selected!");
                return;
            }
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    class UserListAdapter extends ArrayAdapter<User> {
        private final int COLOR_NORMAL = Color.WHITE;
        private final int COLOR_SELECTED = Color.LTGRAY;

        private final int mResourceId;

        private final LayoutInflater mInflater;

        private int mSelectedIndex;

        public UserListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            mResourceId = textViewResourceId;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void setSelected(int index) {
            int total = getCount();
            if (index >= getCount()) {
                mSelectedIndex = total - 1;
            } else {
                mSelectedIndex = index;
            }
        }

        public void updateSelected() {
            setSelected(mSelectedIndex);
        }

        public int getSelected() {
            return getCount() <= 0 ? -1 : mSelectedIndex;
        }

        public User getSelectedUser() {
            if (mSelectedIndex >= 0 && mSelectedIndex < getCount()) {
                return getItem(mSelectedIndex);
            }
            return null;
        }

        @Override
        public int getCount() {
            return mUsers.size();
        }

        @Override
        public User getItem(int position) {
            return mUsers.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final User user = getItem(position);
            View view;
            if (convertView == null) {
                view = mInflater.inflate(mResourceId, parent, false);
            } else {
                view = convertView;
            }

            if (position == mSelectedIndex) {
                view.setBackgroundColor(COLOR_SELECTED);
            } else {
                view.setBackgroundColor(COLOR_NORMAL);
            }
            TextView usernameText = (TextView) view.findViewById(R.id.user_name);
            usernameText.setText(user.user_name);

            ImageView iconView = (ImageView)view.findViewById(R.id.user_icon);
            iconView.setImageBitmap(Constants.getIcon(user.user_icon_filepath));

            Button buttonDelete = (Button)view.findViewById(R.id.button_delete_user);
            buttonDelete.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    showDeleteConfirm(user);
                }
            });

            Button buttonModify = (Button)view.findViewById(R.id.button_modify_user);
            buttonModify.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    showUserInfoDialog(user);
                }
            });

            return view;
        }
    }

    class LatestGameArrayAdapter extends ArrayAdapter<LatestGame> {
        private final int mResourceId;

        private final LayoutInflater mInflater;

        public LatestGameArrayAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            mResourceId = textViewResourceId;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mLatestGames.size();
        }

        @Override
        public LatestGame getItem(int position) {
            return mLatestGames.get(position);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final LatestGame latestGame = getItem(position);
            View view;
            if (convertView == null) {
                view = mInflater.inflate(mResourceId, parent, false);
            } else {
                view = convertView;
            }

            TextView textViewLabel = (TextView) view.findViewById(R.id.game_label);
            textViewLabel.setText(latestGame.game.labelResId);

            Button buttonSettings = (Button)view.findViewById(R.id.game_settings);
            buttonSettings.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    latestGame.game.showSettings(mInflater.getContext());
                }
            });

            Button buttonIntroduction = (Button)view.findViewById(R.id.game_introduction);
            buttonIntroduction.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    startActivity(latestGame.gameIndex);
                }
            });
            return view;
        }
    }

    private class GameArrayAdapter extends ArrayAdapter<Game> {
        private final int mResourceId;

        private final LayoutInflater mInflater;

        public GameArrayAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            mResourceId = textViewResourceId;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return GameResource.getGameNum();
        }

        @Override
        public Game getItem(int position) {
            return GameResource.getGame(position);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final Game game = getItem(position);
            View view;
            if (convertView == null) {
                view = mInflater.inflate(mResourceId, parent, false);
            } else {
                view = convertView;
            }

            TextView textViewLabel = (TextView) view.findViewById(R.id.game_label);
            textViewLabel.setText(game.labelResId);

            Button buttonSettings = (Button)view.findViewById(R.id.game_settings);
            buttonSettings.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    game.showSettings(mInflater.getContext());
                }
            });

            Button buttonIntroduction = (Button)view.findViewById(R.id.game_introduction);
            buttonIntroduction.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    startActivity(position);
                }
            });
            return view;
        }
    }

    class BluetoothPlayerListAdapter extends ArrayAdapter<BluetoothPlayer> {
        private final int mResourceId;

        private final LayoutInflater mInflater;

        public BluetoothPlayerListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            mResourceId = textViewResourceId;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mBluetoothPlayers.size();
        }

        @Override
        public BluetoothPlayer getItem(int position) {
            return mBluetoothPlayers.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final BluetoothPlayer player = getItem(position);
            View view;
            if (convertView == null) {
                view = mInflater.inflate(mResourceId, parent, false);
            } else {
                view = convertView;
            }

            TextView usernameText = (TextView) view.findViewById(R.id.player_name);
            usernameText.setText(player.name);

            ImageView iconView = (ImageView)view.findViewById(R.id.player_icon);
            iconView.setImageBitmap(player.getIconBitmap());

            TextView btNameView = (TextView)view.findViewById(R.id.bluetooth_name);
            btNameView.setText(player.bluetoothName);

            TextView btAddrView = (TextView)view.findViewById(R.id.bluetooth_addr);
            btAddrView.setText(player.bluetoothAddress);

            TextView connectStateView = (TextView)view.findViewById(R.id.connect_state);
            int stateText = player.getStateText();
            if (stateText > 0) {
                connectStateView.setText(stateText);
            } else {
                connectStateView.setText(null);
            }

            return view;
        }
    }

    class RemotePlayerListAdapter extends ArrayAdapter<WifiPlayer> {
        private final int mResourceId;

        private final LayoutInflater mInflater;

        public RemotePlayerListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            mResourceId = textViewResourceId;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mWifiPlayers.size();
        }

        @Override
        public WifiPlayer getItem(int position) {
            return mWifiPlayers.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final WifiPlayer player = getItem(position);
            final Context context = mInflater.getContext();
            View view;
            if (convertView == null) {
                view = mInflater.inflate(mResourceId, parent, false);
            } else {
                view = convertView;
            }

            final CheckBox checkPlayer = (CheckBox)view.findViewById(R.id.check_player);
            checkPlayer.setChecked(false);
            if (player.isConnected()) {
                checkPlayer.setVisibility(getConnectedPlayerNum() > 3 ? View.VISIBLE : View.INVISIBLE);
            } else {
                checkPlayer.setVisibility(View.INVISIBLE);
            }
            checkPlayer.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (checkPlayer.isChecked()) {
                        if (mSelectedPlayers.size() == 3) {
                            Utils.showInfo(context, context.getString(R.string.label_prompt),
                                            context.getString(R.string.prompt_max_3_remote_players));
                            mSelectedPlayers.remove(0);
                        }
                        mSelectedPlayers.add(player);
                    } else {
                        mSelectedPlayers.remove(player);
                    }
                }
            });

            TextView playerNameText = (TextView) view.findViewById(R.id.player_name);
            if (TextUtils.isEmpty(player.name)) {
                playerNameText.setText(player.ipv4);
            } else {
                playerNameText.setText(player.name);
            }

            ImageView iconView = (ImageView)view.findViewById(R.id.player_icon);
            iconView.setImageBitmap(player.getIconBitmap());

            final TextView connectStateView = (TextView)view.findViewById(R.id.connect_state);
            int stateText = player.getStateText();
            if (stateText > 0) {
                connectStateView.setText(stateText);
            } else {
                connectStateView.setText(null);
            }

            final Button buttonConnect = (Button)view.findViewById(R.id.player_connect);
            if (player.isConnected() || player.isConnecting()) {
                buttonConnect.setVisibility(View.GONE);
            } else {
                buttonConnect.setVisibility(View.VISIBLE);
            }
            buttonConnect.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    buttonConnect.setVisibility(View.GONE);
                    connectStateView.setText(R.string.state_connecting);
                }
            });
            final Button buttonDisconnect = (Button)view.findViewById(R.id.player_disconnect);
            buttonDisconnect.setVisibility(player.isConnected() ? View.VISIBLE : View.GONE);
            buttonDisconnect.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDisconnectConfirm(context, buttonDisconnect, connectStateView, player);
                }
            });

            return view;
        }

        private void showDisconnectConfirm(final Context context, final Button buttonDisconnect,
                        final TextView connectStateView, final WifiPlayer player) {
            Utils.showConfirmDialog(context, context.getString(R.string.prompt_disconnect_player,
                            player.getShortInfo()),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mSelectedPlayers.remove(player);
                                    buttonDisconnect.setVisibility(View.GONE);
                                    connectStateView.setText(R.string.state_disconnecting);
                                }
                            });
        }
    }
}
