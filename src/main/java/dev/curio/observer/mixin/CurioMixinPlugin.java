package dev.curio.observer.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Selects the vanilla terrain fix only when the vanilla renderer is in use. */
public final class CurioMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Sodium overwrites setupTerrain and already anchors its renderer to Camera.getPos().
        // Applying our vanilla redirects to that replacement fails before the game can start.
        return !mixinClassName.equals("dev.curio.observer.mixin.WorldRendererMixin")
                || !FabricLoader.getInstance().isModLoaded("sodium");
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
