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
import com.mrfuzzihead.ic2tweaks.WrenchConfig;
import com.mrfuzzihead.ic2tweaks.enet.EnetOverloadConfig;
import com.mrfuzzihead.ic2tweaks.jetpack.JetpackConfig;

/**
 * Late-mixin loader. IC2 is a regular mod, so the IC2 tweaks are late mixins.
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
        File configFile = new File(Launch.minecraftHome, "config/" + IC2Tweaks.MODID + ".cfg");
        EnetOverloadConfig.synchronizeConfiguration(configFile);
        WrenchConfig.synchronizeConfiguration(configFile);
        JetpackConfig.synchronizeConfiguration(configFile);
        return IMixins.getLateMixins(Mixins.class, loadedMods);
    }
}
