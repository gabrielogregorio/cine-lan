package app.medialan;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

/** Leaving the app really closes it: nothing keeps using the stick's memory in the background. */
public class MediaLanApp extends Application implements Application.ActivityLifecycleCallbacks {
    private static final long WAIT_CLOSE_MS = 1500;

    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable close = this::closeAll;
    private int visible;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    /** Without removing the task, Android would reopen the old screen instead of the home screen. */
    private void closeAll() {
        for (ActivityManager.AppTask task : getSystemService(ActivityManager.class).getAppTasks()) {
            task.finishAndRemoveTask();
        }
        Process.killProcess(Process.myPid());
    }

    @Override
    public void onActivityStarted(Activity a) {
        visible++;
        clock.removeCallbacks(close);
    }

    @Override
    public void onActivityStopped(Activity a) {
        visible--;
        if (visible == 0) clock.postDelayed(close, WAIT_CLOSE_MS);
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
