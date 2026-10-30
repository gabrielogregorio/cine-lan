package app.medialan;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MainActivity extends Activity {
    private static final int WIDTH_MENU_CLOSED = 72;
    private static final int WIDTH_MENU_OPEN = 208;
    private static final int DELAY_FEATURED_MS = 300;
    private static final int COLUMNS_GRID = 7;
    private static final int COLUMNS_SEARCH = 5;
    private static final int COLUMNS_KEYBOARD = 6;
    private static final String[] KEYS = {
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R",
        "S", "T", "U", "V", "W", "X", "Y", "Z", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "␣", "⌫"
    };

    private final Handler delay = new Handler(Looper.getMainLooper());
    private View panelHome, panelGrid, panelSearch, panelState, heroArea;
    private ImageView heroLogo;
    private CrossfadeBackground heroBackground;
    private TextView heroTitle, heroMeta, heroOverview, titleGrid, textSearch, stateText, stateRetry;
    private LinearLayout menu;
    private VerticalGridView rows, grid, results, keyboard;
    private Cards.Shelves shelves;
    private final Cards.Poster posterGrid = new Cards.Poster(new ArrayList<>(), this::open, null);
    private final Cards.Poster posterSearch = new Cards.Poster(new ArrayList<>(), this::open, null);
    private final StringBuilder search = new StringBuilder();
    private final List<View> itemsMenu = new ArrayList<>();
    private Runnable featuredPending;
    private int versionSeen = -1;
    private long seedSeen = -1;
    private Supplier<List<Media>> gridLive;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        panelHome = findViewById(R.id.panel_home);
        panelGrid = findViewById(R.id.panel_grid);
        panelSearch = findViewById(R.id.panel_search);
        panelState = findViewById(R.id.panel_state);
        heroArea = findViewById(R.id.hero_area);
        heroBackground = new CrossfadeBackground(findViewById(R.id.hero_background), findViewById(R.id.hero_background_2));
        heroLogo = findViewById(R.id.hero_logo);
        heroTitle = findViewById(R.id.hero_title);
        heroMeta = findViewById(R.id.hero_meta);
        heroOverview = findViewById(R.id.hero_overview);
        titleGrid = findViewById(R.id.title_grid);
        textSearch = findViewById(R.id.text_search);
        stateText = findViewById(R.id.state_text);
        stateRetry = findViewById(R.id.state_retry);
        menu = findViewById(R.id.menu);
        rows = findViewById(R.id.rows);
        grid = findViewById(R.id.grid);
        results = findViewById(R.id.results);
        keyboard = findViewById(R.id.keyboard);

        Cards.alignNoTop(rows);
        Cards.prepareGrid(grid, COLUMNS_GRID);
        grid.setAdapter(posterGrid);
        Cards.prepareGrid(results, COLUMNS_SEARCH);
        results.setAdapter(posterSearch);
        keyboard.setNumColumns(COLUMNS_KEYBOARD);
        keyboard.setAdapter(new Keyboard());
        stateRetry.setOnClickListener(view -> load());

        addMenu(R.drawable.ic_home, "Início", true, () -> show(panelHome));
        addMenu(R.drawable.ic_movies, "Filmes", true,
                () -> showGrid("Filmes", Catalog.sorted(this, Catalog.movies)));
        addMenu(R.drawable.ic_shows, "Séries", true,
                () -> showGrid("Séries", Catalog.sorted(this, Catalog.shows)));
        addMenu(R.drawable.ic_favorites, "Favoritos", true,
                () -> showList("Favoritos", "Nada nos favoritos ainda", () -> Catalog.favorites(UserStore.of(this))));
        addMenu(R.drawable.ic_buggy, "Bugados", true,
                () -> showList("Bugados", "Nada marcado como bugado", () -> Catalog.buggy(UserStore.of(this))));
        addMenu(R.drawable.ic_search, "Buscar", true, () -> show(panelSearch));
        addMenu(R.drawable.ic_folders, "Pastas", false, () -> startActivity(new Intent(this, FoldersActivity.class)));
        addMenu(R.drawable.ic_shuffle, "Sortear filme", false, this::shuffle);
        addMenu(R.drawable.ic_settings, "Ajustes", false, () -> startActivity(new Intent(this, SettingsActivity.class)));
        markMenu(0);
        load();
    }

    private void addMenu(int icon, String label, boolean panel, Runnable action) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_menu, menu, false);
        ImageView image = item.findViewById(R.id.icon);
        TextView text = item.findViewById(R.id.label);
        image.setImageResource(icon);
        text.setText(label);
        text.setVisibility(View.GONE);
        int index = itemsMenu.size();
        ColorStateList claro = ColorStateList.valueOf(getColor(R.color.text));
        ColorStateList dark = ColorStateList.valueOf(getColor(R.color.background));
        image.setImageTintList(claro);
        text.setTextColor(claro);
        item.setOnClickListener(view -> {
            if (panel) {
                markMenu(index);
            }
            action.run();
            if (panel && loaded) {
                focusContent();
            }
        });
        item.setOnFocusChangeListener((view, focus) -> {
            image.setImageTintList(focus ? dark : claro);
            text.setTextColor(focus ? dark : claro);
            delay.post(this::adjustMenu);
        });
        itemsMenu.add(item);
        menu.addView(item);
    }

    private void markMenu(int index) {
        for (int position = 0; position < itemsMenu.size(); position += 1) {
            itemsMenu.get(position).setSelected(position == index);
        }
    }

    private void adjustMenu() {
        boolean open = menu.getFocusedChild() != null;
        int target = Cards.dp(menu, open ? WIDTH_MENU_OPEN : WIDTH_MENU_CLOSED);
        if (menu.getLayoutParams().width == target) {
            return;
        }
        for (View item : itemsMenu) {
            item.findViewById(R.id.label).setVisibility(open ? View.VISIBLE : View.GONE);
        }
        ValueAnimator animator = ValueAnimator.ofInt(menu.getLayoutParams().width, target).setDuration(140);
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = menu.getLayoutParams();
            params.width = (int) animation.getAnimatedValue();
            menu.setLayoutParams(params);
        });
        animator.start();
    }

    private void load() {
        showState("Carregando o acervo...", false);
        Catalog.load(this, (ok, error) -> {
            if (error != null) {
                showState("Não encontrei o servidor em\n" + Catalog.server(this)
                        + "\n\nConfira se ele está ligado, ou troque o endereço em Ajustes.", true);

                return;
            }
            loaded = true;
            panelState.setVisibility(View.GONE);
            buildHome();
            show(panelHome);
            focusWhenReady();
        });
    }

    /** Focus can only move to the shelves once they exist on screen. */
    private void focusWhenReady() {
        rows.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (rows.getChildCount() == 0) {
                    return;
                }
                rows.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                rows.requestFocus();
            }
        });
    }

    private void showState(String text, boolean comButton) {
        panelHome.setVisibility(View.GONE);
        panelGrid.setVisibility(View.GONE);
        panelSearch.setVisibility(View.GONE);
        panelState.setVisibility(View.VISIBLE);
        stateText.setText(text);
        stateRetry.setVisibility(comButton ? View.VISIBLE : View.GONE);
        if (comButton) {
            stateRetry.requestFocus();
        }
    }

    private void buildHome() {
        List<Catalog.Row> fresh = Catalog.rows(this, UserStore.of(this));
        versionSeen = UserStore.version;
        seedSeen = Catalog.seed(this);
        if (shelves == null) {
            shelves = new Cards.Shelves(fresh, this::open, this::resume, this::highlightAfter);
            rows.setAdapter(shelves);
        } else {
            shelves.swap(fresh);
        }
        if (!fresh.isEmpty() && !fresh.get(0).items.isEmpty()) {
            highlight(fresh.get(0).items.get(0));
        }
    }

    private void show(View panel) {
        panelHome.setVisibility(panel == panelHome ? View.VISIBLE : View.GONE);
        panelGrid.setVisibility(panel == panelGrid ? View.VISIBLE : View.GONE);
        panelSearch.setVisibility(panel == panelSearch ? View.VISIBLE : View.GONE);
        boolean comHero = panel == panelHome;
        heroArea.setVisibility(comHero ? View.VISIBLE : View.GONE);
    }

    /** User list: read again on return, because the detail screen can change it. */
    private void showList(String title, String empty, Supplier<List<Media>> source) {
        List<Media> items = source.get();
        showGrid(items.isEmpty() ? empty : title, items);
        gridLive = source;
    }

    private void showGrid(String title, List<Media> items) {
        gridLive = null;
        titleGrid.setText(title);
        posterGrid.swap(items);
        grid.setSelectedPosition(0);
        show(panelGrid);
    }

    private void focusContent() {
        if (panelHome.getVisibility() == View.VISIBLE) {
            rows.requestFocus();
        } else if (panelSearch.getVisibility() == View.VISIBLE) {
            keyboard.requestFocus();
        } else if (posterGrid.getItemCount() > 0) {
            grid.requestFocus();
        } else {
            // An empty grid takes no focus: without this the remote would have nothing selected.
            for (View item : itemsMenu) {
                if (item.isSelected()) {
                    item.requestFocus();
                }
            }
        }
    }

    private void highlightAfter(Media mediaItem) {
        if (featuredPending != null) {
            delay.removeCallbacks(featuredPending);
        }
        featuredPending = () -> highlight(mediaItem);
        delay.postDelayed(featuredPending, DELAY_FEATURED_MS);
    }

    private void highlight(Media mediaItem) {
        heroBackground.show(mediaItem.background());
        Images.logo(heroLogo, mediaItem.logo);
        heroTitle.setText(mediaItem.isEpisode() ? mediaItem.titleFull() : mediaItem.title);
        heroTitle.setVisibility(mediaItem.logo == null ? View.VISIBLE : View.GONE);
        heroMeta.setText(mediaItem.metadata());
        heroOverview.setText(mediaItem.overview);
    }

    private void shuffle() {
        if (Catalog.movies.isEmpty()) {
            return;
        }
        Catalog.queue = Catalog.queueRandom();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_RESUME, false);
        startActivity(intent);
    }

    private void resume(Media mediaItem) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_MEDIA, mediaItem.raw.toString());
        intent.putExtra(DetailActivity.EXTRA_PLAY, true);
        startActivity(intent);
    }

    private void open(Media mediaItem) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_MEDIA, mediaItem.raw.toString());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!loaded) {
            return;
        }
        if (versionSeen != UserStore.version || seedSeen != Catalog.seed(this)) {
            buildHome();
        }
        if (gridLive != null && panelGrid.getVisibility() == View.VISIBLE) {
            posterGrid.swap(gridLive.get());
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Back in the content goes to the menu; only the second Back leaves the app.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP
                && loaded && menu.getFocusedChild() == null) {
            for (View item : itemsMenu) {
                if (item.isSelected()) {
                    item.requestFocus();
                }
            }

            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void type(String key) {
        if ("⌫".equals(key)) {
            if (search.length() > 0) {
                search.setLength(search.length() - 1);
            }
        } else {
            search.append("␣".equals(key) ? " " : key);
        }
        textSearch.setText(search.toString());
        List<Media> found = Catalog.search(search.toString());
        posterSearch.swap(found);
        if (search.length() > 0 && found.isEmpty()) {
            Toast.makeText(this, "Nada encontrado", Toast.LENGTH_SHORT).show();
        }
    }

    private final class Keyboard extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int kind) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_key, parent, false);

            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextView key = (TextView) holder.itemView;
            key.setText(KEYS[position]);
            key.setOnClickListener(view -> type(KEYS[position]));
        }

        @Override
        public int getItemCount() {
            return KEYS.length;
        }
    }
}
