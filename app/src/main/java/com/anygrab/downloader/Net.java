package com.anygrab.downloader;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small helper that inspects a URL and works out a sensible file name, MIME type and size. */
public final class Net {

    public static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 AnyGrab/1.0";

    public static class Info {
        public String fileName = "download";
        public String mime = "application/octet-stream";
        public long size = -1L;
    }

    private static final Map<String, String> EXT_MIME = new HashMap<String, String>();
    private static final Map<String, String> MIME_EXT = new HashMap<String, String>();

    static {
        add("jpg", "image/jpeg");
        add("jpeg", "image/jpeg");
        add("png", "image/png");
        add("webp", "image/webp");
        add("gif", "image/gif");
        add("bmp", "image/bmp");
        add("svg", "image/svg+xml");
        add("heic", "image/heic");
        add("pdf", "application/pdf");
        add("zip", "application/zip");
        add("rar", "application/x-rar-compressed");
        add("7z", "application/x-7z-compressed");
        add("apk", "application/vnd.android.package-archive");
        add("mp3", "audio/mpeg");
        add("m4a", "audio/mp4");
        add("wav", "audio/wav");
        add("ogg", "audio/ogg");
        add("mp4", "video/mp4");
        add("mkv", "video/x-matroska");
        add("webm", "video/webm");
        add("mov", "video/quicktime");
        add("doc", "application/msword");
        add("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        add("xls", "application/vnd.ms-excel");
        add("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        add("ppt", "application/vnd.ms-powerpoint");
        add("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        add("txt", "text/plain");
        add("csv", "text/csv");
        add("json", "application/json");
        add("xml", "application/xml");
        add("html", "text/html");
        add("epub", "application/epub+zip");
        add("ttf", "font/ttf");
        add("otf", "font/otf");
    }

    private static void add(String ext, String mime) {
        EXT_MIME.put(ext, mime);
        if (!MIME_EXT.containsKey(mime)) {
            MIME_EXT.put(mime, ext);
        }
    }

    private Net() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.length() == 0) {
            return null;
        }
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s;
        }
        if (s.startsWith("//")) {
            return "https:" + s;
        }
        if (s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return s;
        }
        return "https://" + s;
    }

    public static boolean looksLikeUrl(String s) {
        String n = normalize(s);
        return n != null && (n.startsWith("http://") || n.startsWith("https://"));
    }

