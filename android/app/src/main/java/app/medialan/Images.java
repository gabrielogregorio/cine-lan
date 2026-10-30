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

    private static String url(ImageView imageView, String path) {
        return Catalog.absolute(imageView.getContext(), path);
    }

    static void cover(ImageView imageView, String path) {
        load(imageView, path, COVER);
    }

    static void frame(ImageView imageView, String path) {
        load(imageView, path, FRAME);
    }

    private static void load(ImageView imageView, String path, RequestOptions options) {
        if (path == null) {
            Glide.with(imageView).clear(imageView);
            imageView.setImageDrawable(null);

            return;
        }
        Glide.with(imageView).load(url(imageView, path)).apply(options).into(imageView);
    }

    static void background(ImageView imageView, String path) {
        if (path == null) {
            return;
        }
        Glide.with(imageView).load(url(imageView, path)).apply(BACKGROUND).transition(DrawableTransitionOptions.withCrossFade(250)).into(imageView);
    }

    static void background(ImageView imageView, String path, RequestListener<Drawable> onLoad) {
        Glide.with(imageView).load(url(imageView, path)).apply(BACKGROUND).listener(onLoad).into(imageView);
    }

    static void logo(ImageView imageView, String path) {
        if (path == null) {
            Glide.with(imageView).clear(imageView);
            imageView.setImageDrawable(null);
            imageView.setVisibility(android.view.View.GONE);

            return;
        }
        imageView.setVisibility(android.view.View.VISIBLE);
        Glide.with(imageView).load(url(imageView, path)).apply(LOGO).into(imageView);
    }

    static void release(Context context) {
        Glide.get(context).clearMemory();
    }
}
