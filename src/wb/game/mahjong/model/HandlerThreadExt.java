package wb.game.mahjong.model;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public class HandlerThreadExt extends HandlerThread {
    private Handler mHandler;

    public HandlerThreadExt(String threadName) {
        super(threadName);
    }

    @Override
    protected final void onLooperPrepared() {
        mHandler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (!doHandleMessage(msg)) {
                    super.handleMessage(msg);
                }
            }
        };
        synchronized(this) {
            notify();
        }
        whenLooperPrepared();
        super.onLooperPrepared();
    }

    protected void whenLooperPrepared() {
        // TODO: empty in the parent.
    }

    protected boolean doHandleMessage(Message msg) {
        // TODO: empty in parent.
        return false;
    }

    private synchronized void keepWaiting() {
        while (mHandler == null) {
            try {
                wait();
            } catch (InterruptedException ie) {
            }
        }
    }

    public void post(Runnable runnable) {
        keepWaiting();
        mHandler.post(runnable);
    }

    public void sendEmptyMessage(int msgWhat) {
        keepWaiting();
        mHandler.sendEmptyMessage(msgWhat);
    }

    public void sendMessage(int what, Object obj) {
        keepWaiting();
        Message msg = mHandler.obtainMessage(what, obj);
        mHandler.sendMessage(msg);
    }

    protected void sendMessageWithArg1(int what, int arg1) {
        Message msg = mHandler.obtainMessage(what);
        msg.arg1 = arg1;
        mHandler.sendMessage(msg);
    }

    public void clear() {
        interrupt();
        mHandler.removeCallbacksAndMessages(null);
    }
}
