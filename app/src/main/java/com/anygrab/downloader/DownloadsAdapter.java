package com.anygrab.downloader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class DownloadsAdapter extends BaseAdapter {

    private final Context context;
    private final List<DownloadItem> data;

    public DownloadsAdapter(Context context, List<DownloadItem> data) {
        this.context = context;
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = LayoutInflater.from(context).inflate(R.layout.row_item, parent, false);
        }
        DownloadItem item = data.get(position);

        TextView ext = (TextView) row.findViewById(R.id.rowExt);
        TextView name = (TextView) row.findViewById(R.id.rowName);
        TextView meta = (TextView) row.findViewById(R.id.rowMeta);
        TextView action = (TextView) row.findViewById(R.id.rowAction);

        String extension = Net.extensionOf(item.name);
        ext.setText(extension == null ? "FILE" : extension.toUpperCase(Locale.US));
        name.setText(item.name);

        String state;
        int color;
        if (item.status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
            state = "completed";
            color = 0xFF22D3A6;
            action.setText("OPEN");
            action.setVisibility(View.VISIBLE);
        } else if (item.isActive()) {
            int p = item.percent();
            state = p >= 0 ? ("downloading " + p + "%") : "downloading";
            color = 0xFF6EA8FE;
            action.setText("…");
            action.setVisibility(View.VISIBLE);
        } else {
            state = "failed" + (item.reason.length() > 0 ? " · " + item.reason : "");
            color = 0xFFE06C75;
            action.setText("RETRY");
            action.setVisibility(View.VISIBLE);
        }
        meta.setText(Net.humanSize(item.total) + "  ·  " + state);
        meta.setTextColor(color);
        return row;
    }
}
