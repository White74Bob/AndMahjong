package wb.game.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.view.View;
import android.view.View.MeasureSpec;

// Refer to
// http://www.2cto.com/kf/201312/265180.html
public class BitmapUtils {
    /** view转Bitmap **/
    public static Bitmap convertViewToBitmap(View view, int bitmapWidth, int bitmapHeight) {
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    /** 将控件转换为bitmap **/
    public static Bitmap convertViewToBitMap(View view) {
        // 打开图像缓存
        view.setDrawingCacheEnabled(true);
        // 必须调用measure和layout方法才能成功保存可视组件的截图到png图像文件
        // 测量View大小
        view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        // 发送位置和尺寸到View及其所有的子View
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        // 获得可视组件的截图
        Bitmap bitmap = view.getDrawingCache();
        return bitmap;
    }

    public static Bitmap getBitmapFromView(View view) {
        Bitmap returnedBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(returnedBitmap);
        Drawable bgDrawable = view.getBackground();
        if (bgDrawable != null) {
            bgDrawable.draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }
        view.draw(canvas);
        return returnedBitmap;
    }

    /** 获取屏幕截图的bitmap对象的代码如下 **/
    public Bitmap getScreenSnapshot(View view) {
        View rootView = view.getRootView();
        rootView.setDrawingCacheEnabled(true);
        rootView.buildDrawingCache();
        // 不明白为什么这里返回一个空，有帖子说不能在oncreat方法中调用
        // 测量View大小
        rootView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        // 发送位置和尺寸到View及其所有的子View
        rootView.layout(0, 0, rootView.getMeasuredWidth(), rootView.getMeasuredHeight());
        // 解决措施，调用上面的measure和layout方法之后，返回值就不再为空
        // 如果想要创建的是固定长度和宽度的呢？
        Bitmap bitmap = rootView.getDrawingCache();
        rootView.destroyDrawingCache();
        return bitmap;
    }

    /** Drawable → Bitmap **/
    public static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable
                .getIntrinsicHeight(),
                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                        : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /** bitmap → drawable */
    public static Drawable bitmapToDrawable(Context context, String filename) {
        Bitmap image = null;
        BitmapDrawable ddd = null;
        try {
            AssetManager am = context.getAssets();
            InputStream is = am.open(filename);
            image = BitmapFactory.decodeStream(is);
            ddd = new BitmapDrawable(context.getResources(), image);
            is.close();
        } catch (Exception e) {
        }
        return ddd;
    }

    /** byte[] → Bitmap **/
    public static Bitmap byteToDrawable(Context context, byte[] bb) {
        Bitmap pp = BitmapFactory.decodeByteArray(bb, 0, bb.length);
        return pp;
    }

    /** Bitmap → byte[] **/
    public static byte[] bitmapToBytes(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] yy = baos.toByteArray();
        return yy;
    }

    /** byte[] → Bitmap **/
    public static Bitmap bytesToBitmap(final byte[] data) {
        if (data == null || data.length <= 0) return null;
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    /** 将text 转换成 bitmap **/
    public static Bitmap createTxtImage(String txt, int txtSize) {
        Bitmap mbmpTest = Bitmap.createBitmap(txt.length() * txtSize + 4, txtSize + 4,
                Config.ARGB_8888);
        Canvas canvasTemp = new Canvas(mbmpTest);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setTextSize(txtSize);
        canvasTemp.drawText(txt, 2, txtSize - 2, p);
        return mbmpTest;
    }

    /** 显示将bitmap进行缩放 **/
    public Bitmap bitmapScanel(Context context, final int resId) {
        // 通过openRawResource获取一个inputStream对象
        InputStream inputStream = context.getResources().openRawResource(resId);
        // 通过一个InputStream创建一个BitmapDrawable对象
        BitmapDrawable drawable = new BitmapDrawable(inputStream);
        // 通过BitmapDrawable对象获得Bitmap对象
        Bitmap bitmap = drawable.getBitmap();
        // 利用Bitmap对象创建缩略图
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, 40, 40);
        return bitmap;
    }

