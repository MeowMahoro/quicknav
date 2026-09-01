package net.quicknav;

import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

/**
 * Lightweight SkyBlock detection and shared Minecraft helpers for the Quick Nav mod.
 * <p>
 * This is a self-contained replacement for the (much larger) {@code Utils} class of Skyblocker.
 * It only detects whether the player is currently on a Hypixel SkyBlock server, which is all the
 * Quick Navigation feature requires.
 */
public final class QuickNavUtils {
	private static final String HYPIXEL_SKYBLOCK_NAMESPACE = "hypixel_skyblock";
	private static final HolderLookup.Provider LOOKUP = VanillaRegistries.createLookup();

	private static boolean isOnSkyblock = false;

	private QuickNavUtils() {}

	public static boolean isOnSkyblock() {
		return isOnSkyblock;
	}

	/**
	 * Updates the {@link #isOnSkyblock} flag. Called every client tick.
	 */
	public static void update() {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || client.player == null) {
			isOnSkyblock = false;
			return;
		}

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) { // Pretend we're always in skyblock when in dev
			isOnSkyblock = true;
			return;
		}

		isOnSkyblock = isConnectedToHypixel(client) && hasSkyblockSidebar(client);
	}

	private static boolean isConnectedToHypixel(Minecraft client) {
		String serverAddress = (client.getCurrentServer() != null) ? client.getCurrentServer().ip.toLowerCase(Locale.ENGLISH) : "";
		String serverBrand = (client.player != null && client.player.connection != null && client.player.connection.serverBrand() != null) ? client.player.connection.serverBrand() : "";

		return serverAddress.contains("hypixel.net") || serverAddress.contains("hypixel.io") || serverBrand.contains("Hypixel BungeeCord");
	}

	private static boolean hasSkyblockSidebar(Minecraft client) {
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BY_ID.apply(1));

		if (objective != null) {
			String title = ChatFormatting.stripFormatting(objective.getDisplayName().getString());
			if (title != null && title.contains(HYPIXEL_SKYBLOCK_NAMESPACE)) return true;
		}

		// Fall back to checking the tab list for the profile name line which is always present in SkyBlock
		if (client.getConnection() != null) {
			for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
				Component displayName = entry.getTabListDisplayName();
				if (displayName != null && displayName.getString().startsWith("Profile: ")) return true;
			}
		}

		return false;
	}

	/**
	 * Tries to get the dynamic registry manager instance currently in use or else returns a static vanilla lookup.
	 */
	public static HolderLookup.Provider getRegistryWrapperLookup() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.getConnection() != null && client.getConnection().registryAccess() != null ? client.getConnection().registryAccess() : LOOKUP;
	}
}
