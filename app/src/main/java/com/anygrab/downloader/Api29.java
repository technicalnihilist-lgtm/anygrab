package com.anygrab.downloader;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** Kept in its own class so older devices never load MediaStore.Downloads. */
final class Api29 {

    private Api29() {
    }

    static void publish(Context ctx, File src, String displayName, String mime) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AnyGrab");
        Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new Exception("Could not create the output file");
        }
        OutputStream out = ctx.getContentResolver().openOutputStream(uri);
        if (out == null) {
            throw new Exception("Could not open the output file");
        }
        FileInputStream in = new FileInputStream(src);
        byte[] buffer = new byte[65536];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        in.close();
        out.flush();
        out.close();
    }
}
