package wb.conn;

import wb.conn.wifi.UdpMessenger.Result;

public interface MessageListener {
    void newMessageComes(final MessageInfo msgInfo);
    void messageSent(final MessageInfo msgInfo, final Result...results);
}
