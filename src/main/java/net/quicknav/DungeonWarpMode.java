package net.quicknav;

/**
 * How dungeon warp protection behaves for Quick Nav buttons.
 */
public enum DungeonWarpMode {
	/**
	 * Automatically protect every {@code /warp} button while inside a dungeon.
	 */
	AUTO,

	/**
	 * Only protect the buttons the player manually selected.
	 */
	MANUAL,

	/**
	 * No protection at all; behaviour is identical inside and outside dungeons.
	 */
	DISABLED
}
