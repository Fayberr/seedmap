package dev.fayber.seedmap.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Oversized 3D models use a separate PIP color render target (vanilla usage
 * 13 = COPY_DST | TEXTURE_BINDING | RENDER_ATTACHMENT). Only SeedMap's
 * oversized-item capture needs that target to additionally allow COPY_SRC.
 * Other picture-in-picture renderers retain the default usage flags.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class OversizedItemCopySourceMixin {
    @ModifyArg(
            method = "prepareTexturesAndProjection",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createTexture(Ljava/util/function/Supplier;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;", ordinal = 0),
            index = 1)
    private int seedmap$enableOversizedItemReadback(int usage) {
        return (Object) this instanceof OversizedItemRenderer
                ? usage | GpuTexture.USAGE_COPY_SRC : usage;
    }
}
