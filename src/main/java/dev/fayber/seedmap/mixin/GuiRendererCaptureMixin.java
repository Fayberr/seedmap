package dev.fayber.seedmap.mixin;

import dev.fayber.seedmap.gui.RenderedItemCapture;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft already renders each unique item model into its GUI atlas.
 * Observe that finished atlas slot only when SeedMap asks for its silhouette.
 * The original item draw is never modified.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererCaptureMixin {
    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"))
    private void seedmap$captureItem(GuiItemRenderState item,
                                      GuiItemAtlas.SlotView slot, CallbackInfo ci) {
        RenderedItemCapture.onResolved(item, slot);
    }
}
