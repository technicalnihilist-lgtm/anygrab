package com.anygrab.downloader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a normal web page, finds the media file it is hiding.
 * Looks at Open Graph tags, HTML5 media tags, JSON-LD and finally raw
 * media URLs embedded anywhere in the markup.
 */
public final class PageGrab {

    public static class Found {
        public String url;
        public boolean hls;
        public boolean audio;
    }

    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;

    private PageGrab() {
    }

    public static String fetch(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", Net.UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            conn.setRequestProperty("Accept-Encoding", "identity");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) {
                throw new Exception("server answered " + code);
            }
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
            byte[] buffer = new byte[1 << 15];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                total += read;
                if (total > MAX_PAGE_BYTES) {
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

    public static Found findMedia(String pageUrl, boolean audioOnly) {
        String html;
        try {
            html = fetch(pageUrl);
        } catch (Exception e) {
            return null;
        }
        if (html == null || html.length() == 0) {
            return null;
        }
        // JS / JSON payloads escape slashes ("https:\/\/cdn..."), so normalise first
        html = html.replace("\\/", "/").replace("\\u002F", "/").replace("\\u002f", "/");

        List<String> video = new ArrayList<String>();
        List<String> audio = new ArrayList<String>();

        collectMeta(html, "og:video:secure_url", video);
        collectMeta(html, "og:video:url", video);
        collectMeta(html, "og:video", video);
        collectMeta(html, "twitter:player:stream", video);
        collectMeta(html, "og:audio:secure_url", audio);
        collectMeta(html, "og:audio:url", audio);
        collectMeta(html, "og:audio", audio);

        collectTagSrc(html, "video", video);
        collectTagSrc(html, "audio", audio);
        collectSources(html, video, audio);

        Matcher jsonLd = Pattern.compile("\"contentUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(html);
        while (jsonLd.find()) {
            String found = unescape(jsonLd.group(1));
            if (isAudio(found)) {
                audio.add(found);
            } else {
                video.add(found);
            }
        }

        Matcher raw = Pattern.compile(
                "https?://[^\"'\\\\\\s<>()]+?\\.(m3u8|mp4|webm|mp3|m4a|mov|ogg|aac)(\\?[^\"'\\\\\\s<>()]*)?",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (raw.find()) {
            String found = unescape(raw.group());
            if (isAudio(found)) {
                audio.add(found);
            } else {
                video.add(found);
            }
        }

        List<String> videoList = resolveAll(video, pageUrl);
        List<String> audioList = resolveAll(audio, pageUrl);

        String pick;
        boolean pickedAudio;
        if (audioOnly) {
            pick = best(audioList, true);
            pickedAudio = true;
            if (pick == null) {
                pick = best(videoList, true);
                pickedAudio = true;
            }
        } else {
            pick = best(videoList, false);
            pickedAudio = false;
        }
        if (pick == null) {
            return null;
        }
        Found found = new Found();
        found.url = pick;
        found.hls = isHls(pick);
        found.audio = pickedAudio;
        return found;
    }

    /** Prefers a single-file download, then an HLS playlist. */
    private static String best(List<String> candidates, boolean audio) {
        String hls = null;
        String direct = null;
        for (int i = 0; i < candidates.size(); i++) {
            String url = candidates.get(i);
            if (isHls(url)) {
                if (hls == null) {
                    hls = url;
                }
            } else if (direct == null) {
                direct = url;
            }
        }
        if (audio) {
            return direct != null ? direct : hls;
        }
        return hls != null ? hls : direct;
    }

    private static List<String> resolveAll(List<String> raw, String pageUrl) {
        Set<String> unique = new LinkedHashSet<String>();
        for (int i = 0; i < raw.size(); i++) {
            String value = raw.get(i);
            if (value == null) {
                continue;
            }
            value = value.trim();
            if (value.length() == 0 || value.startsWith("data:") || value.startsWith("blob:")
                    || value.startsWith("javascript:")) {
                continue;
            }
            try {
                String absolute = new URL(new URL(pageUrl), value).toString();
                if (absolute.startsWith("http")) {
                    unique.add(absolute);
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<String>(unique);
    }

    private static boolean isAudio(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".mp3") || lower.contains(".m4a") || lower.contains(".aac")
                || lower.contains(".ogg") || lower.contains("audio");
    }

    private static boolean isHls(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("playlist.m3u");
    }

    private static void collectMeta(String html, String key, List<String> out) {
        Matcher tags = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            String property = attr(tag, "property");
            if (property == null) {
                property = attr(tag, "name");
            }
            if (property == null || !property.equalsIgnoreCase(key)) {
                continue;
            }
            String content = attr(tag, "content");
            if (content != null && content.length() > 0) {
                out.add(unescape(content));
            }
        }
    }

    private static void collectTagSrc(String html, String tagName, List<String> out) {
        Matcher tags = Pattern.compile("<" + tagName + "\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            String src = attr(tags.group(), "src");
            if (src != null && src.length() > 0) {
                out.add(unescape(src));
            }
        }
    }

    private static void collectSources(String html, List<String> video, List<String> audio) {
        Matcher tags = Pattern.compile("<source\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            String src = attr(tag, "src");
            if (src == null || src.length() == 0) {
                continue;
            }
            String type = attr(tag, "type");
            if (type != null && type.toLowerCase(Locale.US).startsWith("audio")) {
                audio.add(unescape(src));
            } else {
                video.add(unescape(src));
            }
        }
    }

    private static String attr(String tag, String name) {
        Matcher matcher = Pattern.compile(name + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!matcher.find()) {
            return null;
        }
        if (matcher.group(2) != null) {
            return matcher.group(2);
        }
        if (matcher.group(3) != null) {
            return matcher.group(3);
        }
        return matcher.group(4);
    }

    private static String unescape(String value) {
        return value.replace("&amp;", "&").replace("&#38;", "&")
                .replace("&quot;", "\"").replace("&#x2F;", "/").replace("&apos;", "'");
    }
}
