package wb.conn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;

import android.text.TextUtils;
import wb.game.utils.Utils;

public class MessageInfo {
    public static final String EVENT_DISCONNECTED = "Disconnected";
    public static final String EVENT_CONNECTED    = "Connected";

    public static enum MessageType {
        TextMessage,
        FileAudio,
        FileVideo,
        FileImage,
        Bitmap,
        EventDisconnect(EVENT_DISCONNECTED),
        EventConnected(EVENT_CONNECTED),
        Unknown;

        private final String mInfo;

        private MessageType(String str) {
            mInfo = str;
        }

        private MessageType() {
            this(null);
        }

        @Override
        public String toString() {
            if (this == TextMessage || TextUtils.isEmpty(mInfo)) {
                return super.toString();
            }
            return mInfo;
        }

        public byte[] getMessageData() {
            return toString().getBytes();
        }

        public static MessageType getMessageType(final int ordinal) {
            for (MessageType messageType : values()) {
                if (messageType.ordinal() == ordinal) return messageType;
            }
            return Unknown;
        }
    }

    /**
     *  Format: --from/to[IP]--\nmessgeText"
     *          --From[xx.xx.xx.xx] time--
     *          message text
     *
     *          --  To[xx.xx.xx.xx] time--
     *          message text
     */
    private static final String FORMAT_MESSAGE = "--%s[%s]%s--\n%s";

    /**
     * Format: --[xx.xx.xx.xx] Disconnect --
     *         --[xx.xx.xx.xx] Connected --
     */
    private static final String FORMAT_EVENT = "--[%s] %s-";

    /**
     *  Format: --from/to[IP]--\n%d bytes"
     *          --From[xx.xx.xx.xx] time--
     *          %d bytes
     *
     *          --  To[xx.xx.xx.xx] time--
     *          %d bytes
     */
    private static final String FORMAT_BYTES = "--%s[%s]%s--\n%s,%d bytes.";

    private static final String FROM = "From";
    private static final String   TO = "  To";

    public static class PlayerInfo {
        public final String ip;
        public final String playerName;

        public PlayerInfo(String ip, String playerName) {
            this.ip = ip;
            this.playerName = playerName;
        }

        private static final String FORMAT_TO_STRING = "%s";
        private static final String FORMAT_TO_STRING_PLAYER = "%s_%s";

        @Override
        public String toString() {
            if (TextUtils.isEmpty(playerName)) {
                return String.format(FORMAT_TO_STRING, ip);
            }
            return String.format(FORMAT_TO_STRING_PLAYER, ip, playerName);
        }
    }

    public final PlayerInfo playerInfo;
    public final byte[] messageData;
    public final String time;

    public final MessageType messageType;

    /**
     * If true, it is received from the IP;
     * false means the message has been sent to the IP.
     */
    public final boolean isReceived;

    public final String ip;

    public String[] destIps;

    public MessageInfo(MessageType event, InetAddress inetAddresse) {
        this(event.getMessageData(), event, false, getIps(inetAddresse)[0], null);
    }

    public MessageInfo(byte[] messageData, MessageType messageType, boolean isReceived, String ip) {
        this(messageData, messageType, isReceived, ip, null);
    }

    public MessageInfo(byte[] messageData, MessageType messageType, boolean isReceived,
                    String ip, PlayerInfo ipInfo) {
        this.messageData = messageData;
        this.messageType = messageType;
        this.isReceived = isReceived;
        this.ip = ip;
        this.playerInfo = ipInfo;

        this.time = Utils.currentTimeString();
    }

    private static String[] getIps(final InetAddress...inetAddresses) {
        if (inetAddresses == null || inetAddresses.length <= 0) return null;
        String[] ips = new String[inetAddresses.length];
        for (int i = 0; i < ips.length; i++) {
            ips[i] = inetAddresses[i] == null ? null : inetAddresses[i].getHostAddress();
        }
        return ips;
    }

