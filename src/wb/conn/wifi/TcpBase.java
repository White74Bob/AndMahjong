package wb.conn.wifi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.os.Handler;
import android.os.Looper;
import wb.conn.ErrorListener;
import wb.conn.LogListener;
import wb.conn.MessageInfo;
import wb.conn.MessageInfo.MessageType;
import wb.conn.MessageListener;
import wb.game.mahjong.constants.Constants;
import wb.game.utils.Utils;

public abstract class TcpBase {
    //private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    protected final int mPort;
    protected final Handler mHandler;

    private final ErrorListener mErrorListener;

    private final MessageListener mMessageListener;

    private final LogListener mLogListener;

    private ReceiveThread mReceiveThread;

    private Sender mSender;

    public TcpBase(final int port, final Handler handler,
            final ErrorListener errorListener,
            final MessageListener messageListener,
            final LogListener logListener) {
        mPort = port;
        if (handler.getLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("The handler can't run in MainLooper!");
        }
        mHandler = handler;

        mErrorListener = errorListener;
        mMessageListener = messageListener;
        mLogListener = logListener;
    }

    protected abstract Socket createSocket();

    protected abstract String getInfo();

    public synchronized void sendMessage(final MessageInfo messageInfo) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mSender == null) {
                    synchronized(mHandler) {
                        try {
                            mHandler.wait();
                        } catch (InterruptedException ie) {
                            // ...
                        }
                    }
                }
                mSender.sendMessage(messageInfo);
            }
        });
    }

    public synchronized void start() {
        if (mReceiveThread != null) {
            mReceiveThread.stopThread();
        }
        (mReceiveThread = new ReceiveThread()).start();
    }

    public synchronized void stop() {
        if (mReceiveThread != null) {
            mReceiveThread.stopThread();
            mReceiveThread = null;
        }
    }

    protected final void addLog(final String log) {
        if (mLogListener == null) return;
        if (Constants.LOG_WITH_TIME) {
            mLogListener.addLog(Utils.getTextWithTime(log));
            return;
        }
        mLogListener.addLog(log);
    }

    protected void reportException(Exception e, String log) {
        if (mErrorListener == null) return;
        mErrorListener.onException(e, log);
    }

    private void messageSent(MessageInfo msgInfo) {
        if (mMessageListener == null) return;
        mMessageListener.messageSent(msgInfo);
    }

    protected void closeSocket(Socket socket) {
        synchronized (socket) {
            if (socket.isClosed()) return;
            try {
                socket.close();
            } catch (IOException ioe) {
                reportException(ioe, null);
            }
        }
    }

    private class ReceiveThread extends Thread {
        private boolean mIsRunning;

        public synchronized void stopThread() {
            mIsRunning = false;
        }

        @Override
        public void run() {
            final Socket socket = createSocket();
            if (!socket.isConnected()) {
                mErrorListener.onError(getInfo() + "," + socket.toString());
                return;
            }
            mSender = new Sender(socket);
            synchronized(mHandler) {
                mHandler.notify();
            }

            final InetAddress inetAddress = socket.getInetAddress();
            //final String ip = inetAddress.getHostAddress();

            InputStream inputStream = null;
            try {
                inputStream = socket.getInputStream();
            } catch (IOException ioe) {
                reportException(ioe, null);
                return;
            }

            mIsRunning = true;
            DataInputStream dis = new DataInputStream(inputStream);
            while (mIsRunning && !isInterrupted()) {
                try {
                    // Receive a message
                    int dataLen = dis.readInt();
                    if (dataLen <= 0) {
                        throw new RuntimeException("Why received dataLen<=0? " + dataLen);
                    }
                    byte[] data = new byte[dataLen];
                    dis.read(data);
                    if (mMessageListener != null) {
                        final MessageInfo messageInfo = MessageInfo.parseReceivedMessage(inetAddress,
                                    data);
                        mMessageListener.newMessageComes(messageInfo);
                    }
                } catch (UnknownHostException uhe) {
                    reportException(uhe, "Failed to receive message!");
                } catch (IOException ioe) {
                    reportException(ioe, "Failed to receive message!");
                }
            }
            try {
                dis.close();
            } catch(IOException ioe) {
                // ...
            }
            try {
                inputStream.close();
            } catch(IOException ioe) {
                // ...
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mSender != null) {
                        mSender.stop();
                        mSender = null;
                    }
                }
            });

            closeSocket(socket);
        }
    }

    protected abstract MessageInfo[] createMessagesToSend(final MessageType messageType,
                    final byte[] messageData, final String...ips);

    private class Sender {

        private final OutputStream mOutputStream;
        private final DataOutputStream mDos;

        public Sender(Socket socket) {
            try {
                mOutputStream = socket.getOutputStream();
            } catch (IOException ioe) {
                reportException(ioe, null);
                throw new RuntimeException(ioe);
            }
            mDos = new DataOutputStream(mOutputStream);
        }

        public void stop() {
            try {
                mDos.close();
            } catch (IOException ioe) {
                reportException(ioe, "Failed to close sender data outputStream!");
            }
            try {
                mOutputStream.close();
            } catch (IOException ioe) {
                reportException(ioe, "Failed to close sender outputStream!");
            }
        }

        public void sendMessage(MessageInfo messageInfo) {
            try {
                byte[] data = MessageInfo.constructMessageData(messageInfo);
                if (data == null) {
                    throw new RuntimeException("Why send null data?!\n" + messageInfo);
                }
                if (data.length == 0) {
                    throw new RuntimeException("Why send empty data?!\n" + messageInfo);
                }
                mDos.writeInt(data.length);
                mDos.write(data);
                mDos.flush();
                messageSent(messageInfo);
            } catch (IOException ioe) {
                reportException(ioe, "Failed to send message!\n" + messageInfo);
            }
        }
    }
}
