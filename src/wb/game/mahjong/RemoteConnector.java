package wb.game.mahjong;

import android.os.Handler;
import android.os.HandlerThread;
import wb.conn.ErrorListener;
import wb.conn.LogListener;
import wb.conn.MessageInfo;
import wb.conn.MessageInfo.MessageType;
import wb.conn.MessageListener;
import wb.conn.wifi.TcpMessenger;
import wb.conn.wifi.UdpMessenger;
import wb.conn.wifi.UdpMessenger.Result;
import wb.conn.wifi.UdpMessenger.SendResult;
import wb.game.mahjong.constants.Constants;

public class RemoteConnector {
    private static RemoteConnector sInstance;

    public static RemoteConnector getInstance() {
        if (sInstance == null) {
            sInstance = new RemoteConnector();
        }
        return sInstance;
    }

    public interface RemoteListener {
        public void handleReceivedMessage(final MessageInfo messageInfo);
        public void onException(final Exception e, final String log);
        public void onError(final String errorInfo);
    }

    private static final int DEFAULT_UDP_PORT   = 5802;
    private static final int DEFAULT_UDP_PORT_1 = 5804;
    private static final int DEFAULT_TCP_PORT   = 50802;

    private RemoteListener mRemoteListener;
    private RemoteListener mTcpRemoteListener;
    private RemoteListener mUdpRemoteListener;
    private RemoteListener mUdpRemoteListener1;

