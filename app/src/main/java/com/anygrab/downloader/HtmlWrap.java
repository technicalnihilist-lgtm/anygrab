package com.anygrab.downloader;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

/** Turns a downloaded file into a small, self-contained HTML viewer page. */
public final class HtmlWrap {

    private HtmlWrap() {
    }

    public static File wrap(Context ctx, String fileName, String mime) throws Exception {
        String safeName = escapeHtml(fileName);
        String body;
        if (mime != null && mime.startsWith("image/")) {
            body = "<img src=\"" + escapeAttr(fileName) + "\" alt=\"" + safeName + "\">";
        } else if (mime != null && mime.startsWith("video/")) {
            body = "<video controls playsinline src=\"" + escapeAttr(fileName) + "\"></video>";
        } else if (mime != null && mime.startsWith("audio/")) {
            body = "<audio controls src=\"" + escapeAttr(fileName) + "\"></audio>";
        } else if (mime != null && mime.contains("pdf")) {
            body = "<iframe src=\"" + escapeAttr(fileName) + "\"></iframe>";
        } else {
            body = "<a class=\"dl\" href=\"" + escapeAttr(fileName) + "\">Download " + safeName + "</a>";
        }
        String html = "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
                + "<title>" + safeName + "</title>\n<style>\n"
                + ":root{color-scheme:dark}\n"
                + "body{margin:0;min-height:100vh;background:#0B1220;color:#fff;"
                + "font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;"
                + "display:flex;flex-direction:column;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}\n"
                + "h1{font-size:15px;font-weight:600;color:#22D3A6;margin:0 0 6px;text-align:center;word-break:break-all}\n"
                + "p.meta{font-size:11px;color:#7E93AD;margin:0 0 18px}\n"
                + "img,video{max-width:100%;max-height:78vh;border-radius:14px;box-shadow:0 12px 40px rgba(0,0,0,.5)}\n"
                + "audio{width:min(420px,100%)}\n"
                + "iframe{width:100%;height:78vh;border:1px solid #22304A;border-radius:14px;background:#fff}\n"
                + "a.dl{display:inline-block;background:#22D3A6;color:#07131F;text-decoration:none;"
                + "font-weight:700;font-size:13px;padding:14px 22px;border-radius:14px}\n"
                + "footer{margin-top:20px;font-size:10px;color:#4E6076;letter-spacing:.5px}\n"
                + "</style>\n</head>\n<body>\n"
                + "<h1>" + safeName + "</h1>\n"
                + "<p class=\"meta\">" + escapeHtml(mime == null ? "file" : mime) + " &middot; saved by AnyGrab</p>\n"
                + body + "\n<footer>ANYGRAB</footer>\n</body>\n</html>\n";

        String base = MediaConvert.stripExtension(fileName);
        File out = new File(ctx.getCacheDir(), Net.sanitize(base) + ".html");
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(html.getBytes("UTF-8"));
        fos.flush();
        fos.close();
        return out;
    }

    public static File savePage(Context ctx, String title, String html) throws Exception {
        String name = Net.sanitize(title == null || title.trim().length() == 0 ? "page" : title.trim());
        if (!name.toLowerCase().endsWith(".html")) {
            name = name + ".html";
        }
        File out = new File(ctx.getCacheDir(), name);
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(html.getBytes("UTF-8"));
        fos.flush();
        fos.close();
        return out;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeAttr(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
