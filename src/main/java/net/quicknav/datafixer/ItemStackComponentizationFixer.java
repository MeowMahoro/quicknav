package net.quicknav.datafixer;

import static net.azureaaron.legacyitemdfu.LegacyItemStackFixer.getFixer;
import static net.azureaaron.legacyitemdfu.LegacyItemStackFixer.getFirstVersion;
import static net.azureaaron.legacyitemdfu.LegacyItemStackFixer.getLatestVersion;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;

import net.azureaaron.legacyitemdfu.TypeReferences;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import net.quicknav.QuickNavUtils;

/**
 * Contains a data fixer to convert legacy item NBT to the new components system, among other fixers related to the item components system.
 *
 * @see net.minecraft.util.datafix.fixes.ItemStackComponentizationFix
 */
public class ItemStackComponentizationFixer {
	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * Upgrades a legacy (pre-1.20.5, and even pre-1.13 flattening) item NBT to the current
	 * components system using AzureAaron's {@code legacy-item-dfu}. Unlike vanilla's
	 * {@code DataFixers} (which only handles recent versions), this covers all renames such
	 * as {@code minecraft:skull} → {@code minecraft:player_head}, {@code minecraft:wool} →
	 * {@code minecraft:white_wool}, etc., which are present in NEU's Firmament data.
	 *
	 * @return the upgraded {@link ItemStack}, or {@link ItemStack#EMPTY} if it couldn't be fixed up
	 */
	public static ItemStack fixUpItem(CompoundTag nbt) {
		RegistryOps<Tag> ops = QuickNavUtils.getRegistryWrapperLookup().createSerializationContext(NbtOps.INSTANCE);
		Dynamic<Tag> fixed = getFixer()
				.update(TypeReferences.LEGACY_ITEM_STACK, new Dynamic<>(ops, nbt), getFirstVersion(), getLatestVersion());

		return ItemStack.CODEC.parse(fixed)
				.setPartial(ItemStack.EMPTY)
				.resultOrPartial(ItemStackComponentizationFixer::logError)
				.get();
	}

	private static void logError(String error) {
		LOGGER.error("[QuickNav Item Fixer] Failed to fix up item: {}", error);
	}

	public static String componentsAsString(ItemStack stack) {
		return componentsAsString(stack.getComponentsPatch());
	}

	/**
	 * Modified version of {@link net.minecraft.commands.arguments.item.ItemInput#serialize(net.minecraft.core.HolderLookup.Provider)} to only care about changed components.
	 *
	 * @return The components as a string in the format that the {@code /give} command accepts.
	 */
	public static String componentsAsString(DataComponentPatch components) {
		RegistryOps<Tag> nbtRegistryOps = QuickNavUtils.getRegistryWrapperLookup().createSerializationContext(NbtOps.INSTANCE);

		return Arrays.toString(components.entrySet().stream().map(entry -> {
			DataComponentType<?> componentType = entry.getKey();
			Identifier componentId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(componentType);
			if (componentId == null) return null;

			Optional<?> component = entry.getValue();
			if (component.isEmpty()) return "!" + componentId;

			Optional<Tag> encodedComponent = TypedDataComponent.createUnchecked(componentType, component.get()).encodeValue(nbtRegistryOps).result();

			if (encodedComponent.isEmpty()) return null;
			return componentId + "=" + encodedComponent.orElseThrow();
		}).filter(Objects::nonNull).toArray());
	}

	public static ItemStack fromItemString(String itemString, int count) {
		ItemParser reader = new ItemParser(QuickNavUtils.getRegistryWrapperLookup());

		try {
			ItemInput result = reader.parse(new StringReader(itemString));
			ItemStack stack = new ItemStack(result.item(), count);

			//Vanilla skips validation with /give so we will too
			stack.applyComponents(result.components());

			return stack;
		} catch (Exception _) {}

		return ItemStack.EMPTY;
	}

	/**
	 * Constructs an {@link ItemStack} from an {@code itemId}, with item components in string format as returned by {@link #componentsAsString(ItemStack)}, and with a specified stack count.
	 *
	 * @return an {@link ItemStack} or {@link ItemStack#EMPTY} if there was an exception thrown.
	 */
	public static ItemStack fromComponentsString(String itemId, int count, String componentsString) {
		return fromItemString(itemId + componentsString, count);
	}
}
