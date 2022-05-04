package wb.conn.wifi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import wb.conn.ErrorListener;
import wb.conn.LogListener;
import wb.conn.MessageInfo;
import wb.conn.MessageListener;

public class TcpServer {
    protected final int mPort;
    protected final Handler mHandler;

    private final ServerSocket mServerSocket;

    private AcceptThread mAcceptThread;

    private final ErrorListener mErrorListener;
    private final LogListener mLogListener;
    private final MessageListener mMessageListener;

    public TcpServer(final int port, final Handler handler, final ErrorListener errorListener,
                    final MessageListener messageListener, final LogListener logListener)
                    throws IOException {
        mPort = port;
        if (handler.getLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("The handler can't run in MainLooper!");
        }
        mHandler = handler;

        mErrorListener = errorListener;
        mMessageListener = messageListener;
        mLogListener = logListener;

        mServerSocket = newServerSocket();
    }

    private ServerSocket newServerSocket() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(mPort));
        return serverSocket;
        // return new ServerSocket(mPort);
    }

    protected void reportException(Exception e, String log) {
        if (mErrorListener == null) return;
        mErrorListener.onException(e, log);
    }

    private class AcceptThread extends Thread {
        public boolean mRunning;

        private boolean mCloseServerSocket;

        private final ArrayList<TcpUtils.SocketConnection> mClients = new ArrayList<TcpUtils.SocketConnection>();

        public synchronized void startThread() {
            mRunning = true;
            start();
        }

        public synchronized void stopThread(final boolean closeServerSocket) {
            mRunning = false;
            mCloseServerSocket = closeServerSocket;
            interrupt();
        }

        public TcpUtils.SocketConnection findClient(final String ip) {
            synchronized (mClients) {
                for (TcpUtils.SocketConnection client : mClients) {
                    if (TextUtils.equals(ip, client.ip)) return client;
                }
                return null;
            }
        }

        public void sendMessageToClients(final byte[] messageData, final String...ips) {
            if (ips == null) {
                for (TcpUtils.SocketConnection client : mClients) {
                    client.write(messageData);
                }
                return;
            }
            for (String ip : ips) {
                TcpUtils.SocketConnection client = findClient(ip);
                client.write(messageData);
            }
        }

        @Override
        public void run() {
            while (mRunning && !isInterrupted()) {
                try {
                    Socket socket = mServerSocket.accept();
                    mClients.add(new TcpUtils.SocketConnection(socket, mMessageListener,
                                    mErrorListener));
                } catch (IOException ioe) {
                    reportException(ioe, "Failed to get client socket!");
                }
            }
            closeSockets();
            if (mCloseServerSocket) {
                closeServerSocket();
            }
        }

        private void closeSockets() {
            for (TcpUtils.SocketConnection connectionToClient : mClients) {
                connectionToClient.endConnection();
            }
            mClients.clear();
        }

        public String clientsInfo() {
            synchronized(mClients) {
                if (mClients.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Clients:\n");
                    for (TcpUtils.SocketConnection connectionToClient : mClients) {
                        sb.append(connectionToClient.ip).append('\n');
                    }
                    return sb.toString();
                }
            }
            return null;
        }
    }

    public synchronized void start() {
        stop(false);
        mAcceptThread = new AcceptThread();
        mAcceptThread.startThread();
    }

    public synchronized void stop(final boolean closeServerSocket) {
        if (mAcceptThread != null) {
            mAcceptThread.stopThread(closeServerSocket);
        }
        mAcceptThread = null;
    }

    private void closeServerSocket() {
        if (mServerSocket.isClosed()) return;
        try {
            mServerSocket.close();
        } catch (IOException ioe) {
            reportException(ioe, "Failed to close ServerSocket:" + mServerSocket);
        }
    }

    private static final String FORMAT_INFO = "TcpServer[%d]";

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(FORMAT_INFO, mPort));
        if (mAcceptThread != null) {
            sb.append(mAcceptThread.clientsInfo());
        }
        return sb.toString();
    }

    private synchronized void runInHandlerThread(final Runnable runnable) {
        if (mHandler == null) return;
        mHandler.post(runnable);
    }

    public void sendMessage(final MessageInfo messageInfo) {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                TcpUtils.SocketConnection client = mAcceptThread.findClient(messageInfo.ip);
                if (client != null) {
                    client.write(messageInfo);
                }
            }
        });
    }

    public void send(final byte[] messageData, final String...ips){
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mAcceptThread.sendMessageToClients(messageData, ips);
            }
        });
    }
}
