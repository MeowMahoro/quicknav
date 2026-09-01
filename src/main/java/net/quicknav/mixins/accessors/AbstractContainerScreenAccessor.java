package net.quicknav.mixins.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int getX();

	@Accessor("topPos")
	int getY();

	@Accessor("imageWidth")
	int getImageWidth();

	@Accessor("imageHeight")
	int getImageHeight();
}
