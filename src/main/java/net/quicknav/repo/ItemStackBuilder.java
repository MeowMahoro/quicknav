package net.quicknav.repo;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.quicknav.datafixer.ItemStackComponentizationFixer;
import net.quicknav.utils.LegacyStringNbtReader;
import net.quicknav.utils.TextTransformer;
import org.slf4j.Logger;

/**
 * Builds {@link ItemStack}s from {@link SkyblockItem}s. This is a trimmed-down port of Skyblocker's
 * {@code ItemStackBuilder}: it skips pet level number injection and the legacy item fixer is replaced
 * with the quicknav {@link ItemStackComponentizationFixer}.
 */
public class ItemStackBuilder {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static ItemStack fromSkyblockItem(SkyblockItem item) {
		try {
			CompoundTag nbt = new CompoundTag();
			CompoundTag tag = LegacyStringNbtReader.parse(item.nbttag());

			//Construct the legacy (pre-1.20.5) NBT
			nbt.put("tag", tag);
			nbt.putString("id", item.minecraftItemId());
			nbt.putShort("Damage", (short) item.damage());
			nbt.putInt("Count", 1);

			ItemStack stack = ItemStackComponentizationFixer.fixUpItem(nbt);

			//The item couldn't be fixed up
			if (stack.is(Items.AIR)) {
				LOGGER.error("[QuickNav ItemStackBuilder] Failed to build item with skyblock id: {}!", item.skyblockId());
				return createErrorStack(item.skyblockId());
			}

			// The data fixer keeps the legacy "ExtraAttributes" tag nested inside custom_data, but real
			// (server-sent) Hypixel items carry the skyblock item id at the root of custom_data. The
			// resource pack's item models - and therefore the item's texture - resolve from that root id,
			// so the nested tag has to be flattened to match. See Skyblocker's LegacyItemStackFixer.
			CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
			if (customData != null) {
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData.copyTag().getCompoundOrEmpty("ExtraAttributes")));
			}

			// Hide the vanilla attribute and enchantment tooltip lines, just like real skyblock items do.
			TooltipDisplay tooltipDisplay = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
					.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
					.withHidden(DataComponents.ENCHANTMENTS, true);
			stack.set(DataComponents.TOOLTIP_DISPLAY, tooltipDisplay);

			stack.set(DataComponents.CUSTOM_NAME, TextTransformer.fromLegacy(item.displayName()));
			stack.set(DataComponents.LORE, new ItemLore(item.lore().stream().map(line -> (Component) TextTransformer.fromLegacy(line)).toList()));

			return stack;
		} catch (Exception e) {
			LOGGER.error("[QuickNav ItemStackBuilder] Failed to build item with skyblock id: {}!", item.skyblockId(), e);
		}

		return createErrorStack(item.skyblockId());
	}

	private static ItemStack createErrorStack(String skyblockItemId) {
		ItemStack errorStack = new ItemStack(Items.BARRIER);
		errorStack.set(DataComponents.CUSTOM_NAME, Component.nullToEmpty(skyblockItemId));

		return errorStack;
	}
}
