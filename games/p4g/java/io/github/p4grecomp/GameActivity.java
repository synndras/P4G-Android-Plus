package io.github.p4grecomp;

import android.app.NativeActivity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.view.InputDevice;
import android.view.KeyEvent;
import java.io.File;

/** The runtime lives in :game; its native exit must not kill the launcher. */
public class GameActivity extends NativeActivity {
    private boolean pauseScreenOpening;
    private Object backCallback;
    private PerformanceOverlay performanceOverlay;
    static native long nativeFrames();

    private void openSource(String uri, String name, String fdKey, String nameKey) throws Exception {
        try (android.os.ParcelFileDescriptor file = getContentResolver().openFileDescriptor(android.net.Uri.parse(uri), "r")) {
            if (file == null) throw new java.io.IOException("Could not open " + name);
            Os.setenv(nameKey, name, true);
            Os.setenv(fdKey, Integer.toString(file.detachFd()), true);
        }
    }

    @Override protected void onCreate(Bundle state) {
        // The installed payload is checked by the launcher; no source URI, NSP descriptor or keys are opened here.
        try {
            File data = Launcher.dataDir();
            // Android draws the metrics panel; avoid duplicating the native FPS digits.
            Os.setenv("NX_SHOW_FPS", "0", true);
            String uri = getIntent().getStringExtra(ImportActivity.EXTRA_URI);
            Os.unsetenv("NX_GAME_UPDATE_FD");
            if (uri != null) {
                Os.unsetenv("NX_P4G_INSTALLED");
                Os.unsetenv("NX_P4G_GAME_ROOT");
                openSource(uri, getIntent().getStringExtra(ImportActivity.EXTRA_NAME), "NX_GAME_FD", "NX_GAME_NAME");
                String update = getIntent().getStringExtra(ImportActivity.EXTRA_UPDATE_URI);
                if (update != null) openSource(update, getIntent().getStringExtra(ImportActivity.EXTRA_UPDATE_NAME),
                        "NX_GAME_UPDATE_FD", "NX_GAME_UPDATE_NAME");
                Os.setenv("NX_KEYS", Launcher.keysFile(this, "prod.keys").getPath(), true);
                Os.setenv("NX_TITLE_KEYS", Launcher.keysFile(this, "title.keys").getPath(), true);
            } else {
                File installed = Launcher.installationDir(this);
                if (!InstalledGameFiles.present(installed))
                    throw new java.io.IOException("Game installation is incomplete. Install the game again from its NSP/XCI.");
                Os.unsetenv("NX_GAME_FD");
                Os.unsetenv("NX_GAME_NSP");
                Os.unsetenv("NX_GAME_NAME");
                Os.setenv("NX_P4G_INSTALLED", "1", true);
                Os.setenv("NX_P4G_GAME_ROOT", InstalledGameFiles.game(installed).getCanonicalPath(), true);
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(getFilesDir(), "data_root.txt"))) {
                out.write((data.getCanonicalPath() + "\n").getBytes("UTF-8"));
            }
        } catch (Exception e) {
            startActivity(menuIntent().putExtra("game_error", String.valueOf(e.getMessage())));
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        super.onCreate(state);
        Lang.load(this);
        performanceOverlay = new PerformanceOverlay(this,
                Launcher.readConfig(new File(Launcher.dataDir(), "config.txt")));
        if (Build.VERSION.SDK_INT >= 33) backCallback = Api33.register(this);
    }

    private Intent menuIntent() {
        return new Intent(this, Launcher.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    /** Also called by the native input queue when it receives phone Back. */
    public void confirmExit() {
        runOnUiThread(() -> {
            openPauseScreen();
        });
    }

    private void openPauseScreen() {
        if (pauseScreenOpening || isFinishing() || isDestroyed()) return;
        pauseScreenOpening = true;
        startActivity(new Intent(this, PauseActivity.class));
        overridePendingTransition(0, 0);
    }

    @Override public void onBackPressed() { openPauseScreen(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        int source = event.getSource();
        boolean controller = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if ((key == KeyEvent.KEYCODE_BACK && !controller) || key == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openPauseScreen();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume() {
        super.onResume();
        pauseScreenOpening = false;
        if (performanceOverlay != null) performanceOverlay.start();
    }
    @Override protected void onPause() {
        if (performanceOverlay != null) performanceOverlay.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (performanceOverlay != null) performanceOverlay.close();
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) Api33.unregister(this, backCallback);
        super.onDestroy();
    }
    private static final class Api33 {
        static Object register(GameActivity activity) {
            android.window.OnBackInvokedCallback callback = activity::confirmExit;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }
        static void unregister(GameActivity activity, Object callback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                (android.window.OnBackInvokedCallback) callback);
        }
    }
}
