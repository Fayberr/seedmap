package dev.fayber.seedmap.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Minecraft's GUI item atlas is normally sampled only and lacks COPY_SRC.
 * The actual rendered pixels are required to build accurate silhouettes for
 * 3D block/item icons. Add only the readback usage bit to its color texture.
 * Minecraft 26.3 uses the String-label overload here (unlike PIP's Supplier
 * overload). The depth texture's separate invocation stays unchanged.
 */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasCopySourceMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createTexture(Ljava/lang/String;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;", ordinal = 0),
            index = 1)
    private int seedmap$enableGuiItemAtlasReadback(int usage) {
        return usage | GpuTexture.USAGE_COPY_SRC;
    }
}
