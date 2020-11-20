package com.example.bitmaplibrary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * bitmap相关试验
 * bitmap加载
 * bitmap压缩
 * bitmap缓存
 * <p>
 * ARGB_8888：ARGB分别代表的是透明度,红色,绿色,蓝色,每个值分别用8bit来记录,也就是一个像素会占用4byte,共32bit.
 * <p>
 * ARGB_4444：ARGB的是每个值分别用4bit来记录,一个像素会占用2byte,共16bit.
 * <p>
 * RGB_565：R=5bit,G=6bit,B=5bit，不存在透明度,每个像素会占用2byte,共16bit.
 * <p>
 * ALPHA_8:该像素只保存透明度,会占用1byte,共8bit.
 * <p>
 * 在实际应用中而言,建议使用ARGB_8888以及RGB_565。 如果你不需要透明度,选择RGB_565,可以减少一半的内存占用.
 * <p>
 * 在Android3.0以前Bitmap是存放在内存中的，我们需要回收native层和Java层的内存。
 * 在Android3.0以后Bitmap是存放在堆中的，我们只要回收堆内存即可。
 * 官方建议我们3.0以后使用recycle()方法进行回收，该方法可以不主动调用，
 * 因为垃圾回收器会自动收集不可用的Bitmap对象进行回收
 * <p>
 * LruCache原理
 * LruCache是个泛型类，内部采用LinkedHashMap来实现缓存机制，它提供get方法和put方法来获取缓存和添加缓存，
 * 其最重要的方法trimToSize是用来移除最少使用的缓存和使用最久的缓存，并添加最新的缓存到队列中。
 */
public class MainActivity extends AppCompatActivity {
    private LruCache<String, Bitmap> mMemoryCache;

    private ImageView image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initLruCache();
        initView();
    }

    private void initView() {
        image = (ImageView) findViewById(R.id.image);

        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.icon,options);
        image.setImageBitmap(bitmap);
//        System.out.println("calculateInSampleSize:"+calculateInSampleSize(options,10,10));
//        System.out.println("options:"+options);
//        System.out.println("menory:"+bitmap.getByteCount()/1024+"k");
        System.out.println(compressImage(bitmap));
    }

    /**
     * 计算样本容量率
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;

        if (width > reqWidth || height > reqHeight) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }

    /**
     * 缩略图
     * 缩小样本容量值，图片记加载内存计算公式 width*height*config(config为图片的格式)
     * 减少样本容量值能有效减少图片的高度和宽度和像素值
     */
    private Bitmap thumbnail(String  path, int maxWidth, int maxHeight,boolean autoRotate) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        /**
         * inJustDecodeBounds
         * 如果将这个值置为true，那么在解码的时候将不会返回bitmap，只会返回这个bitmap的尺寸。这个属性的目的是，
         * 如果你只想知道一个bitmap的尺寸，但又不想将其加载到内存时。这是一个非常有用的属性。
         */
        options.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        options.inJustDecodeBounds = false;
        int sampleSize = calculateInSampleSize(options,maxWidth,maxHeight);
        options.inSampleSize =sampleSize;
        /**
         * inPreferredConfig
         * 这个值是设置色彩模式，默认值是ARGB_8888，在这个模式下，一个像素点占用4bytes空间，一般对透明度不做要求的话，
         * 一般采用RGB_565模式，这个模式下一个像素点占用2bytes。
         */
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        /**
         * 以下俩个属性安卓5以后被弃用
         */
        //options.inPurgeable =true;
        //options.inInputShareable = true;
        if(bitmap !=null&&!bitmap.isRecycled()){
            bitmap.recycle();
        }
        bitmap = BitmapFactory.decodeFile(path,options);
        return bitmap;
    }

    /**
     * 保存Bitmap
     */
    private String save(Bitmap  bitmap, Bitmap.CompressFormat format, int quality, File desFile) {
        try{
            FileOutputStream out = new FileOutputStream(desFile);
            if(bitmap.compress(format,quality,out)){
                out.flush();
                out.close();
            }
            if(bitmap!=null&&!bitmap.isRecycled()){
                bitmap.recycle();
            }
            return  desFile.getAbsolutePath();
        }catch (FileNotFoundException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 保存到SD卡
     */
    private String save(Bitmap  bitmap, Bitmap.CompressFormat format, int quality, Context context) {
        if(!Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED)){
            return null;
        }
        File dir = new File(Environment.getExternalStorageDirectory()+
                "/"+context.getPackageName());
        if(!dir.exists()){
            dir.mkdir();
        }
        File desFile = new File(dir, UUID.randomUUID().toString());
        return save(bitmap,format,quality,desFile);
    }

    /**
     * bitmap压缩
     *   质量压缩方法：在保持像素的前提下改变图片的位深及透明度等，来达到压缩图片的目的:
     *   bitmap图片的大小不会改变
     *   bytes.length是随着quality变小而变小的。
     *   这样适合去传递二进制的图片数据，比如分享图片，要传入二进制数据过去，限制500kb之内。
     *
     * 另外还有采样率方法和缩放方法，理论上俩者相同去改变图片的宽高从而达到压缩目的，采样率的解释就是
     * 配合inJustDecodeBounds，先获取图片的宽、高(这个过程就是取样)。
     * inJustDecodeBounds为true的时候bitmap会返回null但是宽高是存在的
     */
    public static Bitmap compressImage(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 把ByteArrayInputStream数据生成图片
        Bitmap bitmap = null;
        /**
         * 这里compress压缩的图片存储在本地的图片大小，所以不能直接解决oom错误
         */
        image.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] bytes = baos.toByteArray();
        // 把压缩后的数据baos存放到bytes中
        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap != null) {
            System.out.println(bitmap +"   "+ baos.toByteArray());
        }
        return bitmap;
    }


    /**
     * bitmap缓存
     */
    private void initLruCache(){
        // 获取到可用内存的最大值，使用内存超出这个值会引起OutOfMemory异常。
        // LruCache通过构造函数传入缓存值，以KB为单位。
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // 使用最大可用内存值的1/8作为缓存的大小。
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // 重写此方法来衡量每张图片的大小，默认返回图片数量。
                return bitmap.getByteCount() / 1024;
            }
        };
    }
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }
    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }
}