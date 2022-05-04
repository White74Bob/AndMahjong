package wb.game.mahjong.model;

import wb.conn.MessageInfo;

public class BluetoothPlayer extends RemotePlayer {
    public final String bluetoothName;
    public final String bluetoothAddress;

    public BluetoothPlayer(String name, Gender gender, String iconFilepath,
            String bluetoothName, String bluetoothAddr) {
        super(name, gender, iconFilepath);
        this.bluetoothName = bluetoothName;
        this.bluetoothAddress = bluetoothAddr;
    }

    @Override
    protected void doDetermineIgnored(boolean fromLocalManager) {
        // Nothing is done.
    }

    @Override
    protected void whenReadyToThrow(final long startTime, final boolean fromLocalManager) {
        // Nothing is done.
    }

    @Override
    public void handleRemoteMessage(MessageInfo messageInfo) {
        //
    }
}
