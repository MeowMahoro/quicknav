package net.quicknav.repo;

import java.util.List;

/**
 * A lightweight model of a SkyBlock item, extracted from the NEU repository.
 * <p>
 * The {@code nbttag} is the legacy (pre-1.20.5) NBT string as shipped by the NEU repository; it is converted
 * to an actual {@link net.minecraft.world.item.ItemStack} lazily by {@link ItemStackBuilder} on first use.
 */
public record SkyblockItem(String skyblockId, String displayName, String minecraftItemId, int damage, String nbttag, List<String> lore) {
}