    /** 放大缩小图片 **/
    public static Bitmap zoomBitmap(Bitmap bitmap, int w, int h) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Matrix matrix = new Matrix();
        float scaleWidht = ((float) w / width);
        float scaleHeight = ((float) h / height);
        matrix.postScale(scaleWidht, scaleHeight);
        Bitmap newbmp = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        return newbmp;
    }

    /** 获得圆角图片的方法 **/
    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, float roundPx) {
        Bitmap output = Bitmap
                .createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    /** 对 bitmap 进行裁剪 **/
    public Bitmap bitmapClip(Context context, int id, int x, int y) {
        Bitmap map = BitmapFactory.decodeResource(context.getResources(), id);
        map = Bitmap.createBitmap(map, x, y, 120, 120);
        return map;
    }

    /** 图片的倒影效果 */
    public static Bitmap createReflectedImage(Bitmap originalImage) {
        final int reflectionGap = 4;
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        Matrix matrix = new Matrix();
        matrix.preScale(1, -1);
        // Create a Bitmap with the flip matrix applied to it.
        // We only want the bottom half of the image.
        Bitmap reflectionImage = Bitmap.createBitmap(originalImage, 0, height / 2, width,
                height / 2, matrix, false);
        // Create a new bitmap with same width but taller to fit reflection
        Bitmap bitmapWithReflection = Bitmap.createBitmap(width, (height + height / 2),
                Config.ARGB_8888);
        // Create a new Canvas with the bitmap that's big enough for
        // the image plus gap plus reflection
        Canvas canvas = new Canvas(bitmapWithReflection);
        // Draw in the original image
        canvas.drawBitmap(originalImage, 0, 0, null);
        // Draw in the gap
        Paint defaultPaint = new Paint();
        canvas.drawRect(0, height, width, height + reflectionGap, defaultPaint);
        // Draw in the reflection
        canvas.drawBitmap(reflectionImage, 0, height + reflectionGap, null);
        // Create a shader that is a linear gradient that covers the reflection
        Paint paint = new Paint();
        LinearGradient shader = new LinearGradient(0, originalImage.getHeight(), 0,
                bitmapWithReflection.getHeight() + reflectionGap, 0x70ffffff, 0x00ffffff,
                TileMode.CLAMP);
        // Set the paint to use this shader (linear gradient)
        paint.setShader(shader);
        // Set the Transfer mode to be porter duff and destination in
        paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
        // Draw a rectangle using the paint with our linear gradient
        canvas.drawRect(0, height, width, bitmapWithReflection.getHeight() + reflectionGap, paint);
        return bitmapWithReflection;
    }

    // 90度旋转bitmap.
    public static Bitmap rotateBitmap90(Bitmap bm) {
        Matrix matrix = new Matrix();
        matrix.reset();
        matrix.setRotate(90);
        return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
    }

    // 方法1. 利用Bitmap.createBitmap
    public static Bitmap rotateBitmap(Bitmap bm, final int orientationDegree) {
        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
    }

    // 方法2. 利用Canvas.drawBitmap
    public static Bitmap rotateBitmap1(Bitmap bm, final int orientationDegree) {
        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        float targetX, targetY;
        if (orientationDegree == 90) {
            targetX = bm.getHeight();
            targetY = 0;
        } else {
            targetX = bm.getHeight();
            targetY = bm.getWidth();
        }

        final float[] values = new float[9];
        m.getValues(values);

        float x1 = values[Matrix.MTRANS_X];
        float y1 = values[Matrix.MTRANS_Y];

        m.postTranslate(targetX - x1, targetY - y1);

        Bitmap bm1 = Bitmap.createBitmap(bm.getHeight(), bm.getWidth(), Bitmap.Config.ARGB_8888);

        Paint paint = new Paint();
        Canvas canvas = new Canvas(bm1);
        canvas.drawBitmap(bm, m, paint);

        return bm1;
    }
    /* 以上两种旋转bitmap的方法来自于
     * http://blog.sina.com.cn/s/blog_783ede0301014mln.html
     *
     * 性能测试：
     * 1. 手机
     * CPU : MTK6575 ,1G Hz
     * MEM : 512MB
     * OS : andoid 2.3.7
     *
     * 2.图片尺寸1632 * 1224
     *
     * 结果：
     *
     * 1. 方法1在280 - 350毫秒间， 方法2在110毫秒左右。
     * 2. 方法2优于方法1
     */


    public static boolean saveBitmapToPngFile(Bitmap bm, File file) {
        FileOutputStream outStream = null;

        try {
            outStream = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            return true;
        } catch (Exception e) {
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

    public static boolean copyToPngFile(String srcFilepath, String destFilepath) {
        Bitmap bm = BitmapFactory.decodeFile(srcFilepath);
        return copyToPngFile(bm, destFilepath);
    }

    public static boolean copyToPngFile(Bitmap bm, String destFilepath) {
        File destFile = new File(destFilepath);
        if (destFile.exists()) {
            destFile.delete();
        }
        return saveBitmapToPngFile(bm, destFile);
    }
}
