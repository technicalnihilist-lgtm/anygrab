package com.anygrab.downloader;

import android.app.DownloadManager;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Pulls the audio or the video out of a media file using the platform
 * MediaExtractor / MediaMuxer pair. Samples are copied, never re-encoded.
 *
 * The extractor is configured exactly once: calling selectTrack() again while
 * reading resets that track to its first sample, which is what broke 1.1.0.
 * With several tracks selected we rely on getSampleTrackIndex() instead.
 */
public final class MediaConvert {

    public static class Result {
        /** Null when the file already was what the user asked for. */
        public File file;
        public String displayName;
        public String mime;
        public String note;
    }

    private static final int MP4 = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    private static final int WEBM = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;

    private MediaConvert() {
    }

    public static Result audioFromDownload(Context ctx, long id) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(ctx, uriOf(ctx, id), null);
            return muxAudio(ctx, extractor, titleOf(ctx, id));
        } finally {
            release(extractor);
        }
    }

    public static Result videoFromDownload(Context ctx, long id) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(ctx, uriOf(ctx, id), null);
            return muxVideo(ctx, extractor, titleOf(ctx, id));
        } finally {
            release(extractor);
        }
    }

    public static Result audioFromFile(Context ctx, File source, String title) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(source.getAbsolutePath());
            return muxAudio(ctx, extractor, title);
        } finally {
            release(extractor);
        }
    }

    public static Result videoFromFile(Context ctx, File source, String title) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(source.getAbsolutePath());
            return muxVideo(ctx, extractor, title);
        } finally {
            release(extractor);
        }
    }

    private static Result muxAudio(Context ctx, MediaExtractor extractor, String title) throws Exception {
        int audioTrack = -1;
        MediaFormat audioFormat = null;
        boolean hasVideo = false;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                continue;
            }
            if (mime.startsWith("audio/") && audioTrack < 0) {
                audioTrack = i;
                audioFormat = format;
            } else if (mime.startsWith("video/")) {
                hasVideo = true;
            }
        }
        if (audioTrack < 0 || audioFormat == null) {
            throw new Exception("this file has no audio track");
        }

        Result result = new Result();
        if (!hasVideo) {
            result.note = "That file is already audio only, kept as it is";
            return result;
        }

        String mime = audioFormat.getString(MediaFormat.KEY_MIME);
        boolean webm = mime != null && (mime.contains("opus") || mime.contains("vorbis"));
        String extension = webm ? "webm" : "m4a";
        File out = new File(ctx.getCacheDir(),
                Net.sanitize(MediaConvert.stripExtension(title)) + "." + extension);
        writeSingleTrack(extractor, audioTrack, webm ? WEBM : MP4, out);

        result.file = out;
        result.displayName = out.getName();
        result.mime = webm ? "audio/webm" : "audio/mp4";
        result.note = "Audio saved as " + extension;
        return result;
    }

    private static Result muxVideo(Context ctx, MediaExtractor extractor, String title) throws Exception {
        int videoTrack = -1;
        int audioTrack = -1;
        String videoMime = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                continue;
            }
            if (mime.startsWith("video/") && videoTrack < 0) {
                videoTrack = i;
                videoMime = mime;
            } else if (mime.startsWith("audio/") && audioTrack < 0) {
                audioTrack = i;
            }
        }
        if (videoTrack < 0) {
            throw new Exception("this file has no video track");
        }

        boolean webm = videoMime != null
                && (videoMime.contains("vp8") || videoMime.contains("vp9") || videoMime.contains("av01"));
        String extension = webm ? "webm" : "mp4";
        File out = new File(ctx.getCacheDir(),
                Net.sanitize(MediaConvert.stripExtension(title)) + "." + extension);
        writeTracks(extractor, videoTrack, audioTrack, webm ? WEBM : MP4, out);

        Result result = new Result();
        result.file = out;
        result.displayName = out.getName();
        result.mime = webm ? "video/webm" : "video/mp4";
        result.note = "Video saved as " + extension + " (no quality loss)";
        return result;
    }

    private static void writeSingleTrack(MediaExtractor extractor, int track, int container, File out)
            throws Exception {
        MediaMuxer muxer = new MediaMuxer(out.getAbsolutePath(), container);
        boolean started = false;
        try {
            MediaFormat format = extractor.getTrackFormat(track);
            extractor.selectTrack(track);
            int outTrack = muxer.addTrack(format);
            muxer.start();
            started = true;

            ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) {
                    break;
                }
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                muxer.writeSampleData(outTrack, buffer, info);
                if (!extractor.advance()) {
                    break;
                }
            }
        } finally {
            if (started) {
                try {
                    muxer.stop();
                } catch (Exception ignored) {
                }
            }
            try {
                muxer.release();
            } catch (Exception ignored) {
            }
        }
    }

    /** Both tracks are selected once up front; samples are routed by getSampleTrackIndex(). */
    private static void writeTracks(MediaExtractor extractor, int videoTrack, int audioTrack,
                                    int container, File out) throws Exception {
        MediaMuxer muxer = new MediaMuxer(out.getAbsolutePath(), container);
        boolean started = false;
        try {
            int outVideo = muxer.addTrack(extractor.getTrackFormat(videoTrack));
            int outAudio = -1;
            if (audioTrack >= 0) {
                try {
                    outAudio = muxer.addTrack(extractor.getTrackFormat(audioTrack));
                } catch (Exception e) {
                    outAudio = -1;
                }
            }

            extractor.selectTrack(videoTrack);
            if (outAudio >= 0) {
                extractor.selectTrack(audioTrack);
            }
            muxer.start();
            started = true;

            ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int track = extractor.getSampleTrackIndex();
                if (track < 0) {
                    break;
                }
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) {
                    break;
                }
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                int target = (track == audioTrack && outAudio >= 0) ? outAudio : outVideo;
                muxer.writeSampleData(target, buffer, info);
                if (!extractor.advance()) {
                    break;
                }
            }
        } finally {
            if (started) {
                try {
                    muxer.stop();
                } catch (Exception ignored) {
                }
            }
            try {
                muxer.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static void release(MediaExtractor extractor) {
        try {
            extractor.release();
        } catch (Exception ignored) {
        }
    }

    static Uri uriOf(Context ctx, long downloadId) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = dm.getUriForDownloadedFile(downloadId);
        if (uri == null) {
            throw new IllegalStateException("the file is no longer available");
        }
        return uri;
    }

    static String titleOf(Context ctx, long downloadId) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        android.database.Cursor cursor = null;
        try {
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
        return "anygrab-" + downloadId;
    }

    static String stripExtension(String name) {
        if (name == null || name.length() == 0) {
            return "anygrab";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }
}
