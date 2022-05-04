package wb.conn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import android.text.TextUtils;
import wb.conn.MessageInfo.MessageType;
import wb.conn.MessageInfo.PlayerInfo;
import wb.game.mahjong.constants.Constants;

public class RemoteMessage {
    public static enum ConnMessage {
        // 以下是MainActivity UDP消息.
        MSG_UDP_SCAN,
        MSG_UDP_OK,
        MSG_UDP_CONNECT_0,
        MSG_UDP_CONNECT_1,
        MSG_UDP_SEND_ICON,
        MSG_UDP_PLAYER_UPDATE,
        MSG_UDP_GOTO_GAME_REQUEST,
        MSG_UDP_GOTO_GAME_OK,
        MSG_UDP_GOTO_GAME_NO,
        MSG_UDP_GOTO_GAME,
        // 以下是MahjongActivity TCP消息.
        MSG_OK_TO_START,
        MSG_CHECK_REMOTE_STATE,
        MSG_REMOTE_STATE,
        MSG_GET_2_PLAYERS,
        MSG_3_PLAYERS,
        MSG_GET_LOCATIONS,
        MSG_LOCATIONS,
        MSG_GET_BANKER_INFO,
        MSG_BANKER_INFO,
        MSG_REQUEST_LIVE_TILE_NUM,
        MSG_LIVE_TILE_NUM,
        MSG_WHEN_TILES_READY,
        MSG_PLAYER_THREAD_NOTIFY,
        MSG_GAME_START,
        MSG_DUMMY_IP_CHANGED,
        MSG_REQUEST_START_PLAYING,
        MSG_START_PLAYING,
        MSG_CIRCLE_INCREASE,
        MSG_UIMESSAGE_NULL,
        MSG_UIMESSAGE_ARG1,
        MSG_UIMESSAGE_PLAYER,
        MSG_UIMESSAGE_OBJ,
        MSG_UPDATE_WAITING,
        MSG_DETERMINE_IGNORED_TYPE,
        MSG_SET_IGNORED_TYPE,
        MSG_REQUEST_SHOWN_TILE,
        MSG_SHOWN_TILE,
        MSG_PLAYER_ADD_TILE,
        MSG_CHECK_13_TILES_OK,
        MSG_PLAYER_13_TILES_STATE,
        MSG_PLAYER_13_TILES,
        MSG_PLAYER_NEW_TILE,
        MSG_PLAYER_READY_TO_THROW,
        MSG_PLAYER_THREW_TILE,
        MSG_PLAYER_TAKE_ACTION,
        MSG_PLAYER_ACTIONS_IGNORED,
        MSG_GAME_END,
        MSG_GAME_OVER,
        MSG_DISCONNECT;

        public static ConnMessage getConnMessage(final int index) {
            for (ConnMessage connMessage : values()) {
                if (connMessage.ordinal() == index) return connMessage;
            }
            return null;
        }
    }

    public static enum DataType {
        String,
        Bitmap,
        NoContent,
        Unknown;

        public static DataType getDataType(final int ordinal) {
            for (DataType dataType : values()) {
                if (dataType.ordinal() == ordinal) return dataType;
            }
            return Unknown;
        }
    }

    public final ConnMessage connMessage;
    public final DataType dataType;

    public final String remoteIp;
    public final boolean isReceived;
    public String destIp;

    public final Object content;

    // 发出去的消息.
    public RemoteMessage(final ConnMessage connMessage, final String remoteIp,
                    final DataType dataType, final Object content) {
        this(connMessage, remoteIp, false, dataType, content);
    }

    // 发出去的无内容的消息.
    public RemoteMessage(final ConnMessage connMessage, final String remoteIp) {
        this(connMessage, remoteIp, false, DataType.NoContent, null);
    }

    public RemoteMessage(final ConnMessage connMessage, final String remoteIp,
                    final boolean isReceived, final DataType dataType, final Object content) {
        this.connMessage = connMessage;
        this.isReceived = isReceived;
        this.dataType = dataType;
        this.remoteIp = remoteIp;
        this.content = content;
    }

    private static final String FORMAT_TOSTRING_NO_CONTENT = "[%s, %s, %s]";

    private static final String FORMAT_TOSTRING_STRING_CONTENT = "[%s, %s, %s] %s";

    private static final String FORMAT_TOSTRING_BYTES_CONTENT = "[%s, %s, %s] %d bytes";

    private static final String FORMAT_TOSTRING_NO_CONTENT_DEST = "[%s, %s, %s->%s]";

    private static final String FORMAT_TOSTRING_STRING_CONTENT_DEST = "[%s, %s, %s->%s] %s";

    private static final String FORMAT_TOSTRING_BYTES_CONTENT_DEST = "[%s, %s, %s->%s] %d bytes";

