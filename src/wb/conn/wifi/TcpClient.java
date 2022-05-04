package wb.conn.wifi;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import android.os.Handler;
import android.text.TextUtils;
import wb.conn.ErrorListener;
import wb.conn.LogListener;
import wb.conn.MessageInfo;
import wb.conn.MessageListener;
import wb.game.mahjong.constants.Constants;
import wb.game.utils.Utils;

public class TcpClient {
    protected final int mPort;

    private final String mLocalIp;

    private String mServerIp;

    protected final Handler mHandler;

    private final ErrorListener mErrorListener;
    private final LogListener mLogListener;
    private final MessageListener mMessageListener;

    private TcpUtils.SocketConnection mSocketConnection;

    public TcpClient(final int port,final String serverIp, final String localIp,
            final Handler handler, final ErrorListener errorListener,
            final MessageListener messageListener,
            final LogListener logListener) {
        if (TextUtils.isEmpty(serverIp)) {
            throw new IllegalArgumentException("server IP can NOT be NULL!");
        }
        mPort = port;
        mHandler = handler;

        mErrorListener = errorListener;
        mMessageListener = messageListener;
        mLogListener = logListener;

        mServerIp = serverIp;
        mLocalIp = localIp;
    }

    public String getServerIp() {
        return mServerIp;
    }

    public void setServerIp(final String serverIp) {
        mServerIp = serverIp;
    }

    private static final String FORMAT_INFO = "TcpClient[%s:%d]->%s";

    @Override
    public String toString() {
        return String.format(FORMAT_INFO, mLocalIp, mPort, mServerIp);
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

    private void setupSocket() {
        try {
            Socket socket = new Socket(mServerIp, mPort);
            mSocketConnection = new TcpUtils.SocketConnection(socket, mMessageListener,
                            mErrorListener);
        } catch (UnknownHostException uhe) {
            throw new RuntimeException("Failed to createSocket for TcpClient", uhe);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to createSocket for TcpClient", ioe);
        }
    }

    public void start() {
        stop();
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                setupSocket();
            }
        });
    }

    public void stop() {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                if (mSocketConnection != null) {
                    mSocketConnection.endConnection();
                    mSocketConnection = null;
                }
            }
        });
    }

    private synchronized void runInHandlerThread(final Runnable runnable) {
        if (mHandler == null) return;
        mHandler.post(runnable);
    }

    public void sendMessage(final MessageInfo messageInfo) {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mSocketConnection.write(messageInfo);
            }
        });
    }

    public void send(final byte[] messageData){
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mSocketConnection.write(messageData);
            }
        });
    }
}
