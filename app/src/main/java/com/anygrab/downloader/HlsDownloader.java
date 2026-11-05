package com.anygrab.downloader;

import android.content.Context;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Downloads an HLS stream (.m3u8) by fetching its playlist, choosing one
 * variant, then pulling every segment and stitching them into a single file
 * that MediaExtractor can read. Plain MPEG-TS segments concatenate as-is;
 * fMP4 streams get their init segment first.
 */
public final class HlsDownloader {

    private static final int MAX_SEGMENTS = 4000;
    private static final long MAX_BYTES = 1200L * 1024L * 1024L;

    private HlsDownloader() {
    }

    public static File download(Context ctx, String playlistUrl, boolean audioOnly) throws Exception {
        String base = playlistUrl;
        String body = fetchText(base);

        for (int depth = 0; depth < 3 && body.contains("#EXT-X-STREAM-INF"); depth++) {
            String variant = pickVariant(body, base, audioOnly);
            if (variant == null) {
                throw new Exception("the playlist lists no playable stream");
            }
            base = variant;
            body = fetchText(base);
        }

        if (body.contains("#EXT-X-KEY") && !body.contains("METHOD=NONE")) {
            throw new Exception("this stream is encrypted (DRM), so it cannot be saved");
        }
        if (body.indexOf("#EXT-X-ENDLIST") < 0 && body.contains("#EXT-X-MEDIA-SEQUENCE")) {
            // live stream: still try, but the result will be a partial capture
        }

        boolean fragmented = body.contains("#EXT-X-MAP");
        String mapUri = fragmented ? firstMapUri(body, base) : null;
        List<String> segments = parseSegments(body, base);
        if (segments.isEmpty()) {
            throw new Exception("the playlist has no segments");
        }

        String suffix = fragmented ? ".mp4" : ".ts";
        File out = new File(ctx.getCacheDir(), "stream-" + System.currentTimeMillis() + suffix);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 16);
        long total = 0L;
        try {
            if (mapUri != null) {
                total += copy(mapUri, os, MAX_BYTES - total);
            }
            int limit = Math.min(segments.size(), MAX_SEGMENTS);
            for (int i = 0; i < limit; i++) {
                if (total >= MAX_BYTES) {
                    break;
                }
                total += copy(segments.get(i), os, MAX_BYTES - total);
            }
        } finally {
            os.flush();
            os.close();
        }
        if (out.length() == 0L) {
            throw new Exception("nothing could be downloaded from this stream");
        }
        return out;
    }

    private static List<String> parseSegments(String body, String base) {
        List<String> segments = new ArrayList<String>();
        String[] lines = body.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            segments.add(absolute(base, line));
        }
        return segments;
    }

    private static String firstMapUri(String body, String base) {
        String[] lines = body.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-MAP")) {
                int start = line.indexOf("URI=\"");
                if (start >= 0) {
                    int end = line.indexOf('"', start + 5);
                    if (end > start) {
                        return absolute(base, line.substring(start + 5, end));
                    }
                }
            }
        }
        return null;
    }

    private static String pickVariant(String body, String base, boolean audioOnly) {
        String[] lines = body.split("\\r?\\n");
        String audioRendition = null;
        String bestUri = null;
        long bestBandwidth = -1L;
        String lowUri = null;
        long lowBandwidth = Long.MAX_VALUE;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-MEDIA") && line.toUpperCase(Locale.US).contains("TYPE=AUDIO")) {
                int start = line.indexOf("URI=\"");
                if (start >= 0) {
                    int end = line.indexOf('"', start + 5);
                    if (end > start && audioRendition == null) {
                        audioRendition = absolute(base, line.substring(start + 5, end));
                    }
                }
                continue;
            }
            if (!line.startsWith("#EXT-X-STREAM-INF")) {
                continue;
            }
            long bandwidth = 0L;
            int at = line.toUpperCase(Locale.US).indexOf("BANDWIDTH=");
            if (at >= 0) {
                int cursor = at + 10;
                StringBuilder digits = new StringBuilder();
                while (cursor < line.length() && Character.isDigit(line.charAt(cursor))) {
                    digits.append(line.charAt(cursor));
                    cursor++;
                }
                try {
                    bandwidth = Long.parseLong(digits.toString());
                } catch (Exception ignored) {
                    bandwidth = 0L;
                }
            }
            String uri = null;
            for (int j = i + 1; j < lines.length; j++) {
                String candidate = lines[j].trim();
                if (candidate.length() == 0) {
                    continue;
                }
                if (!candidate.startsWith("#")) {
                    uri = absolute(base, candidate);
                }
                break;
            }
            if (uri == null) {
                continue;
            }
            if (bandwidth > bestBandwidth) {
                bestBandwidth = bandwidth;
                bestUri = uri;
            }
            if (bandwidth < lowBandwidth) {
                lowBandwidth = bandwidth;
                lowUri = uri;
            }
        }

        if (audioOnly) {
            if (audioRendition != null) {
                return audioRendition;
            }
            return lowUri != null ? lowUri : bestUri;
        }
        return bestUri != null ? bestUri : audioRendition;
    }

    private static String absolute(String base, String relative) {
        try {
            return new URL(new URL(base), relative).toString();
        } catch (Exception e) {
            return relative;
        }
    }

    private static String fetchText(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("User-Agent", Net.UA);
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) {
                throw new Exception("playlist request failed (" + code + ")");
            }
            InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1 << 15);
            byte[] buffer = new byte[1 << 14];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (out.size() > 4 * 1024 * 1024) {
                    break;
                }
            }
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static long copy(String url, OutputStream os, long remaining) throws Exception {
        HttpURLConnection conn = null;
        long written = 0L;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", Net.UA);
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) {
                throw new Exception("segment request failed (" + code + ")");
            }
            InputStream in = conn.getInputStream();
            byte[] buffer = new byte[1 << 15];
            int read;
            while ((read = in.read(buffer)) > 0) {
                if (written >= remaining) {
                    break;
                }
                os.write(buffer, 0, read);
                written += read;
            }
            in.close();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return written;
    }
}
