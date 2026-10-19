package com.mrfuzzihead.ic2tweaks.mixins;

import java.io.File;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.launchwrapper.Launch;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.mrfuzzihead.ic2tweaks.IC2Tweaks;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;

/**
 * Late-mixin loader. IC2 is a regular mod, so the energy-net mixins are late mixins.
 * <p>
 * {@code getMixins()} runs before {@code FMLPreInitializationEvent}, so the config file is resolved and
 * read here (using {@link Launch#minecraftHome}) to let the {@code setApplyIf} conditions see the real
 * {@code ic2tweaks.cfg} values instead of the hard-coded field defaults.
 */
@LateMixin
public class LateMixinsLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.ic2tweaks.late.json";
    }

    @Nonnull
    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        EnetOverloadConfig
            .synchronizeConfiguration(new File(Launch.minecraftHome, "config/" + IC2Tweaks.MODID + ".cfg"));
        return IMixins.getLateMixins(Mixins.class, loadedMods);
    }
}
