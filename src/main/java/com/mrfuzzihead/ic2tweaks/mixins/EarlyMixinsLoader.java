package com.mrfuzzihead.ic2tweaks.mixins;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod/early-mixin loader. Vanilla classes such as {@code EntityPlayer} are loaded before the late-mixin
 * phase (the runtime check for the armour-HUD fix proved it), so that mixin has to be registered as an early
 * mixin. GTNHMixins discovers early loaders through the coremod list, hence this class implements both
 * {@link IFMLLoadingPlugin} and {@link IEarlyMixinLoader}.
 */
@IFMLLoadingPlugin.Name("IC2TweaksCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public final class EarlyMixinsLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.ic2tweaks.early.json";
    }

    @Nonnull
    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
