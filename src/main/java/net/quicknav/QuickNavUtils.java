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
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
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
	/**
	 * The scoreboard area name shown while inside a Catacombs dungeon instance.
	 */
	private static final String DUNGEON_AREA_NAME = "The Catacombs";
	private static final HolderLookup.Provider LOOKUP = VanillaRegistries.createLookup();

	private static boolean isOnSkyblock = false;
	private static boolean isInDungeon = false;

	private QuickNavUtils() {}

	public static boolean isOnSkyblock() {
		return isOnSkyblock;
	}

	/**
	 * @return whether the player is currently inside a Catacombs dungeon instance
	 */
	public static boolean isInDungeon() {
		return isInDungeon;
	}

	/**
	 * Updates the {@link #isOnSkyblock} and {@link #isInDungeon} flags. Called every client tick.
	 */
	public static void update() {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || client.player == null) {
			isOnSkyblock = false;
			isInDungeon = false;
			return;
		}

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) { // Pretend we're always in skyblock when in dev
			isOnSkyblock = true;
			isInDungeon = false;
			return;
		}

		isOnSkyblock = isConnectedToHypixel(client) && hasSkyblockSidebar(client);
		isInDungeon = isOnSkyblock && hasDungeonScoreboard(client);
	}

	/**
	 * Checks the sidebar scoreboard for the Catacombs dungeon area name. Hypixel displays
	 * "The Catacombs" as the area line while inside a dungeon instance (as opposed to the
	 * "Dungeon Hub" shown outside of one).
	 */
	private static boolean hasDungeonScoreboard(Minecraft client) {
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BY_ID.apply(1));
		if (objective == null) return false;

		for (ScoreHolder scoreHolder : scoreboard.getTrackedPlayers()) {
			if (!scoreboard.listPlayerScores(scoreHolder).containsKey(objective)) continue;
			PlayerTeam team = scoreboard.getPlayersTeam(scoreHolder.getScoreboardName());
			if (team == null) continue;

			String line = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();
			String stripped = ChatFormatting.stripFormatting(line);
			if (stripped != null && stripped.contains(DUNGEON_AREA_NAME)) return true;
		}
		return false;
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
