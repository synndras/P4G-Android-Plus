package io.github.p4grecomp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P4G comic-style pause overlay; the game is held at its next Vulkan present underneath. */
public final class PauseActivity extends Activity {
    private static final String[] CATEGORY_KEYS = {"video", "performance", "effects", "interface", "controls", "system"};
    private static final String[] CATEGORY_TITLES = {"VIDEO", "RENDIMIENTO", "EFECTOS", "INTERFAZ", "CONTROLES", "SISTEMA"};
    private static final String[] CATEGORY_HINTS = {
            "Resolución y formato de pantalla", "Límite de cuadros y filtrado", "Escalado y presentación de imagen",
            "Overlay e indicadores", "Controles táctiles y mando", "Información de esta sesión"
    };
    private final List<P4gPauseCategoryButton> categories = new ArrayList<>();
    private final List<View> activeControls = new ArrayList<>();
    private final Map<String, String> config = new LinkedHashMap<>();
    private final Map<SeekBar, SliderSpec> sliders = new LinkedHashMap<>();
    private final Map<View, Runnable> adjustLeft = new LinkedHashMap<>();
    private final Map<View, Runnable> adjustRight = new LinkedHashMap<>();
    private int selectedCategory;
    private int selectedControl;
    private long lastAxisMove;
    private LinearLayout optionRows;
    private ScrollView optionScroll;
    private TextView panelTitle;
    private TextView panelHint;
    private P4gPausePanelDrawable panelArtwork;

    private static final class SliderSpec {
        final String key;
        final int min, step;
        final String suffix;
        final TextView value;
        SliderSpec(String key, int min, int step, String suffix, TextView value) {
            this.key = key; this.min = min; this.step = step; this.suffix = suffix; this.value = value;
        }
        int value(int progress) { return min + progress * step; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureOverlayWindow();
        immersive();
        config.putAll(Launcher.readConfig(new File(Launcher.dataDir(), "config.txt")));
        buildMenu();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void configureOverlayWindow() {
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = Build.VERSION.SDK_INT >= 31 ? .34f : .48f;
        if (Build.VERSION.SDK_INT >= 31) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            attributes.setBlurBehindRadius(dp(14));
        }
        window.setAttributes(attributes);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void buildMenu() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x22080603);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(14), dp(10), dp(14), dp(8));

        shell.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(68)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(0, dp(3), 0, dp(5));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        contentParams.topMargin = dp(4);
        contentParams.bottomMargin = dp(3);
        shell.addView(content, contentParams);

        LinearLayout categoryRail = buildCategoryRail();
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(0, -1, 1f);
        railParams.rightMargin = dp(8);
        content.addView(categoryRail, railParams);

        FrameLayout panel = buildOptionsPanel();
        content.addView(panel, new LinearLayout.LayoutParams(0, -1, 3f));
        shell.addView(buildFooter(), new LinearLayout.LayoutParams(-1, dp(32)));

        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        selectCategory(0);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        P4gLogoView logo = new P4gLogoView(this);
        header.addView(logo, new LinearLayout.LayoutParams(dp(184), dp(60)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        TextView paused = text("EN PAUSA", 17, P4gTheme.YELLOW, true);
        identity.addView(paused);
        TextView hint = text("Ajustes guardados · se aplican al reabrir el juego", 10, 0xFFE9E1CA, false);
        hint.setSingleLine(true);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(hint);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -1, 1f);
        identityParams.leftMargin = dp(10);
        header.addView(identity, identityParams);

        P4gPauseActionButton exit = new P4gPauseActionButton(this, false);
        exit.setText("↩  SALIR");
        exit.setContentDescription("Salir del juego");
        exit.setOnClickListener(v -> confirmExit());
        LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(dp(104), dp(46));
        exitParams.rightMargin = dp(8);
        header.addView(exit, exitParams);

        P4gPauseActionButton resume = new P4gPauseActionButton(this, true);
        resume.setText("▶  CONTINUAR");
        resume.setContentDescription("Continuar la partida");
        resume.setOnClickListener(v -> resumeGame());
        header.addView(resume, new LinearLayout.LayoutParams(dp(142), dp(46)));
        return header;
    }

