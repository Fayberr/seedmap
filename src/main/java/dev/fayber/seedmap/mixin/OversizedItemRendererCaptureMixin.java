package dev.fayber.seedmap.mixin;

import dev.fayber.seedmap.gui.RenderedItemCapture;
import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch GUI item models too large for the vanilla item atlas, including
 * resource-pack-replaced 3D boats and other oversized item models.
 */
@Mixin(OversizedItemRenderer.class)
public abstract class OversizedItemRendererCaptureMixin {
    @Inject(method = "blitTexture", at = @At("HEAD"))
    private void seedmap$captureOversized(OversizedItemRenderState item,
                                           GuiRenderState gui, CallbackInfo ci) {
        var texture = ((PictureInPictureTextureAccessor) (Object) this).seedmap$getTexture();
        RenderedItemCapture.onOversizedResolved(item.guiItemRenderState(), texture,
                item.x0(), item.y0(), item.x1(), item.y1());
    }
}
