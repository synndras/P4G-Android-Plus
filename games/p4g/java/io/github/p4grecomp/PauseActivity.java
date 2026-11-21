package io.github.p4grecomp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Translucent pause menu. The native game process is held at its next Vulkan present until this activity closes. */
public final class PauseActivity extends Activity {
    private final List<Button> actions = new ArrayList<>();
    private final Map<String, String> config = new LinkedHashMap<>();
    private int selected;
    private long lastAxisMove;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = .70f;
        window.setAttributes(attributes);
        immersive();
        config.putAll(Launcher.readConfig(new File(Launcher.dataDir(), "config.txt")));
        buildMenu();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void buildMenu() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xA915120B);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(22), dp(24), dp(20));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xF2221D13);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(3), P4gTheme.YELLOW);
        card.setBackground(shape);

        TextView title = new TextView(this);
        title.setText("PERSONA 4 GOLDEN   ·   PAUSADO");
        title.setTextColor(P4gTheme.YELLOW);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setPadding(0, 0, 0, dp(3));
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(this);
        hint.setText("La partida está detenida. Los cambios de rendimiento se aplican al volver a abrirla.");
        hint.setTextColor(P4gTheme.MUTED);
        hint.setTextSize(13);
        hint.setPadding(0, 0, 0, dp(12));
        card.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        addAction(card, "REANUDAR", this::resumeGame, true);
        addAction(card, "PANTALLA", this::showDisplayOptions, false);
        addAction(card, "RENDIMIENTO", this::showPerformanceOptions, false);
        addAction(card, "CONTROLES", this::openControls, false);
        addAction(card, "INFORMACIÓN DEL SISTEMA", this::showSystemInfo, false);
        addAction(card, "SALIR DEL JUEGO", this::confirmExit, false);

        FrameLayout.LayoutParams panel = new FrameLayout.LayoutParams(
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(540)),
                -2, Gravity.CENTER);
        root.addView(card, panel);
        setContentView(root);
        updateSelection();
    }

    private void addAction(LinearLayout card, String label, Runnable run, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setMinHeight(dp(48));
        button.setFocusable(true);
        P4gTheme.button(button, primary);
        button.setOnClickListener(v -> {
            selected = actions.indexOf(button);
            updateSelection();
            run.run();
        });
        button.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                selected = actions.indexOf(button);
                updateSelection();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.bottomMargin = dp(6);
        card.addView(button, params);
        actions.add(button);
    }

    private void updateSelection() {
        for (int i = 0; i < actions.size(); i++) P4gTheme.button(actions.get(i), i == selected);
    }

    private void moveSelection(int direction) {
        if (actions.isEmpty()) return;
        selected = (selected + direction + actions.size()) % actions.size();
        updateSelection();
        actions.get(selected).requestFocus();
    }

    private void resumeGame() { finish(); }

    private void showDisplayOptions() {
        new P4gDialog.Builder(this).setTitle("PANTALLA")
                .setItems(new String[] {"Resolución", "Relación de aspecto"}, (dialog, which) -> {
                    if (which == 0) choose("RESOLUTION", "Resolución de salida",
                            new String[] {"360p", "480p", "720p", "1080p"});
                    else choose("NX_STRETCH", "Relación de aspecto",
                            new String[] {"0", "hor+", "16:10", "1"},
                            new String[] {"16:9", "HOR+", "16:10", "Estirar"});
                }).show();
    }

    private void showPerformanceOptions() {
        new P4gDialog.Builder(this).setTitle("RENDIMIENTO")
                .setItems(new String[] {"Límite de FPS", "Filtración anisotrópica", "Escalado FSR"}, (dialog, which) -> {
                    if (which == 0) choose("NX_FPS_CAP", "Límite de FPS",
                            new String[] {"30", "45", "60"}, new String[] {"30 FPS", "45 FPS", "60 FPS"});
                    else if (which == 1) choose("NX_MAX_ANISO", "Filtración anisotrópica",
                            new String[] {"1", "2", "4", "8", "16"},
                            new String[] {"Desactivada", "2x", "4x", "8x", "16x"});
                    else choose("NX_FSR", "Escalado FSR", new String[] {"0", "1"},
                            new String[] {"Desactivado", "Activado"});
                }).show();
    }

    private void choose(String key, String title, String[] values) { choose(key, title, values, values); }

    private void choose(String key, String title, String[] values, String[] labels) {
        int selectedValue = "RESOLUTION".equals(key) ? 2 : 0;
        String current = config.get(key);
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selectedValue = i;
        new P4gDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selectedValue, (dialog, which) -> {
            config.put(key, values[which]);
            if ("RESOLUTION".equals(key)) Launcher.putResolution(config, values[which]);
            if (Launcher.writeConfig(new File(Launcher.dataDir(), "config.txt"), config)) {
                Toast.makeText(this, "Guardado. Se aplicará al volver a abrir el juego.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No se pudo guardar config.txt.", Toast.LENGTH_LONG).show();
            }
            dialog.dismiss();
        }).show();
    }

    private void openControls() {
        new P4gDialog.Builder(this).setTitle("CONTROLES")
                .setItems(new String[] {"Posición de controles táctiles", "Asignación de botones del mando"},
                        (dialog, which) -> startActivity(new Intent(this,
                                which == 0 ? ControlsEditor.class : GamepadEditorActivity.class)))
                .show();
    }

    private void showSystemInfo() {
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String version = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        String abi = Build.SUPPORTED_ABIS.length == 0 ? "desconocida" : Build.SUPPORTED_ABIS[0];
        new P4gDialog.Builder(this).setTitle("INFORMACIÓN DEL SISTEMA")
                .setMessage(model + "\n" + version + "\nABI: " + abi + "\n\nPersona 4 Golden Android Plus")
                .setPositiveButton("CERRAR", null).show();
    }

    private void confirmExit() {
        new P4gDialog.Builder(this).setTitle("¿Salir del juego?")
                .setMessage("El progreso que no se haya guardado en el juego podría perderse.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("SALIR", (dialog, which) -> {
                    Intent intent = new Intent(this, Launcher.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra("returned_to_menu", true);
                    startActivity(intent);
                    finish();
                }).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (event.getRepeatCount() == 0) moveSelection(-1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getRepeatCount() == 0) moveSelection(1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_BUTTON_START) {
            if (event.getRepeatCount() == 0) actions.get(selected).performClick();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_B || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getRepeatCount() == 0) resumeGame();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        if ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && event.getAction() == MotionEvent.ACTION_MOVE) {
            float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(y) < .5f) y = event.getAxisValue(MotionEvent.AXIS_Y);
            long now = SystemClock.uptimeMillis();
            if (Math.abs(y) > .65f && now - lastAxisMove > 240) {
                moveSelection(y < 0 ? -1 : 1);
                lastAxisMove = now;
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override public void onBackPressed() { resumeGame(); }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }
}
