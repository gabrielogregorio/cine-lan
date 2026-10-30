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
        void onPick(Media mediaItem);
    }

    private Cards() {}

    static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static void animateFocus(View view, boolean focus) {
        float target = focus ? SCALE_FOCUS : 1f;
        view.animate().scaleX(target).scaleY(target).setDuration(130).start();
    }

    /** Focus fixed on the left and the shelf moving under it, as in streaming apps. */
    static void alignHorizontal(HorizontalGridView gridView, int insetDp) {
        gridView.setWindowAlignment(BaseGridView.WINDOW_ALIGN_NO_EDGE);
        gridView.setWindowAlignmentOffsetPercent(0);
        gridView.setWindowAlignmentOffset(dp(gridView, insetDp));
        gridView.setItemAlignmentOffsetPercent(0);
        gridView.setHorizontalSpacing(0);
    }

    static void alignNoTop(VerticalGridView gridView) {
        gridView.setWindowAlignment(BaseGridView.WINDOW_ALIGN_LOW_EDGE);
        gridView.setWindowAlignmentOffsetPercent(0);
        gridView.setWindowAlignmentOffset(0);
        gridView.setItemAlignmentOffsetPercent(0);
    }

    /** Keeps cards ready so grid scrolling does not have to create new views. */
    static void prepareGrid(VerticalGridView gridView, int columns) {
        gridView.setNumColumns(columns);
        gridView.setHasFixedSize(true);
        gridView.setItemViewCacheSize(columns * 2);
        gridView.getRecycledViewPool().setMaxRecycledViews(0, CARDS_KEPT);
    }

    static void showProgress(ProgressBar bar, UserStore.Progress progress) {
        if (progress != null && progress.positionMs > 0 && progress.permille() > 0) {
            bar.setProgress(progress.watched ? 1000 : progress.permille());
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

            Vh(View view) {
                super(view);
                cover = view.findViewById(R.id.cover);
                cover.setClipToOutline(true);
                name = view.findViewById(R.id.name);
                running = view.findViewById(R.id.running);
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
        public void onBindViewHolder(@NonNull Vh holder, int position) {
            Media mediaItem = items.get(position);
            holder.name.setText(mediaItem.isEpisode() ? mediaItem.titleFull() : mediaItem.title);
            Images.cover(holder.cover, mediaItem.cover());
            showProgress(holder.running, mediaItem.isShow() ? null : UserStore.of(holder.itemView.getContext()).progress(mediaItem.id));
            holder.itemView.setOnClickListener(view -> pick.onPick(mediaItem));
            holder.itemView.setOnFocusChangeListener((view, focus) -> {
                animateFocus(view, focus);
                if (focus && onFocused != null) {
                    onFocused.onPick(mediaItem);
                }
            });
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
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

            Vh(View view) {
                super(view);
                cover = view.findViewById(R.id.cover);
                cover.setClipToOutline(true);
                name = view.findViewById(R.id.name);
                running = view.findViewById(R.id.running);
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
        public void onBindViewHolder(@NonNull Vh holder, int position) {
            Media mediaItem = items.get(position);
            holder.name.setText(mediaItem.episode > 0 ? mediaItem.episode + ". " + mediaItem.title : mediaItem.title);
            Images.frame(holder.cover, mediaItem.thumb != null ? mediaItem.thumb : mediaItem.background());
            showProgress(holder.running, UserStore.of(holder.itemView.getContext()).progress(mediaItem.id));
            holder.itemView.setOnClickListener(view -> pick.onPick(mediaItem));
            holder.itemView.setOnFocusChangeListener((view, focus) -> animateFocus(view, focus));
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

            Vh(View view) {
                super(view);
                title = view.findViewById(R.id.title_row);
                cards = view.findViewById(R.id.cards);
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
            Vh holder = new Vh(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
            alignHorizontal(holder.cards, INSET_SHELF_DP);
            holder.cards.setRecycledViewPool(pool);
            holder.cards.setHasFixedSize(true);
            holder.cards.setInitialPrefetchItemCount(CARDS_AHEAD);

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull Vh holder, int position) {
            Catalog.Row row = rows.get(position);
            holder.title.setText(row.title);
            holder.cards.clearOnScrollListeners();
            holder.cards.setAdapter(new Poster(row.items, row.resumes() ? resume : pick, onFocused));
            Integer saves = positions.get(row.title);
            holder.cards.setSelectedPosition(saves == null ? 0 : Math.min(saves, row.items.size() - 1));
            holder.cards.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
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
