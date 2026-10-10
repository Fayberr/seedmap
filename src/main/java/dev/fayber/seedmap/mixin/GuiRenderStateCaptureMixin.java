package dev.fayber.seedmap.mixin;

import dev.fayber.seedmap.gui.RenderedItemCapture;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Associates SeedMap's own item icons with their exact render state.
 * This works for both regular atlas items and oversized 3D/PIP items,
 * without guessing their positions during the later render pass.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateCaptureMixin {
    @Inject(method = "addItem", at = @At("HEAD"))
    private void seedmap$trackItem(GuiItemRenderState item, CallbackInfo ci) {
        RenderedItemCapture.onItemQueued(item);
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void seedmap$clearFrame(CallbackInfo ci) {
        RenderedItemCapture.endFrame();
    }
}
