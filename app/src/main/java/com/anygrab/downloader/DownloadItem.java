package com.anygrab.downloader;

public class DownloadItem {
    public long id;
    public String name = "";
    public String mime = "application/octet-stream";
    public long total = -1L;
    public long done = 0L;
    public int status = 0;
    public String reason = "";

    public boolean isActive() {
        return status == android.app.DownloadManager.STATUS_PENDING
                || status == android.app.DownloadManager.STATUS_RUNNING
                || status == android.app.DownloadManager.STATUS_PAUSED;
    }

    public int percent() {
        if (total <= 0) {
            return -1;
        }
        return (int) Math.min(100, (done * 100L) / total);
    }
}