    @Override
    public String toString() {
        switch (messageType) {
            case TextMessage:
                return String.format(FORMAT_MESSAGE, (isReceived ? FROM : TO), ip, time,
                                new String(messageData));
            case Bitmap:
                return String.format(FORMAT_BYTES, (isReceived ? FROM : TO), ip, time,
                                messageType, messageData.length);
            default:
                return String.format(FORMAT_EVENT, ip, messageType.toString());
        }
    }

    // 解析received message.
    public static MessageInfo parseReceivedMessage(final InetAddress inetAddress,
                    final byte[] messageData) throws IOException {
        return parseReceivedMessage(getIps(inetAddress)[0], messageData);
    }

    // 解析received message.
    public static MessageInfo parseReceivedMessage(final String ip, final byte[] messageData)
                    throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
        DataInputStream dis = new DataInputStream(bais);
        
        MessageType messageType = null;
        int destIpCount = -1;
        try {
            messageType = MessageType.getMessageType(dis.readInt());
            destIpCount = dis.readInt();
            String[] destIps = null;
            if (destIpCount > 0) {
                destIps = new String[destIpCount];
                for (int i = 0; i < destIpCount; i++) {
                    destIps[i] = dis.readUTF();
                }
            }
            boolean hasPlayerInfo = dis.readBoolean();
            PlayerInfo playerInfo = null;
            if (hasPlayerInfo) {
                String playerIp = dis.readUTF();
                String playerName = dis.readUTF();
                playerInfo = new PlayerInfo(playerIp, playerName);
            }

            int dataLen = dis.readInt();
            byte[] data = new byte[dataLen];
            dis.read(data);
            MessageInfo newMessage = new MessageInfo(data, messageType, true, ip, playerInfo);
            newMessage.destIps = destIps;
            return newMessage;
        } catch (EOFException eofe) {
            throw new RuntimeException(
                            "messageType:" + messageType + ", destIpCount=" + destIpCount, eofe);
        } finally {
            try {
                dis.close();
            } catch (IOException ioe) {
                // TODO: Nothing?
            }
            try {
                bais.close();
            } catch (IOException ioe) {
                // TODO: Nothing?
            }
        }
    }

    // 构造发出去的message.
    public static byte[] constructMessageData(final MessageInfo messageInfo) throws IOException {
        return constructMessageData(messageInfo.messageType, messageInfo.destIps,
                        messageInfo.playerInfo, messageInfo.messageData);
    }

    public static byte[] constructMessageData(final MessageType messageType,
                    final PlayerInfo playerInfo, final byte[] messageData)
                    throws IOException {
        return constructMessageData(messageType, null, playerInfo, messageData);
    }

    public static byte[] constructMessageData(final MessageType messageType, final String[] destIps,
                    final PlayerInfo playerInfo, final byte[] messageData)
                    throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(messageType.ordinal());
            if (destIps == null || destIps.length <= 0) {
                dos.writeInt(0); // No destIps.
            } else {
                dos.write(destIps.length);
                for (String destIp : destIps) {
                    dos.writeUTF(destIp);
                }
            }
            if (playerInfo == null) {
                dos.writeBoolean(false);
            } else {
                dos.writeBoolean(true);
                dos.writeUTF(playerInfo.ip);
                if (TextUtils.isEmpty(playerInfo.playerName)) {
                    throw new RuntimeException("Why null playername for " + playerInfo.ip);
                }
                dos.writeUTF(playerInfo.playerName);
            }
            if (messageData != null && messageData.length > 0) {
                dos.writeInt(messageData.length);
                dos.write(messageData);
            } else {
                dos.writeInt(0);
            }
            dos.flush();
            return baos.toByteArray();
        } finally {
            try {
                dos.close();
            } catch (IOException ioe) {
                // TODO: nothing?
            }
            try {
                baos.close();
            } catch (IOException ioe) {
                // TODO: nothing?
            }
        }
    }
}
