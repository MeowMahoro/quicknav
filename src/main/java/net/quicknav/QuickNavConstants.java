package net.quicknav;

import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Holds generic static constants for the Quick Nav mod.
 */
public interface QuickNavConstants {
	Supplier<MutableComponent> PREFIX = () -> Component.empty()
			.append(Component.literal("[").withStyle(ChatFormatting.GRAY))
			.append(createQuickNavGradient("QuickNav"))
			.append(Component.literal("] ").withStyle(ChatFormatting.GRAY));

	static Component createQuickNavGradient(String string) {
		MutableComponent component = Component.empty();
		for (int i = 0; i < string.length(); i++) {
			component.append(Component.literal(string.substring(i, i + 1))
					.withColor(interpolate(0x00FF4C, 0x14D0FF, (float) i / (string.length() - 1))));
		}
		return component;
	}

	private static int interpolate(int start, int end, float t) {
		int sr = (start >> 16) & 0xFF;
		int sg = (start >> 8) & 0xFF;
		int sb = start & 0xFF;
		int er = (end >> 16) & 0xFF;
		int eg = (end >> 8) & 0xFF;
		int eb = end & 0xFF;
		int r = (int) (sr + (er - sr) * t);
		int g = (int) (sg + (eg - sg) * t);
		int b = (int) (sb + (eb - sb) * t);
		return (r << 16) | (g << 8) | b;
	}
}
