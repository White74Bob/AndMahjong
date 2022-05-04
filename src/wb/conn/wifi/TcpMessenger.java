package wb.conn.wifi;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import wb.conn.ErrorListener;
import wb.conn.LogListener;
import wb.conn.MessageInfo;
import wb.conn.MessageListener;

public class TcpMessenger {
    private final int mPort;

    private TcpClient mClient;

    private final TcpServer mServer;

    private final Handler mHandler;

    private final MessageListener mMessageListener;
    private final ErrorListener mErrorListener;
    private final LogListener mLogListener;

    private final String mServerIp;

    public TcpMessenger(final int port, Handler handler, String serverIp, ErrorListener errorListener,
                    MessageListener messageListener, LogListener logListener) throws Exception {
        if (handler.getLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("TcpMessenger can't use MainLooper!");
        }
        mHandler = handler;
        mPort = port;
        mServerIp = serverIp;

        mErrorListener = errorListener;
        mMessageListener = messageListener;
        mLogListener = logListener;

        if (isServer()) {
            mServer = new TcpServer(mPort, mHandler, mErrorListener, mMessageListener, mLogListener);
            mClient = null;
        } else {
            mServer = null;
            mClient = new TcpClient(mPort, mServerIp, WifiUtils.getIpInWifi(), mHandler,
                            mErrorListener, mMessageListener, mLogListener);
        }
    }

    public boolean isServer() {
        return TextUtils.isEmpty(mServerIp);
    }

    public synchronized void start() {
        if (isServer()) {
            mServer.start();
        } else {
            mClient.start();
        }
    }

    public synchronized void stop() {
        if (mServer != null) {
            mServer.stop(true);
        }
        if (mClient != null) {
            mClient.stop();
        }
    }

    public synchronized void sendMessage(final MessageInfo messageInfo) {
        if (isServer()) {
            mServer.sendMessage(messageInfo);
        } else {
            mClient.sendMessage(messageInfo);
        }
    }
}
