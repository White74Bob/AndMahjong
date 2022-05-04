package wb.game.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Random;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.Toast;
import wb.game.mahjong.R;

public class Utils {
    public static void showInfo(Context context, String title, String info) {
        AlertDialog.Builder infoDialog = new Builder(context);
        if (title != null) {
            infoDialog.setTitle(title);
        }
        if (info != null) {
            infoDialog.setMessage(info);
        }
        infoDialog.setPositiveButton(android.R.string.ok, null);
        infoDialog.create().show();
    }

    public interface ViewInit {
        void initViews(View rootView);
    }

    public interface ViewInitWithPositiveNegative extends ViewInit {
        void onPositiveClick(View rootView);
        void onNegativeClick(View rootView);
    }

    public static AlertDialog showViewDialog(final Context context, final int viewLayoutId,
                    final String title, final ViewInit viewInit) {
        if (viewInit instanceof ViewInitWithPositiveNegative) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            final View rootView = inflater.inflate(viewLayoutId, null);
            if (viewInit != null) {
                viewInit.initViews(rootView);
            }
            final ViewInitWithPositiveNegative viewInitWithPositiveNegative = (ViewInitWithPositiveNegative)viewInit;
            return showViewDialog(context, title, rootView,
                        /*positiveButtonListener*/
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                viewInitWithPositiveNegative.onPositiveClick(rootView);
                            }
                        },
                        /*negativeButtonListener*/
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                viewInitWithPositiveNegative.onNegativeClick(rootView);
                            }
                        });
        }
        return showViewDialog(context, viewLayoutId, title, viewInit, null, null);
    }

    public static AlertDialog showViewDialog(final Context context, final int viewLayoutId,
                    final String title, final ViewInit viewInit,
                    final DialogInterface.OnClickListener positiveButtonListener,
                    final DialogInterface.OnClickListener negativeButtonListener) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View rootView = inflater.inflate(viewLayoutId, null);
        if (viewInit != null) {
            viewInit.initViews(rootView);
        }
        return showViewDialog(context, title, rootView, positiveButtonListener,
                        negativeButtonListener);
    }

    public static AlertDialog showViewDialog(final Context context, final String title,
                    final View view, final DialogInterface.OnClickListener positiveButtonListener,
                    final DialogInterface.OnClickListener negativeButtonListener) {
        AlertDialog.Builder builder = new Builder(context);
        builder.setTitle(title);
        builder.setView(view);

        builder.setPositiveButton(android.R.string.ok, positiveButtonListener);
        if (negativeButtonListener != null) {
            builder.setNegativeButton(android.R.string.cancel, negativeButtonListener);
        }

        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    public static AlertDialog showListDialog(final Context context, final String title,
                    final ListAdapter listAdapter, final DialogInterface.OnClickListener onClickListener) {
        AlertDialog.Builder builder = new Builder(context);
        builder.setTitle(title);
        builder.setAdapter(listAdapter, onClickListener);
        builder.setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    public static void showToast(Context context, String message) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    public static void showConfirmDialog(final Context context, final String confirmPrompt,
                    final DialogInterface.OnClickListener positiveButtonListener) {
        showConfirmDialog(context, confirmPrompt, null, positiveButtonListener, null, null);
    }

    public static void showConfirmDialog(final Context context, final String confirmPrompt,
                    final String positiveButtonLabel,
                    final DialogInterface.OnClickListener positiveButtonListener,
                    final String negativeButtonLabel,
                    final DialogInterface.OnClickListener negativeButtonListener) {
        final AlertDialog.Builder builder = new Builder(context);

        builder.setTitle(android.R.string.dialog_alert_title);

        builder.setMessage(confirmPrompt);

        if (TextUtils.isEmpty(positiveButtonLabel)) {
            builder.setPositiveButton(android.R.string.ok, positiveButtonListener);
        } else {
            builder.setPositiveButton(positiveButtonLabel, positiveButtonListener);
        }
        if (TextUtils.isEmpty(negativeButtonLabel)) {
            builder.setNegativeButton(android.R.string.cancel, negativeButtonListener);
        } else {
            builder.setNegativeButton(negativeButtonLabel, negativeButtonListener);
        }

        builder.show();
    }

    // 显示自定义toast;
    // 这里是一个ImageView + ToastView
    public static void showToast(final Context context, final String info, final Bitmap bitmap) {
        Toast toast = Toast.makeText(context, info, Toast.LENGTH_SHORT);

        ImageView imageView = new ImageView(context);
        if (bitmap == null) {
            imageView.setImageResource(R.drawable.ic_launcher);
        } else {
            imageView.setImageBitmap(bitmap);
        }

        View toastView = toast.getView();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setVerticalGravity(Gravity.CENTER_VERTICAL);

        layout.addView(imageView);
        layout.addView(toastView);

        toast.setView(layout);
        toast.show();
    }

    public static String currentTimeString() {
        Calendar cal = Calendar.getInstance();

        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int date = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);

        final String timeFormat = "%4d-%02d-%02d %02d:%02d:%02d ";
        return String.format(timeFormat, year, month, date, hour, minute, second);
    }

    /**
     * Format: yyyy-MM-dd HH:mm:ss
     *         text
     */
    private static final String TIME_TEXT_FORMAT = "%s\n%s";
    public static String getTextWithTime(final String text) {
        return String.format(TIME_TEXT_FORMAT, currentTimeString(), text);
    }

    private static final Timestamp sTimestamp = new Timestamp(System.currentTimeMillis());
    public static String currentTimeDetails() {
        sTimestamp.setTime(System.currentTimeMillis());
        return sTimestamp.toString();
    }

    public static boolean isMainThread() {
        Looper looper = Looper.myLooper();
        return looper != null && looper == Looper.getMainLooper();
    }

    public static String getCurrentProcess(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == pid) {
                //Log.d(TAG, "returns from getCurrentProcess...");
                return processInfo.processName;
            }
        }
        return getProcess(context);
    }

    public static String getProcess(Context context) {
        BufferedReader cmdlineReader = null;
        try {
            cmdlineReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream("/proc/"
                    + android.os.Process.myPid() + "/cmdline"), "iso-8859-1"));
            int c;
            StringBuilder processName = new StringBuilder();
            while ((c = cmdlineReader.read()) > 0) {
                processName.append((char) c);
            }
            //Log.d(TAG, "returns from getProcess...");
            return processName.toString();
        } catch (IOException ioe) {
            return ioe.toString();
        } finally {
            if (cmdlineReader != null) {
                try {
                    cmdlineReader.close();
                } catch (IOException ioe) {
                }
            }
        }
    }

    private static final String FORMAT_THREAD_INFO = "Thread name:%s,Thread id:%d\ntid:%d";

    public static String getCurrentThreadInfo() {
        final Thread currentThread = Thread.currentThread();
        String threadInfo = String.format(FORMAT_THREAD_INFO,
                currentThread.getName(),
                currentThread.getId(),
                android.os.Process.myTid());
        return threadInfo;
    }

    public static String getExceptionInfo(Exception e) {
        return getInfo(e);
    }

    public static String getInfo(Throwable t) {
        return getThrowableStackTrace(t);
    }

    // Format:
    // throwable string
    //
    // throwable message
    // throwable call stack
    //   ...
    // Caused by (Optional)
    // throwable cause info...
    public static String getThrowableStackTrace(Throwable t) {
        final String message = t.getMessage();
        final Throwable tCause = t.getCause();

        StringBuilder sb = new StringBuilder();

        sb.append(t.toString()).append('\n');

        if (!TextUtils.isEmpty(message)) {
            sb.append('\n').append(message).append('\n');
        }
        StackTraceElement[] trace = t.getStackTrace();
        for (StackTraceElement ste : trace) {
            sb.append(ste.toString());
            sb.append('\n');
        }

        if (tCause != null) {
            sb.append("Caused by\n");
            sb.append(getThrowableStackTrace(tCause));
        }

        return sb.toString();
    }

    private static final Random sRandom = new Random();

    public static int getRandomInt(final int min, final int max) {
        if (min < 0 || min > max) {
            throw new IllegalArgumentException();
        }
        if (min == max) {
            return min;
        }
        int randNumber;
        do {
            randNumber = sRandom.nextInt() % (max + 1);
        } while (randNumber < min || randNumber > max);
        return randNumber;
    }

    public static boolean getRandomBoolean() {
        return sRandom.nextBoolean();
    }

    // 找出最大值
    public static int max(int...values) {
        if (values == null || values.length <= 0) {
            throw new IllegalArgumentException("input values: " + values);
        }
        int max = values[0];
        for (int value : values) {
            if (max < value) {
                max = value;
            }
        }
        return max;
    }

    // 找出最小值
    public static int min(int...values) {
        if (values == null || values.length <= 0) {
            throw new IllegalArgumentException("input values: " + values);
        }
        int min = values[0];
        for (int value : values) {
            if (min > value) {
                min = value;
            }
        }
        return min;
    }

    public static String getEditTextInput(final EditText editText) {
        Editable editable = editText == null ? null : editText.getEditableText();
        if (editable == null) {
            return null;
        }
        return editable.toString();
    }

    public static String[] readStringArray(SharedPreferences sharedPref, String key, String defValue,
                    String separator) {
        String value = null;
        try {
            value = sharedPref.getString(key, defValue);
        } catch (Exception e) {
            return null;
        }
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String[] array = value.split(separator);
        return array;
    }

    private static enum DocumentAuthority {
        ExternalStorage("com.android.externalstorage.documents"),
        Downloads("com.android.providers.downloads.documents"),
        Media("com.android.providers.media.documents"),
        Unknown();

        public final String authority;

        private DocumentAuthority(final String authority) {
            this.authority = authority;
        }

        private DocumentAuthority() {
            this.authority = null;
        }

        public static DocumentAuthority getDocumentAuthority(final Uri uri) {
            final String authority = uri.getAuthority();
            for (DocumentAuthority da : values()) {
                if (TextUtils.equals(da.authority, authority)) {
                    return da;
                }
            }
            return Unknown;
        }
    }

    private static enum MediaType {
        Image("image", MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
        Video("video", MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
        Audio("audio", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
        Unknown();

        public final String type;
        public final Uri externalContentUri;

        private MediaType(final String type, final Uri externalContentUri) {
            this.type = type;
            this.externalContentUri = externalContentUri;
        }

        private MediaType() {
            this.type = null;
            this.externalContentUri = null;
        }

        public static MediaType getMediaType(final String input) {
            for (MediaType mediaType : values()) {
                if (TextUtils.equals(mediaType.type, input)) {
                    return mediaType;
                }
            }
            return Unknown;
        }
    }

    // Refer to
    // http://stackoverflow.com/questions/13209494/how-to-get-the-full-file-path-from-uri
    public static String getFilepathFromContentUri(Context context, Uri contentUri) {
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions since(from) KITKAT (Android 4.4), we need to
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract
                        .isDocumentUri(context.getApplicationContext(), contentUri)) {
            DocumentAuthority documentAuthority = DocumentAuthority.getDocumentAuthority(contentUri);
            final String docId = DocumentsContract.getDocumentId(contentUri);
            final String[] split = docId.split(":");
            switch (documentAuthority) {
                case ExternalStorage:
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                case Downloads:
                    contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"),
                                Long.valueOf(docId));
                    break;
                case Media:
                    final String type = split[0];
                    MediaType mediaType = MediaType.getMediaType(type);
                    if (mediaType == MediaType.Unknown) return null;
                    contentUri = mediaType.externalContentUri;
                    selection = "_id=?";
                    selectionArgs = new String[] { split[1] };
                    break;
                default:
                    return null;
            }
        }

        Cursor cursor = null;
        final String[] proj = { MediaStore.Images.Media.DATA };
        try {
            cursor = context.getContentResolver().query(contentUri, proj, selection, selectionArgs,
                            null);
            if (cursor == null) return contentUri.getPath();
            int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            if (column_index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(column_index);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static String getFilePath(final Context context, final Uri uri) {
        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            return getFilepathFromContentUri(context, uri);
        }
        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            return uri.getPath();
        }
        return null;
    }

    // 以下是另一种代码实现, 根据content uri来获取image file path.
    // 可以看到api11之前和api11-18以及api19之后的区别.
    public static String getImageFileRealFilePathFromUri(final Context context,
                    final Uri contentUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return getImageFileRealPathFromURI_API19(context, contentUri);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return getRealPathFromURI_API11to18(context, contentUri);
        }
        return getRealPathFromURI_BelowAPI11(context, contentUri);
    }

    public static String getImageFileRealPathFromURI_API19(Context context, Uri uri) {
        String wholeID = DocumentsContract.getDocumentId(uri);

        // Split at colon, use second item in the array
        String id = wholeID.split(":")[1];

        // where id is equal to
        String sel = MediaStore.Images.Media._ID + "=?";

        String[] columns = { MediaStore.Images.Media.DATA };

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        columns, sel, new String[]{ id }, null);
            int columnIndex = cursor.getColumnIndex(columns[0]);
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(columnIndex);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static String getRealPathFromURI_API11to18(Context context, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader cursorLoader = new CursorLoader(context, contentUri, proj, null, null, null);
        Cursor cursor = null;
        try {
            cursor = cursorLoader.loadInBackground();

            if(cursor == null) return null;
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            if (cursor.moveToFirst()) {
                return cursor.getString(column_index);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static String getRealPathFromURI_BelowAPI11(Context context, Uri contentUri){
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            if (cursor.moveToFirst()) {
                return cursor.getString(column_index);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static boolean saveToFile(final String filepath, final byte[] data) {
        if (data == null || data.length <= 0) return false;
        File file = new File(filepath);
        if (file.exists()) {
            file.delete();
        }
        return saveToFile(file, data);
    }

    public static boolean saveToFile(final File file, final byte[] data) {
        FileOutputStream outStream = null;

        try {
            outStream = new FileOutputStream(file);
            outStream.write(data);
            outStream.flush();
            return true;
        } catch (IOException ioe) {
            return false;
        } finally {
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException ioe) {
                }
            }
        }
    }

    public static byte[] readFileData(final String filepath) {
        File file = new File(filepath);
        if (!file.exists()) return null;
        byte[] data = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            data = new byte[(int)file.length()];
        } catch (FileNotFoundException fnfe) {
            return null;
        }
        try {
            fis.read(data);
            return data;
        } catch (IOException ioe) {
            return null;
        } finally {
            try {
                fis.close();
            } catch (IOException ioe) {
                // TODO:Nothing?
            }
        }
    }

    public static byte[] copyData(final byte[] data) {
        byte[] newData = new byte[data.length];
        for (int i = 0; i < newData.length; i++) {
            newData[i] = data[i];
        }
        return newData;
    }
}
