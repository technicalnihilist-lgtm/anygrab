import android.content.Context;
import com.anygrab.downloader.HlsDownloader;
import com.anygrab.downloader.PageGrab;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class TestAll {

    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("PASS  " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name + "  -> " + detail);
        }
    }

    static final Map<String, String> TEXT = new HashMap<String, String>();
    static final Map<String, byte[]> BIN = new HashMap<String, byte[]>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            public void handle(HttpExchange ex) throws java.io.IOException {
                String path = ex.getRequestURI().getPath();
                byte[] body = BIN.get(path);
                if (body == null) {
                    String text = TEXT.get(path);
                    body = text == null ? null : text.getBytes("UTF-8");
                }
                if (body == null) {
                    ex.sendResponseHeaders(404, -1);
                    ex.close();
                    return;
                }
                ex.getResponseHeaders().add("Content-Type", path.endsWith(".m3u8")
                        ? "application/vnd.apple.mpegurl" : "application/octet-stream");
                ex.sendResponseHeaders(200, body.length);
                OutputStream os = ex.getResponseBody();
                os.write(body);
                os.close();
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        // ---------- fixture data ----------
        TEXT.put("/master.m3u8",
                "#EXTM3U\n"
                        + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aac\",NAME=\"English\",URI=\"audio.m3u8\"\n"
                        + "#EXT-X-STREAM-INF:BANDWIDTH=400000,CODECS=\"avc1.42e00a,mp4a.40.2\"\n"
                        + "low.m3u8\n"
                        + "#EXT-X-STREAM-INF:BANDWIDTH=2400000,CODECS=\"avc1.640028,mp4a.40.2\"\n"
                        + "high.m3u8\n");
        TEXT.put("/low.m3u8", media("low-"));
        TEXT.put("/high.m3u8", media("high-"));
        TEXT.put("/audio.m3u8", media("audio-"));
        TEXT.put("/plain.m3u8", media("plain-"));
        TEXT.put("/drm.m3u8", "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.bin\"\n"
                + "#EXTINF:4,\nseg1.ts\n#EXT-X-ENDLIST\n");
        BIN.put("/seg1.ts", bytes(1000, 1));
        BIN.put("/seg2.ts", bytes(1500, 2));
        BIN.put("/seg3.ts", bytes(700, 3));

        // ---------- page fixtures ----------
        TEXT.put("/og.html", "<html><head>"
                + "<meta property=\"og:video:secure_url\" content=\"https://cdn.example.com/og-video.mp4\">"
                + "<meta name=\"twitter:player:stream\" content=\"https://cdn.example.com/twitter.mp4\">"
                + "</head><body>hi</body></html>");
        TEXT.put("/tag.html", "<html><body><video controls poster=\"p.jpg\">"
                + "<source src=\"/media/movie.webm\" type=\"video/webm\"></video></body></html>");
        TEXT.put("/hls.html", "<html><body><div id=\"player\" data-src=\"x\"></div>"
                + "<script>var u = \"https:\\/\\/cdn.example.com\\/live\\/index.m3u8?token=abc\";</script>"
                + "</body></html>");
        TEXT.put("/audio.html", "<html><body><audio src=\"https://cdn.example.com/ep.mp3\"></audio></body></html>");
        TEXT.put("/empty.html", "<html><body><p>nothing here</p></body></html>");
        TEXT.put("/jsonld.html", "<html><head><script type=\"application/ld+json\">"
                + "{\"@type\":\"VideoObject\",\"contentUrl\":\"https://cdn.example.com/ld.mp4\"}"
                + "</script></head></html>");

        Context ctx = new Context() {
            public File getCacheDir() {
                File dir = new File("/tmp/hlstest");
                dir.mkdirs();
                return dir;
            }
        };

        // ---------- HLS: master playlist, video ----------
        File v = HlsDownloader.download(ctx, base + "/master.m3u8", false);
        check("HLS master picks the highest bandwidth variant",
                same(v, concat(bytes(1000, 1), bytes(1500, 2), bytes(700, 3))), "bytes differ");

        // ---------- HLS: master playlist, audio ----------
        File a = HlsDownloader.download(ctx, base + "/master.m3u8", true);
        byte[] expectAudio = concat(bytes(1000, 1), bytes(1500, 2), bytes(700, 3));
        check("HLS master with audioOnly uses the EXT-X-MEDIA audio rendition",
                same(a, expectAudio), "bytes differ");

        // ---------- HLS: direct media playlist ----------
        File p = HlsDownloader.download(ctx, base + "/plain.m3u8", false);
        check("HLS direct media playlist concatenates every segment", same(p, expectAudio), "bytes differ");

        // ---------- HLS: encrypted ----------
        String drm = "";
        try {
            HlsDownloader.download(ctx, base + "/drm.m3u8", false);
        } catch (Exception e) {
            drm = e.getMessage() == null ? "error" : e.getMessage();
        }
        check("HLS encrypted playlist is refused with a clear message",
                drm.toLowerCase().contains("encrypted"), "got: " + drm);

        // ---------- page extraction ----------
        PageGrab.Found og = PageGrab.findMedia(base + "/og.html", false);
        check("page: og:video is found", og != null && og.url.equals("https://cdn.example.com/og-video.mp4"),
                String.valueOf(og == null ? null : og.url));

        PageGrab.Found tag = PageGrab.findMedia(base + "/tag.html", false);
        check("page: relative <source> src is resolved against the page",
                tag != null && tag.url.equals(base + "/media/movie.webm"),
                String.valueOf(tag == null ? null : tag.url));

        PageGrab.Found hls = PageGrab.findMedia(base + "/hls.html", false);
        check("page: escaped m3u8 inside a script is found",
                hls != null && hls.url.contains("index.m3u8") && hls.hls,
                String.valueOf(hls == null ? null : hls.url));

        PageGrab.Found mp3 = PageGrab.findMedia(base + "/audio.html", true);
        check("page: audio mode finds the mp3", mp3 != null && mp3.url.endsWith("ep.mp3"),
                String.valueOf(mp3 == null ? null : mp3.url));

        PageGrab.Found ld = PageGrab.findMedia(base + "/jsonld.html", false);
        check("page: JSON-LD contentUrl is found", ld != null && ld.url.endsWith("ld.mp4"),
                String.valueOf(ld == null ? null : ld.url));

        PageGrab.Found none = PageGrab.findMedia(base + "/empty.html", false);
        check("page: page without media returns null (no bogus download)", none == null,
                String.valueOf(none == null ? null : none.url));

        server.stop(0);
        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) {
            System.exit(1);
        }
    }

    static String media(String prefix) {
        return "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n"
                + "#EXTINF:4.0,\nseg1.ts\n#EXTINF:4.0,\nseg2.ts\n#EXTINF:4.0,\nseg3.ts\n#EXT-X-ENDLIST\n";
    }

    static byte[] bytes(int size, int seed) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i * 31 + seed * 7) & 0xFF);
        }
        return data;
    }

    static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    static boolean same(File file, byte[] expected) throws Exception {
        if (file == null || !file.exists()) {
            return false;
        }
        byte[] actual = new byte[(int) file.length()];
        FileInputStream in = new FileInputStream(file);
        int read = 0;
        while (read < actual.length) {
            int n = in.read(actual, read, actual.length - read);
            if (n < 0) {
                break;
            }
            read += n;
        }
        in.close();
        if (read != expected.length) {
            System.out.println("   size " + read + " vs expected " + expected.length);
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i]) {
                System.out.println("   first difference at byte " + i);
                return false;
            }
        }
        return true;
    }
}
