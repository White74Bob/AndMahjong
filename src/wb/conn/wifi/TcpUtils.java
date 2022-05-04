package wb.conn.wifi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import wb.conn.ErrorListener;
import wb.conn.MessageInfo;
import wb.conn.MessageListener;

public class TcpUtils {
    public static byte[] readMessageData(final DataInputStream dis,
                    final ErrorListener errorListener) {
        try {
            int dataLen = dis.readInt();
            if (dataLen <= 0) {
                throw new RuntimeException("Why receive empty/null data?! " + dataLen);
            }
            byte[] data = new byte[dataLen];
            dis.read(data);
            return data;
        } catch (IOException ioe) {
            if (errorListener != null) {
                errorListener.onException(ioe, "Fail to read message!");
            }
            return null;
        }
    }

    public static void sendMessageData(final DataOutputStream dos, final byte[] messageData,
                    final ErrorListener errorListener) {
        try {
            dos.writeInt(messageData.length);
            dos.write(messageData);
            dos.flush();
        } catch (IOException ioe) {
            if (errorListener != null) {
                errorListener.onException(ioe, "Fail to write message!");
            }
        }
    }

    public static class SocketConnection {
        private final Socket mSocket;

        private final MessageListener mMessageListener;
        private final ErrorListener mErrorListener;

        private final DataInputStream mDis;
        private final DataOutputStream mDos;

        public final String ip;

        private boolean mReceiveThreadRunning;

        public SocketConnection(Socket socket, MessageListener messageListener,
                        ErrorListener errorListener) throws IOException {
            mSocket = socket;
            mMessageListener = messageListener;
            mErrorListener = errorListener;

            ip = socket.getInetAddress().getHostAddress();

            mDis = new DataInputStream(socket.getInputStream());
            mDos = new DataOutputStream(socket.getOutputStream());

            mReceiveThreadRunning = true;

            startReceiveThread();
        }

        private void startReceiveThread() {
            Thread readThread = new Thread() {
                @Override
                public void run() {
                    while (mReceiveThreadRunning) {
                        byte[] data = readMessageData(mDis, mErrorListener);
                        if (data == null || data.length <= 0) {
                            //throw new RuntimeException("Why received null data in " + mSocket);
                            continue; // Ignore this read...
                        }
                        MessageInfo messageInfo = null;
                        try {
                            messageInfo = MessageInfo.parseReceivedMessage(ip, data);
                        } catch (Exception e) {
                            throw new RuntimeException(
                                            "Failed to parseReceivedMessage for " + mSocket
                                                            + "\ndata len:" + data.length,
                                            e);
                        }
                        if (mMessageListener != null) {
                            mMessageListener.newMessageComes(messageInfo);
                        }
                    }
                    try {
                        mDis.close();
                    } catch(IOException ioe) {
                        // ...
                    }
                    try {
                        mDos.close();
                    } catch(IOException ioe) {
                        // ...
                    }
                    closeSocket(mSocket);
                }
            };

            //readThread.setDaemon(true); // terminate when main ends
            readThread.start();
        }

        private void closeSocket(Socket socket) {
            synchronized (socket) {
                if (socket.isClosed()) return;
                try {
                    socket.close();
                } catch (IOException ioe) {
                    reportException(ioe, null);
                }
            }
        }

        public void endConnection() {
            mReceiveThreadRunning = false;
        }

        public void write(MessageInfo messageInfo) {
            try {
                write(MessageInfo.constructMessageData(messageInfo));
            } catch (IOException ioe) {
                mErrorListener.onException(ioe, "Failed to send message:\n" + messageInfo.toString());
            }
        }

        public void write(final byte[] messageData) {
            sendMessageData(mDos, messageData, mErrorListener);
        }

        protected void reportException(Exception e, String log) {
            if (mErrorListener == null) return;
            mErrorListener.onException(e, log);
        }
    }
}
