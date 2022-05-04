package wb.conn.wifi;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.os.Handler;
import android.os.Looper;
import wb.conn.ErrorListener;
import wb.conn.LogListener;
import wb.conn.MessageInfo;
import wb.conn.MessageInfo.MessageType;
import wb.conn.MessageListener;
import wb.conn.MessageUtils;
import wb.game.mahjong.constants.Constants;
import wb.game.utils.Utils;

public class UdpMessenger {
    private final static int K = 1024;
    private final static int DEFAULT_BUFFER_SIZE = 59 * K;

    // UDP不能传送超过65536字节的datagram packet.
    private static final int UDP_PACKET_SIZE_LIMIT = 65536;

    private static final String FORMAT_UDP_MESSENGER_NAME = "UDP Messenger - %d";

    private final int mPort;

    private final Receiver mReceiver;
    private final Sender mSender;

    private final Handler mHandler;

    private final MessageListener mMessageListener;
    private final ErrorListener mErrorListener;
    private final LogListener mLogListener;

    public UdpMessenger(final int port, Handler handler, ErrorListener errorListener,
                    MessageListener messageListener, LogListener logListener) {
        if (port <= 1024) {
            throw new RuntimeException("UDP port must be greater than 1024!");
        }
        mPort = port;
        mReceiver = new Receiver();
        mSender = new Sender();

        if (handler.getLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("UdpMessenger can't use MainLooper!");
        }
        mHandler = handler;

        mErrorListener = errorListener;
        mMessageListener = messageListener;
        mLogListener = logListener;
    }

    private void addLog(final String log) {
        if (mLogListener == null) return;
        if (Constants.LOG_WITH_TIME) {
            mLogListener.addLog(Utils.getTextWithTime(log));
            return;
        }
        mLogListener.addLog(log);
    }

    public int getPort() {
        return mPort;
    }

    public synchronized void start() {
        mReceiver.start();
        mSender.start();
    }

    public synchronized void stop(String[] connectedIps) {
        mReceiver.stop();
        mSender.stop(connectedIps);
    }

    public void sendMessage(final MessageInfo messageInfo) {
        synchronized(mSender) {
            mSender.sendMessage(messageInfo);
        }
    }

    public void sendMessage(final MessageType messageType, final byte[] messageData, final String...ips) {
        synchronized(mSender) {
            byte[] message_data = null;
            try {
                message_data = MessageInfo.constructMessageData(messageType, null, messageData);
                mSender.sendMessage(message_data, ips);
            } catch (IOException ioe) {
                // ...
            }
        }
    }

    private void reportException(Exception e, String log) {
        if (mErrorListener == null) return;
        mErrorListener.onException(e, log);
    }

    private void reportException(Exception e) {
        reportException(e, null);
    }

    private static final String FORMAT_THREAD_NAME_RECEIVER = "UDP Listener - %d";
    private class Receiver {
        private boolean mIsAlive;

        public Receiver() {
        }

        public synchronized void start() {
            if (mIsAlive) return;
            startListenThread();
        }

        public synchronized void startListenThread() {
            (new Thread(String.format(FORMAT_THREAD_NAME_RECEIVER, mPort)) {
                @Override
                public void run() {
                    startListen();
                }
            }).start();
        }

        public synchronized void stop() {
            mIsAlive = false;
        }

        private void startListen() {
            DatagramSocket datagramSocket = null;
            try {
                datagramSocket = new DatagramSocket(null);
                datagramSocket.setReuseAddress(true);
                datagramSocket.bind(new InetSocketAddress(mPort));
                //datagramSocket = new DatagramSocket(mPort);
            } catch (SocketException se) {
                reportException(se);
                return;
            }

            mIsAlive = true;
            final byte[] dataBuffer = new byte[DEFAULT_BUFFER_SIZE];
            DatagramPacket datagramPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
            while (mIsAlive) {
                try {
                    datagramSocket.receive(datagramPacket);
                } catch (IOException ioe) {
                    reportException(ioe, "Failed to datagramSocket.receive!");
                    continue;
                }
                final InetAddress fromInetAddress = datagramPacket.getAddress();
                addLog("Received from " + fromInetAddress + ",len:" + datagramPacket.getLength());
                final byte[] newData = Utils.copyData(datagramPacket.getData());
                runInHandlerThread(new Runnable() {
                    @Override
                    public void run() {
                        MessageInfo messageInfo = null;
                        try {
                            messageInfo = MessageInfo.parseReceivedMessage(
                                            fromInetAddress, newData);
                        } catch (IOException ioe) {
                            reportException(ioe, "Failed to parseReceivedMessage");
                            return;
                        }
                        if (messageInfo.ip.equals(WifiUtils.getIpInWifi())) {
                            //Exception e = new RuntimeException("Why received MSG from self?!\n" +  messageInfo);
                            //reportException(e, "messageInfo.ip:" + messageInfo.ip + "\nfromInetAddress:" + fromInetAddress);
                            return; // 如果误用了广播，则UDP会收到自己发送的消息. 现在忽略自己发送的消息吧。
                        }
                        if (mMessageListener != null) {
                            mMessageListener.newMessageComes(messageInfo);
                        }
                    }
                });
            }
            if (!datagramSocket.isClosed()) {
                datagramSocket.close();
            }
        }
    }

