package app.medialan;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * Two background layers: the new one loads hidden and fades in over the old one.
 * Never reuse the drawable in use as a placeholder: with little RAM Glide recycles its bitmap
 * and the next draw crashes the app ("trying to use a recycled bitmap").
 */
final class CrossfadeBackground {
    private static final int FADE_MS = 200;

    private final ImageView[] layers;
    private int visible;
    private String current;

    CrossfadeBackground(ImageView forward, ImageView back) {
        layers = new ImageView[] {forward, back};
    }

    void show(String path) {
        if (path == null || path.equals(current)) return;
        current = path;
        ImageView next = layers[1 - visible];
        next.animate().cancel();
        next.setAlpha(0f);
        Images.background(next, path, new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object template,
                    @NonNull Target<Drawable> target, boolean first) {
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable d, @NonNull Object template, Target<Drawable> target,
                    @NonNull DataSource source, boolean first) {
                next.post(() -> swap(next));
                return false;
            }
        });
    }

    private void swap(ImageView next) {
        ImageView previous = layers[visible];
        visible = 1 - visible;
        next.bringToFront();
        next.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            if (layers[visible] == next) Glide.with(previous).clear(previous);
        }).start();
    }
}
