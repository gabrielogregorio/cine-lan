package app.medialan;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The library as it is on disk, with filters to find what has no cover or no data. */
public class FoldersActivity extends Activity {
    private static final int COLUMNS_FILES = 4;
    private static final int ALL = 0;
    private static final int WITHOUT_COVER = 1;
    private static final int WITHOUT_DATA = 2;

    private static final class File {
        final Media media;
        final boolean hasCover;
        final boolean hasData;

        File(Media media, boolean hasCover, boolean hasData) {
            this.media = media;
            this.hasCover = hasCover;
            this.hasData = hasData;
        }

        boolean passes(int filter) {
            return filter == ALL || (filter == WITHOUT_COVER ? !hasCover : !hasData);
        }
    }

    private static final class Folder {
        final String name;
        final List<File> files = new ArrayList<>();

        Folder(String name) {
            this.name = name;
        }

        int count(int filter) {
            int total = 0;
            for (File a : files) if (a.passes(filter)) total++;
            return total;
        }
    }

    private final List<Folder> folders = new ArrayList<>();
    private final List<Folder> visible = new ArrayList<>();
    private TextView title, filterAll, filterCover, filterData;
    private VerticalGridView grid;
    private final AdapterFolders adapterFolders = new AdapterFolders();
    private Folder open;
    private int filter = ALL;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_folders);
        title = findViewById(R.id.title);
        filterAll = findViewById(R.id.filter_all);
        filterCover = findViewById(R.id.filter_cover);
        filterData = findViewById(R.id.filter_data);
        grid = findViewById(R.id.grid);
        filterAll.setOnClickListener(v -> apply(ALL));
        filterCover.setOnClickListener(v -> apply(WITHOUT_COVER));
        filterData.setOnClickListener(v -> apply(WITHOUT_DATA));
        title.setText("Carregando pastas...");
        String url = Catalog.server(this) + "/api/folders";
        Catalog.inBackground(() -> new JSONObject(Catalog.download(url)), (d, error) -> {
            if (d == null) {
                title.setText("Servidor indisponível");
                return;
            }
            read(d);
            showRoot();
            filterAll.requestFocus();
        });
    }

    private void read(JSONObject d) {
        JSONArray list = d.optJSONArray("folders");
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            Folder folder = new Folder(o.optString("folder"));
            JSONArray items = o.optJSONArray("items");
            for (int j = 0; items != null && j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                try {
                    item.put("title", item.optString("file"));
                } catch (JSONException ignorada) {
                    // key and value are strings: this cannot fail
                }
                folder.files.add(new File(new Media(item), item.optBoolean("has_cover"), item.optBoolean("has_nfo")));
            }
            folders.add(folder);
        }
    }

    private void apply(int fresh) {
        filter = fresh;
        if (open != null) showFiles(open);
        else showRoot();
    }

    private void countFilters(List<Folder> source) {
        int all = 0, withoutCover = 0, withoutData = 0;
        for (Folder p : source) {
            all += p.count(ALL);
            withoutCover += p.count(WITHOUT_COVER);
            withoutData += p.count(WITHOUT_DATA);
        }
        filterAll.setText((filter == ALL ? "● " : "") + "Todos  " + all);
        filterCover.setText((filter == WITHOUT_COVER ? "● " : "") + "Sem capa  " + withoutCover);
        filterData.setText((filter == WITHOUT_DATA ? "● " : "") + "Sem dados  " + withoutData);
    }

    private void showRoot() {
        open = null;
        title.setText("Pastas do acervo");
        countFilters(folders);
        visible.clear();
        for (Folder p : folders) if (p.count(filter) > 0) visible.add(p);
        grid.setNumColumns(1);
        grid.setAdapter(adapterFolders);
        adapterFolders.notifyDataSetChanged();
    }

    private void showFiles(Folder folder) {
        open = folder;
        title.setText(folder.name);
        countFilters(List.of(folder));
        List<Media> filtered = new ArrayList<>();
        for (File a : folder.files) if (a.passes(filter)) filtered.add(a.media);
        grid.setNumColumns(COLUMNS_FILES);
        grid.setAdapter(new Cards.Episode(filtered, m -> play(filtered, m)));
    }

    private void play(List<Media> files, Media picked) {
        Catalog.queue = new ArrayList<>(files);
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_INDEX, files.indexOf(picked));
        startActivity(i);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && open != null) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                showRoot();
                grid.requestFocus();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private final class AdapterFolders extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int kind) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Folder p = visible.get(position);
            TextView row = (TextView) h.itemView;
            int total = p.count(filter);
            row.setText(p.name + "   ·   " + total + (total == 1 ? " arquivo" : " arquivos"));
            row.setOnClickListener(v -> {
                showFiles(p);
                grid.requestFocus();
            });
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }
    }
}
