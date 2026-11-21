package io.github.p4grecomp;

public final class LauncherSettingsTest {
    public static void main(String[] args) throws Exception {
        Launcher.Option option = new Launcher.Option("NX_PIPE_PREWARM_LOAD", "Carga", "Load",
                new String[] {"light", "standard"}, new String[] {"Leve", "Padrão"},
                new String[] {"Light", "Standard"}, 1);
        option.select("light");
        check(option.pick == 0);
        option.select("invalid-old-profile-value");
        check(option.pick == 1);
        option.select(null);
        check(option.pick == option.defaultPick);
        check(Launcher.optionGroup("NX_OVERLAY_SIZE").equals("overlay"));
        check(Launcher.isOverlayToggle("NX_SHOW_FPS"));
        check(Launcher.isOverlayToggle("NX_OVERLAY_RAM"));
        check(!Launcher.isOverlayToggle("NX_OVERLAY_SIZE"));
        check(Launcher.optionGroup("NX_TOUCH_OPACITY").equals("controls"));
        check(Launcher.optionGroup("NX_TOUCH_SHOW").equals("controls"));
        check(Launcher.optionGroup("NX_MAX_ANISO").equals("quick"));
        check(Launcher.optionGroup("NX_TRACE_PERF").equals("logs"));
        check(java.util.Arrays.equals(Launcher.presetValues("performance"),
                new String[] {"480p", "30", "1"}));
        check(java.util.Arrays.equals(Launcher.presetValues("normal"),
                new String[] {"720p", "30", "4"}));
        check(java.util.Arrays.equals(Launcher.presetValues("quality"),
                new String[] {"1080p", "30", "16"}));
        check(Launcher.optionGroup("NX_GAMEPAD_LAYOUT").equals("controls"));
        check(Launcher.optionGroup("NX_GAMEPAD_MAP").equals("controls"));
        java.util.Map<String, String> config = new java.util.HashMap<>();
        config.put("NX_FSR", "1");
        config.put("NX_SHADOWS", "0");
        config.put("NX_PIPE_PREWARM", "2");
        config.put("NX_TOUCH_SIZE", "125");
        Launcher.removeRetiredSettings(config);
        check(config.size() == 1 && "125".equals(config.get("NX_TOUCH_SIZE")));
        Launcher.putResolution(config, "360p");
        check("640x360".equals(config.get("resolution")));
        check("640x360".equals(config.get("NX_RENDER_RES")));
        check("1".equals(config.get("NX_HANDHELD")));
        java.io.File dir = java.nio.file.Files.createTempDirectory("p4g-config").toFile();
        java.io.File file = new java.io.File(dir, "config.txt");
        try {
            config.put("NAME", "Configuração");
            check(Launcher.writeConfig(file, config));
            check(Launcher.readConfig(file).equals(config));
            java.util.Map<String, String> broken = new java.util.LinkedHashMap<String, String>() {
                @Override public java.util.Set<java.util.Map.Entry<String, String>> entrySet() {
                    throw new IllegalStateException("interrupted write");
                }
            };
            check(!Launcher.writeConfig(file, broken));
            check(Launcher.readConfig(file).equals(config));
            check(dir.list().length == 1);
        } finally {
            file.delete();
            dir.delete();
        }
        check(Launcher.DEFAULT_LANDSCAPE);
        System.out.println("Launcher settings tests passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
