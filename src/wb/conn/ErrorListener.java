package wb.conn;

public interface ErrorListener {
    void onException(Exception e, String log);
    void onError(String errorInfo);
}
