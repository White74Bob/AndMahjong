package wb.game.mahjong.model;

import wb.game.mahjong.R;

// The game always needs 4 players.
// 4 players might be in 3 kinds:
// DummyPlayer: manipulated by the app.
// LocalPlayer: always the current user locating in bottom.
// RemotePlayer: WifiPlayer or BluetoothPlayer.
public abstract class RemotePlayer extends Player {
    public enum PlayerConnectState {
        StateNotConnected,
        StateDisconnecting,
        StateConnecting,
        StateConnected,
        StateConnectedObtainingPlayerInfo,
        StateConnectedInfoObtained,
        StateConnectedPlaying,
        StateConnectFailed;
    }

    private PlayerConnectState mConnectState = PlayerConnectState.StateNotConnected;

    public RemotePlayer(String name, Gender gender, String iconFilepath) {
        super(name, gender, iconFilepath);
    }

    public void updateConnectState(PlayerConnectState connectState) {
        mConnectState = connectState;
    }

    public boolean isNotConnected() {
        return (mConnectState == PlayerConnectState.StateNotConnected
                || mConnectState == PlayerConnectState.StateConnectFailed);
    }

    public boolean isConnected() {
        return mConnectState == PlayerConnectState.StateConnected;
    }

    public boolean isConnecting() {
        return mConnectState == PlayerConnectState.StateConnecting;
    }

    public int getStateText() {
        switch (mConnectState) {
            case StateNotConnected:
                return R.string.state_not_connected;
            case StateConnecting:
                return R.string.state_connecting;
            case StateConnected:
            case StateConnectedInfoObtained:
                return R.string.state_connected;
            case StateConnectedObtainingPlayerInfo:
                return R.string.state_connected_obtaining_info;
            case StateConnectedPlaying:
                break;
            case StateConnectFailed:
                return R.string.state_connect_failed;
            default:
                return -1;
        }
        return -1;
    }
}
