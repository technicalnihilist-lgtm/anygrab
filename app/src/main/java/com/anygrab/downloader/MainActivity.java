package com.anygrab.downloader;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String REPO_URL = "https://github.com/technicalnihilist-lgtm/anygrab";
    private static final String PREFS = "anygrab";
    private static final int REQ_STORAGE = 41;
    private static final String HOME = "https://www.google.com";

    private DownloadManager downloadManager;
    private SharedPreferences prefs;

    private EditText etUrl;
    private EditText etBrowse;
    private ListView list;
    private TextView emptyView;
    private TextView tabGrab;
    private TextView tabBrowse;
    private TextView btnDownload;
    private View pageGrab;
    private View pageBrowse;
    private ProgressBar progress;
    private WebView web;
    private DownloadsAdapter adapter;

    private final List<DownloadItem> items = new ArrayList<DownloadItem>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String format = "auto";
    private boolean browsing = false;
    private boolean busy = false;
    private boolean ticking = false;
    private String queuedUrl = null;

    private final BroadcastReceiver completeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id > 0L) {
                onDownloadFinished(id);
            }
            refresh();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        etUrl = (EditText) findViewById(R.id.etUrl);
        etBrowse = (EditText) findViewById(R.id.etBrowse);
        list = (ListView) findViewById(R.id.listDownloads);
        emptyView = (TextView) findViewById(R.id.emptyView);
        tabGrab = (TextView) findViewById(R.id.tabGrab);
        tabBrowse = (TextView) findViewById(R.id.tabBrowse);
        btnDownload = (TextView) findViewById(R.id.btnDownload);
        pageGrab = findViewById(R.id.pageGrab);
        pageBrowse = findViewById(R.id.pageBrowse);
        progress = (ProgressBar) findViewById(R.id.progress);
        web = (WebView) findViewById(R.id.web);

        adapter = new DownloadsAdapter(this, items);
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);

        tabGrab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(false);
            }
        });
        tabBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(true);
            }
        });

        findViewById(R.id.btnRepo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openExternal(Uri.parse(REPO_URL));
            }
        });

        findViewById(R.id.btnPaste).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteClipboard();
            }
        });
        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etUrl.setText("");
            }
        });
        findViewById(R.id.btnClearAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearFinished();
            }
        });

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFromField(etUrl.getText().toString());
            }
        });

        wireChip(R.id.chipAuto, "auto");
        wireChip(R.id.chipJpg, "jpg");
        wireChip(R.id.chipPng, "png");
        wireChip(R.id.chipWebp, "webp");

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DownloadItem item = items.get(position);
                if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                    openDownload(item);
                } else if (!item.isActive()) {
                    etUrl.setText(REPO_URL);
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                shareDownload(items.get(position));
                return true;
            }
        });

        setupWebView();
        applyFormat("auto");
        handleIntent(getIntent());
    }

    private void wireChip(int id, final String value) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFormat(value);
            }
        });
    }

    private void applyFormat(String value) {
        format = value;
        int[] ids = {R.id.chipAuto, R.id.chipJpg, R.id.chipPng, R.id.chipWebp};
        String[] values = {"auto", "jpg", "png", "webp"};
        for (int i = 0; i < ids.length; i++) {
            TextView chip = (TextView) findViewById(ids[i]);
            boolean active = values[i].equals(value);
            chip.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
            chip.setTextColor(active ? 0xFF07131F : 0xFF9FB3C8);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String shared = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            shared = intent.getData().toString();
        }
        if (shared != null) {
            String url = firstUrl(shared);
            if (url != null) {
                showTab(false);
                etUrl.setText(url);
                toast("Link received. Tap Download now.");
            }
        }
    }

    private String firstUrl(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (Net.looksLikeUrl(parts[i])) {
                return parts[i];
            }
        }
        return Net.looksLikeUrl(text) ? text.trim() : null;
    }

    private void showTab(boolean browseTab) {
        browsing = browseTab;
        pageGrab.setVisibility(browseTab ? View.GONE : View.VISIBLE);
        pageBrowse.setVisibility(browseTab ? View.VISIBLE : View.GONE);
        tabGrab.setBackgroundResource(browseTab ? android.R.color.transparent : R.drawable.bg_tab_active);
        tabBrowse.setBackgroundResource(browseTab ? R.drawable.bg_tab_active : android.R.color.transparent);
        tabGrab.setTextColor(browseTab ? 0xFF9FB3C8 : 0xFF07131F);
        tabBrowse.setTextColor(browseTab ? 0xFF07131F : 0xFF9FB3C8);
        if (browseTab && web.getUrl() == null) {
            web.loadUrl(HOME);
        }
    }

    private void pasteClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            toast("Clipboard is empty");
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            toast("Clipboard is empty");
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            toast("Clipboard is empty");
            return;
        }
        String url = firstUrl(text.toString());
        etUrl.setText(url != null ? url : text.toString().trim());
        etUrl.setSelection(etUrl.getText().length());
    }

    private void setupWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setUserAgentString(Net.UA);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progress.setVisibility(View.GONE);
                } else {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(newProgress);
                }
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                startFromField(url);
            }
        });

        findViewById(R.id.btnGo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFromBrowseField();
            }
        });
        etBrowse.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    loadFromBrowseField();
                    return true;
                }
                return false;
            }
        });
    }

    private void loadFromBrowseField() {
        String raw = etBrowse.getText().toString().trim();
        if (raw.length() == 0) {
            return;
        }
        String url = Net.normalize(raw);
        if (url == null) {
            return;
        }
        hideKeyboard();
        web.loadUrl(url);
    }

    private void startFromField(String raw) {
        if (busy) {
            return;
        }
        final String url = Net.normalize(raw);
        if (url == null) {
            toast("Paste a link first");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Only http and https links can be downloaded");
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            queuedUrl = url;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        hideKeyboard();
        busy = true;
        btnDownload.setText("Checking link…");
        etUrl.setText(url);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Net.Info info = Net.probe(url);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        btnDownload.setText(R.string.download_now);
                        enqueue(url, info);
                    }
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_STORAGE) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && queuedUrl != null) {
                String url = queuedUrl;
                queuedUrl = null;
                startFromField(url);
            } else {
                toast("Storage permission is needed to save files");
            }
        }
    }

    private void enqueue(String url, Net.Info info) {
        try {
            String name = Net.sanitize(info.fileName);
            if (name.length() == 0) {
                name = "anygrab-file";
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(name);
            request.setDescription("AnyGrab");
            request.setMimeType(info.mime);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AnyGrab/" + uniqueName(name));
            long id = downloadManager.enqueue(request);
            if (!"auto".equals(format)) {
                prefs.edit().putString("format_" + id, format).apply();
            }
            toast("Downloading " + name);
            refresh();
        } catch (Exception e) {
            toast("Could not start the download: " + e.getMessage());
        }
    }

    private String uniqueName(String name) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AnyGrab");
            File candidate = new File(dir, name);
            if (!candidate.exists()) {
                return name;
            }
            String ext = Net.extensionOf(name);
            String base = name;
            if (ext != null) {
                base = name.substring(0, name.length() - ext.length() - 1);
            }
            for (int i = 1; i < 500; i++) {
                String next = base + " (" + i + ")" + (ext == null ? "" : "." + ext);
                if (!new File(dir, next).exists()) {
                    return next;
                }
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private void onDownloadFinished(final long id) {
        final String wanted = prefs.getString("format_" + id, "auto");
        prefs.edit().remove("format_" + id).apply();
        if ("auto".equals(wanted)) {
            return;
        }
        if (!isSuccessful(id)) {
            return;
        }
        toast("Converting to " + wanted.toUpperCase() + "…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    File converted = ImageConvert.convert(MainActivity.this, id, wanted);
                    String display = converted.getName();
                    String mime = wanted.equals("png") ? "image/png"
                            : (wanted.equals("webp") ? "image/webp" : "image/jpeg");
                    ImageConvert.publish(MainActivity.this, converted, display, mime);
                    try {
                        downloadManager.remove(id);
                    } catch (Exception ignored) {
                    }
                    message = "Saved " + display + " to Downloads/AnyGrab";
                } catch (Exception e) {
                    message = "Could not convert this file (" + e.getMessage() + "). The original was kept.";
                }
                final String finalMessage = message;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        toast(finalMessage);
                        refresh();
                    }
                });
            }
        }).start();
    }

    private boolean isSuccessful(long id) {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                return index >= 0 && cursor.getInt(index) == DownloadManager.STATUS_SUCCESSFUL;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    private void refresh() {
        items.clear();
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterByStatus(DownloadManager.STATUS_PENDING
                    | DownloadManager.STATUS_RUNNING
                    | DownloadManager.STATUS_PAUSED
                    | DownloadManager.STATUS_SUCCESSFUL
                    | DownloadManager.STATUS_FAILED);
            cursor = downloadManager.query(query);
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int mimeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                int totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int doneIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                while (cursor.moveToNext()) {
                    DownloadItem item = new DownloadItem();
                    item.id = idIndex >= 0 ? cursor.getLong(idIndex) : 0L;
                    item.name = titleIndex >= 0 ? cursor.getString(titleIndex) : "file";
                    if (item.name == null || item.name.length() == 0) {
                        item.name = "file";
                    }
                    item.mime = mimeIndex >= 0 ? cursor.getString(mimeIndex) : "application/octet-stream";
                    if (item.mime == null) {
                        item.mime = "application/octet-stream";
                    }
                    item.total = totalIndex >= 0 ? cursor.getLong(totalIndex) : -1L;
                    item.done = doneIndex >= 0 ? cursor.getLong(doneIndex) : 0L;
                    item.status = statusIndex >= 0 ? cursor.getInt(statusIndex) : 0;
                    item.reason = reasonIndex >= 0 ? reasonText(cursor.getInt(reasonIndex)) : "";
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        adapter.notifyDataSetChanged();

        boolean active = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isActive()) {
                active = true;
                break;
            }
        }
        if (active && !ticking) {
            ticking = true;
            ui.postDelayed(ticker, 900L);
        }
    }

    private String reasonText(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "no space";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "file exists";
            case DownloadManager.ERROR_FILE_ERROR:
                return "file error";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "network data error";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "server refused the download";
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "cannot resume";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "too many redirects";
            case DownloadManager.ERROR_UNKNOWN:
                return "unknown error";
            default:
                return "";
        }
    }

    private void openDownload(DownloadItem item) {
        Uri uri = downloadManager.getUriForDownloadedFile(item.id);
        if (uri == null) {
            toast("File is no longer available");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, item.mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("No app can open this file");
        }
    }

    private void shareDownload(DownloadItem item) {
        Uri uri = downloadManager.getUriForDownloadedFile(item.id);
        if (uri == null) {
            toast("File is no longer available");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(item.mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Share " + item.name));
        } catch (Exception e) {
            toast("Nothing to share with");
        }
    }

    private void clearFinished() {
        for (int i = 0; i < items.size(); i++) {
            DownloadItem item = items.get(i);
            if (item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED) {
                try {
                    downloadManager.remove(item.id);
                } catch (Exception ignored) {
                }
            }
        }
        toast("Finished downloads removed from the list");
        refresh();
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("No browser found");
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (browsing) {
            if (web.canGoBack()) {
                web.goBack();
                return;
            }
            showTab(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(completeReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(completeReceiver, filter);
        }
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(completeReceiver);
        } catch (Exception ignored) {
        }
    }
}
