package net.quicknav;

import net.minecraft.network.chat.Component;

/**
 * Detects SkyBlock "paged storage" containers (Ender Chest pages and Backpack slots) based on
 * their public Hypixel container title format.
 * <p>
 * These containers are commonly covered by full-screen GUI overlays from third-party mods.
 * When such an overlay is active the Quick Nav tab buttons would float on top of it and swallow
 * clicks, so they are suppressed inside these containers. This is a self-contained heuristic and
 * intentionally references no third-party mod.
 */
public final class QuickNavStoragePages {
	private static final String ENDER_CHEST_PREFIX = "Ender Chest (";
	private static final String ENDER_CHEST_SUFFIX = ")";
	private static final String BACKPACK_MARKER = "Backpack";
	private static final String BACKPACK_SLOT_MARKER = "(Slot #";

	private QuickNavStoragePages() {}

	/**
	 * @return whether the given container title identifies a paged storage container
	 *         ("Ender Chest (1/9)" or "… Backpack (Slot #1)").
	 */
	public static boolean isStoragePage(Component title) {
		if (title == null) return false;
		String raw = title.getString().trim();
		if (raw.isEmpty()) return false;
		return isEnderChestPage(raw) || isBackpackPage(raw);
	}

	private static boolean isEnderChestPage(String title) {
		if (!title.startsWith(ENDER_CHEST_PREFIX) || !title.endsWith(ENDER_CHEST_SUFFIX)) return false;
		String inner = title.substring(ENDER_CHEST_PREFIX.length(), title.length() - ENDER_CHEST_SUFFIX.length());
		int slash = inner.indexOf('/');
		if (slash <= 0 || slash == inner.length() - 1) return false;
		return isPositiveInt(inner.substring(0, slash)) && isPositiveInt(inner.substring(slash + 1));
	}

	private static boolean isBackpackPage(String title) {
		int marker = title.indexOf(BACKPACK_SLOT_MARKER);
		if (marker <= 0) return false;
		if (!title.substring(0, marker).contains(BACKPACK_MARKER)) return false;
		String after = title.substring(marker + BACKPACK_SLOT_MARKER.length()).trim();
		return after.endsWith(")") && isPositiveInt(after.substring(0, after.length() - 1));
	}

	private static boolean isPositiveInt(String s) {
		if (s.isEmpty()) return false;
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) return false;
		}
		return true;
	}
}
