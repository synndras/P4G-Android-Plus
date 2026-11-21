package io.github.p4grecomp;

import android.app.Activity;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.system.Os;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reassigns Android gamepad buttons to P4G's real Npad input bits. */
public final class GamepadEditorActivity extends Activity implements InputManager.InputDeviceListener {
    private static final String MAP_KEY = "NX_GAMEPAD_MAP";
    private static final String[] ACTIONS = {"A", "B", "X", "Y", "L", "R", "ZL", "ZR", "+", "−", "L3", "R3",
            "D-pad izquierda", "D-pad arriba", "D-pad derecha", "D-pad abajo"};
    private static final int[] KEY_CHOICES = {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN
    };

    private final List<Button> rows = new ArrayList<>();
    private InputManager inputManager;
    private TextView deviceStatus, captureStatus;
    private int[] mapping;
    private int captureAction = -1;
    private int selectedRow;
    private long lastAxisMove;

    private int dp(int value) { return P4gTheme.dp(this, value); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        P4gTheme.apply(this);
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) inputManager.registerInputDeviceListener(this, null);
        Map<String, String> config = Launcher.readConfig(new File(Launcher.dataDir(), "config.txt"));
        mapping = readMapping(config);
        buildScreen();
    }

    @Override protected void onDestroy() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(P4gTheme.BACKGROUND);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        TextView title = new TextView(this);
        title.setText("CONTROLES · MANDO FÍSICO");
        title.setTextColor(P4gTheme.YELLOW);
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        deviceStatus = new TextView(this);
        deviceStatus.setTextColor(P4gTheme.MUTED);
        deviceStatus.setTextSize(14);
        deviceStatus.setPadding(0, dp(6), 0, dp(4));
        root.addView(deviceStatus, new LinearLayout.LayoutParams(-1, -2));
        captureStatus = new TextView(this);
        captureStatus.setText("Selecciona una acción y pulsa el botón del mando que quieras asignar.");
        captureStatus.setTextColor(P4gTheme.TEXT);
        captureStatus.setTextSize(14);
        captureStatus.setPadding(0, dp(4), 0, dp(10));
        root.addView(captureStatus, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < ACTIONS.length; i++) {
            final int action = i;
            Button row = new Button(this);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setPadding(dp(14), 0, dp(12), 0);
            row.setTextSize(14);
            row.setOnClickListener(v -> beginCapture(action));
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused) { selectedRow = action; updateRows(); }
            });
            rows.add(row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
            params.bottomMargin = dp(4);
            list.addView(row, params);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, 0);
        Button restore = actionButton("RESTAURAR PREDETERMINADOS");
        Button close = actionButton("CERRAR");
        footer.addView(restore, new LinearLayout.LayoutParams(0, dp(48), 1));
        footer.addView(close, new LinearLayout.LayoutParams(dp(120), dp(48)));
        root.addView(footer);
        restore.setOnClickListener(v -> restoreDefaults());
        close.setOnClickListener(v -> finish());
        setContentView(root);
        updateRows();
        updateDeviceStatus();
        rows.get(0).requestFocus();
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        P4gTheme.button(button, false);
        return button;
    }

    private void updateRows() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setText(ACTIONS[i] + "     ·     " + keyName(mapping[i])
                    + (captureAction == i ? "     ·     PULSA AHORA" : ""));
            P4gTheme.button(rows.get(i), i == selectedRow || captureAction == i);
        }
    }

    private void beginCapture(int action) {
        captureAction = action;
        selectedRow = action;
        captureStatus.setText("Pulsa el botón físico que quieres asignar a «" + ACTIONS[action] + "».");
        updateRows();
    }

    private void capture(int keyCode) {
        int key = canonicalGamepadKey(keyCode);
        if (!knownGamepadKey(key)) {
            Toast.makeText(this, "Ese control no se puede asignar a una acción de botón.", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] previous = mapping.clone();
        int old = mapping[captureAction];
        for (int i = 0; i < mapping.length; i++) if (i != captureAction && mapping[i] == key) mapping[i] = old;
        mapping[captureAction] = key;
        captureAction = -1;
        if (persist(false)) captureStatus.setText("Asignación guardada; se aplicará al reanudar o iniciar el juego.");
        else mapping = previous;
        updateRows();
    }

    private void restoreDefaults() {
        mapping = defaultMapping(Launcher.readConfig(new File(Launcher.dataDir(), "config.txt")));
        captureAction = -1;
        if (persist(true)) captureStatus.setText("Asignación predeterminada restaurada.");
        updateRows();
    }

    private boolean persist(boolean defaults) {
        File file = new File(Launcher.dataDir(), "config.txt");
        Map<String, String> config = Launcher.readConfig(file);
        if (defaults) config.remove(MAP_KEY);
        else config.put(MAP_KEY, encode(mapping));
        if (!Launcher.writeConfig(file, config)) {
            Toast.makeText(this, "No se pudo guardar config.txt.", Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            // Keep the running :game process in sync; a missing config key still
            // selects the layout-based default on the next launch.
            Os.setenv(MAP_KEY, encode(mapping), true);
        } catch (Exception ignored) {
            // The config file remains authoritative for the next game launch.
        }
        return true;
    }

    private int[] readMapping(Map<String, String> config) {
        String saved = config.get(MAP_KEY);
        if (saved != null) {
            String[] fields = saved.split(",", -1);
            if (fields.length == ACTIONS.length) {
                int[] result = new int[fields.length];
                boolean valid = true;
                try {
                    for (int i = 0; i < fields.length; i++) {
                        result[i] = canonicalGamepadKey(Integer.parseInt(fields[i]));
                        if (!knownGamepadKey(result[i])) valid = false;
                        for (int j = 0; j < i; j++) if (result[i] == result[j]) valid = false;
                    }
                } catch (NumberFormatException e) { valid = false; }
                if (valid) return result;
            }
        }
        return defaultMapping(config);
    }

    private int[] defaultMapping(Map<String, String> config) {
        boolean xbox = !"0".equals(config.get("NX_GAMEPAD_LAYOUT"));
        return new int[] {xbox ? KeyEvent.KEYCODE_BUTTON_A : KeyEvent.KEYCODE_BUTTON_B,
                xbox ? KeyEvent.KEYCODE_BUTTON_B : KeyEvent.KEYCODE_BUTTON_A,
                xbox ? KeyEvent.KEYCODE_BUTTON_X : KeyEvent.KEYCODE_BUTTON_Y,
                xbox ? KeyEvent.KEYCODE_BUTTON_Y : KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN};
    }

    private int canonicalGamepadKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_1: return KeyEvent.KEYCODE_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_2: return KeyEvent.KEYCODE_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_3: return KeyEvent.KEYCODE_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_4: return KeyEvent.KEYCODE_BUTTON_X;
            case KeyEvent.KEYCODE_BUTTON_5: case KeyEvent.KEYCODE_BUTTON_C: return KeyEvent.KEYCODE_BUTTON_L2;
            case KeyEvent.KEYCODE_BUTTON_6: case KeyEvent.KEYCODE_BUTTON_Z: return KeyEvent.KEYCODE_BUTTON_R2;
            case KeyEvent.KEYCODE_BUTTON_7: return KeyEvent.KEYCODE_BUTTON_L1;
            case KeyEvent.KEYCODE_BUTTON_8: return KeyEvent.KEYCODE_BUTTON_R1;
            case KeyEvent.KEYCODE_BUTTON_9: return KeyEvent.KEYCODE_BUTTON_SELECT;
            case KeyEvent.KEYCODE_BUTTON_10: case KeyEvent.KEYCODE_BUTTON_MODE: return KeyEvent.KEYCODE_BUTTON_START;
            case KeyEvent.KEYCODE_BUTTON_11: return KeyEvent.KEYCODE_BUTTON_THUMBL;
            case KeyEvent.KEYCODE_BUTTON_12: return KeyEvent.KEYCODE_BUTTON_THUMBR;
            default: return key;
        }
    }

    private boolean knownGamepadKey(int key) {
        for (int choice : KEY_CHOICES) if (key == choice) return true;
        return false;
    }

    private String keyName(int key) {
        return KeyEvent.keyCodeToString(key).replace("KEYCODE_BUTTON_", "")
                .replace("KEYCODE_DPAD_", "D-pad ").replace("KEYCODE_", "");
    }

    private String encode(int[] values) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i != 0) value.append(',');
            value.append(values[i]);
        }
        return value.toString();
    }

    private void updateDeviceStatus() {
        String name = null;
        if (inputManager != null) for (int id : inputManager.getInputDeviceIds()) {
            InputDevice device = inputManager.getInputDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                name = device.getName();
                break;
            }
        }
        deviceStatus.setText(name == null ? "MANDO: no detectado · Bluetooth o USB" : "MANDO CONECTADO: " + name);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (captureAction >= 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) capture(event.getKeyCode());
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                selectedRow = (selectedRow + 1) % rows.size();
                rows.get(selectedRow).requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_ENTER
                    || key == KeyEvent.KEYCODE_DPAD_CENTER) {
                rows.get(selectedRow).performClick();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_LEFT) {
                selectedRow = (selectedRow + rows.size() - 1) % rows.size();
                rows.get(selectedRow).requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_BUTTON_B || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
                finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        if ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && event.getAction() == MotionEvent.ACTION_MOVE) {
            float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(y) < .5f) y = event.getAxisValue(MotionEvent.AXIS_Y);
            long now = android.os.SystemClock.uptimeMillis();
            if (Math.abs(y) > .65f && now - lastAxisMove > 240) {
                selectedRow = (selectedRow + (y > 0 ? 1 : rows.size() - 1)) % rows.size();
                rows.get(selectedRow).requestFocus();
                lastAxisMove = now;
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override public void onInputDeviceAdded(int id) { updateDeviceStatus(); }
    @Override public void onInputDeviceRemoved(int id) { updateDeviceStatus(); }
    @Override public void onInputDeviceChanged(int id) { updateDeviceStatus(); }
}