    private final MessageListener mUdpMessageListener = new MessageListener() {
        @Override
        public void newMessageComes(final MessageInfo msgInfo) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mUdpRemoteListener != null) {
                        mUdpRemoteListener.handleReceivedMessage(msgInfo);
                    }
                }
            });
        }

        @Override
        public void messageSent(final MessageInfo msgInfo, final Result... results) {
            if (results == null) return;
            for (Result result : results) {
                if (result.result == SendResult.Succeeded) {
                    // addMessage(msgInfo);
                } else {
                    Constants.debug("Message[" + msgInfo + "] NOT sent!\n" + result.toString());
                }
            }
        }
    };

    private final MessageListener mUdpMessageListener1 = new MessageListener() {
        @Override
        public void newMessageComes(final MessageInfo msgInfo) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mUdpRemoteListener1 != null) {
                        mUdpRemoteListener1.handleReceivedMessage(msgInfo);
                    }
                }
            });
        }

        @Override
        public void messageSent(final MessageInfo msgInfo, final Result... results) {
            if (results == null) return;
            for (Result result : results) {
                if (result.result == SendResult.Succeeded) {
                    // addMessage(msgInfo);
                } else {
                    Constants.debug("Message[" + msgInfo + "] NOT sent!\n" + result.toString());
                }
            }
        }
    };

    private final MessageListener mTcpMessageListener = new MessageListener() {
        @Override
        public void newMessageComes(final MessageInfo msgInfo) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mTcpRemoteListener != null) {
                        mTcpRemoteListener.handleReceivedMessage(msgInfo);
                    }
                }
            });
        }

        @Override
        public void messageSent(final MessageInfo msgInfo, final Result... results) {
            if (results == null) return;
            for (Result result : results) {
                if (result.result == SendResult.Succeeded) {
                    // addMessage(msgInfo);
                } else {
                    Constants.debug("Message[" + msgInfo + "] NOT sent!\n" + result.toString());
                }
            }
        }
    };

    private final ErrorListener mUdpErrorListener = new ErrorListener() {
        @Override
        public void onException(final Exception e, final String log) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mUdpRemoteListener != null) {
                        mUdpRemoteListener.onException(e, log);
                    }
                }
            });
        }

        @Override
        public void onError(final String errorInfo) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mUdpRemoteListener != null) {
                        mUdpRemoteListener.onError(errorInfo);
                    }
                }
            });
        }
    };

    private final ErrorListener mUdpErrorListener1 = new ErrorListener() {
        @Override
        public void onException(final Exception e, final String log) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mUdpRemoteListener != null) {
                        mUdpRemoteListener1.onException(e, log);
                    }
                }
            });
        }

        @Override
        public void onError(final String errorInfo) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mUdpRemoteListener != null) {
                        mUdpRemoteListener.onError(errorInfo);
                    }
                }
            });
        }
    };

    private final ErrorListener mTcpErrorListener = new ErrorListener() {
        @Override
        public void onException(final Exception e, final String log) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mTcpRemoteListener != null) {
                        mTcpRemoteListener.onException(e, log);
                    }
                }
            });
        }

        @Override
        public void onError(final String errorInfo) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    if (mTcpRemoteListener != null) {
                        mTcpRemoteListener.onError(errorInfo);
                    }
                }
            });
        }
    };

    private final LogListener mLogListener = new LogListener() {
        @Override
        public void addLog(String log) {
            Constants.debug(log);
        }
    };

    private UdpMessenger mUdpMessenger;
    private UdpMessenger mUdpMessenger1;
    private TcpMessenger mTcpMessenger;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private static final String FORMAT_REMOTE_CONNECTOR_THREAD = "RemoteConnector - %d";
    private RemoteConnector() {
        mHandlerThread = new HandlerThread(String.format(FORMAT_REMOTE_CONNECTOR_THREAD,
                        android.os.Process.myPid()));
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        initNetwork();
    }

    private void initNetwork() {
        switch (Constants.sNetwork) {
            case Wifi:
                mUdpMessenger = new UdpMessenger(DEFAULT_UDP_PORT, mHandler, mUdpErrorListener,
                                mUdpMessageListener, mLogListener);
                break;
            case Hotspot:
                break;
            case Bluetooth:
                break;
            default:
                break;
        }
    }

    public synchronized void startWifiUdp(final RemoteListener remoteListener) {
        mUdpMessenger.start();
        mUdpRemoteListener = remoteListener;
    }

    public synchronized void startWifiUdp1(final RemoteListener remoteListener) {
        if (mUdpMessenger1 == null) {
            mUdpMessenger1 = new UdpMessenger(DEFAULT_UDP_PORT_1, mHandler, mUdpErrorListener1,
                            mUdpMessageListener1, mLogListener);
        }
        mUdpMessenger1.start();
        mUdpRemoteListener1 = remoteListener;
    }

    public synchronized void startWifiTcp(String serverIp, final RemoteListener remoteListener)
                    throws Exception {
        if (mTcpMessenger != null) {
            mTcpMessenger.stop();
        }
        mTcpMessenger = new TcpMessenger(DEFAULT_TCP_PORT, mHandler, serverIp, mTcpErrorListener,
                    mTcpMessageListener, mLogListener);
        mTcpMessenger.start();
        mTcpRemoteListener = remoteListener;
    }

    public synchronized void start(final RemoteListener remoteListener) {
        mRemoteListener = remoteListener;
        switch (Constants.sNetwork) {
            case Wifi:
                //mTcpMessenger.start(mHandler);
                //mUdpMessenger.start(mHandler);
                break;
            case Hotspot:
                break;
            case Bluetooth:
                break;
            default:
                break;
        }
    }

    public synchronized void stop() {
        mRemoteListener = null;
        mHandler.removeCallbacksAndMessages(null);
        switch (Constants.sNetwork) {
            case Wifi:
                //mTcpMessenger.stop();
                //mUdpMessenger.stop();
                break;
            case Hotspot:
                break;
            case Bluetooth:
                break;
            default:
                break;
        }
    }

    public synchronized void stopWifiUdp(String[] connectedIps) {
        mUdpRemoteListener = null;
        mUdpMessenger.stop(connectedIps);
    }

    public synchronized void stopWifiUdp1(String[] connectedIps) {
        mUdpRemoteListener1 = null;
        mUdpMessenger1.stop(connectedIps);
    }

    public synchronized void stopWifiTcp() {
        mTcpRemoteListener = null;
        mTcpMessenger.stop();
    }

    private synchronized void runInHandlerThread(final Runnable runnable) {
        if (mHandler != null) {
            mHandler.post(runnable);
        }
    }

    public synchronized void sendMessageTcp(final MessageInfo messageInfo) {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mTcpMessenger.sendMessage(messageInfo);
            }
        });
    }

    public synchronized void sendMessageUdp(final MessageInfo messageInfo) {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mUdpMessenger.sendMessage(messageInfo);
            }
        });
    }

    public synchronized void sendMessageUdp1(final MessageInfo messageInfo) {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mUdpMessenger1.sendMessage(messageInfo);
            }
        });
    }

    public synchronized void sendMessageUdp(final MessageType messageType, final byte[] messageData,
                    final String...ips) {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mUdpMessenger.sendMessage(messageType,  messageData, ips);
            }
        });
    }

    public synchronized void sendMessageUdp1(final MessageType messageType, final byte[] messageData,
                    final String...ips) {
        runInHandlerThread(new Runnable() {
            @Override
            public void run() {
                mUdpMessenger1.sendMessage(messageType,  messageData, ips);
            }
        });
    }
}
