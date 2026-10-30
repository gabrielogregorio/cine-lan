package app.medialan;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Player built for a remote: OK pauses, left and right seek, up opens audio and subtitles. */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    static final String EXTRA_INDEX = "indice";
    static final String EXTRA_RESUME = "resume";
    private static final long STEP_MS = 10_000;
    private static final long WAIT_CONFIRM_MS = 600;
    private static final long TIME_PANEL_MS = 4_000;
    private static final long INTERVAL_SAVE_MS = 10_000;
    private static final long WAIT_NEXT_MS = 6_000;
    private static final String LABEL = "MediaLan";

    private final Handler clock = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private UserStore user;
    private List<Media> queue;
    private int index;
    private View panel, tracksPanel, buttonsNeighbors;
    private TextView previous, next;
    private LinearLayout tracksList;
    private ProgressBar bar, loading;
    private TextView title, time, notice;
    private long targetMs = -1;
    private long lastRecord;
    private boolean waitingNext;
    private long homeMs;

    private final Runnable hide = () -> {
        if (player != null && player.isPlaying() && !buttonsNeighbors.hasFocus()) panel.setVisibility(View.GONE);
    };
    private final Runnable confirmJump = () -> {
        if (player != null && targetMs >= 0) {
            player.seekTo(targetMs);
            targetMs = -1;
        }
    };
    private final Runnable playNext = () -> playIndex(index + 1, false);
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updatePanel();
            long now = System.currentTimeMillis();
            if (player != null && player.isPlaying() && now - lastRecord >= INTERVAL_SAVE_MS) {
                save();
                lastRecord = now;
            }
            clock.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        queue = Catalog.queue;
        if (queue.isEmpty()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_player);
        Images.release(this);
        user = UserStore.of(this);
        panel = findViewById(R.id.panel);
        buttonsNeighbors = findViewById(R.id.buttons_neighbors);
        previous = findViewById(R.id.previous);
        next = findViewById(R.id.next);
        previous.setOnClickListener(v -> swapNeighbor(-1));
        next.setOnClickListener(v -> swapNeighbor(1));
        tracksPanel = findViewById(R.id.tracks_panel);
        tracksList = findViewById(R.id.tracks_list);
        bar = findViewById(R.id.bar);
        loading = findViewById(R.id.loading);
        title = findViewById(R.id.title);
        time = findViewById(R.id.time);
        notice = findViewById(R.id.notice);

        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(STEP_MS)
                .setSeekForwardIncrementMs(STEP_MS)
                .build();
        PlayerView video = findViewById(R.id.video);
        video.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                loading.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_ENDED) onDone();
            }

            @Override
            public void onRenderedFirstFrame() {
                Log.i(LABEL, "first frame in " + (System.currentTimeMillis() - homeMs) + " ms");
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                loading.setVisibility(View.GONE);
                boolean left = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;
                notice.setText(left ? "Este arquivo não está mais no acervo."
                        : "Não consegui tocar este arquivo.\n" + error.getErrorCodeName());
                notice.setVisibility(View.VISIBLE);
            }
        });
        playIndex(getIntent().getIntExtra(EXTRA_INDEX, 0), getIntent().getBooleanExtra(EXTRA_RESUME, true));
    }

    private void playIndex(int fresh, boolean resume) {
        if (fresh >= queue.size()) {
            finish();
            return;
        }
        clock.removeCallbacks(playNext);
        waitingNext = false;
        notice.setVisibility(View.GONE);
        releaseNeighbors();
        if (player.getCurrentMediaItem() != null && fresh != index) save();
        index = fresh;
        Media m = queue.get(index);
        title.setText(m.titleFull());
        prepareNeighbors();
        loading.setVisibility(View.VISIBLE);
        showPanel();
        String url = Catalog.server(this) + "/api/items/" + m.id;
        Catalog.inBackground(() -> new JSONObject(Catalog.download(url)), (detail, error) -> {
            if (player != null && index == fresh) start(m, detail, resume);
        });
    }

    /** Subtitles only come with the item detail. Without it the video still plays. */
    private void start(Media m, JSONObject detail, boolean resume) {
        player.setMediaItem(new MediaItem.Builder()
                .setUri(Uri.parse(Catalog.absolute(this, m.url)))
                .setSubtitleConfigurations(subtitles(detail))
                .build());
        UserStore.Progress p = user.progress(m.id);
        if (resume && p != null && p.resumable()) player.seekTo(p.positionMs);
        homeMs = System.currentTimeMillis();
        player.prepare();
        player.play();
    }

    private List<MediaItem.SubtitleConfiguration> subtitles(JSONObject detail) {
        List<MediaItem.SubtitleConfiguration> out = new ArrayList<>();
        JSONArray list = detail == null ? null : detail.optJSONArray("subtitles");
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject l = list.optJSONObject(i);
            String path = l.optString("url");
            String language = l.optString("language");
            out.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(Catalog.absolute(this, path)))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(language.isEmpty() ? null : language)
                    .build());
        }
        return out;
    }

    /** Previous and next wait in the panel: arrow down and OK. */
    private void prepareNeighbors() {
        previous.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
        next.setVisibility(index + 1 < queue.size() ? View.VISIBLE : View.GONE);
        if (index > 0) previous.setText("⏮  " + queue.get(index - 1).titleFull());
        if (index + 1 < queue.size()) next.setText("⏭  " + queue.get(index + 1).titleFull());
        buttonsNeighbors.setVisibility(queue.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private void focusNeighbors() {
        if (queue.size() < 2) {
            showPanel();
            return;
        }
        previous.setFocusable(true);
        next.setFocusable(true);
        panel.setVisibility(View.VISIBLE);
        (next.getVisibility() == View.VISIBLE ? next : previous).requestFocus();
    }

    private void releaseNeighbors() {
        previous.setFocusable(false);
        next.setFocusable(false);
    }

    private void swapNeighbor(int direction) {
        playIndex(index + direction, false);
    }

    private void onDone() {
        save();
        if (index + 1 >= queue.size()) {
            finish();
            return;
        }
        waitingNext = true;
        notice.setText("Próximo: " + queue.get(index + 1).titleFull() + "\nOK para começar agora");
        notice.setVisibility(View.VISIBLE);
        clock.postDelayed(playNext, WAIT_NEXT_MS);
    }

    private void save() {
        if (player == null || player.getDuration() == C.TIME_UNSET) return;
        boolean ended = player.getPlaybackState() == Player.STATE_ENDED;
        long duration = player.getDuration();
        user.save(queue.get(index), ended ? duration : player.getCurrentPosition(), duration);
    }

    private void showPanel() {
        panel.setVisibility(View.VISIBLE);
        updatePanel();
        clock.removeCallbacks(hide);
        clock.postDelayed(hide, TIME_PANEL_MS);
    }

    private void updatePanel() {
        if (player == null || panel.getVisibility() != View.VISIBLE) return;
        long duration = Math.max(player.getDuration(), 0);
        long position = targetMs >= 0 ? targetMs : player.getCurrentPosition();
        bar.setProgress(duration > 0 ? (int) (position * 10000 / duration) : 0);
        bar.setSecondaryProgress(duration > 0 ? (int) (player.getBufferedPosition() * 10000 / duration) : 0);
        String text = Text.clock(position) + " / " + Text.clock(duration);
        if (!text.contentEquals(time.getText())) time.setText(text);
    }

    private void jump(int direction, int repeat) {
        if (player == null) return;
        long base = targetMs >= 0 ? targetMs : player.getCurrentPosition();
        // Holding the key speeds up: from 10 s to 30 s and 60 s per step.
        long step = repeat > 30 ? STEP_MS * 6 : repeat > 8 ? STEP_MS * 3 : STEP_MS;
        long max = Math.max(player.getDuration() - 1000, 0);
        targetMs = Math.max(0, Math.min(base + direction * step, max));
        clock.removeCallbacks(confirmJump);
        clock.postDelayed(confirmJump, WAIT_CONFIRM_MS);
        showPanel();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (tracksPanel.getVisibility() == View.VISIBLE) {
            if (key != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
            if (e.getAction() == KeyEvent.ACTION_UP) closeTracks();
            return true;
        }
        if (buttonsNeighbors.hasFocus()) {
            if (key != KeyEvent.KEYCODE_BACK && key != KeyEvent.KEYCODE_DPAD_UP) return super.dispatchKeyEvent(e);
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                releaseNeighbors();
                showPanel();
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (e.getRepeatCount() > 0) return true;
                if (waitingNext) {
                    clock.removeCallbacks(playNext);
                    playIndex(index + 1, false);
                } else if (player != null) {
                    player.setPlayWhenReady(!player.getPlayWhenReady());
                    showPanel();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                jump(-1, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                jump(1, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (e.getRepeatCount() == 0) openTracks();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (e.getRepeatCount() == 0) focusNeighbors();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (index + 1 < queue.size()) swapNeighbor(1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (index > 0) swapNeighbor(-1);
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private void openTracks() {
        if (player == null) return;
        List<String> names = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO && g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                int track = i;
                String mark = g.isTrackSelected(i) ? "✓ " : "";
                String kind = g.getType() == C.TRACK_TYPE_AUDIO ? "Áudio: " : "Legenda: ";
                names.add(mark + kind + nameOfTrack(g.getTrackFormat(i), i));
                actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), track))
                        .build()));
            }
        }
        if (tracks.isTypeSelected(C.TRACK_TYPE_TEXT)) {
            names.add("Desligar legenda");
            actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()));
        }
        if (names.isEmpty()) {
            showPanel();
            return;
        }
        // Panel inside this window: an AlertDialog here never received the Back key.
        tracksList.removeAllViews();
        for (int i = 0; i < names.size(); i++) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_track, tracksList, false);
            Runnable action = actions.get(i);
            row.setText(names.get(i));
            row.setOnClickListener(v -> {
                action.run();
                closeTracks();
            });
            tracksList.addView(row);
        }
        tracksPanel.setVisibility(View.VISIBLE);
        tracksList.getChildAt(0).requestFocus();
    }

    private void closeTracks() {
        tracksPanel.setVisibility(View.GONE);
    }

    private static String nameOfTrack(Format f, int position) {
        if (f.label != null) return f.label;
        if (f.language != null && !"und".equals(f.language)) return new Locale(f.language).getDisplayLanguage(new Locale("pt", "BR"));
        return "Faixa " + (position + 1);
    }

    @Override
    protected void onStart() {
        super.onStart();
        clock.post(tick);
    }

    private void registerFrames() {
        DecoderCounters c = player == null ? null : player.getVideoDecoderCounters();
        Format f = player == null ? null : player.getVideoFormat();
        if (c == null) return;
        c.ensureUpdated();
        Log.i(LABEL, "frames " + c.renderedOutputBufferCount + " dropped " + c.droppedBufferCount
                + " longest run " + c.maxConsecutiveDroppedBufferCount
                + " format " + (f == null ? "?" : f.sampleMimeType + " " + f.width + "x" + f.height));
    }

    @Override
    protected void onStop() {
        super.onStop();
        registerFrames();
        save();
        clock.removeCallbacksAndMessages(null);
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
