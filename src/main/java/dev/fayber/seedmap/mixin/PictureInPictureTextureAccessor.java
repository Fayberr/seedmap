package dev.fayber.seedmap.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.jspecify.annotations.Nullable;

@Mixin(PictureInPictureRenderer.class)
public interface PictureInPictureTextureAccessor {
    @Accessor("texture")
    @Nullable GpuTexture seedmap$getTexture();
}