    private LinearLayout buildCategoryRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(6), dp(5), dp(6), dp(5));
        rail.setBackground(new P4gNavBackdrop());
        for (int i = 0; i < CATEGORY_TITLES.length; i++) {
            final int index = i;
            P4gPauseCategoryButton button = new P4gPauseCategoryButton(this);
            button.setText(CATEGORY_TITLES[i]);
            button.setContentDescription(CATEGORY_TITLES[i] + ": " + CATEGORY_HINTS[i]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1f);
            params.bottomMargin = dp(3);
            rail.addView(button, params);
            categories.add(button);
            button.setOnClickListener(v -> selectCategory(index));
        }
        return rail;
    }

    private FrameLayout buildOptionsPanel() {
        FrameLayout panel = new FrameLayout(this);
        panelArtwork = new P4gPausePanelDrawable(this);
        panel.setBackground(panelArtwork);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(13), dp(22), dp(13));
        panelTitle = text("VIDEO", 24, P4gTheme.INK, true);
        panelTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        content.addView(panelTitle, new LinearLayout.LayoutParams(-1, -2));
        panelHint = text("Resolución y formato de pantalla", 11, 0xFF332B12, false);
        content.addView(panelHint, new LinearLayout.LayoutParams(-1, -2));
        View underline = new View(this);
        underline.setBackgroundColor(P4gTheme.INK);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(74), dp(3));
        lineParams.topMargin = dp(5);
        lineParams.bottomMargin = dp(7);
        content.addView(underline, lineParams);

        optionScroll = new ScrollView(this);
        optionScroll.setFillViewport(false);
        optionScroll.setClipToPadding(false);
        optionScroll.setVerticalScrollBarEnabled(false);
        optionRows = new LinearLayout(this);
        optionRows.setOrientation(LinearLayout.VERTICAL);
        optionScroll.addView(optionRows, new ScrollView.LayoutParams(-1, -2));
        content.addView(optionScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        panel.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return panel;
    }

    private View buildFooter() {
        TextView footer = text("L1/R1  CATEGORÍAS    ·    ↑/↓  OPCIONES    ·    ←/→  CAMBIAR    ·    A  SELECCIONAR    ·    B  REANUDAR    ·    X  SALIR",
                10, P4gTheme.YELLOW, true);
        footer.setGravity(Gravity.CENTER);
        footer.setSingleLine(true);
        footer.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footer.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE8110F0A);
        bg.setStroke(dp(1), P4gTheme.YELLOW);
        footer.setBackground(bg);
        return footer;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    private void selectCategory(int index) {
        if (CATEGORY_TITLES.length == 0) return;
        selectedCategory = (index + CATEGORY_TITLES.length) % CATEGORY_TITLES.length;
        for (int i = 0; i < categories.size(); i++) categories.get(i).setCategorySelected(i == selectedCategory);
        panelTitle.setText(CATEGORY_TITLES[selectedCategory]);
        panelHint.setText(CATEGORY_HINTS[selectedCategory]);
        int[] accents = {P4gTheme.YELLOW, 0xFFFFE661, 0xFFFFA828, 0xFF57C6D8, 0xFF75D28D, 0xFFFFFFFF};
        panelArtwork.setAccent(accents[selectedCategory]);
        optionRows.removeAllViews();
        activeControls.clear();
        sliders.clear();
        adjustLeft.clear();
        adjustRight.clear();
        selectedControl = 0;
        optionScroll.scrollTo(0, 0);
        switch (CATEGORY_KEYS[selectedCategory]) {
            case "video": buildVideoOptions(); break;
            case "performance": buildPerformanceOptions(); break;
            case "effects": buildEffectsOptions(); break;
            case "interface": buildInterfaceOptions(); break;
            case "controls": buildControlsOptions(); break;
            case "system": buildSystemInfo(); break;
            default: break;
        }
        updateControlSelection();
    }

    private void buildVideoOptions() {
        addSelector("Resolución interna", "Selecciona una resolución disponible", "RESOLUTION",
                new String[] {"360p", "480p", "720p", "1080p"}, new String[] {"360p", "480p", "720p", "1080p"}, "720p");
        addSelector("Relación de aspecto", "Formato de imagen del juego", "NX_STRETCH",
                new String[] {"0", "hor+", "16:10", "1"}, new String[] {"16:9", "HOR+", "16:10", "Estirar"}, "0");
    }

    private void buildPerformanceOptions() {
        addSelector("Límite de cuadros", "Frecuencia máxima de presentación", "NX_FPS_CAP",
                new String[] {"30", "45", "60"}, new String[] {"30 FPS", "45 FPS", "60 FPS"}, "30");
        addSelector("Filtrado anisotrópico", "Calidad del filtrado de texturas", "NX_MAX_ANISO",
                new String[] {"1", "2", "4", "8", "16"}, new String[] {"Desactivado", "2×", "4×", "8×", "16×"}, "4");
        addToggle("Registro detallado de rendimiento", "Escribe métricas de diagnóstico", "NX_TRACE_PERF", "0");
    }

    private void buildEffectsOptions() {
        addToggle("Escalado AMD FSR", "Activa el escalado espacial de imagen", "NX_FSR", "0");
        TextView note = text("Los cambios gráficos se guardan en config.txt y se aplican al volver a abrir el juego.",
                12, 0xFF332B12, false);
        note.setPadding(dp(2), dp(9), dp(12), dp(4));
        optionRows.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildInterfaceOptions() {
        addToggle("Contador de FPS", "Muestra los cuadros por segundo", "NX_SHOW_FPS", "0");
        addToggle("Uso de CPU", "Incluye CPU en el monitor de rendimiento", "NX_OVERLAY_CPU", "0");
        addToggle("Uso de GPU", "Incluye GPU cuando el controlador lo permite", "NX_OVERLAY_GPU", "0");
        addToggle("Memoria RAM", "Incluye memoria en el monitor", "NX_OVERLAY_RAM", "0");
        addSlider("Tamaño del monitor", "Escala de los indicadores en pantalla", "NX_OVERLAY_SIZE", 50, 150, 5, "%", 100);
    }

    private void buildControlsOptions() {
        addToggle("Controles táctiles", "Muestra el panel táctil durante el juego", "NX_TOUCH_SHOW", "1");
        addSlider("Tamaño de botones", "Escala de los botones táctiles", "NX_TOUCH_SIZE", 50, 160, 5, "%", 100);
        addSlider("Opacidad táctil", "Brillo de los controles superpuestos", "NX_TOUCH_OPACITY", 10, 100, 5, "%", 85);
        addSlider("Sensibilidad del stick", "Respuesta del stick táctil", "NX_STICK_SENSITIVITY", 25, 300, 5, "%", 100);
        addSlider("Sensibilidad de cámara", "Respuesta de la cámara táctil", "NX_CAMERA_SENSITIVITY", 25, 300, 5, "%", 100);
        addSelector("Modo del stick izquierdo", "Fijo o relativo al tocar", "NX_TOUCH_LEFT_STICK_MODE",
                new String[] {"fixed", "floating"}, new String[] {"Fijo", "Flotante"}, "fixed");
        addSelector("Distribución del mando", "Posición de los botones A/B/X/Y", "NX_GAMEPAD_LAYOUT",
                new String[] {"0", "1"}, new String[] {"Nintendo", "Xbox"}, "1");
        addAction("Editar controles táctiles", "Ajusta posición y tamaño de cada botón", "EDITAR", () ->
                startActivity(new Intent(this, ControlsEditor.class)));
        addAction("Asignar botones del mando", "Configura la asignación del gamepad", "ASIGNAR", () ->
                startActivity(new Intent(this, GamepadEditorActivity.class)));
    }

    private void buildSystemInfo() {
        addInfoRow("DISPOSITIVO", Build.MANUFACTURER + " " + Build.MODEL);
        addInfoRow("ANDROID", Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT);
        addInfoRow("ARQUITECTURA", Build.SUPPORTED_ABIS.length == 0 ? "Desconocida" : Build.SUPPORTED_ABIS[0]);
        addInfoRow("JUEGO", "Persona 4 Golden · P4GP");
    }

    private void addInfoRow(String label, String value) {
        LinearLayout row = createRow(label, "Información de esta sesión");
        TextView detail = text(value, 13, P4gTheme.INK, true);
        detail.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(detail, new LinearLayout.LayoutParams(dp(190), dp(42)));
        addRowToPanel(row);
    }

    private LinearLayout createRow(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(2), dp(7), dp(2));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 14, P4gTheme.INK, true);
        words.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView detail = text(subtitle, 10, 0xFF403718, false);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        row.addView(words, new LinearLayout.LayoutParams(0, -1, 1f));
        return row;
    }

    private void addSelector(String title, String hint, String key, String[] values, String[] labels, String fallback) {
        int index = indexFor(key, values, fallback);
        LinearLayout row = createRow(title, hint);
        P4gPauseValueButton value = new P4gPauseValueButton(this);
        value.setText(labels[index]);
        value.setContentDescription(title + ": " + labels[index] + ". Usa izquierda o derecha para cambiar.");
        row.addView(value, new LinearLayout.LayoutParams(dp(154), dp(38)));
        Runnable next = () -> cycleSelector(key, values, labels, fallback, value, 1);
        adjustLeft.put(value, () -> cycleSelector(key, values, labels, fallback, value, -1));
        adjustRight.put(value, next);
        value.setOnFocusChangeListener((v, focused) -> {
            if (focused) selectedControl = activeControls.indexOf(value);
            value.setSelected(focused || activeControls.indexOf(value) == selectedControl);
        });
        value.setOnClickListener(v -> {
            selectedControl = activeControls.indexOf(value);
            next.run();
            updateControlSelection();
        });
        registerControl(value);
        addRowToPanel(row);
    }

    private int indexFor(String key, String[] values, String fallback) {
        String current = config.get(key);
        if (current == null || current.length() == 0) current = fallback;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) return i;
        return 0;
    }

    private void cycleSelector(String key, String[] values, String[] labels, String fallback,
                               P4gPauseValueButton button, int direction) {
        int index = indexFor(key, values, fallback);
        index = (index + direction + values.length) % values.length;
        button.setText(labels[index]);
        button.setContentDescription(key + ": " + labels[index]);
        saveOption(key, values[index]);
    }

    private void addToggle(String title, String hint, String key, String fallback) {
        LinearLayout row = createRow(title, hint);
        P4gPauseValueButton toggle = new P4gPauseValueButton(this);
        updateToggleLabel(toggle, key, fallback);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(118), dp(38)));
        Runnable flip = () -> {
            String current = config.get(key);
            boolean enabled = current == null ? "1".equals(fallback) : "1".equals(current);
            saveOption(key, enabled ? "0" : "1");
            updateToggleLabel(toggle, key, fallback);
        };
        toggle.setOnClickListener(v -> {
            selectedControl = activeControls.indexOf(toggle);
            flip.run();
            updateControlSelection();
        });
        adjustLeft.put(toggle, flip);
        adjustRight.put(toggle, flip);
        toggle.setOnFocusChangeListener((v, focused) -> {
            if (focused) selectedControl = activeControls.indexOf(toggle);
            toggle.setSelected(focused || activeControls.indexOf(toggle) == selectedControl);
        });
        registerControl(toggle);
        addRowToPanel(row);
    }

    private void updateToggleLabel(P4gPauseValueButton toggle, String key, String fallback) {
        String current = config.get(key);
        boolean enabled = current == null ? "1".equals(fallback) : "1".equals(current);
        toggle.setText(enabled ? "ACTIVADO" : "DESACTIVADO");
        toggle.setContentDescription(key + (enabled ? " activado" : " desactivado"));
    }

    private void addSlider(String title, String hint, String key, int min, int max, int step,
                           String suffix, int fallback) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(2), dp(3), dp(8), dp(2));
        LinearLayout row = createRow(title, hint);
        TextView value = text("", 14, P4gTheme.INK, true);
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(dp(58), dp(30)));
        outer.addView(row, new LinearLayout.LayoutParams(-1, dp(32)));

        P4gPauseSeekBar seek = new P4gPauseSeekBar(this);
        int steps = (max - min) / step;
        seek.setMax(steps);
        int current = parseInteger(config.get(key), fallback);
        current = Math.max(min, Math.min(max, current));
        seek.setProgress(Math.round((current - min) / (float) step));
        SliderSpec spec = new SliderSpec(key, min, step, suffix, value);
        sliders.put(seek, spec);
        value.setText(current + suffix);
        outer.addView(seek, new LinearLayout.LayoutParams(-1, dp(32)));
        registerControl(seek);
        seek.setOnFocusChangeListener((v, focused) -> {
            if (focused) selectedControl = activeControls.indexOf(seek);
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean dragging;
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int number = spec.value(progress);
                spec.value.setText(number + spec.suffix);
                if (fromUser && !dragging) saveOption(spec.key, Integer.toString(number));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                saveOption(spec.key, Integer.toString(spec.value(bar.getProgress())));
            }
        });
        addRowToPanel(outer);
    }

    private int parseInteger(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void addAction(String title, String hint, String label, Runnable action) {
        LinearLayout row = createRow(title, hint);
        P4gPauseValueButton button = new P4gPauseValueButton(this);
        button.setText(label);
        button.setContentDescription(title);
        row.addView(button, new LinearLayout.LayoutParams(dp(154), dp(38)));
        button.setOnClickListener(v -> {
            selectedControl = activeControls.indexOf(button);
            updateControlSelection();
            action.run();
        });
        button.setOnFocusChangeListener((v, focused) -> {
            if (focused) selectedControl = activeControls.indexOf(button);
            button.setSelected(focused || activeControls.indexOf(button) == selectedControl);
        });
        registerControl(button);
        addRowToPanel(row);
    }

    private void addRowToPanel(View row) {
        optionRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View separator = new View(this);
        separator.setBackgroundColor(0x66120F08);
        LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(-1, dp(1));
        rule.topMargin = dp(2);
        rule.bottomMargin = dp(2);
        optionRows.addView(separator, rule);
    }

    private void registerControl(View view) {
        activeControls.add(view);
        view.setFocusable(true);
    }

    private boolean saveOption(String key, String value) {
        Map<String, String> previous = new LinkedHashMap<>(config);
        config.put(key, value);
        if ("RESOLUTION".equals(key)) Launcher.putResolution(config, value);
        if (!Launcher.writeConfig(new File(Launcher.dataDir(), "config.txt"), config)) {
            config.clear();
            config.putAll(previous);
            Toast.makeText(this, "No se pudo guardar config.txt.", Toast.LENGTH_LONG).show();
            return false;
        }
        if ("NX_TOUCH_SIZE".equals(key) || "NX_TOUCH_OPACITY".equals(key)
                || "NX_STICK_SENSITIVITY".equals(key) || "NX_CAMERA_SENSITIVITY".equals(key)) {
            Toast.makeText(this, "Guardado · se aplicará al volver a abrir el juego.", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void updateControlSelection() {
        if (activeControls.isEmpty()) { selectedControl = 0; return; }
        selectedControl = Math.max(0, Math.min(selectedControl, activeControls.size() - 1));
        for (int i = 0; i < activeControls.size(); i++) {
            View control = activeControls.get(i);
            boolean selected = i == selectedControl;
            control.setSelected(selected);
            if (control instanceof P4gPauseValueButton) ((P4gPauseValueButton) control).setSelected(selected);
        }
        View current = activeControls.get(selectedControl);
        current.requestFocus();
        optionScroll.post(() -> optionScroll.requestChildFocus(current, current));
    }

    private void moveControl(int direction) {
        if (activeControls.isEmpty()) return;
        selectedControl = (selectedControl + direction + activeControls.size()) % activeControls.size();
        updateControlSelection();
    }

    private View currentControl() {
        if (activeControls.isEmpty()) return null;
        selectedControl = Math.max(0, Math.min(selectedControl, activeControls.size() - 1));
        return activeControls.get(selectedControl);
    }

    private void adjustCurrentControl(int direction) {
        View control = currentControl();
        if (control instanceof SeekBar) {
            SeekBar seek = (SeekBar) control;
            SliderSpec spec = sliders.get(seek);
            if (spec == null) return;
            seek.setProgress(Math.max(0, Math.min(seek.getMax(), seek.getProgress() + direction)));
            saveOption(spec.key, Integer.toString(spec.value(seek.getProgress())));
        } else {
            Runnable adjust = (direction < 0 ? adjustLeft : adjustRight).get(control);
            if (adjust != null) adjust.run();
        }
    }

    private void confirmExit() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(18));
        box.setBackground(new P4gPausePanelDrawable(this));
        TextView title = text("¿SALIR DEL JUEGO?", 21, P4gTheme.INK, true);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        box.addView(title);
        TextView warning = text("El progreso que todavía no se haya guardado podría perderse.", 13, 0xFF332B12, false);
        warning.setPadding(0, dp(6), 0, dp(14));
        box.addView(warning);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        P4gPauseActionButton cancel = new P4gPauseActionButton(this, false);
        cancel.setText("SEGUIR JUGANDO");
        P4gPauseActionButton confirm = new P4gPauseActionButton(this, true);
        confirm.setText("SALIR");
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(150), dp(44));
        cancelParams.rightMargin = dp(8);
        actions.addView(cancel, cancelParams);
        actions.addView(confirm, new LinearLayout.LayoutParams(dp(100), dp(44)));
        box.addView(actions);
        dialog.setContentView(box);
        dialog.setCancelable(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, Launcher.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("returned_to_menu", true);
            startActivity(intent);
            finish();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = .46f;
            window.setAttributes(lp);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(30), dp(500)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void resumeGame() { finish(); }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_L2) {
            if (event.getRepeatCount() == 0) selectCategory(selectedCategory - 1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_R1 || key == KeyEvent.KEYCODE_BUTTON_R2) {
            if (event.getRepeatCount() == 0) selectCategory(selectedCategory + 1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (event.getRepeatCount() == 0) moveControl(-1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.getRepeatCount() == 0) moveControl(1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getRepeatCount() == 0) adjustCurrentControl(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (event.getRepeatCount() == 0) {
                View control = currentControl();
                if (control != null && !(control instanceof SeekBar)) control.performClick();
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_B || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getRepeatCount() == 0) resumeGame();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_START) {
            if (event.getRepeatCount() == 0) resumeGame();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_X || key == KeyEvent.KEYCODE_BUTTON_SELECT) {
            if (event.getRepeatCount() == 0) confirmExit();
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
                moveControl(y < 0 ? -1 : 1);
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
