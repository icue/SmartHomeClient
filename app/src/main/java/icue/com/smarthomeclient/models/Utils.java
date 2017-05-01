package icue.com.smarthomeclient.models;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.util.Base64;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by Icue on 2017/4/28.
 */

public final class Utils {

    private static String dateFormat = "MM/dd/yyyy  HH:mm:ss";
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);

    public static Bitmap decodeFromBase64(String image) throws IOException, IllegalArgumentException {
        byte[] decodedByteArray = android.util.Base64.decode(image, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.length);
    }

    public static String ConvertMilliSecondsToFormattedDate(String milliSeconds){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Long.parseLong(milliSeconds));
        return simpleDateFormat.format(calendar.getTime());
    }

    public static String audioEncode(File file) {
        byte[] bytes = null;
        try {
            bytes = FileUtils.readFileToByteArray(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Base64.encodeToString(bytes, 0);
    }

    public static void playAudio(String audio, File file) {
        try {
            byte[] decoded = Base64.decode(audio, 0);
            MediaPlayer mediaPlayer = new MediaPlayer();
            File tempMp3 = File.createTempFile("audio_", "amr", file);
            tempMp3.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempMp3);
            fos.write(decoded);
            fos.close();
            mediaPlayer.reset();
            FileInputStream fis = new FileInputStream(tempMp3);
            mediaPlayer.setDataSource(fis.getFD());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void showToastMsg(Context context, String msg) {
        Toast.makeText(context, msg , Toast.LENGTH_SHORT).show();
    }

    public static void showToastMsg(Context context, String msg, int length) {
        Toast.makeText(context, msg , Toast.LENGTH_LONG).show();
    }

    public static Bitmap decodeSampledBitmapFromDrawable(String image, int reqWidth, int reqHeight)
            throws IOException, IllegalArgumentException {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        byte[] decodedByteArray = android.util.Base64.decode(image, Base64.DEFAULT);
        BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.length, options);
//        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.length, options);
    }


    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
