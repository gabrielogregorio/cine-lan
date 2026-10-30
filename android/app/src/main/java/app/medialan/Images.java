package app.medialan;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;

/** Fixed sizes and RGB_565: the stick has 1 GB of RAM and the library has thousands of images. */
final class Images {
    private static final RequestOptions COVER =
            new RequestOptions().override(232, 348).format(DecodeFormat.PREFER_RGB_565).centerCrop();
    private static final RequestOptions FRAME =
            new RequestOptions().override(384, 216).format(DecodeFormat.PREFER_RGB_565).centerCrop();
    private static final RequestOptions BACKGROUND =
            new RequestOptions().override(960, 540).format(DecodeFormat.PREFER_RGB_565).centerCrop();
    private static final RequestOptions LOGO = new RequestOptions().override(480, 180).fitCenter();

    private Images() {}

    private static String url(ImageView v, String path) {
        return Catalog.absolute(v.getContext(), path);
    }

    static void cover(ImageView v, String path) {
        load(v, path, COVER);
    }

    static void frame(ImageView v, String path) {
        load(v, path, FRAME);
    }

    private static void load(ImageView v, String path, RequestOptions options) {
        if (path == null) {
            Glide.with(v).clear(v);
            v.setImageDrawable(null);
            return;
        }
        Glide.with(v).load(url(v, path)).apply(options).into(v);
    }

    static void background(ImageView v, String path) {
        if (path == null) return;
        Glide.with(v).load(url(v, path)).apply(BACKGROUND).transition(DrawableTransitionOptions.withCrossFade(250)).into(v);
    }

    static void background(ImageView v, String path, RequestListener<Drawable> onLoad) {
        Glide.with(v).load(url(v, path)).apply(BACKGROUND).listener(onLoad).into(v);
    }

    static void logo(ImageView v, String path) {
        if (path == null) {
            Glide.with(v).clear(v);
            v.setImageDrawable(null);
            v.setVisibility(android.view.View.GONE);
            return;
        }
        v.setVisibility(android.view.View.VISIBLE);
        Glide.with(v).load(url(v, path)).apply(LOGO).into(v);
    }

    static void release(Context c) {
        Glide.get(c).clearMemory();
    }
}
