package net.quicknav.mixins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class QuickNavScreenMixin {
	@Inject(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractDeferredSubtitles()V"))
	protected void quicknav$extractUnselectedQuickNavButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = At.Shift.AFTER))
	protected void quicknav$extractSelectedQuickNavButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {}
}