    @Override
    public String toString() {
        if (TextUtils.isEmpty(destIp)) {
            if (content == null) {
                return String.format(FORMAT_TOSTRING_NO_CONTENT, connMessage, dataType, remoteIp);
            }
            if (content instanceof String) {
                return String.format(FORMAT_TOSTRING_STRING_CONTENT, connMessage, dataType,
                                remoteIp, content);
            }
            if (content instanceof byte[]) {
                return String.format(FORMAT_TOSTRING_BYTES_CONTENT, connMessage, dataType,
                                remoteIp, ((byte[])content).length);
            }
            return String.format(FORMAT_TOSTRING_STRING_CONTENT, connMessage, dataType, remoteIp,
                        content.toString());
        }
        if (content == null) {
            return String.format(FORMAT_TOSTRING_NO_CONTENT_DEST, connMessage, dataType, remoteIp,
                            destIp);
        }
        if (content instanceof String) {
            return String.format(FORMAT_TOSTRING_STRING_CONTENT_DEST, connMessage, dataType,
                            remoteIp, destIp, content);
        }
        if (content instanceof byte[]) {
            return String.format(FORMAT_TOSTRING_BYTES_CONTENT_DEST, connMessage, dataType,
                            remoteIp, destIp, ((byte[])content).length);
        }
        return String.format(FORMAT_TOSTRING_STRING_CONTENT_DEST, connMessage, dataType, remoteIp,
                        destIp, content.toString());
    }

    // Parse received message.
    public static RemoteMessage parse(final MessageInfo messageInfo) {
        final String fromIp = messageInfo.ip;Constants.debug("RemoteMessage.parse, messageInfo.messageType:"+messageInfo.messageType);

        ByteArrayInputStream bais = new ByteArrayInputStream(messageInfo.messageData);
        DataInputStream dis = new DataInputStream(bais);
        String destIp = null;
        ConnMessage connMessage = null;
        try {
            connMessage = ConnMessage.getConnMessage(dis.readInt());Constants.debug("RemoteMessage.parse, connMessage:"+connMessage);
            if (dis.readBoolean()) {
                destIp = dis.readUTF();
            }
            RemoteMessage newRemoteMessage = null;
            switch (messageInfo.messageType) {
                case TextMessage:
                    newRemoteMessage = new RemoteMessage(connMessage, fromIp, true, DataType.String,
                                    dis.readUTF());
                    break;
                case Bitmap:
                    int dataLen = dis.readInt();
                    byte[] data = new byte[dataLen];
                    dis.read(data);
                    newRemoteMessage = new RemoteMessage(connMessage, fromIp, true, DataType.Bitmap,
                                    data);
                    break;
                default:
                    newRemoteMessage = new RemoteMessage(connMessage, fromIp, true,
                                    DataType.NoContent, null);
                    break;
            }
            newRemoteMessage.destIp = destIp;
            return newRemoteMessage;
        } catch (IOException ioe) {//Constants.debug("What's wrong in parse?" + ioe);
            //return null;
            throw new RuntimeException("What's wrong in parse " + connMessage, ioe);
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

    // 构造发出去的消息.
    public MessageInfo constructMessage() {
        MessageType messageType;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(connMessage.ordinal());
            if (TextUtils.isEmpty(destIp)) {
                dos.writeBoolean(false);
            } else {
                dos.writeBoolean(true);
                dos.writeUTF(destIp);
            }
            switch (dataType) {
                case String:
                    dos.writeUTF(content.toString());
                    messageType = MessageType.TextMessage;
                    break;
                case Bitmap:
                    byte[] data = (byte[])content;
                    dos.writeInt(data.length);
                    dos.write(data);
                    messageType = MessageType.Bitmap;
                    break;
                case NoContent:
                default:
                    messageType = MessageType.Unknown;
                    break;
            }
            dos.flush();
            return new MessageInfo(baos.toByteArray(), messageType, isReceived, remoteIp);
        } catch (IOException ioe) {
            return null;
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

    public static byte[] constructMessageData(final ConnMessage connMessage,
                    final DataType dataType, final Object content) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(connMessage.ordinal());
            dos.writeBoolean(false); // No destIp.
            switch (dataType) {
                case String:
                    dos.writeUTF(content.toString());
                    // messageType = MessageType.TextMessage;
                    break;
                case Bitmap:
                    byte[] data = (byte[]) content;
                    dos.writeInt(data.length);
                    dos.write(data);
                    // messageType = MessageType.Bitmap;
                    break;
                case NoContent:
                default:
                    // messageType = MessageType.Unknown;
                    break;
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException ioe) {
            return null;
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

    // 构造发出去的文本消息.
    public static MessageInfo constructStringMessage(final RemoteMessage.ConnMessage connMessage,
                    final String destIp, final PlayerInfo playerInfo, final String content) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(connMessage.ordinal());
            dos.writeBoolean(false); // No another dest IP.
            if (!TextUtils.isEmpty(content)) {
                dos.writeUTF(content);
            }
            dos.writeChar('\0'); // EOF
            dos.flush();
            return new MessageInfo(baos.toByteArray(), MessageType.TextMessage, false, destIp,
                            playerInfo);
        } catch (IOException ioe) {
            return null;
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