    public static enum SendResult {
        Succeeded,
        Failed;
    }

    public static class Result {
        public final SendResult result;

        public final Exception e;

        public final String info;

        public Result(SendResult result) {
            this(result, null, null);
        }

        public Result(Exception e) {
            this(SendResult.Failed, e, null);
        }

        public Result(SendResult result, String info) {
            this(result, null, info);
        }

        public Result(SendResult result, Exception e, String info) {
            this.result = result;
            this.info = info;
            this.e = e;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString());
            if (info != null) {
                sb.append('\n').append(info);
            }
            if (e != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
            }
            return sb.toString();
        }
    }

    private synchronized void runInHandlerThread(final Runnable runnable) {
        if (mHandler != null) {
            mHandler.post(runnable);
        }
    }

    private class Sender {
        private DatagramSocket mSocket;

        private synchronized void closeSocket() {
            if (mSocket != null) {
                if (!mSocket.isClosed()) {
                    mSocket.close();
                }
                mSocket = null;
            }
        }

        public synchronized void start() {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    closeSocket();
                    try {
                        mSocket = new DatagramSocket();
                    } catch (IOException ioe) {
                        throw new RuntimeException("Failed to create UDP sender socket!" + ioe);
                    }
                }
            });
        }

        public synchronized void stop(final String[] destIps) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    final int destNum = (destIps == null ? 0 : destIps.length);
                    if (destNum > 0) {
                        sendEvent(MessageType.EventDisconnect, destIps);
                    } else {
                        closeSocket();
                    }
                }
            });
        }

        public synchronized void sendEvent(final MessageType event, final String...ips) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    byte[] message_data = null;
                    try {
                        message_data = MessageInfo.constructMessageData(event, null,
                                        event.getMessageData());
                        sendData(message_data, ips);
                        if (event == MessageType.EventDisconnect) {
                            closeSocket();
                        }
                    } catch (IOException ioe) {
                        reportException(ioe, "Failed to sendEvent!");
                    }
                }
            });
        }

        public synchronized void sendMessage(final byte[] messageData, final String...ips) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    sendData(messageData, ips);
                }
            });
        }

        public synchronized void sendMessage(final MessageInfo messageInfo) {
            runInHandlerThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Result[] results = doSendData(MessageInfo.constructMessageData(messageInfo),
                                        messageInfo.ip);
                        if (results != null && mMessageListener != null) {//Constants.debug("sendMessage, " + messageInfo);
                            mMessageListener.messageSent(messageInfo, results);
                        }
                    } catch (IOException ioe) {
                        reportException(ioe, "Failed to send message!\n" + messageInfo);
                    }
                }
            });
        }

        private Result[] sendData(final byte[] messageData, final String... ips) {
            try {
                return doSendData(messageData, ips);
            } catch (Exception e) {
                reportException(e, "Failed to send data to " + MessageUtils.getIps(ips));
                return null;
            }
        }
/*
        private String getIpsString(final String[] ips) {
            StringBuilder sb = new StringBuilder();
            for (String ip : ips) {
                if (sb.length() > 0) sb.append(',');
                sb.append(ip);
            }
            return sb.toString();
        }
*/
        private Result[] doSendData(final byte[] messageData, final String... ips) {
            if (mSocket == null) return null;
            if (messageData == null) {
                throw new RuntimeException("NULL data?!");
            }
            if (messageData.length <= 0) {
                throw new RuntimeException("Empty data?!");
            }
            if (messageData.length >= DEFAULT_BUFFER_SIZE) {
                throw new RuntimeException("Too big data size:" + messageData.length + " bytes, default:" + DEFAULT_BUFFER_SIZE);
            }//Constants.debug("doSendData, " + messageType + ", datalen:" + messageData.length + ", to" + ips[0]);

            Result[] results = new Result[ips.length];
            InetAddress[] toInetAddresses = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) {
                toInetAddresses[i] = null;
                /*if (ips[i].equals(WifiUtils.getIpInWifi())) {
                    reportException(new RuntimeException("Why self ip appears here?!s"), getIpsString(ips));
                    continue;
                }*/
                try {
                    toInetAddresses[i] = InetAddress.getByName(ips[i]);
                } catch (UnknownHostException uhe) {
                    results[i] = new Result(SendResult.Failed, uhe,
                                    "InetAddress.getByName " + ips[i] + ", parsedIp null");
                }
            }

            DatagramPacket packet = null;
            for (int i = 0; i < toInetAddresses.length; i++) {
                if (toInetAddresses[i] == null) {
                    continue;
                }
                if (packet == null) {
                    packet = new DatagramPacket(messageData, messageData.length, toInetAddresses[i],
                                    mPort);
                } else {
                    packet.setAddress(toInetAddresses[i]);
                }
                try {
                    mSocket.send(packet);
                    results[i] = new Result(SendResult.Succeeded, ips[i]);
                } catch (IOException ioe) {
                    results[i] = new Result(SendResult.Failed, ioe,
                                    ips[i] + ",msgData.length:" + messageData.length);
                }
            }
            return results;
        }
    }
}
