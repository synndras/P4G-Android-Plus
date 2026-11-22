package io.github.p4grecomp;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Routes to preparation or the main menu after checking the actual files. */
public class Launcher extends Activity {
    static final String DATA = "Persona 4 Golden";
    static final boolean DEFAULT_LANDSCAPE = true;

    static final class Option {
        final String key, pt, en;
        final String[] values, labelsPt, labelsEn;
        int pick;
        final int defaultPick;

        Option(String key, String pt, String en, String[] values, String[] labelsPt, String[] labelsEn, int pick) {
            this.key = key;
            this.pt = pt;
            this.en = en;
            this.values = values;
            this.labelsPt = labelsPt;
            this.labelsEn = labelsEn;
            this.pick = pick;
            this.defaultPick = pick;
        }

        void select(String value) {
            pick = defaultPick;
            for (int i = 0; i < values.length; i++) if (values[i].equals(value)) pick = i;
        }
    }

    final List<Option> options = new ArrayList<>();
    final Map<String, Boolean> drawerStates = new LinkedHashMap<>();
    final Map<String, View> sectionCards = new LinkedHashMap<>();
    final Map<String, LinearLayout> sectionBodies = new LinkedHashMap<>();
    final Map<String, Button> sectionHeaders = new LinkedHashMap<>();
    final Map<String, Button> navButtons = new LinkedHashMap<>();
    final Map<String, P4gQuickButton> quickButtons = new LinkedHashMap<>();
    final Map<String, String> config = new LinkedHashMap<>();
    TextView status;
    Button play;
    P4gQuickButton updateNotice;
    String updateReleaseUrl;
    int updateCheckGeneration;
    ScrollView launcherScroll;
    P4gBackgroundView backgroundScene;
    View homePanel;
    P4gScrollRail scrollRail;
    String selectedNav = "home";
    private static final String[] CATEGORY_NAV_ORDER = {"home", "display", "overlay", "controls", "content", "more"};
    static final int PICK_ZIP = 1, PICK_FOLDER = 2, PICK_GAME = 3, PICK_KEYS = 4, PREPARE = 6, PICK_CPK = 7,
            PICK_DRIVER = 8, PICK_UPDATE = 9;

    String t(String portuguese, String english) { return Lang.t(portuguese, english); }

    String label(Option o) { return t(o.labelsPt[o.pick], o.labelsEn[o.pick]); }

    static File dataDir() { return new File(Environment.getExternalStorageDirectory(), DATA); }

