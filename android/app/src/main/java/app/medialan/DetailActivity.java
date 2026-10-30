package app.medialan;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.leanback.widget.HorizontalGridView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DetailActivity extends Activity {
    static final String EXTRA_MEDIA = "media";
    static final String EXTRA_PLAY = "play";
    private static final int SIZE_SIMILAR = 16;
    private static final int HEIGHT_POSTERS_DP = 194;
    private static final int HEIGHT_EPISODES_DP = 170;

    private Media media;
    private UserStore user;
    private TextView watch, doStart, favorite, buggy, random, similar, cast, titleList;
    private HorizontalGridView seasons, list;
    private final List<List<Media>> episodesBySeason = new ArrayList<>();
    private final List<Integer> numbers = new ArrayList<>();
    private List<Media> allEpisodes = new ArrayList<>();
    private Cards.Episode adapterEpisodes;
    private int seasonCurrent;
    private boolean watchingSimilar;
    private final Random shuffle = new Random();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_detail);
        user = UserStore.of(this);
        try {
            media = new Media(new JSONObject(getIntent().getStringExtra(EXTRA_MEDIA)));
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        watch = findViewById(R.id.watch);
        doStart = findViewById(R.id.do_start);
        favorite = findViewById(R.id.favorite);
        buggy = findViewById(R.id.buggy);
        random = findViewById(R.id.random);
        similar = findViewById(R.id.similar);
        cast = findViewById(R.id.cast);
        titleList = findViewById(R.id.title_list);
        seasons = findViewById(R.id.seasons);
        list = findViewById(R.id.list);
        Cards.alignHorizontal(seasons, 0);
        Cards.alignHorizontal(list, 0);

        Media target = media;
        if (media.isEpisode() && Catalog.findShow(media.showId) != null) {
            target = Catalog.findShow(media.showId);
        }
        Images.background(findViewById(R.id.background), target.background());
        Images.logo(findViewById(R.id.logo), target.logo);
        TextView title = findViewById(R.id.title);
        title.setText(target.title);
        title.setVisibility(target.logo == null ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.meta)).setText(target.metadata());
        ((TextView) findViewById(R.id.overview)).setText(target.overview);

        favorite.setOnClickListener(v -> {
            user.toggleFavorite(showOrMovie());
            updateButtons();
        });
        buggy.setOnClickListener(v -> {
            user.toggleBuggy(showOrMovie());
            updateButtons();
        });
        if (media.isShow() || media.isEpisode()) {
            loadShow(media.isShow() ? media.id : media.showId);
        } else {
            loadMovie();
        }
        watch.requestFocus();
    }

    private Media showOrMovie() {
        Media show = media.isEpisode() ? Catalog.findShow(media.showId) : null;
        return show != null ? show : media;
    }

    private void loadMovie() {
        String url = Catalog.server(this) + "/api/items/" + media.id;
        Catalog.inBackground(() -> new JSONObject(Catalog.download(url)), (d, error) -> {
            if (d != null) showCast(d.optJSONObject("extra"));
        });
        showSimilar(media);
        watch.setOnClickListener(v -> play(Collections.singletonList(media), 0, true));
        doStart.setOnClickListener(v -> play(Collections.singletonList(media), 0, false));
        updateButtons();
        if (getIntent().getBooleanExtra(EXTRA_PLAY, false)) play(Collections.singletonList(media), 0, true);
    }

    private void showSimilar(Media target) {
        titleList.setText("Parecidos");
        titleList.setVisibility(View.VISIBLE);
        list.getLayoutParams().height = Cards.dp(list, HEIGHT_POSTERS_DP);
        list.setVisibility(View.VISIBLE);
        list.setAdapter(new Cards.Poster(Similar.neighbors(target, Catalog.library(), SIZE_SIMILAR), this::openOther, null));
        list.setSelectedPosition(0);
    }

    private void showEpisodes() {
        titleList.setVisibility(View.GONE);
        list.getLayoutParams().height = Cards.dp(list, HEIGHT_EPISODES_DP);
        list.setAdapter(adapterEpisodes);
        selectSeason(seasonDoIndex(nextIndex()));
    }

    private void toggleSimilar() {
        watchingSimilar = !watchingSimilar;
        similar.setText(watchingSimilar ? "Episódios" : "Parecidos");
        seasons.setVisibility(!watchingSimilar && episodesBySeason.size() > 1 ? View.VISIBLE : View.GONE);
        if (watchingSimilar) showSimilar(showOrMovie());
        else showEpisodes();
    }

    private void openOther(Media m) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(EXTRA_MEDIA, m.raw.toString());
        startActivity(i);
    }

    private void showCast(JSONObject extra) {
        if (extra == null) return;
        List<String> parts = new ArrayList<>();
        String directors = join(extra.optJSONArray("directors"));
        String actors = join(extra.optJSONArray("actors"));
        if (!directors.isEmpty()) parts.add("Direção: " + directors);
        if (!actors.isEmpty()) parts.add("Elenco: " + actors);
        cast.setText(String.join("   ", parts));
    }

    private static String join(JSONArray a) {
        List<String> names = new ArrayList<>();
        for (int i = 0; a != null && i < a.length() && i < 6; i++) {
            if (!a.optString(i).isEmpty()) names.add(a.optString(i));
        }
        return String.join(", ", names);
    }

    private void loadShow(String id) {
        watch.setText("Carregando...");
        String url = Catalog.server(this) + "/api/shows/" + id;
        Catalog.inBackground(() -> new JSONObject(Catalog.download(url)), (d, error) -> {
            if (d == null) {
                watch.setText("Servidor indisponível");
                return;
            }
            buildShow(new Media(d), d);
        });
    }

    private void buildShow(Media show, JSONObject d) {
        JSONArray list_ = d.optJSONArray("seasons");
        for (int i = 0; list_ != null && i < list_.length(); i++) {
            JSONObject t = list_.optJSONObject(i);
            JSONArray eps = t.optJSONArray("episodes");
            List<Media> episodes = new ArrayList<>();
            for (int j = 0; eps != null && j < eps.length(); j++) {
                Media e = new Media(eps.optJSONObject(j));
                e.inheritOf(show);
                episodes.add(e);
            }
            numbers.add(t.optInt("number"));
            episodesBySeason.add(episodes);
            if (t.optInt("number") != 0) allEpisodes.addAll(episodes);
        }
        if (allEpisodes.isEmpty() && !episodesBySeason.isEmpty()) allEpisodes = episodesBySeason.get(0);
        showCast(d.optJSONObject("extra"));

        adapterEpisodes = new Cards.Episode(new ArrayList<>(), e -> {
            int index = allEpisodes.indexOf(e);
            if (index >= 0) play(allEpisodes, index, true);
            else play(Collections.singletonList(e), 0, true);
        });
        list.setVisibility(View.VISIBLE);
        list.setAdapter(adapterEpisodes);
        if (episodesBySeason.size() > 1) {
            seasons.setVisibility(View.VISIBLE);
            seasons.setAdapter(new Tabs());
        }
        watch.setOnClickListener(v -> {
            int index = nextIndex();
            if (index >= 0) play(allEpisodes, index, true);
        });
        doStart.setOnClickListener(v -> play(allEpisodes, 0, false));
        random.setVisibility(View.VISIBLE);
        random.setOnClickListener(v -> play(allEpisodes, shuffle.nextInt(allEpisodes.size()), false));
        similar.setVisibility(View.VISIBLE);
        similar.setOnClickListener(v -> toggleSimilar());
        selectSeason(seasonDoIndex(nextIndex()));
        updateButtons();
        if (getIntent().getBooleanExtra(EXTRA_PLAY, false)) resumeEpisode();
    }

    private void resumeEpisode() {
        for (int i = 0; i < allEpisodes.size(); i++) {
            if (allEpisodes.get(i).id.equals(media.id)) {
                play(allEpisodes, i, true);
                return;
            }
        }
    }

    private int seasonDoIndex(int index) {
        if (index < 0) return 0;
        Media e = allEpisodes.get(index);
        int t = numbers.indexOf(e.season);
        return Math.max(t, 0);
    }

    private void selectSeason(int index) {
        if (index >= episodesBySeason.size()) return;
        seasonCurrent = index;
        List<Media> episodes = episodesBySeason.get(index);
        adapterEpisodes.swap(episodes);
        int playNext = nextIndex();
        int position = playNext < 0 ? -1 : episodes.indexOf(allEpisodes.get(playNext));
        list.setSelectedPosition(Math.max(position, 0));
    }

    /** Where the show stopped: the episode in progress, or the one after the last finished. */
    private int nextIndex() {
        if (allEpisodes.isEmpty()) return -1;
        String last = user.lastOfShow(allEpisodes.get(0).showId);
        for (int i = 0; last != null && i < allEpisodes.size(); i++) {
            if (allEpisodes.get(i).id.equals(last)) {
                UserStore.Progress p = user.progress(last);
                boolean ended = p != null && p.watched;
                return ended ? Math.min(i + 1, allEpisodes.size() - 1) : i;
            }
        }
        return 0;
    }

    private void updateButtons() {
        favorite.setText(user.favorite(showOrMovie().id) ? "♥  Favorito" : "♡  Favoritar");
        buggy.setText(user.buggy(showOrMovie().id) ? "✓  Bugado" : "Bugado");
        if (!allEpisodes.isEmpty()) {
            Media e = allEpisodes.get(Math.max(nextIndex(), 0));
            UserStore.Progress p = user.progress(e.id);
            boolean resume = p != null && p.resumable();
            watch.setText((resume ? "▶  Continuar  " : "▶  Assistir  ") + "T" + e.season
                    + (e.episode > 0 ? " E" + e.episode : ""));
            doStart.setVisibility(View.GONE);
            return;
        }
        if (media.isShow() || media.isEpisode()) return;
        UserStore.Progress p = user.progress(media.id);
        boolean resume = p != null && p.resumable();
        watch.setText(resume ? "▶  Continuar de " + Text.clock(p.positionMs) : "▶  Assistir");
        doStart.setVisibility(resume ? View.VISIBLE : View.GONE);
    }

    private void play(List<Media> queue, int index, boolean resume) {
        if (queue.isEmpty() || queue.get(index).url == null) {
            Toast.makeText(this, "Sem arquivo para tocar", Toast.LENGTH_SHORT).show();
            return;
        }
        Catalog.queue = new ArrayList<>(queue);
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_INDEX, index);
        i.putExtra(PlayerActivity.EXTRA_RESUME, resume);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (watch == null) return;
        if (adapterEpisodes != null) adapterEpisodes.notifyDataSetChanged();
        updateButtons();
    }

    private final class Tabs extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int kind) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            TextView tab = (TextView) h.itemView;
            int number = numbers.get(position);
            tab.setText(number == 0 ? "Extras" : "Temporada " + number);
            tab.setOnFocusChangeListener((v, focus) -> {
                if (focus && position != seasonCurrent) selectSeason(position);
            });
        }

        @Override
        public int getItemCount() {
            return numbers.size();
        }
    }
}
