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
        } catch (JSONException | NullPointerException error) {
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

        favorite.setOnClickListener(view -> {
            user.toggleFavorite(showOrMovie());
            updateButtons();
        });
        buggy.setOnClickListener(view -> {
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
        Catalog.inBackground(() -> new JSONObject(Catalog.download(url)), (data, error) -> {
            if (data != null) {
                showCast(data.optJSONObject("extra"));
            }
        });
        showSimilar(media);
        watch.setOnClickListener(view -> play(Collections.singletonList(media), 0, true));
        doStart.setOnClickListener(view -> play(Collections.singletonList(media), 0, false));
        updateButtons();
        if (getIntent().getBooleanExtra(EXTRA_PLAY, false)) {
            play(Collections.singletonList(media), 0, true);
        }
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
        if (watchingSimilar) {
            showSimilar(showOrMovie());
        } else {
            showEpisodes();
        }
    }

    private void openOther(Media mediaItem) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(EXTRA_MEDIA, mediaItem.raw.toString());
        startActivity(intent);
    }

    private void showCast(JSONObject extra) {
        if (extra == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        String directors = join(extra.optJSONArray("directors"));
        String actors = join(extra.optJSONArray("actors"));
        if (!directors.isEmpty()) {
            parts.add("Direção: " + directors);
        }
        if (!actors.isEmpty()) {
            parts.add("Elenco: " + actors);
        }
        cast.setText(String.join("   ", parts));
    }

    private static String join(JSONArray array) {
        List<String> names = new ArrayList<>();
        for (int position = 0; array != null && position < array.length() && position < 6; position += 1) {
            if (!array.optString(position).isEmpty()) {
                names.add(array.optString(position));
            }
        }

        return String.join(", ", names);
    }

    private void loadShow(String id) {
        watch.setText("Carregando...");
        String url = Catalog.server(this) + "/api/shows/" + id;
        Catalog.inBackground(() -> new JSONObject(Catalog.download(url)), (data, error) -> {
            if (data == null) {
                watch.setText("Servidor indisponível");

                return;
            }
            buildShow(new Media(data), data);
        });
    }

    private void buildShow(Media show, JSONObject data) {
        JSONArray list_ = data.optJSONArray("seasons");
        for (int position = 0; list_ != null && position < list_.length(); position += 1) {
            JSONObject seasonJson = list_.optJSONObject(position);
            JSONArray eps = seasonJson.optJSONArray("episodes");
            List<Media> episodes = new ArrayList<>();
            for (int nestedPosition = 0; eps != null && nestedPosition < eps.length(); nestedPosition += 1) {
                Media episode = new Media(eps.optJSONObject(nestedPosition));
                episode.inheritOf(show);
                episodes.add(episode);
            }
            numbers.add(seasonJson.optInt("number"));
            episodesBySeason.add(episodes);
            if (seasonJson.optInt("number") != 0) {
                allEpisodes.addAll(episodes);
            }
        }
        if (allEpisodes.isEmpty() && !episodesBySeason.isEmpty()) {
            allEpisodes = episodesBySeason.get(0);
        }
        showCast(data.optJSONObject("extra"));

        adapterEpisodes = new Cards.Episode(new ArrayList<>(), episode -> {
            int index = allEpisodes.indexOf(episode);
            if (index >= 0) {
                play(allEpisodes, index, true);
            } else {
                play(Collections.singletonList(episode), 0, true);
            }
        });
        list.setVisibility(View.VISIBLE);
        list.setAdapter(adapterEpisodes);
        if (episodesBySeason.size() > 1) {
            seasons.setVisibility(View.VISIBLE);
            seasons.setAdapter(new Tabs());
        }
        watch.setOnClickListener(view -> {
            int index = nextIndex();
            if (index >= 0) {
                play(allEpisodes, index, true);
            }
        });
        doStart.setOnClickListener(view -> play(allEpisodes, 0, false));
        random.setVisibility(View.VISIBLE);
        random.setOnClickListener(view -> play(allEpisodes, shuffle.nextInt(allEpisodes.size()), false));
        similar.setVisibility(View.VISIBLE);
        similar.setOnClickListener(view -> toggleSimilar());
        selectSeason(seasonDoIndex(nextIndex()));
        updateButtons();
        if (getIntent().getBooleanExtra(EXTRA_PLAY, false)) {
            resumeEpisode();
        }
    }

    private void resumeEpisode() {
        for (int position = 0; position < allEpisodes.size(); position += 1) {
            if (allEpisodes.get(position).id.equals(media.id)) {
                play(allEpisodes, position, true);

                return;
            }
        }
    }

    private int seasonDoIndex(int index) {
        if (index < 0) {
            return 0;
        }
        Media episode = allEpisodes.get(index);
        int position = numbers.indexOf(episode.season);

        return Math.max(position, 0);
    }

    private void selectSeason(int index) {
        if (index >= episodesBySeason.size()) {
            return;
        }
        seasonCurrent = index;
        List<Media> episodes = episodesBySeason.get(index);
        adapterEpisodes.swap(episodes);
        int playNext = nextIndex();
        int position = playNext < 0 ? -1 : episodes.indexOf(allEpisodes.get(playNext));
        list.setSelectedPosition(Math.max(position, 0));
    }

    /** Where the show stopped: the episode in progress, or the one after the last finished. */
    private int nextIndex() {
        if (allEpisodes.isEmpty()) {
            return -1;
        }
        String last = user.lastOfShow(allEpisodes.get(0).showId);
        for (int position = 0; last != null && position < allEpisodes.size(); position += 1) {
            if (allEpisodes.get(position).id.equals(last)) {
                UserStore.Progress progress = user.progress(last);
                boolean ended = progress != null && progress.watched;

                return ended ? Math.min(position + 1, allEpisodes.size() - 1) : position;
            }
        }

        return 0;
    }

    private void updateButtons() {
        favorite.setText(user.favorite(showOrMovie().id) ? "♥  Favorito" : "♡  Favoritar");
        buggy.setText(user.buggy(showOrMovie().id) ? "✓  Bugado" : "Bugado");
        if (!allEpisodes.isEmpty()) {
            Media episode = allEpisodes.get(Math.max(nextIndex(), 0));
            UserStore.Progress progress = user.progress(episode.id);
            boolean resume = progress != null && progress.resumable();
            watch.setText((resume ? "▶  Continuar  " : "▶  Assistir  ") + "T" + episode.season
                    + (episode.episode > 0 ? " E" + episode.episode : ""));
            doStart.setVisibility(View.GONE);

            return;
        }
        if (media.isShow() || media.isEpisode()) {
            return;
        }
        UserStore.Progress progress = user.progress(media.id);
        boolean resume = progress != null && progress.resumable();
        watch.setText(resume ? "▶  Continuar de " + Text.clock(progress.positionMs) : "▶  Assistir");
        doStart.setVisibility(resume ? View.VISIBLE : View.GONE);
    }

    private void play(List<Media> queue, int index, boolean resume) {
        if (queue.isEmpty() || queue.get(index).url == null) {
            Toast.makeText(this, "Sem arquivo para tocar", Toast.LENGTH_SHORT).show();

            return;
        }
        Catalog.queue = new ArrayList<>(queue);
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index);
        intent.putExtra(PlayerActivity.EXTRA_RESUME, resume);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (watch == null) {
            return;
        }
        if (adapterEpisodes != null) {
            adapterEpisodes.notifyDataSetChanged();
        }
        updateButtons();
    }

    private final class Tabs extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int kind) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);

            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextView tab = (TextView) holder.itemView;
            int number = numbers.get(position);
            tab.setText(number == 0 ? "Extras" : "Temporada " + number);
            tab.setOnFocusChangeListener((view, focus) -> {
                if (focus && position != seasonCurrent) {
                    selectSeason(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return numbers.size();
        }
    }
}
