
#include <nx/game.h>
#include <nx/runtime.h>
#include <cstdlib>

namespace nx {

const game::Info game::kInfo = {
    "p4g",
    "Persona 4 Golden",
    0x010062B01525C000ull,
    "io.github.synndras.p4gp",
    "private/p4g",
    0,
    "C433D980688380A6"
};

void game::after_init() {

#if defined(__ANDROID__)
    auto default_setting = [](const char* name, const char* value) {
        if (!std::getenv(name)) setenv(name, value, 0);
    };
    default_setting("NX_PIPE_PREWARM", "0");
    default_setting("NX_AFFINITY", "0");
    // The game asks for 16x anisotropic filtering on its ground and walls; on phone GPUs (Adreno 6xx, Mali-G5x/G7x)
    // every pixel of the 3D pass pays for it. 4x is hard to tell apart at 720p and saves fragment/texture time.
    // NX_MAX_ANISO=16 in config.txt restores the game's own value.
    default_setting("NX_MAX_ANISO", "4");
    // 0.4.5 bounded vertex uploads scan every index of every indexed draw on the replay thread; in the logs it trimmed
    // 0 bindings ("bound" always equals "needed"), so it only cost CPU. 0.4.2 did not have it. NX_VERTEX_UPLOAD_RANGES=1
    // turns it back on.
    default_setting("NX_VERTEX_UPLOAD_RANGES", "0");
#endif

    log("p4g: runtime initialized");
    log("p4g: beginning game-specific initialization");
}

void game::renderer_start() {
    log("p4g: renderer started");
}

void game::first_frame() {
    log("p4g: first frame presented");
}

} // namespace nx