    static File installationDir(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) throw new IllegalStateException("App storage is unavailable. Try again after storage is mounted.");
        return dir;
    }

    static final class Api30Helper {
        static boolean isExternalStorageManager() {
            return Environment.isExternalStorageManager();
        }

        static int[] safeArea(WindowInsets insets) {
            android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[] {safe.left, safe.top, safe.right, safe.bottom};
        }
    }

    boolean hasAccess() {
        if (Build.VERSION.SDK_INT < 30)
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return Api30Helper.isExternalStorageManager();
    }

    // ---- source game file (needed only while installing the local game data) ----

    SharedPreferences prefs() { return getSharedPreferences("game", MODE_PRIVATE); }

    String gameUri() { return prefs().getString("uri", null); }

    String gameName() { return prefs().getString("name", ""); }

    String updateUri() { return prefs().getString("update_uri", null); }

    String updateName() { return prefs().getString("update_name", ""); }

    static File keysFile(Context c, String name) { return new File(new File(c.getFilesDir(), "keys"), name); }

    boolean hasKeys() { return keysFile(this, "prod.keys").length() > 0; }

    boolean gameReadable() {
        try (ParcelFileDescriptor f = getContentResolver().openFileDescriptor(Uri.parse(gameUri()), "r")) {
            return f != null;
        } catch (Exception e) {
            return false;
        }
    }

    boolean updateReadable() {
        if (updateUri() == null) return true;
        try (ParcelFileDescriptor f = getContentResolver().openFileDescriptor(Uri.parse(updateUri()), "r")) {
            return f != null;
        } catch (Exception e) {
            return false;
        }
    }

    boolean directMode() { return prefs().getBoolean("direct_mode", false); }

    void addStorageMode(LinearLayout layout) {
        String[] modes = {t("Instalar em Android/data", "Install in Android/data"),
                t("Rodar pelo NSP/XCI", "Run from NSP/XCI")};
        addRow(layout, t("Modo de preparação", "Setup mode"), modes[directMode() ? 1 : 0], v ->
                new P4gDialog.Builder(this).setTitle(t("Modo de preparação", "Setup mode"))
                        .setSingleChoiceItems(modes, directMode() ? 1 : 0, (dialog, index) -> {
                            dialog.dismiss();
                            boolean direct = index == 1;
                            if (direct == directMode()) return;
                            prefs().edit().putBoolean("direct_mode", direct).apply();
                            installedCheck = new InstalledGameFiles.RecentCheck();
                            refresh();
                        }).setNegativeButton(t("Cancelar", "Cancel"), null).show());
    }

    boolean launchedGame;
    volatile InstalledGameFiles.RecentCheck installedCheck = new InstalledGameFiles.RecentCheck();

    void recoverDirectUpdate() {
        new P4gDialog.Builder(this)
                .setTitle(t("Atualização não encontrada", "Update not found"))
                .setMessage(t("Selecione novamente o NSP da atualização ou prepare apenas o jogo base.",
                        "Select the update NSP again or prepare just the base game."))
                .setPositiveButton(t("Selecionar novamente", "Select again"), (d, w) -> pick(PICK_UPDATE, "*/*", false))
                .setNeutralButton(t("Usar só o jogo base", "Use base game only"), (d, w) -> {
                    prefs().edit().remove("update_uri").remove("update_name").remove("direct_ready").apply();
                    if (hasAccess() && hasKeys() && gameUri() != null && gameReadable()) prepare();
                    else refresh();
                })
                .setNegativeButton(t("Cancelar", "Cancel"), null).show();
    }

    boolean installedReady() throws IOException {
        File installed = installationDir(this);
        InstalledGameFiles.migrate(dataDir(), installed);
        return installedCheck.ready(installed);
    }

    String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last.substring(last.lastIndexOf('/') + 1);
    }

    void pick(int request, String type, boolean multiple) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, request);
    }

    void onGamePicked(Uri uri) {
        String name = displayName(uri);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".nsp") && !lower.endsWith(".xci")) {
            tell(t("Arquivo inválido", "Not a game file"),
                    t("Escolha o arquivo .nsp ou .xci do jogo base, não o arquivo de atualização.",
                            "Choose the base game's .nsp or .xci file (not the update's)."));
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {  // a provider without persistable grants: works while the app runs
        }
        prefs().edit().putString("uri", uri.toString()).putString("name", name)
                .remove("p4g_prepared_uri").remove("direct_ready").apply();
    }

    void onUpdatePicked(Uri uri) {
        String name = displayName(uri);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".nsp")) {
            tell(t("Arquivo inválido", "Not an update file"),
                    t("Escolha a atualização do P4G em formato .nsp.",
                            "Choose the P4G update in .nsp format."));
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        prefs().edit().putString("update_uri", uri.toString()).putString("update_name", name).remove("direct_ready").apply();
    }

    // prod.keys (and title.keys, when the dump needs it): copied into the app's own storage, never shared or logged.
    void onKeysPicked(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null)
            for (int k = 0; k < clip.getItemCount(); k++) uris.add(clip.getItemAt(k).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        boolean prod = false;
        for (Uri uri : uris) {
            String name = displayName(uri).toLowerCase(Locale.ROOT);
            String target = name.contains("title") ? "title.keys" : "prod.keys";
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                byte[] buf = new byte[1 << 20];
                int n = 0, r;
                while (in != null && n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                String text = new String(buf, 0, n, "UTF-8");
                if (target.equals("prod.keys") && !text.contains("header_key") && !text.contains("master_key_00")) continue;
                File f = keysFile(this, target);
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    out.write(buf, 0, n);
                }
                prod |= target.equals("prod.keys");
            } catch (Exception ignored) {
            }
        }
        if (!prod && !hasKeys())
            tell(t("Chaves inválidas", "Not a keys file"),
                    t("Esse arquivo não contém as chaves (header_key). Escolha o prod.keys do seu console.",
                            "That file has no keys (header_key). Choose your console's prod.keys."));
    }

    void prepare() {
        Intent i = new Intent(this, ImportActivity.class);
        i.putExtra(ImportActivity.EXTRA_URI, gameUri());
        i.putExtra(ImportActivity.EXTRA_NAME, gameName());
        i.putExtra(ImportActivity.EXTRA_UPDATE_URI, updateUri());
        i.putExtra(ImportActivity.EXTRA_UPDATE_NAME, updateName());
        i.putExtra("direct_mode", directMode());
        startActivityForResult(i, PREPARE);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            Bundle drawers = state.getBundle("drawers");
            if (drawers != null)
                for (String key : drawers.keySet()) drawerStates.put(key, drawers.getBoolean(key));
        }
        Lang.load(this);
        applyLauncherOrientation();
        P4gTheme.apply(this);
        if (launcherLandscape()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        options.add(new Option("RESOLUTION", "Resolução de saída", "Output resolution",
                new String[] {"360p", "480p", "720p", "1080p", "1440p", "2160p", "native"},
                new String[] {"360p", "480p", "720p", "1080p", "1440p", "2160p", "Nativa"},
                new String[] {"360p", "480p", "720p", "1080p", "1440p", "2160p", "Native"}, 2));
        options.add(new Option("NX_STRETCH", "Formato da tela", "Screen format",
                new String[] {"0", "hor+", "16:10", "1"},
                new String[] {"16:9", "HOR+", "16:10", "Esticar"},
                new String[] {"16:9", "HOR+", "16:10", "Stretch"}, 0));
        options.add(new Option("NX_FPS_CAP", "Limite de FPS", "FPS limit", new String[] {"30", "45", "60"},
                new String[] {"30 FPS", "45 FPS", "60 FPS"},
                new String[] {"30 FPS", "45 FPS", "60 FPS"}, 0));
        options.add(new Option("NX_MAX_ANISO", "Filtragem anisotrópica", "Anisotropic filtering",
                new String[] {"1", "2", "4", "8", "16"},
                new String[] {"Desativada (mais desempenho)", "2x (rápido)", "4x (padrão)",
                        "8x (mais qualidade)", "16x (qualidade máxima)"},
                new String[] {"Off (best performance)", "2x (faster)", "4x (default)",
                        "8x (higher quality)", "16x (best quality)"}, 2));
        options.add(new Option("NX_FSR", "Escalado FSR", "FSR upscaling",
                new String[] {"0", "1"}, new String[] {"Desativado", "Ativado"},
                new String[] {"Off", "On"}, 0));
        options.add(new Option("NX_OVERLAY_SIZE", "Tamanho do overlay", "Overlay size",
                new String[] {"50", "80", "100", "125", "150"},
                new String[] {"50%", "80%", "100% (padrão)", "125%", "150%"},
                new String[] {"50%", "80%", "100% (default)", "125%", "150%"}, 2));
        options.add(new Option("NX_SHOW_FPS", "FPS", "FPS", new String[] {"0", "1"},
                new String[] {"Desativado", "Ativado"}, new String[] {"Off", "On"}, 0));
        for (String metric : new String[] {"CPU", "GPU", "RAM"}) {
            options.add(new Option("NX_OVERLAY_" + metric, metric, metric, new String[] {"0", "1"},
                    new String[] {"Desativado", "Ativado"}, new String[] {"Off", "On"}, 0));
        }
        options.add(new Option("NX_TRACE_PERF", "Estatísticas detalhadas de desempenho", "Detailed performance logging",
                new String[] {"0", "1"}, new String[] {"Desativado", "Ativado"},
                new String[] {"Off", "On"}, 0));
        options.add(new Option("NX_TOUCH_SHOW", "Controles na tela", "On-screen controls", new String[] {"1", "0"},
                new String[] {"Ativados", "Desativados"}, new String[] {"On", "Off"}, 0));
        options.add(new Option("NX_GAMEPAD_LAYOUT", "Layout do controle físico", "Gamepad layout",
                new String[] {"0", "1"}, new String[] {"Nintendo (A à direita)", "Xbox (A embaixo)"},
                new String[] {"Nintendo (A right)", "Xbox (A bottom)"}, 1));
        options.add(new Option("NX_TOUCH_LEFT_STICK_MODE", "Modo do analógico esquerdo", "Left analog mode",
                new String[] {"fixed", "floating"}, new String[] {"Fixo", "Flutuante"},
                new String[] {"Fixed", "Floating"}, 0));
        options.add(new Option("NX_TOUCH_SIZE", "Tamanho dos botões", "Button size",
                new String[] {"80", "100", "125"}, new String[] {"Pequeno", "Normal", "Grande"},
                new String[] {"Small", "Normal", "Large"}, 1));
        options.add(new Option("NX_TOUCH_OPACITY", "Opacidade dos botões", "Button opacity",
                new String[] {"50", "85", "100"}, new String[] {"Baixa", "Normal", "Alta"},
                new String[] {"Low", "Normal", "High"}, 1));
        options.add(new Option("NX_STICK_SENSITIVITY", "Sensibilidade do analógico", "Touch stick sensitivity",
                new String[] {"50", "75", "100", "125", "150", "200"},
                new String[] {"50%", "75%", "100%", "125%", "150%", "200%"},
                new String[] {"50%", "75%", "100%", "125%", "150%", "200%"}, 2));
        options.add(new Option("NX_CAMERA_SENSITIVITY", "Sensibilidade da câmera", "Touch camera sensitivity",
                new String[] {"50", "75", "100", "125", "150", "200", "250"},
                new String[] {"50%", "75%", "100%", "125%", "150%", "200%", "250%"},
                new String[] {"50%", "75%", "100%", "125%", "150%", "200%", "250%"}, 2));
        options.add(new Option("P4G_LAUNCHER_CRT", "Estática CRT del launcher", "Launcher CRT static",
                new String[] {"1", "0"}, new String[] {"Activada", "Desactivada"}, new String[] {"On", "Off"}, 0));
        options.add(new Option("P4G_LAUNCHER_CRT_INTENSITY", "Intensidad de la estática", "Static intensity",
                new String[] {"20", "42", "72"}, new String[] {"Suave", "Media", "Alta"},
                new String[] {"Low", "Medium", "High"}, 1));
        build(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backgroundScene != null) backgroundScene.setAnimating(true);
        Intent intent = getIntent();
        if (intent.hasExtra("game_error") || (launchedGame && !intent.getBooleanExtra("returned_to_menu", false)))
            installedCheck = new InstalledGameFiles.RecentCheck();
        launchedGame = false;
        intent.removeExtra("returned_to_menu");
        refresh();
        showGameError(intent);
    }

    @Override protected void onPause() {
        if (backgroundScene != null) backgroundScene.setAnimating(false);
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    void showGameError(Intent intent) {
        String error = intent.getStringExtra("game_error");
        if (error != null) {
            intent.removeExtra("game_error");
            tell(t("Não foi possível abrir o jogo", "Could not open the game"), error);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        Bundle drawers = new Bundle();
        for (Map.Entry<String, Boolean> entry : drawerStates.entrySet())
            drawers.putBoolean(entry.getKey(), entry.getValue());
        state.putBundle("drawers", drawers);
    }

    boolean readyForPlay;
    boolean launcherLandscape() {
        return getSharedPreferences("launcher", MODE_PRIVATE)
                .getBoolean("landscape", DEFAULT_LANDSCAPE);
    }

    void applyLauncherOrientation() {
        boolean landscape = launcherLandscape();
        setRequestedOrientation(landscape ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }
    int refreshGeneration;

    @Override protected void onDestroy() {
        if (modManager != null) modManager.dismiss();
        if (driverSelector != null) driverSelector.dismiss();
        super.onDestroy();
    }

    private LinearLayout settingsGroup(LinearLayout root, String key, String title, String subtitle, boolean open) {
        boolean closedUntilPressed = "launcher".equals(key) || "logs".equals(key);
        if (closedUntilPressed) open = false;
        else if (drawerStates.containsKey(key)) open = drawerStates.get(key);
        drawerStates.put(key, open);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0x6E0D0A05);
        card.setPadding(dp(6), dp(3), dp(6), dp(6));
        sectionCards.put(key, card);
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.bottomMargin = dp(8);
        root.addView(card, spacing);
        P4gMenuButton header = new P4gMenuButton(this);
        header.setArtSelected(true);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setTextSize(18);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        sectionHeaders.put(key, header);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), dp(4), dp(10), dp(8));
        TextView hint = new TextView(this);
        hint.setText(subtitle);
        hint.setTextColor(P4gTheme.MUTED);
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, dp(4));
        hint.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        body.addView(hint);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        // Preserve expansion across refreshes and Activity recreation during this visit.
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        sectionBodies.put(key, body);
        header.setText((open ? "−  " : "+  ") + title);
        header.setContentDescription(title + (open ? ", collapse" : ", expand"));
        header.setOnClickListener(v -> {
            boolean expanded = body.getVisibility() != View.VISIBLE;
            drawerStates.put(key, expanded);
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
            header.setText((expanded ? "−  " : "+  ") + title);
            header.setContentDescription(title + (expanded ? ", collapse" : ", expand"));
        });
        return body;
    }

    private void addLauncherSettings(LinearLayout parent) {
        LinearLayout launcherSettings = settingsGroup(parent, "launcher", t("Configurações do launcher", "Launcher settings"),
                t("Idioma e orientação", "Language and orientation"), false);
        addRow(launcherSettings, t("Idioma do launcher", "Launcher language"),
                Lang.currentName(), v -> showLanguagePicker());

        addRow(launcherSettings, t("Orientação do launcher", "Launcher orientation"),
                getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("landscape", DEFAULT_LANDSCAPE)
                        ? t("Horizontal", "Landscape") : t("Vertical", "Portrait"), v -> {
                    SharedPreferences settings = getSharedPreferences("launcher", MODE_PRIVATE);
                    settings.edit().putBoolean("landscape",
                            !settings.getBoolean("landscape", DEFAULT_LANDSCAPE)).apply();
                    applyLauncherOrientation();
                });
        addRow(launcherSettings, t("Redefinir configurações", "Reset settings"),
                t("Redefinir", "Reset"), v -> new P4gDialog.Builder(this)
                        .setMessage(t("Redefinir o idioma e a orientação do launcher?",
                                "Reset the launcher language and orientation?"))
                        .setNegativeButton(t("Cancelar", "Cancel"), null)
                        .setPositiveButton(t("Redefinir", "Reset"), (dialog, which) -> {
                            getSharedPreferences("launcher", MODE_PRIVATE).edit().remove("language").remove("landscape").apply();
                            Lang.load(this);
                            applyLauncherOrientation();
                            build(readyForPlay);
                            refresh();
                        }).show());
    }

    void build(boolean ready) {
        updateCheckGeneration++;
        updateNotice = null;
        updateReleaseUrl = null;
        sectionCards.clear();
        sectionBodies.clear();
        sectionHeaders.clear();
        navButtons.clear();
        quickButtons.clear();
        selectedNav = "home";
        launcherScroll = null;
        backgroundScene = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ready ? Color.TRANSPARENT : P4gTheme.BACKGROUND);
        root.setPadding(dp(18), dp(10), dp(18), dp(14));

        TextView title = new TextView(this);
        title.setText("P4GP · Persona 4 Golden");
        title.setTextColor(P4gTheme.YELLOW);
        title.setPadding(0, dp(4), 0, dp(2));
        title.setTextSize(25);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView version = new TextView(this);
        version.setText("P4GP  ·  " + appVersionName());
        version.setTextColor(P4gTheme.MUTED);
        version.setTextSize(12);
        version.setTypeface(Typeface.DEFAULT_BOLD);
        version.setPadding(0, dp(3), 0, 0);
        root.addView(version);

        status = new TextView(this);
        status.setTextColor(P4gTheme.MUTED);
        status.setTextSize(13);
        status.setPadding(0, dp(4), 0, dp(8));
        root.addView(status);

        if (!ready) {
        P4gTheme.section(root, t("Preparação do jogo", "Game setup"));
        addStorageMode(root);
        addRow(root, t("Arquivo do jogo (.nsp / .xci)", "Game file (.nsp / .xci)"), t("Escolher", "Choose"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pick(PICK_GAME, "*/*", false);
                    }
                });
        addRow(root, t("Atualização do P4G .nsp (opcional)", "P4G update .nsp (optional)"),
                updateUri() == null ? t("Escolher", "Choose") : updateName(), v -> pick(PICK_UPDATE, "*/*", false));
        addRow(root, t("Chaves (prod.keys)", "Keys (prod.keys)"), t("Escolher", "Choose"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(PICK_KEYS, "*/*", true);
            }
        });


        play = new Button(this);
        P4gTheme.button(play, true);
        play.setAllCaps(false);
        play.setText(t("Preparar jogo", "Prepare"));
        play.setTextSize(18);
        root.addView(play, new LinearLayout.LayoutParams(-1, dp(54)));
        play.setOnClickListener(v -> {
            if (!hasAccess()) askAccess();
            else if (gameUri() == null || !gameReadable()) pick(PICK_GAME, "*/*", false);
            else if (directMode() && !updateReadable()) recoverDirectUpdate();
            else if (!hasKeys()) pick(PICK_KEYS, "*/*", true);
            else { prefs().edit().remove("p4g_prepared_uri").apply(); prepare(); }
        });
        addLauncherSettings(root);
        ScrollView setup = new ScrollView(this);
        setup.setBackgroundColor(P4gTheme.BACKGROUND);
        setup.setFillViewport(true);
        setup.addView(root);
        setContentView(setup);
        applySafeAreaInsets(setup);
        return;
        }

        // The prepared launcher uses a game-style home composition. The setup screen above
        // remains separate so the CRT and artwork never appear in setup or editor activities.
        root.removeAllViews();
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        status = new TextView(this);

        play = new P4gPlayButton(this);
        play.setTextSize(26);
        play.setText("¡JUGAR!");
        play.setOnClickListener(v -> installShadersAndPlay());

        LinearLayout quick = settingsGroup(root, "display", t("Tela e gráficos", "Display and graphics"), "", false);
        LinearLayout controls = settingsGroup(root, "controls", t("Controles", "Controls"), "", false);
        LinearLayout content = settingsGroup(root, "content", t("Jogo, saves e mods", "Game, saves and mods"), "", false);
        LinearLayout overlay = settingsGroup(root, "overlay", t("Monitor de desempenho", "Performance monitor"), "", false);
        LinearLayout moreOptions = settingsGroup(root, "more", t("Mais opções", "More options"), "", false);
        addLauncherSettings(moreOptions);
        addRow(quick, t("Predefinição gráfica", "Graphics preset"), t("Escolher", "Choose"), v ->
                new P4gDialog.Builder(this).setTitle(t("Predefinição gráfica", "Graphics preset"))
                        .setItems(new String[] {t("Desempenho", "Performance"), t("Equilibrado", "Balanced"), t("Qualidade", "Quality")},
                                (dialog, which) -> applyPreset(new String[] {"performance", "normal", "quality"}[which])).show());
        addRow(moreOptions, t("Perfis salvos", "Saved profiles"), t("Escolher", "Choose"), v ->
                new P4gDialog.Builder(this).setTitle(t("Perfis salvos", "Saved profiles"))
                        .setItems(new String[] {t("Perfil 1", "Profile 1"), t("Perfil 2", "Profile 2"), t("Perfil 3", "Profile 3")},
                                (dialog, which) -> showProfile(which + 1)).show());

        P4gTheme.section(content, t("Atualização do jogo", "Game update"));
        addStorageMode(content);
        TextView updateHelp = new TextView(this);
        updateHelp.setText(directMode() ? t("Mantenha o jogo base e o update acessíveis para jogar.", "Keep the base game and update accessible to play.") : t(
                "Para aplicar o update, selecione novamente o NSP/XCI original e o update NSP. A atualização depende de blocos do jogo base. Depois da preparação, os arquivos NSP não serão necessários para jogar.",
                "To apply an update, select the original NSP/XCI again and the update NSP. A Switch update depends on blocks from the base game and cannot use only the already extracted files. Neither NSP is needed to play after preparation finishes."));
        updateHelp.setTextColor(Color.LTGRAY);
        updateHelp.setTextSize(14);
        updateHelp.setPadding(0, dp(4), 0, dp(8));
        content.addView(updateHelp);
        addRow(content, t("Jogo base original (NSP/XCI)", "Original base game (NSP/XCI)"),
                gameUri() == null ? t("Escolher", "Choose") : gameName(), v -> pick(PICK_GAME, "*/*", false));
        addRow(content, t("Arquivo do update (.nsp)", "Update file (.nsp)"),
                updateUri() == null ? t("Escolher", "Choose") : updateName(), v -> pick(PICK_UPDATE, "*/*", false));
        addRow(content, t("Aplicar update", "Apply update"), t("Preparar", "Prepare"), v -> {
            if (!hasAccess()) askAccess();
            else if (gameUri() == null || !gameReadable()) pick(PICK_GAME, "*/*", false);
            else if (updateUri() == null || !updateReadable()) pick(PICK_UPDATE, "*/*", false);
            else if (!hasKeys()) pick(PICK_KEYS, "*/*", true);
            else prepare();
        });
        P4gTheme.section(content, t("Saves e mods", "Saves & mods"));
        LinearLayout diagnostics = settingsGroup(moreOptions, "logs", t("Logs e diagnóstico", "Logs & diagnostics"), "", false);
        P4gTheme.section(moreOptions, t("Comunidade", "Community"));
        addRow(moreOptions, "GitHub", t("Abrir", "Open"),
                v -> openExternal("https://github.com/synndras/P4G-Android-Plus"));
        addRow(moreOptions, "Discord", t("Entrar", "Join"),
                v -> openExternal("https://discord.gg/yF6FtNK6rM"));
        addRow(moreOptions, t("Reportar um problema", "Report an issue"), t("Abrir", "Open"),
                v -> openExternal("https://github.com/synndras/P4G-Android-Plus/issues/new/choose"));
        for (final Option o : options) {
            String group = optionGroup(o.key);
            if (isOverlayToggle(o.key)) {
                CheckBox toggle = new CheckBox(this);
                toggle.setText(t(o.pt, o.en));
                toggle.setTextColor(P4gTheme.TEXT);
                toggle.setTextSize(16);
                toggle.setMinHeight(dp(44));
                toggle.setPadding(dp(4), 0, dp(4), 0);
                toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(P4gTheme.YELLOW));
                toggle.setChecked("1".equals(o.values[o.pick]));
                toggle.setTag(o);
                toggle.setOnClickListener(v -> {
                    o.pick = ((CheckBox) v).isChecked() ? 1 : 0;
                    save();
                });
                overlay.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(2), 0, dp(2));
            TextView name = new TextView(this);
            name.setText(t(o.pt, o.en));
            name.setTextColor(Color.WHITE);
            name.setTextSize(16);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            final Button value = new Button(this);
            P4gTheme.button(value, false);
            compactButton(value);
            value.setAllCaps(false);
            value.setMinWidth(dp(110));
            value.setMaxWidth(dp(160));
            row.addView(value);
            value.setOnClickListener(v -> {
                String[] labels = new String[o.values.length];
                for (int i = 0; i < labels.length; i++) labels[i] = t(o.labelsPt[i], o.labelsEn[i]);
                new P4gDialog.Builder(this).setTitle(t(o.pt, o.en)).setSingleChoiceItems(labels, o.pick, (dialog, which) -> {
                    dialog.dismiss();
                    o.pick = which;
                    if (save()) value.setText(label(o));
                }).show();
            });
            value.setTag(o);
            (group.equals("logs") ? diagnostics :
                    group.equals("overlay") ? overlay : group.equals("controls") ? controls : quick).addView(row);
        }
        addDriverRow(quick);
        addLogsRow(diagnostics);

        LinearLayout edit = new LinearLayout(this);
        edit.setOrientation(LinearLayout.HORIZONTAL);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setPadding(0, dp(2), 0, dp(2));
        TextView editName = new TextView(this);
        editName.setText(t("Posição dos controles", "Controls position"));
        editName.setTextColor(Color.WHITE);
        editName.setTextSize(16);
        edit.addView(editName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button editButton = new Button(this);
        P4gTheme.button(editButton, false);
        compactButton(editButton);
        editButton.setAllCaps(false);
        editButton.setMinWidth(dp(110));
        editButton.setText(t("Editar layout", "Edit layout"));
        edit.addView(editButton);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                save();
                startActivity(new Intent(Launcher.this, ControlsEditor.class));
            }
        });
        controls.addView(edit);
        addRow(controls, "Botones del mando", "Asignar", v ->
                startActivity(new Intent(Launcher.this, GamepadEditorActivity.class)));

        // Keep the reset action as the final item in every settings section.
        addResetRow(quick, "quick");
        addResetRow(controls, "controls");
        addResetRow(overlay, "overlay");
        addResetRow(diagnostics, "logs");

        // Saves: in from a ZIP or a folder (a Switch backup or another player's), out to a ZIP in Download.
        LinearLayout saves = new LinearLayout(this);
        saves.setOrientation(LinearLayout.HORIZONTAL);
        saves.setGravity(Gravity.CENTER_VERTICAL);
        saves.setPadding(0, dp(2), 0, dp(2));
        TextView savesName = new TextView(this);
        savesName.setText(t("Saves", "Saves"));
        savesName.setTextColor(Color.WHITE);
        savesName.setTextSize(16);
        saves.addView(savesName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button importButton = new Button(this);
        P4gTheme.button(importButton, false);
        compactButton(importButton);
        importButton.setAllCaps(false);
        importButton.setMinWidth(dp(88));
        importButton.setText(t("Importar", "Import"));
        saves.addView(importButton);
        Button exportButton = new Button(this);
        P4gTheme.button(exportButton, false);
        compactButton(exportButton);
        exportButton.setAllCaps(false);
        exportButton.setMinWidth(dp(88));
        exportButton.setText(t("Exportar", "Export"));
        saves.addView(exportButton);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                askImport();
            }
        });
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                exportSaves();
            }
        });
        content.addView(saves);

        // Translation CPK import
        LinearLayout transRow = new LinearLayout(this);
        transRow.setOrientation(LinearLayout.HORIZONTAL);
        transRow.setGravity(Gravity.CENTER_VERTICAL);
        transRow.setPadding(0, dp(2), 0, dp(2));
        TextView transName = new TextView(this);
        transName.setText(t("Tradução / Mod CPK", "Translation / CPK mod"));
        transName.setTextColor(Color.WHITE);
        transName.setTextSize(16);
        transRow.addView(transName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button transButton = new Button(this);
        P4gTheme.button(transButton, false);
        compactButton(transButton);
        transButton.setAllCaps(false);
        transButton.setMinWidth(dp(110));
        transButton.setText(t("Importar CPK", "Import CPK"));
        transRow.addView(transButton);
        transButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                pick(PICK_CPK, "*/*", false);
            }
        });
        content.addView(transRow);
        addModsRow(content);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setFillViewport(true);
        scroll.addView(root);
        launcherScroll = scroll;
        homePanel = new View(this);
        root.addView(homePanel, 0, new LinearLayout.LayoutParams(-1, -2));
        for (String section : new String[] {"display", "controls", "content", "overlay", "more"}) {
            View card = sectionCards.get(section);
            if (card != null) card.setVisibility(View.GONE);
            LinearLayout body = sectionBodies.get(section);
            if (body != null) {
                body.setVisibility(View.VISIBLE);
                drawerStates.put(section, true);
            }
        }

        FrameLayout scene = new FrameLayout(this);
        backgroundScene = new P4gBackgroundView(this);
        scene.addView(backgroundScene, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout screen = new LinearLayout(this);
        boolean landscape = launcherLandscape();
        screen.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.TRANSPARENT);
        if (landscape) {
            screen.addView(createNavigation(true), new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout page = buildMainPage(scroll);
            screen.addView(page, new LinearLayout.LayoutParams(0, -1, 3f));
        } else {
            screen.setOrientation(LinearLayout.VERTICAL);
            screen.addView(buildTopBar(true), new LinearLayout.LayoutParams(-1, dp(74)));
            screen.addView(createNavigation(false), new LinearLayout.LayoutParams(-1, dp(52)));
            FrameLayout scrollFrame = buildScrollFrame(scroll);
            screen.addView(scrollFrame, new LinearLayout.LayoutParams(-1, 0, 1f));
            screen.addView(buildActionBar(false), new LinearLayout.LayoutParams(-1, dp(120)));
        }
        scene.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        setContentView(scene);
        backgroundScene.setAnimating(true);
        applySafeAreaInsets(scene);
        navigateTo("home");
        updateBackgroundEffect();
        if (ready && updateNotice != null) checkForUpdates();
    }

    private void updateBackgroundEffect() {
        updateQuickButtons();
        if (backgroundScene == null) return;
        boolean enabled = true;
        int intensity = 42;
        for (Option option : options) {
            if ("P4G_LAUNCHER_CRT".equals(option.key)) enabled = "1".equals(option.values[option.pick]);
            else if ("P4G_LAUNCHER_CRT_INTENSITY".equals(option.key)) {
                try { intensity = Integer.parseInt(option.values[option.pick]); }
                catch (NumberFormatException ignored) { intensity = 42; }
            }
        }
        backgroundScene.setStaticEffect(enabled, intensity);
    }

    private void updateQuickButtons() {
        for (Map.Entry<String, P4gQuickButton> entry : quickButtons.entrySet()) {
            Option option = findOption(entry.getKey());
            if (option == null) continue;
            P4gQuickButton button = entry.getValue();
            String prefix = "RESOLUTION".equals(option.key) ? "RES" : "NX_STRETCH".equals(option.key) ? "VIEW" :
                    "NX_FPS_CAP".equals(option.key) ? "FPS" : "NX_MAX_ANISO".equals(option.key) ? "ANISO" : "FSR";
            button.setText(prefix + " " + quickValue(option));
            button.setContentDescription(t(option.pt + ": " + label(option), option.en + ": " + label(option)));
            button.setActive("1".equals(option.values[option.pick]) || "60".equals(option.values[option.pick])
                    || "hor+".equalsIgnoreCase(option.values[option.pick]));
        }
    }

    private LinearLayout createNavigation(boolean vertical) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        nav.setGravity(vertical ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackground(new P4gNavBackdrop());
        if (vertical) {
            P4gLogoView logo = new P4gLogoView(this);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(-1, dp(78));
            logoParams.bottomMargin = dp(5);
            nav.addView(logo, logoParams);
            LinearLayout options = new LinearLayout(this);
            options.setOrientation(LinearLayout.VERTICAL);
            options.setGravity(Gravity.CENTER_VERTICAL);
            String[][] entries = { {"home", "JUGAR"}, {"display", "PANTALLA"}, {"overlay", "RENDIMIENTO"},
                    {"controls", "CONTROLES"}, {"content", "ARCHIVOS"},
                    {"more", t("MÁS OPCIONES", "MORE OPTIONS")}, {"credits", "CRÉDITOS"} };
            for (String[] entry : entries) addNavigationButton(options, entry[0], entry[1], true);
            nav.addView(options, new LinearLayout.LayoutParams(-1, 0, 1f));
            TextView version = new TextView(this);
            version.setText("P4GP · " + appVersionName());
            version.setTextColor(Color.WHITE);
            version.setTextSize(10);
            version.setTypeface(Typeface.DEFAULT_BOLD);
            version.setGravity(Gravity.CENTER);
            version.setSingleLine(true);
            nav.addView(version, new LinearLayout.LayoutParams(-1, dp(20)));
        } else {
            addNavigationButton(nav, "home", "JUGAR", false);
            addNavigationButton(nav, "display", "PANTALLA", false);
            addNavigationButton(nav, "overlay", "RENDIMIENTO", false);
            addNavigationButton(nav, "controls", "CONTROLES", false);
            addNavigationButton(nav, "content", "ARCHIVOS", false);
            addNavigationButton(nav, "more", t("MÁS OPCIONES", "MORE OPTIONS"), false);
            addNavigationButton(nav, "credits", "CRÉDITOS", false);
            HorizontalScrollView horizontal = new HorizontalScrollView(this);
            horizontal.setHorizontalScrollBarEnabled(false);
            horizontal.addView(nav, new HorizontalScrollView.LayoutParams(-2, -1));
            return new NavigationHost(this, horizontal);
        }
        return nav;
    }

    private LinearLayout buildMainPage(ScrollView scroll) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.addView(buildTopBar(false), new LinearLayout.LayoutParams(-1, dp(58)));
        page.addView(buildScrollFrame(scroll), new LinearLayout.LayoutParams(-1, 0, 1f));
        page.addView(buildActionBar(true), new LinearLayout.LayoutParams(-1, dp(120)));
        return page;
    }

    private FrameLayout buildScrollFrame(ScrollView scroll) {
        FrameLayout frame = new FrameLayout(this);
        scrollRail = new P4gScrollRail(this);
        scrollRail.attach(scroll);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(scrollRail, new FrameLayout.LayoutParams(dp(16), -1, Gravity.RIGHT));
        return frame;
    }

    private LinearLayout buildActionBar(boolean landscape) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(12), dp(5), dp(12), dp(5));
        View panel = buildStatusPanel();
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(66), landscape ? 45f : 1f);
        statusParams.rightMargin = dp(8);
        actions.addView(panel, statusParams);
        LinearLayout playColumn = new LinearLayout(this);
        playColumn.setOrientation(LinearLayout.VERTICAL);
        playColumn.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        updateNotice = new P4gQuickButton(this);
        updateNotice.setTextSize(14);
        updateNotice.setActive(true);
        updateNotice.setText(t("NUEVA ACTUALIZACIÓN", "NEW UPDATE"));
        updateNotice.setContentDescription(t("Hay una nueva versión disponible", "A new version is available"));
        updateNotice.setVisibility(View.GONE);
        updateNotice.setMinHeight(dp(38));
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(dp(240), dp(38));
        noticeParams.gravity = Gravity.END;
        noticeParams.bottomMargin = dp(3);
        playColumn.addView(updateNotice, noticeParams);
        playColumn.addView(play, new LinearLayout.LayoutParams(-1, dp(66)));
        updateNotice.setOnClickListener(v -> openExternal(updateReleaseUrl == null
                ? "https://github.com/synndras/P4G-Android-Plus/releases" : updateReleaseUrl));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, -2, landscape ? 40f : 1f);
        actions.addView(playColumn, playParams);
        if (landscape) actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 15f));
        return actions;
    }

    private void checkForUpdates() {
        final int request = ++updateCheckGeneration;
        final String installedVersion;
        try {
            installedVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return;
        }
        new Thread(() -> {
            String latestVersion = null;
            String latestUrl = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                        "https://api.github.com/repos/synndras/P4G-Android-Plus/releases").openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "P4GP-Android-Launcher");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream stream = connection.getInputStream();
                         java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[2048];
                        int count;
                        while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        JSONArray releases = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                        for (int i = 0; i < releases.length(); i++) {
                            JSONObject release = releases.optJSONObject(i);
                            if (release == null || release.optBoolean("draft", false)) continue;
                            String candidate = release.optString("tag_name", "");
                            if (candidate.isEmpty()) continue;
                            if (latestVersion == null || compareVersions(candidate, latestVersion) > 0) {
                                latestVersion = candidate;
                                latestUrl = release.optString("html_url", "");
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Keep the launcher quiet when offline or when GitHub cannot be reached.
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String releaseVersion = latestVersion;
            final String releaseUrl = latestUrl;
            runOnUiThread(() -> {
                if (request != updateCheckGeneration || updateNotice == null || isFinishing() || isDestroyed()) return;
                if (releaseVersion == null || compareVersions(releaseVersion, installedVersion) <= 0) return;
                updateReleaseUrl = releaseUrl;
                updateNotice.setText(t("NUEVA ACTUALIZACIÓN · " + releaseVersion,
                        "NEW UPDATE · " + releaseVersion));
                updateNotice.setContentDescription(t("Actualizar a " + releaseVersion,
                        "Update to " + releaseVersion));
                updateNotice.setVisibility(View.VISIBLE);
            });
        }, "P4GP-update-check").start();
    }

    private static int compareVersions(String first, String second) {
        String a = first == null ? "" : first.replaceFirst("^[vV]", "");
        String b = second == null ? "" : second.replaceFirst("^[vV]", "");
        String[] aParts = a.split("-", 2);
        String[] bParts = b.split("-", 2);
        String[] aNumbers = aParts[0].split("\\.");
        String[] bNumbers = bParts[0].split("\\.");
        for (int i = 0; i < Math.max(aNumbers.length, bNumbers.length); i++) {
            int av = i < aNumbers.length ? leadingNumber(aNumbers[i]) : 0;
            int bv = i < bNumbers.length ? leadingNumber(bNumbers[i]) : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        boolean aPre = aParts.length > 1 && !aParts[1].isEmpty();
        boolean bPre = bParts.length > 1 && !bParts[1].isEmpty();
        if (aPre != bPre) return aPre ? -1 : 1;
        return aPre ? aParts[1].compareToIgnoreCase(bParts[1]) : 0;
    }

    private static int leadingNumber(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        try { return end == 0 ? 0 : Integer.parseInt(value.substring(0, end)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void addNavigationButton(LinearLayout nav, String key, String label, boolean vertical) {
        P4gMenuButton button = new P4gMenuButton(this);
        button.setArtSelected(key.equals(selectedNav));
        button.setText(label);
        button.setContentDescription(label);
        button.setTextSize(vertical ? 15 : 13);
        button.setGravity(vertical ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        button.setMinHeight(dp(vertical ? 34 : 38));
        button.setOnClickListener(v -> {
            if ("credits".equals(key)) showCredits();
            else navigateTo(key);
        });
        LinearLayout.LayoutParams params = vertical
                ? new LinearLayout.LayoutParams(-1, 0, 1f)
                : new LinearLayout.LayoutParams(dp(132), -1);
        if (vertical) params.bottomMargin = dp(3);
        else params.rightMargin = dp(4);
        nav.addView(button, params);
        navButtons.put(key, button);
    }

    private void navigateTo(String key) {
        selectedNav = key;
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            Button button = entry.getValue();
            if (button instanceof P4gMenuButton) ((P4gMenuButton) button).setArtSelected(entry.getKey().equals(key));
        }
        if (launcherScroll == null) return;
        if (homePanel != null) homePanel.setVisibility("home".equals(key) ? View.VISIBLE : View.GONE);
        for (String section : new String[] {"display", "controls", "content", "overlay", "more"}) {
            View card = sectionCards.get(section);
            if (card != null) card.setVisibility(section.equals(key) ? View.VISIBLE : View.GONE);
        }
        if ("home".equals(key)) {
            launcherScroll.smoothScrollTo(0, 0);
            if (scrollRail != null) scrollRail.invalidate();
            return;
        }
        String group = key;
        View target = sectionCards.get(group);
        LinearLayout body = sectionBodies.get(group);
        Button header = sectionHeaders.get(group);
        if (body != null && body.getVisibility() != View.VISIBLE) {
            body.setVisibility(View.VISIBLE);
            drawerStates.put(group, true);
            if (header != null) header.setText("−  " + header.getText().toString().replaceFirst("^[+−]  ", ""));
        }
        if (target != null) launcherScroll.post(() -> {
            launcherScroll.smoothScrollTo(0, 0);
            focusFirstSubmenuControl(target, sectionHeaders.get(key));
        });
        if (scrollRail != null) scrollRail.invalidate();
    }

    private void cycleLauncherCategory(int direction) {
        int current = 0;
        for (int i = 0; i < CATEGORY_NAV_ORDER.length; i++) {
            if (CATEGORY_NAV_ORDER[i].equals(selectedNav)) { current = i; break; }
        }
        int next = (current + direction + CATEGORY_NAV_ORDER.length) % CATEGORY_NAV_ORDER.length;
        navigateTo(CATEGORY_NAV_ORDER[next]);
    }

    private void collectSubmenuFocusables(View view, View skip, List<View> out) {
        if (view == null || view == skip || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return;
        if (view.isFocusable() && view.isClickable()) out.add(view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collectSubmenuFocusables(group.getChildAt(i), skip, out);
        }
    }

    private List<View> submenuFocusables() {
        ArrayList<View> focusables = new ArrayList<>();
        View section = sectionCards.get(selectedNav);
        collectSubmenuFocusables(section, sectionHeaders.get(selectedNav), focusables);
        return focusables;
    }

    private void focusFirstSubmenuControl(View section, View skip) {
        ArrayList<View> focusables = new ArrayList<>();
        collectSubmenuFocusables(section, skip, focusables);
        if (!focusables.isEmpty()) focusables.get(0).requestFocus();
    }

    private boolean moveSubmenuFocus(int direction) {
        List<View> focusables = submenuFocusables();
        if (focusables.isEmpty()) return false;
        int current = focusables.indexOf(getCurrentFocus());
        int next = current < 0 ? (direction > 0 ? 0 : focusables.size() - 1)
                : (current + direction + focusables.size()) % focusables.size();
        return focusables.get(next).requestFocus();
    }

    private boolean adjustFocusedOption(int direction) {
        View focused = getCurrentFocus();
        if (focused == null || !(focused.getTag() instanceof Option)) return false;
        Option option = (Option) focused.getTag();
        if (option.values.length < 2) return true;
        option.pick = (option.pick + direction + option.values.length) % option.values.length;
        if (save()) updateButtons(getWindow().getDecorView());
        return true;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.getRepeatCount() == 0) cycleLauncherCategory(key == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1);
            return true;
        }
        if (!"home".equals(selectedNav)) {
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (moveSubmenuFocus(key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)) return true;
            } else if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (adjustFocusedOption(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)) return true;
            } else if (key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_ENTER
                    || key == KeyEvent.KEYCODE_DPAD_CENTER) {
                View focused = getCurrentFocus();
                if (event.getRepeatCount() == 0 && focused != null && focused.isClickable()) {
                    focused.performClick();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showCredits() {
        String[] links = {
                "https://github.com/nxdevxz/p4g-android",
                "https://github.com/Brandon04Gonzalez",
                "https://github.com/Chakeyy",
                "https://github.com/synndras/P4G-Android-Plus"
        };
        String[] people = {
                "nxdevxz · " + t("proyecto original", "original project"),
                "Brandon04Gonzalez",
                "Chakeyy",
                "synndras · " + t("creador de este fork", "fork creator")
        };
        new P4gDialog.Builder(this)
                .setTitle(t("Créditos de P4GP", "P4GP credits"))
                .setMessage(t("Proyecto original, colaboradores y creador de este fork.",
                        "Original project, contributors, and creator of this fork."))
                .setItems(people, (dialog, which) -> {
                    dialog.dismiss();
                    openExternal(links[which]);
                }).show();
    }

    private String appVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "0.5.7"; }
    }

    private View buildTopBar(boolean showLogo) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(5), dp(8), dp(5));
        if (showLogo) {
            P4gLogoView logo = new P4gLogoView(this);
            bar.addView(logo, new LinearLayout.LayoutParams(dp(250), -1));
        }
        HorizontalScrollView chips = new HorizontalScrollView(this);
        chips.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[][] quickOptions = {
                {"RES", "RESOLUTION"}, {"VIEW", "NX_STRETCH"},
                {"FPS", "NX_FPS_CAP"}, {"ANISO", "NX_MAX_ANISO"}, {"FSR", "NX_FSR"}
        };
        for (String[] spec : quickOptions) {
            Option option = findOption(spec[1]);
            if (option == null) continue;
            P4gQuickButton chip = new P4gQuickButton(this);
            chip.setText(spec[0] + " " + quickValue(option));
            chip.setActive("1".equals(option.values[option.pick]) || "60".equals(option.values[option.pick])
                    || "hor+".equalsIgnoreCase(option.values[option.pick]));
            chip.setContentDescription(t(option.pt + ": " + label(option), option.en + ": " + label(option)));
            int chipWidth = "NX_STRETCH".equals(option.key) ? 106 : 88;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(chipWidth), dp(34));
            cp.leftMargin = dp(3); cp.rightMargin = dp(3);
            row.addView(chip, cp);
            quickButtons.put(option.key, chip);
            chip.setOnClickListener(v -> showOptionPicker(option));
        }
        P4gQuickButton logs = new P4gQuickButton(this);
        logs.setText("LOGS EXPORT");
        logs.setActive(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(98), dp(34));
        lp.leftMargin = dp(3); lp.rightMargin = dp(3);
        row.addView(logs, lp);
        logs.setOnClickListener(v -> exportLogs());
        chips.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        bar.addView(chips, new LinearLayout.LayoutParams(0, -1, 1));
        return bar;
    }

    private View buildStatusPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(16), dp(7), dp(16), dp(7));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF20C0B09);
        bg.setStroke(dp(3), P4gTheme.YELLOW);
        panel.setBackground(bg);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView ready = new TextView(this);
        ready.setText("LISTO PARA JUGAR");
        ready.setTextColor(Color.WHITE);
        ready.setTextSize(16);
        ready.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        text.addView(ready);
        status.setTextSize(12);
        status.setTextColor(P4gTheme.YELLOW);
        status.setMaxLines(1);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(status);
        panel.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tv = new TextView(this);
        tv.setText("▣");
        tv.setTextColor(P4gTheme.YELLOW);
        tv.setTextSize(26);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(8), 0, dp(8), 0);
        panel.addView(tv);
        if (!launcherLandscape()) {
            TextView version = new TextView(this);
            version.setText("P4GP · " + appVersionName());
            version.setTextColor(Color.WHITE);
            version.setTextSize(10);
            version.setTypeface(Typeface.DEFAULT_BOLD);
            panel.addView(version);
        }
        return panel;
    }

    private Option findOption(String key) {
        for (Option option : options) if (key.equals(option.key)) return option;
        return null;
    }

    private String quickValue(Option option) {
        if ("RESOLUTION".equals(option.key)) return option.values[option.pick].toUpperCase(Locale.ROOT);
        if ("NX_STRETCH".equals(option.key)) return label(option).toUpperCase(Locale.ROOT);
        if ("NX_MAX_ANISO".equals(option.key)) return "1".equals(option.values[option.pick]) ? "OFF" : option.values[option.pick] + "X";
        if ("NX_FSR".equals(option.key)) return "1".equals(option.values[option.pick]) ? "ON" : "OFF";
        if ("NX_FPS_CAP".equals(option.key)) return option.values[option.pick];
        return label(option).toUpperCase(Locale.ROOT);
    }

    private void showOptionPicker(Option option) {
        String[] labels = new String[option.values.length];
        for (int i = 0; i < labels.length; i++) labels[i] = t(option.labelsPt[i], option.labelsEn[i]);
        new P4gDialog.Builder(this).setTitle(t(option.pt, option.en)).setSingleChoiceItems(labels, option.pick, (dialog, which) -> {
            dialog.dismiss();
            option.pick = which;
            save();
            View row = launcherScroll == null ? null : launcherScroll.findViewWithTag(option);
            if (row instanceof Button) ((Button) row).setText(label(option));
            updateQuickButtons();
        }).show();
    }

    /** Keeps the portrait navigation scrollable without a second navigation implementation. */
    private static final class NavigationHost extends LinearLayout {
        NavigationHost(Context context, HorizontalScrollView content) {
            super(context);
            setOrientation(VERTICAL);
            addView(content, new LinearLayout.LayoutParams(-1, -1));
        }
    }

    boolean installingShaders;

    static String optionGroup(String key) {
        if (key.equals("NX_TRACE_PERF")) return "logs";
        if (key.equals("NX_SHOW_FPS") || key.startsWith("NX_OVERLAY_")) return "overlay";
        if (key.equals("NX_GAMEPAD_LAYOUT") || key.equals("NX_GAMEPAD_MAP") || key.startsWith("NX_TOUCH_") || key.equals("NX_STICK_SENSITIVITY") || key.equals("NX_CAMERA_SENSITIVITY")) return "controls";
        return "quick";
    }

    static boolean isOverlayToggle(String key) {
        return key.equals("NX_SHOW_FPS") || key.equals("NX_OVERLAY_CPU") ||
                key.equals("NX_OVERLAY_GPU") || key.equals("NX_OVERLAY_RAM");
    }

    static String[] presetValues(String preset) {
        if ("performance".equals(preset)) return new String[] {"480p", "30", "1"};
        if ("quality".equals(preset)) return new String[] {"1080p", "30", "16"};
        return new String[] {"720p", "30", "4"};
    }

    private void applyPreset(String preset) {
        String[] keys = {"RESOLUTION", "NX_FPS_CAP", "NX_MAX_ANISO"};
        String[] values = presetValues(preset);
        for (Option option : options)
            for (int i = 0; i < keys.length; i++)
                if (keys[i].equals(option.key)) option.select(values[i]);
        if (save()) {
            updateButtons(getWindow().getDecorView());
            Toast.makeText(this, t("Predefinição aplicada", "Preset applied"), Toast.LENGTH_SHORT).show();
        }
    }

    private void addResetRow(LinearLayout parent, String group) {
        addRow(parent, t("Redefinir configurações", "Reset settings"), t("Redefinir", "Reset"),
                v -> new P4gDialog.Builder(this)
                        .setMessage(t("Redefinir as configurações desta seção para o padrão?",
                                "Reset this section's settings to defaults?"))
                        .setNegativeButton(t("Cancelar", "Cancel"), null)
                        .setPositiveButton(t("Redefinir", "Reset"), (dialog, which) -> {
                            for (Option o : options) if (optionGroup(o.key).equals(group)) o.pick = o.defaultPick;
                            save();
                            updateButtons(getWindow().getDecorView());
                        }).show());
    }

    private void showLanguagePicker() {
        new P4gDialog.Builder(this)
                .setTitle(t("Idioma do launcher", "Launcher language"))
                .setSingleChoiceItems(Lang.names(), Lang.currentIndex(), (dialog, which) -> {
                    Lang.select(this, which);
                    dialog.dismiss();
                    build(readyForPlay);
                    refresh();
                })
                .setNegativeButton(t("Cancelar", "Cancel"), null)
                .show();
    }

    private void showProfile(int slot) {
        SharedPreferences profile = getSharedPreferences("profile_" + slot, MODE_PRIVATE);
        String title = t("Perfil ", "Profile ") + slot;
        new P4gDialog.Builder(this).setTitle(title)
                .setItems(new String[] {t("Carregar", "Load"), t("Salvar configuração atual", "Save current settings")}, (dialog, action) -> {
                    if (action == 0) {
                        if (!profile.getBoolean("saved", false)) {
                            tell(title, t("Este perfil ainda está vazio.", "This profile is empty."));
                            return;
                        }
                        for (Option o : options) {
                            String value = profile.getString(o.key, o.values[o.defaultPick]);
                            o.select(value);
                        }
                        save();
                        updateButtons(getWindow().getDecorView());
                    } else {
                        new P4gDialog.Builder(this).setTitle(title)
                                .setMessage(t("Salvar as opções atuais neste perfil? Substitui o perfil anterior.",
                                        "Save current options here? This replaces the previous profile."))
                                .setNegativeButton(t("Cancelar", "Cancel"), null)
                                .setPositiveButton(t("Salvar", "Save"), (confirm, which) -> {
                                    SharedPreferences.Editor edit = profile.edit().clear().putBoolean("saved", true);
                                    for (Option o : options) edit.putString(o.key, o.values[o.pick]);
                                    if (!edit.commit()) tell(title, t("Não foi possível salvar.", "Could not save."));
                                    else Toast.makeText(this, t("Perfil salvo", "Profile saved"), Toast.LENGTH_SHORT).show();
                                }).show();
                    }
                }).show();
    }

    void installShadersAndPlay() {
        if (!hasAccess()) { askAccess(); return; }
        // Cache readiness was checked off the UI thread by refresh(). Missing stages
        // are compiled by the renderer on first use, never installed from APK assets.
        launchAfterShaderInstall();
    }

    void launchAfterShaderInstall() {
        if (importingSaves || importingCpk || importingDriver || (modManager != null && modManager.busy())
                || (driverSelector != null && driverSelector.busy())) return;
        if (!readyForPlay) { refresh(); return; }
        try { SaveFiles.recover(); }
        catch (IOException e) { tell(t("Falha ao recuperar os saves", "Save recovery failed"), String.valueOf(e.getMessage())); return; }
        if (!save()) return;
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(ImportActivity.EXTRA_NAME, gameName());
        if (directMode()) {
            i.putExtra(ImportActivity.EXTRA_URI, gameUri());
            i.putExtra(ImportActivity.EXTRA_UPDATE_URI, updateUri());
            i.putExtra(ImportActivity.EXTRA_UPDATE_NAME, updateName());
        }
        launchedGame = true;
        startActivity(i);
    }

    void refresh() {
        if (installingShaders || importingSaves || isFinishing() || isDestroyed()) return;
        final int generation = ++refreshGeneration;
        play.setEnabled(false);
        status.setText(t("Verificando a instalação…", "Checking preparation…"));
        new Thread(() -> {
            final boolean access = hasAccess();
            if (access && new File(dataDir(), "game/installed.tsv").isFile()) runOnUiThread(() -> {
                if (generation == refreshGeneration && !isFinishing() && !isDestroyed()) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    status.setText(t("Verificando os dados instalados…", "Checking or moving installed game data…"));
                }
            });
            final boolean direct = directMode();
            final String base = gameUri();
            final boolean keys = hasKeys();
            final boolean directBaseReadable = access && direct && base != null && gameReadable();
            final boolean updateMissing = directBaseReadable && !updateReadable();
            boolean checkedReady = false;
            String storageProblem = "";
            try {
                checkedReady = direct ? directBaseReadable && !updateMissing && keys &&
                        base.equals(prefs().getString("direct_ready", "")) : access && installedReady();
            } catch (Exception e) {
                storageProblem = String.valueOf(e.getMessage());
                Log.w("P4G-Launch", "Could not check installed game data", e);
            }
            final boolean ready = checkedReady;
            final String storageError = storageProblem;
            final boolean readable = direct ? directBaseReadable : access && !ready && base != null && gameReadable();
            String preparationError;
            try { preparationError = access ? PreparationFiles.failure(InstalledGameFiles.shaders(installationDir(this))) : ""; }
            catch (Exception e) { preparationError = String.valueOf(e.getMessage()); }
            final String shaderError = preparationError;
            runOnUiThread(() -> {
                if (generation != refreshGeneration || isFinishing() || isDestroyed()) return;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                readyForPlay = ready;
                load();
                for (Option o : options) {
                    o.select(config.get(o.key));
                }
                build(ready);
                updateButtons(getWindow().getDecorView());
                play.setEnabled(true);
                if (ready) {
                    String revision;
                    try { revision = directMode() ? gameName() : InstalledGameFiles.revision(InstalledGameFiles.game(installationDir(this))); }
                    catch (Exception e) { revision = "unknown"; }
                    status.setText(t("Pronto para jogar · " + revision, "Ready to play · " + revision));
                    play.setText("¡JUGAR!");
                } else {
                    String game = gameUri() == null ? t("Nenhum jogo selecionado", "No game selected") : gameName();
                    status.setText(game + "\nprod.keys: " + (keys ? t("selecionado", "selected") : t("não selecionado", "not selected")) +
                        (!access ? t("\nPermita o acesso aos arquivos para preparar o jogo.", "\nAllow file access to prepare the game.") :
                         !readable && gameUri() != null ? t("\nSelecione novamente o arquivo do jogo.", "\nSelect the game file again.") :
                         updateMissing ? t("\nAtualização não encontrada. Selecione novamente o NSP ou use só o jogo base.", "\nUpdate not found. Select the NSP again or use just the base game.") :
                         readable && keys && directMode() ? t("\nMantenha o arquivo acessível para jogar.", "\nKeep the file accessible to play.") :
                         readable && keys ? t("\nInstale os dados para continuar. Mantenha o NSP/XCI até o fim da instalação.", "\nInstall the game data to continue. Keep the NSP/XCI until installation finishes.") : "") +
                        (storageError.isEmpty() ? "" : "\n" + storageError) +
                        (shaderError.isEmpty() ? "" : t("\nFalha ao gerar shaders:\n", "\nShader generation failed:\n") + shaderError));
                    play.setText(updateMissing ? t("Resolver atualização", "Resolve update") : t("Preparar jogo", "Prepare"));
                }
            });
        }, "P4G-setup-check").start();
    }

    void updateButtons(View v) {
        if (v instanceof CheckBox && v.getTag() instanceof Option) {
            Option o = (Option) v.getTag();
            ((CheckBox) v).setChecked("1".equals(o.values[o.pick]));
        } else if (v instanceof Button && v.getTag() instanceof Option) {
            Option o = (Option) v.getTag();
            ((Button) v).setText(label((Option) v.getTag()));
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int k = 0; k < g.getChildCount(); k++) updateButtons(g.getChildAt(k));
        }
    }

    void tell(String title, String message) {
        new P4gDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            tell(t("Não foi possível abrir o link", "Could not open link"), url);
        }
    }

    void askImport() {
        new P4gDialog.Builder(this)
                .setTitle(t("Importar saves", "Import saves"))
                .setMessage(t("Escolha um arquivo ZIP ou uma pasta com saves do Persona 4 Golden. "
                                + "Seus saves atuais serão copiados antes da importação.",
                        "Pick a ZIP file or a folder with Persona 4 Golden saves (for example, a ZIP exported earlier). Your current saves "
                                + "are copied aside first."))
                .setPositiveButton(t("Arquivo ZIP", "ZIP file"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip",
                                "application/x-zip-compressed", "application/octet-stream"});
                        startActivityForResult(i, PICK_ZIP);
                    }
                })
                .setNeutralButton(t("Pasta", "Folder"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
                    }
                })
                .setNegativeButton(t("Cancelar", "Cancel"), null)
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PREPARE) {
            installedCheck = new InstalledGameFiles.RecentCheck();
            if (result == RESULT_OK && gameUri() != null) {
                prefs().edit().putString(directMode() ? "direct_ready" : "p4g_prepared_uri", gameUri()).apply();
                Log.i("P4G-Launch", "Local game installation complete");
            }
            refresh();
            return;
        }
        if (request == PICK_DRIVER) {
            if (result == RESULT_OK && data != null && data.getData() != null) importDriver(data.getData());
            else showDrivers();
            return;
        }
        if (result != RESULT_OK || data == null) return;
        if (request == PICK_KEYS) {
            onKeysPicked(data);
            refresh();
            return;
        }
        if (data.getData() == null) return;
        if (request == PICK_GAME) {
            onGamePicked(data.getData());
            refresh();
            return;
        }
        if (request == PICK_UPDATE) {
            onUpdatePicked(data.getData());
            refresh();
            return;
        }
        if (request == PICK_CPK) {
            importCpk(data.getData());
            return;
        }
        if (request == PICK_ZIP || request == PICK_FOLDER) importSaves(request, data.getData());
    }

    boolean importingSaves;
    void importSaves(int request, Uri uri) {
        if (importingSaves) return;
        importingSaves = true;
        play.setEnabled(false);
        status.setText("Importing saves...");
        new Thread(() -> {
            SaveFiles.Result imported = null;
            String failure = null;
            try {
                imported = request == PICK_ZIP ? SaveFiles.importZip(getContentResolver(), uri)
                    : SaveFiles.importTree(getContentResolver(), uri);
            } catch (Exception e) { failure = String.valueOf(e.getMessage()); }
            final SaveFiles.Result result = imported;
            final String error = failure;
            runOnUiThread(() -> {
                importingSaves = false;
                if (isFinishing() || isDestroyed()) return;
                refresh();
                if (error != null) tell("Import failed", error);
                else if (result == null) tell("No saves found",
                    "Choose the folder or ZIP containing the P4G data0001.bin and data0001.binslot files. Include system.bin and system.binslot if they are in your backup.");
                else tell("Saves imported", result.files + " files imported. Select Load Game in P4G." +
                    (result.backup == null ? "" : "\n\nPrevious saves backed up to: user/" + result.backup));
            });
        }, "P4G-save-import").start();
    }

    private boolean importingDriver;
    private DriverSelector driverSelector;

    private void addDriverRow(LinearLayout root) {
        addRow(root, t("Driver gráfico", "GPU driver"), t("Selecionar", "Select"), v -> showDrivers());
    }

    private void showDrivers() {
        if (importingDriver) return;
        if (driverSelector == null) driverSelector = new DriverSelector(this);
        driverSelector.show();
    }

    private void importDriver(Uri uri) {
        if (importingDriver) return;
        importingDriver = true;
        String name = displayName(uri);
        Context app = getApplicationContext();
        Toast.makeText(this, t("Importando driver…", "Importing driver…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                CustomDrivers.install(app, uri, name);
                runOnUiThread(() -> finishDriverImport(null));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> finishDriverImport(message));
            }
        }, "P4G-driver-import").start();
    }

    private void finishDriverImport(String error) {
        importingDriver = false;
        if (isFinishing() || isDestroyed()) return;
        if (error != null) {
            new P4gDialog.Builder(this).setTitle("Driver import failed").setMessage(error)
                    .setPositiveButton("Back", (d, w) -> showDrivers()).show();
        } else {
            Toast.makeText(this, "Driver imported and selected.", Toast.LENGTH_SHORT).show();
            showDrivers();
        }
    }

    private boolean exportingLogs;

    private void addLogsRow(LinearLayout root) {
        addRow(root, "Logs", t("Exportar", "Export"), v -> exportLogs());
    }

    private void exportLogs() {
        if (exportingLogs) return;
        exportingLogs = true;
        Context app = getApplicationContext();
        Toast.makeText(this, t("Exportando logs…", "Exporting logs…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            DiagnosticLogs.Exported exported = null;
            String error = null;
            try {
                exported = DiagnosticLogs.export(app);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final DiagnosticLogs.Exported file = exported;
            final String failure = error;
            runOnUiThread(() -> {
                exportingLogs = false;
                if (isFinishing() || isDestroyed()) return;
                if (failure != null) tell(t("Não foi possível exportar os logs", "Could not export logs"), failure);
                else new P4gDialog.Builder(this)
                        .setTitle(t("Logs exportados", "Logs exported"))
                        .setMessage(Lang.f("Salvos em Downloads/%s.", "Saved to Downloads/%s.", file.name))
                        .setPositiveButton(t("Compartilhar", "Share"), (dialog, which) -> shareLogs(file))
                        .setNegativeButton(t("Fechar", "Close"), null)
                        .show();
            });
        }, "P4G-log-export").start();
    }

    private void shareLogs(DiagnosticLogs.Exported file) {
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, file.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(ClipData.newRawUri(file.name, file.uri));
        startActivity(Intent.createChooser(share, t("Compartilhar logs", "Share logs")));
    }

    void exportSaves() {
        try {
            java.io.File out = SaveFiles.export();
            // Listed at once by the file pickers and the Files app (written straight to disk, it would wait for a scan).
            if (out != null) android.media.MediaScannerConnection.scanFile(this, new String[] {out.getPath()}, null, null);
            if (out == null) tell(t("Nenhum save", "No saves"), t("Ainda não há saves.", "There are no saves yet."));
            else tell(t("Saves exportados", "Saves exported"),
                    Lang.f("Salvos em Downloads/%s. Use o botão Importar para restaurá-los.",
                            "Saved to Download/%s. The Import button takes it back as is.", out.getName()));
        } catch (Exception e) {
            tell(t("Não foi possível exportar", "Export failed"), String.valueOf(e.getMessage()));
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 100) refresh();
    }

    void askAccess() {
        if (Build.VERSION.SDK_INT < 30) {
            requestPermissions(new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    // config.txt: KEY=value lines, in order.
    static Map<String, String> readConfig(File f) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!f.canRead()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (eq > 0) out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static boolean writeConfig(File f, Map<String, String> config) {
        File temp = null;
        try {
            temp = File.createTempFile("config-", ".tmp", f.getAbsoluteFile().getParentFile());
            try (FileOutputStream stream = new FileOutputStream(temp);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                for (Map.Entry<String, String> e : config.entrySet()) {
                    writer.write(e.getKey() + "=" + e.getValue());
                    writer.write('\n');
                }
                writer.flush();
                stream.getFD().sync();
            }
            Files.move(temp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (temp != null) temp.delete();
        }
    }

    void load() {
        config.clear();
        config.putAll(readConfig(new File(dataDir(), "config.txt")));
    }

    static void putResolution(Map<String, String> target, String resolution) {
        putResolution(target, resolution, null);
    }

    static void putResolution(Map<String, String> target, String resolution, String nativeDimensions) {
        target.put("RESOLUTION", resolution);
        String dimensions;
        boolean handheld;
        if ("360p".equals(resolution)) { dimensions = "640x360"; handheld = true; }
        else if ("480p".equals(resolution)) { dimensions = "854x480"; handheld = true; }
        else if ("720p".equals(resolution)) { dimensions = "1280x720"; handheld = true; }
        else if ("1080p".equals(resolution)) { dimensions = "1920x1080"; handheld = false; }
        else if ("1440p".equals(resolution)) { dimensions = "2560x1440"; handheld = false; }
        else if ("2160p".equals(resolution)) { dimensions = "3840x2160"; handheld = false; }
        else if ("native".equalsIgnoreCase(resolution)) {
            dimensions = nativeDimensions == null ? "1920x1080" : nativeDimensions;
            handheld = false;
        } else { dimensions = "1280x720"; handheld = true; }
        target.put("NX_HANDHELD", handheld ? "1" : "0");
        target.put("resolution", dimensions);
        target.put("NX_RENDER_RES", dimensions);
        target.put("NX_RES", resolution);
    }

    static String nativeResolutionDimensions(Activity activity) {
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(size);
        if (size.x <= 0 || size.y <= 0) {
            size.x = activity.getResources().getDisplayMetrics().widthPixels;
            size.y = activity.getResources().getDisplayMetrics().heightPixels;
        }
        int width = Math.max(size.x, size.y) & ~1;
        int height = Math.min(size.x, size.y) & ~1;
        return Math.max(2, width) + "x" + Math.max(2, height);
    }

    static void removeRetiredSettings(Map<String, String> settings) {
        for (String key : new String[] {"RENDER_SCALE", "NX_ANISO_MAX", "NX_OVERLAY_FRAME_TIME", "language",
                "GRAPHICS", "NX_FSR", "NX_HUD_1080", "NX_SHADOWS", "NX_MALI_LOW_SHADOWS", "NX_DISABLE_BLOOM",
                "NX_VIBRATION", "NX_TOUCH_HIDE", "NX_PIPE_PREWARM", "NX_PIPE_PREWARM_LOAD", "NX_AFFINITY",
                "NX_P4G_MOTION_BLUR", "NX_LOD_BIAS"}) settings.remove(key);
    }

    boolean save() {
        if (!hasAccess()) { askAccess(); return false; }
        dataDir().mkdirs();
        if (!dataDir().isDirectory()) {
            tell(t("Erro", "Error"), t("Não foi possível abrir a pasta de configurações.", "Could not open the settings folder."));
            return false;
        }
        load();
        for (Option o : options) config.put(o.key, o.values[o.pick]);
        putResolution(config, config.get("RESOLUTION"), nativeResolutionDimensions(this));
        removeRetiredSettings(config);
        if (writeConfig(new File(dataDir(), "config.txt"), config)) {
            updateBackgroundEffect();
            return true;
        }
        tell(t("Erro ao salvar", "Could not save"), t("Verifique o espaço livre e a permissão de armazenamento.",
                "Check free space and storage permission."));
        load();
        for (Option o : options) o.select(config.get(o.key));
        updateButtons(getWindow().getDecorView());
        return false;
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private boolean importingCpk;
    private ModManager modManager;

    private void addModsRow(LinearLayout root) {
        addRow(root, t("Mods e traduções", "Mods and translations"), t("Gerenciar", "Manage"), v -> {
            if (!hasAccess()) { askAccess(); return; }
            if (importingCpk) return;
            if (modManager == null) modManager = new ModManager(this);
            modManager.show();
        });
    }

    void importCpk(Uri uri) {
        if (importingCpk) return;
        String name = displayName(uri);
        if (name == null || name.length() <= 4 || !name.toLowerCase(Locale.ROOT).endsWith(".cpk")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            tell(t("Erro ao importar CPK", "Could not import CPK"),
                    t("Selecione um arquivo .cpk com o nome original.",
                            "Select a .cpk file with its original filename."));
            return;
        }
        installCpk(uri, name);
    }

    private void installCpk(Uri uri, String target) {
        if (importingCpk) return;
        importingCpk = true;
        Toast.makeText(this, t("Importando CPK…", "Importing CPK…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String error = null;
            String installed = null;
            try {
                installed = copyCpk(uri, target);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final String failure = error;
            final String installedPath = installed;
            runOnUiThread(() -> {
                importingCpk = false;
                if (isFinishing() || isDestroyed()) return;
                if (failure == null) {
                    tell(t("CPK instalado", "CPK installed"), installedPath
                            + (installedPath.startsWith("mods/_translation/")
                                ? "\nEnable translation in Mods and translations to apply it."
                                : t("\nSerá aplicado quando o jogo for aberto novamente.", "\nApplied the next time you open the game.")));
                } else {
                    tell(t("Erro ao importar CPK", "Could not import CPK"), failure);
                }
            });
        }, "P4G-cpk-import").start();
    }

    private String copyCpk(Uri uri, String target) throws IOException {
        synchronized (ModFiles.class) {
            // Preserve the original RomFS filename, including language archives such as data_e.cpk.
            File romfs = ModFiles.translationRomfs(dataDir());
            if (!romfs.isDirectory() && !romfs.mkdirs()) throw new IOException("Could not create the mod folder.");
            File staged = File.createTempFile(".import-", ".tmp", romfs);
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                if (stream == null) throw new IOException("Could not open the selected file.");
                try (DataInputStream in = new DataInputStream(stream);
                     FileOutputStream out = new FileOutputStream(staged)) {
                    int magic = in.readInt();
                    if (magic != 0x43504B20) throw new IOException("The selected file is not a CRI CPK archive.");
                    out.write(new byte[] {'C', 'P', 'K', ' '});
                    SaveFiles.copy(in, out);
                    out.getFD().sync();
                }
                Files.move(staged.toPath(), new File(romfs, target).toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return dataDir().toPath().relativize(new File(romfs, target).toPath()).toString().replace(File.separatorChar, '/');
            } finally {
                if (staged.exists() && !staged.delete()) Log.w("P4G-Launch", "Could not remove staged CPK");
            }
        }
    }

    void addRow(LinearLayout root, String label, String button, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button b = new Button(this);
        P4gTheme.button(b, false);
        compactButton(b);
        b.setAllCaps(false);
        b.setMinWidth(dp(110));
        b.setMaxWidth(dp(160));
        b.setText(button);
        b.setOnClickListener(click);
        row.addView(b);
        root.addView(row);
    }

    private void compactButton(Button button) {
        button.setTextSize(13);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
    }

    private void applySafeAreaInsets(View view) {
        if (launcherLandscape()) return;
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int[] safe;
            if (Build.VERSION.SDK_INT >= 30) {
                safe = Api30Helper.safeArea(insets);
            } else {
                safe = new int[] {
                        insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()
                };
            }
            target.setPadding(safe[0], safe[1], safe[2], safe[3]);
            return insets;
        });
        view.requestApplyInsets();
    }
}
