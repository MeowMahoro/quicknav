package net.quicknav;

import java.util.function.Predicate;

/**
 * Compatibility hooks with other mods.
 */
public class QuickNavCompat {
	private static Predicate<String> hiddenModElementsProvider = _ -> false;

	/**
	 * Register a provider used by the Catharsis mod to hide GUI elements.
	 */
	public static void hiddenGuiElements(Predicate<String> provider) {
		hiddenModElementsProvider = provider;
	}

	public static boolean isGuiElementHidden(String element) {
		return hiddenModElementsProvider.test(element);
	}
}
