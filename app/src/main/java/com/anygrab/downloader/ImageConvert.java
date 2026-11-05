package com.anygrab.downloader;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Re-encodes a downloaded image into the format the user picked. */
public final class ImageConvert {

    private ImageConvert() {
    }

    public static File convert(Context ctx, long downloadId, String format) throws Exception {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        ParcelFileDescriptor pfd = dm.openDownloadedFile(downloadId);
        if (pfd == null) {
            throw new Exception("File not available");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            pfd.close();
            throw new Exception("Not an image");
        }
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 4096) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, opts);
        pfd.close();
        if (bitmap == null) {
            throw new Exception("Could not decode the image");
        }

        Bitmap.CompressFormat compressFormat;
        String extension;
        if ("png".equals(format)) {
            compressFormat = Bitmap.CompressFormat.PNG;
            extension = "png";
        } else if ("webp".equals(format)) {
            if (Build.VERSION.SDK_INT >= 30) {
                compressFormat = Bitmap.CompressFormat.WEBP_LOSSY;
            } else {
                compressFormat = Bitmap.CompressFormat.WEBP;
            }
            extension = "webp";
        } else {
            compressFormat = Bitmap.CompressFormat.JPEG;
            extension = "jpg";
        }

        String title = titleOf(dm, downloadId);
        String base = title;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        File out = new File(ctx.getCacheDir(), Net.sanitize(base) + "." + extension);
        FileOutputStream fos = new FileOutputStream(out);
        bitmap.compress(compressFormat, 92, fos);
        fos.flush();
        fos.close();
        bitmap.recycle();
        return out;
    }

    public static void publish(Context ctx, File src, String displayName, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            Api29.publish(ctx, src, displayName, mime);
            return;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AnyGrab");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Cannot create the AnyGrab folder");
        }
        File dst = new File(dir, displayName);
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buffer = new byte[65536];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        in.close();
        out.flush();
        out.close();
        src.delete();
    }

    private static String titleOf(DownloadManager dm, long id) {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);
            cursor = dm.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                if (index >= 0) {
                    String title = cursor.getString(index);
                    if (title != null && title.length() > 0) {
                        return title;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "anygrab-" + id;
    }
}