    /** Does a lightweight ranged GET (so it also works on servers that reject HEAD) and reads the headers. */
    public static Info probe(String url) {
        Info info = new Info();
        String current = url;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Range", "bytes=0-0");

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null) {
                        break;
                    }
                    current = new URL(new URL(current), location).toString();
                    continue;
                }

                String disposition = conn.getHeaderField("Content-Disposition");
                String contentType = conn.getHeaderField("Content-Type");
                long size = -1L;
                String contentRange = conn.getHeaderField("Content-Range");
                if (contentRange != null && contentRange.indexOf('/') >= 0) {
                    try {
                        size = Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1).trim());
                    } catch (Exception ignored) {
                    }
                }
                if (size < 0) {
                    String contentLength = conn.getHeaderField("Content-Length");
                    if (contentLength != null) {
                        try {
                            size = Long.parseLong(contentLength.trim());
                        } catch (Exception ignored) {
                        }
                    }
                }
                conn.disconnect();

                info.mime = cleanMime(contentType);
                info.fileName = buildName(disposition, current, info.mime);
                info.size = size;
                return info;
            } catch (Exception e) {
                if (conn != null) {
                    conn.disconnect();
                }
                break;
            }
        }
        info.mime = guessMimeFromUrl(url);
        info.fileName = buildName(null, url, info.mime);
        return info;
    }

    private static String cleanMime(String raw) {
        if (raw == null || raw.length() == 0) {
            return "application/octet-stream";
        }
        String m = raw.trim().toLowerCase(Locale.US);
        int semi = m.indexOf(';');
        if (semi > 0) {
            m = m.substring(0, semi).trim();
        }
        if (m.length() == 0 || m.equals("binary/octet-stream")) {
            return "application/octet-stream";
        }
        return m;
    }

    public static String guessMimeFromUrl(String url) {
        String ext = extensionOf(url);
        String mime = ext == null ? null : EXT_MIME.get(ext);
        return mime == null ? "application/octet-stream" : mime;
    }

    public static String extensionOf(String url) {
        if (url == null) {
            return null;
        }
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int h = path.indexOf('#');
        if (h >= 0) {
            path = path.substring(0, h);
        }
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = last.lastIndexOf('.');
        if (dot <= 0 || dot == last.length() - 1) {
            return null;
        }
        String ext = last.substring(dot + 1).toLowerCase(Locale.US);
        if (ext.length() > 5 || !ext.matches("[a-z0-9]+")) {
            return null;
        }
        return ext;
    }

    private static String buildName(String disposition, String url, String mime) {
        String name = null;
        if (disposition != null) {
            String star = valueOf(disposition, "filename*=");
            if (star != null) {
                int idx = star.indexOf("''");
                name = idx >= 0 ? star.substring(idx + 2) : star;
                try {
                    name = java.net.URLDecoder.decode(name, "UTF-8");
                } catch (Exception ignored) {
                }
            }
            if (name == null) {
                name = valueOf(disposition, "filename=");
                if (name != null && name.length() > 1) {
                    char first = name.charAt(0);
                    if (first == '"' || first == '\'') {
                        name = name.substring(1);
                    }
                    if (name.length() > 0) {
                        char last = name.charAt(name.length() - 1);
                        if (last == '"' || last == '\'') {
                            name = name.substring(0, name.length() - 1);
                        }
                    }
                }
            }
        }
        if (name == null || name.trim().length() == 0) {
            String path = url;
            int q = path.indexOf('?');
            if (q >= 0) {
                path = path.substring(0, q);
            }
            int slash = path.lastIndexOf('/');
            String last = slash >= 0 ? path.substring(slash + 1) : path;
            try {
                last = java.net.URLDecoder.decode(last, "UTF-8");
            } catch (Exception ignored) {
            }
            if (last.length() > 0 && !last.contains(".")) {
                String ext = MIME_EXT.get(mime);
                if (ext != null) {
                    last = last + "." + ext;
                }
            }
            name = last;
        }
        name = sanitize(name);
        if (name.length() == 0) {
            String ext = MIME_EXT.get(mime);
            name = "anygrab-" + System.currentTimeMillis() + (ext == null ? "" : "." + ext);
        }
        if (!name.contains(".")) {
            String ext = MIME_EXT.get(mime);
            if (ext != null) {
                name = name + "." + ext;
            }
        }
        return name;
    }

    private static String valueOf(String header, String key) {
        int i = header.toLowerCase(Locale.US).indexOf(key);
        if (i < 0) {
            return null;
        }
        String rest = header.substring(i + key.length()).trim();
        int end = rest.indexOf(';');
        if (end >= 0) {
            rest = rest.substring(0, end);
        }
        rest = rest.trim();
        return rest.length() == 0 ? null : rest;
    }

    public static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.replace('\\', '_').replace('/', '_').replace(':', '_')
                .replace('*', '_').replace('?', '_').replace('"', '_')
                .replace('<', '_').replace('>', '_').replace('|', '_')
                .replace('\n', '_').replace('\r', '_').replace('\t', '_');
        s = s.trim();
        while (s.startsWith(".")) {
            s = s.substring(1);
        }
        if (s.length() > 110) {
            String ext = extensionOf(s);
            int keep = 110 - (ext == null ? 0 : ext.length() + 1);
            s = s.substring(0, Math.max(8, keep)) + (ext == null ? "" : "." + ext);
        }
        return s;
    }

    public static String humanSize(long bytes) {
        if (bytes < 0) {
            return "unknown size";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
