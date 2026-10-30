package app.medialan;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cards with TV focus: poster, episode and the shelves holding them. */
final class Cards {
    private static final float SCALE_FOCUS = 1.09f;
    private static final int INSET_SHELF_DP = 12;
    private static final int CARDS_AHEAD = 8;
    private static final int CARDS_KEPT = 40;

    interface Action {
        void onPick(Media m);
    }

    private Cards() {}

    static int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    static void animateFocus(View v, boolean focus) {
        float target = focus ? SCALE_FOCUS : 1f;
        v.animate().scaleX(target).scaleY(target).setDuration(130).start();
    }

    /** Focus fixed on the left and the shelf moving under it, as in streaming apps. */
    static void alignHorizontal(HorizontalGridView g, int insetDp) {
        g.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
        g.setWindowAlignmentOffsetPercent(0);
        g.setWindowAlignmentOffset(dp(g, insetDp));
        g.setItemAlignmentOffsetPercent(0);
        g.setHorizontalSpacing(0);
    }

    static void alignNoTop(VerticalGridView g) {
        g.setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);
        g.setWindowAlignmentOffsetPercent(0);
        g.setWindowAlignmentOffset(0);
        g.setItemAlignmentOffsetPercent(0);
    }

    /** Keeps cards ready so grid scrolling does not have to create new views. */
    static void prepareGrid(VerticalGridView g, int columns) {
        g.setNumColumns(columns);
        g.setHasFixedSize(true);
        g.setItemViewCacheSize(columns * 2);
        g.getRecycledViewPool().setMaxRecycledViews(0, CARDS_KEPT);
    }

    static void showProgress(ProgressBar bar, UserStore.Progress p) {
        if (p != null && p.positionMs > 0 && p.permille() > 0) {
            bar.setProgress(p.watched ? 1000 : p.permille());
            bar.setVisibility(View.VISIBLE);
        } else {
            bar.setVisibility(View.GONE);
        }
    }

    static final class Poster extends RecyclerView.Adapter<Poster.Vh> {
        static final class Vh extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView name;
            final ProgressBar running;

            Vh(View v) {
                super(v);
                cover = v.findViewById(R.id.cover);
                cover.setClipToOutline(true);
                name = v.findViewById(R.id.name);
                running = v.findViewById(R.id.running);
            }
        }

        private List<Media> items;
        private final Action pick;
        private final Action onFocused;

        Poster(List<Media> items, Action pick, Action onFocused) {
            this.items = items;
            this.pick = pick;
            this.onFocused = onFocused;
        }

        void swap(List<Media> fresh) {
            items = fresh;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Vh onCreateViewHolder(@NonNull ViewGroup parent, int kind) {
            return new Vh(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Vh h, int position) {
            Media m = items.get(position);
            h.name.setText(m.isEpisode() ? m.titleFull() : m.title);
            Images.cover(h.cover, m.cover());
            showProgress(h.running, m.isShow() ? null : UserStore.of(h.itemView.getContext()).progress(m.id));
            h.itemView.setOnClickListener(v -> pick.onPick(m));
            h.itemView.setOnFocusChangeListener((v, focus) -> {
                animateFocus(v, focus);
                if (focus && onFocused != null) onFocused.onPick(m);
            });
            h.itemView.setScaleX(1f);
            h.itemView.setScaleY(1f);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static final class Episode extends RecyclerView.Adapter<Episode.Vh> {
        static final class Vh extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView name;
            final ProgressBar running;

            Vh(View v) {
                super(v);
                cover = v.findViewById(R.id.cover);
                cover.setClipToOutline(true);
                name = v.findViewById(R.id.name);
                running = v.findViewById(R.id.running);
            }
        }

        private List<Media> items;
        private final Action pick;

        Episode(List<Media> items, Action pick) {
            this.items = items;
            this.pick = pick;
        }

        void swap(List<Media> fresh) {
            items = fresh;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Vh onCreateViewHolder(@NonNull ViewGroup parent, int kind) {
            return new Vh(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Vh h, int position) {
            Media m = items.get(position);
            h.name.setText(m.episode > 0 ? m.episode + ". " + m.title : m.title);
            Images.frame(h.cover, m.thumb != null ? m.thumb : m.background());
            showProgress(h.running, UserStore.of(h.itemView.getContext()).progress(m.id));
            h.itemView.setOnClickListener(v -> pick.onPick(m));
            h.itemView.setOnFocusChangeListener((v, focus) -> animateFocus(v, focus));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** Home screen shelves. Each one remembers where focus stopped, even after being recycled. */
    static final class Shelves extends RecyclerView.Adapter<Shelves.Vh> {
        static final class Vh extends RecyclerView.ViewHolder {
            final TextView title;
            final HorizontalGridView cards;

            Vh(View v) {
                super(v);
                title = v.findViewById(R.id.title_row);
                cards = v.findViewById(R.id.cards);
            }
        }

        private List<Catalog.Row> rows;
        private final Action pick;
        private final Action resume;
        private final Action onFocused;
        private final Map<String, Integer> positions = new HashMap<>();
        private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();

        Shelves(List<Catalog.Row> rows, Action pick, Action resume, Action onFocused) {
            this.rows = rows;
            this.pick = pick;
            this.resume = resume;
            this.onFocused = onFocused;
            pool.setMaxRecycledViews(0, CARDS_KEPT);
        }

        void swap(List<Catalog.Row> fresh) {
            rows = fresh;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Vh onCreateViewHolder(@NonNull ViewGroup parent, int kind) {
            Vh h = new Vh(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
            alignHorizontal(h.cards, INSET_SHELF_DP);
            h.cards.setRecycledViewPool(pool);
            h.cards.setHasFixedSize(true);
            h.cards.setInitialPrefetchItemCount(CARDS_AHEAD);
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull Vh h, int position) {
            Catalog.Row row = rows.get(position);
            h.title.setText(row.title);
            h.cards.clearOnScrollListeners();
            h.cards.setAdapter(new Poster(row.items, row.resumes() ? resume : pick, onFocused));
            Integer saves = positions.get(row.title);
            h.cards.setSelectedPosition(saves == null ? 0 : Math.min(saves, row.items.size() - 1));
            h.cards.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
                @Override
                public void onChildViewHolderSelected(
                        @NonNull RecyclerView parent, RecyclerView.ViewHolder child, int pos, int sub) {
                    positions.put(row.title, pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
