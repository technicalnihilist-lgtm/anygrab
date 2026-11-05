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
import java.util.Locale;

/**
 * Pulls audio or video out of a downloaded media file using the platform
 * MediaExtractor / MediaMuxer pair. Nothing is re-encoded: the compressed
 * samples are copied into a fresh container, so it is fast and lossless.
 */
public final class MediaConvert {

    public static class Result {
        public File file;
        public String displayName;
        public String mime;
        public String note;
    }

    private MediaConvert() {
    }

    private static final int MP4 = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
    private static final int WEBM = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;

    public static Result extractAudio(Context ctx, long downloadId) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(ctx, uriOf(ctx, downloadId), null);
            int audio = -1;
            int video = -1;
            MediaFormat audioFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    continue;
                }
                if (mime.startsWith("audio/") && audio < 0) {
                    audio = i;
                    audioFormat = format;
                } else if (mime.startsWith("video/") && video < 0) {
                    video = i;
                }
            }
            if (audio < 0 || audioFormat == null) {
                throw new Exception("No audio track in this file");
            }
            if (video < 0) {
                throw new Exception("This file is already audio only");
            }

            String mime = audioFormat.getString(MediaFormat.KEY_MIME);
            boolean webm = mime != null && (mime.contains("opus") || mime.contains("vorbis"));
            int container = webm ? WEBM : MP4;
            String extension = webm ? "webm" : "m4a";

            String title = titleOf(ctx, downloadId);
            String base = stripExtension(title);
            File out = new File(ctx.getCacheDir(), Net.sanitize(base) + "." + extension);

            Result result = new Result();
            result.file = out;
            result.displayName = out.getName();
            result.mime = webm ? "audio/webm" : "audio/mp4";
            result.note = "Audio track extracted (" + extension + ")";

            muxSingleTrack(extractor, audio, container, out.getAbsolutePath());
            return result;
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
    }

    public static Result remuxVideo(Context ctx, long downloadId) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(ctx, uriOf(ctx, downloadId), null);
            int video = -1;
            int audio = -1;
            String videoMime = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    continue;
                }
                if (mime.startsWith("video/") && video < 0) {
                    video = i;
                    videoMime = mime;
                } else if (mime.startsWith("audio/") && audio < 0) {
                    audio = i;
                }
            }
            if (video < 0) {
                throw new Exception("No video track in this file");
            }
            boolean webm = videoMime != null
                    && (videoMime.contains("vp8") || videoMime.contains("vp9") || videoMime.contains("av01"));
            int container = webm ? WEBM : MP4;
            String extension = webm ? "webm" : "mp4";

            String title = titleOf(ctx, downloadId);
            String base = stripExtension(title);
            File out = new File(ctx.getCacheDir(), Net.sanitize(base) + "." + extension);

            Result result = new Result();
            result.file = out;
            result.displayName = out.getName();
            result.mime = webm ? "video/webm" : "video/mp4";
            result.note = "Video remuxed to " + extension + " (no quality loss)";

            muxTracks(extractor, video, audio, container, out.getAbsolutePath());
            return result;
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static void muxSingleTrack(MediaExtractor extractor, int track, int container, String path)
            throws Exception {
        MediaMuxer muxer = new MediaMuxer(path, container);
        boolean started = false;
        try {
            MediaFormat format = extractor.getTrackFormat(track);
            extractor.selectTrack(track);
            int outTrack = muxer.addTrack(format);
            muxer.start();
            started = true;

            ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20);
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

    private static void muxTracks(MediaExtractor extractor, int videoTrack, int audioTrack,
                                  int container, String path) throws Exception {
        MediaMuxer muxer = new MediaMuxer(path, container);
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
            muxer.start();
            started = true;

            ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            extractor.selectTrack(videoTrack);
            long videoTime = extractor.getSampleTime();
            boolean videoDone = videoTime < 0;

            long audioTime = Long.MAX_VALUE;
            boolean audioDone = outAudio < 0;
            if (!audioDone) {
                extractor.selectTrack(audioTrack);
                audioTime = extractor.getSampleTime();
                audioDone = audioTime < 0;
            }

            while (!videoDone || !audioDone) {
                boolean takeVideo = !videoDone && (audioDone || videoTime <= audioTime);
                if (takeVideo) {
                    extractor.selectTrack(videoTrack);
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        videoDone = true;
                        continue;
                    }
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = extractor.getSampleTime();
                    info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    muxer.writeSampleData(outVideo, buffer, info);
                    if (!extractor.advance()) {
                        videoDone = true;
                    } else {
                        videoTime = extractor.getSampleTime();
                        videoDone = videoTime < 0;
                    }
                } else {
                    extractor.selectTrack(audioTrack);
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        audioDone = true;
                        continue;
                    }
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = extractor.getSampleTime();
                    info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    muxer.writeSampleData(outAudio, buffer, info);
                    if (!extractor.advance()) {
                        audioDone = true;
                    } else {
                        audioTime = extractor.getSampleTime();
                        audioDone = audioTime < 0;
                    }
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

    static Uri uriOf(Context ctx, long downloadId) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = dm.getUriForDownloadedFile(downloadId);
        if (uri == null) {
            throw new IllegalStateException("File is not available");
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

    static String mimeFor(String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.US);
        if (ext.equals("m4a")) {
            return "audio/mp4";
        }
        if (ext.equals("mp3")) {
            return "audio/mpeg";
        }
        if (ext.equals("webm")) {
            return "video/webm";
        }
        if (ext.equals("mp4")) {
            return "video/mp4";
        }
        if (ext.equals("html")) {
            return "text/html";
        }
        return "application/octet-stream";
    }
}
