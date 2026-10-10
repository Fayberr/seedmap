package dev.fayber.seedmap.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.fayber.seedmap.gui.SeedMapSettings;

/**
 * Optional Mod Menu entrypoint. Fabric resolves this class only when Mod Menu
 * requests the "modmenu" entrypoint, so no Mod Menu dependency is required.
 */
public final class SeedMapModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Pass through the real parent for correct return navigation.
        return SeedMapSettings::create;
    }
}
