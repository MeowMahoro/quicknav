package net.quicknav.mixins.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.screens.Screen;

@Mixin(PopupScreen.class)
public interface PopupScreenAccessor {
	@Accessor("backgroundScreen")
	Screen getUnderlyingScreen();
}
