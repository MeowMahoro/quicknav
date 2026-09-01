package net.quicknav;

import net.azureaaron.dandelion.api.ButtonOption;
import net.azureaaron.dandelion.api.ConfigCategory;
import net.azureaaron.dandelion.api.Option;
import net.azureaaron.dandelion.api.OptionGroup;
import net.azureaaron.dandelion.api.OptionListener;
import net.azureaaron.dandelion.api.controllers.EnumController;
import net.azureaaron.dandelion.api.controllers.IntegerController;
import net.azureaaron.dandelion.api.controllers.ItemController;
import net.azureaaron.dandelion.api.controllers.StringController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import net.quicknav.datafixer.ItemStackComponentizationFixer;
import net.quicknav.gui.ItemSelectionPopup;
import net.quicknav.scheduler.Scheduler;

public class QuickNavCategory {
	public static ConfigCategory create(QuickNavConfig defaults, QuickNavConfig config) {
		return ConfigCategory.createBuilder()
				.id(QuickNavMod.id("config/quicknav"))
				.name(Component.translatable("quicknav.config.quickNav"))

				//Toggle
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.enableQuickNav"))
						.description(Component.translatable("quicknav.config.quickNav.enableQuickNav.@Tooltip"))
						.binding(defaults.enableQuickNav,
								() -> config.enableQuickNav,
								newValue -> config.enableQuickNav = newValue)
						.controller(QuickNavConfigUtils.createBooleanController())
						.build())

				//Dungeon warp protection mode
				.option(Option.<DungeonWarpMode>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.dungeonWarpMode"))
						.description(Component.translatable("quicknav.config.quickNav.dungeonWarpMode.@Tooltip"))
						.binding(defaults.dungeonWarpMode,
								() -> config.dungeonWarpMode,
								newValue -> config.dungeonWarpMode = newValue)
						.controller(EnumController.<DungeonWarpMode>createBuilder()
								.dropdown(true)
								.formatter(mode -> switch (mode) {
									case AUTO -> Component.translatable("quicknav.config.quickNav.dungeonWarpMode.auto");
									case MANUAL -> Component.translatable("quicknav.config.quickNav.dungeonWarpMode.manual");
									case DISABLED -> Component.translatable("quicknav.config.quickNav.dungeonWarpMode.disabled");
								})
								.build())
						.listener((option, type) -> {
							if (type == OptionListener.UpdateType.VALUE_CHANGE) {
								// Rebuild the screen so the manual button selection group appears/disappears.
								Scheduler.queueOpenScreen(QuickNavConfigManager.createGUI(null));
							}
						})
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.warpButtonsRedInDungeon"))
						.description(Component.translatable("quicknav.config.quickNav.warpButtonsRedInDungeon.@Tooltip"))
						.binding(defaults.warpButtonsRedInDungeon,
								() -> config.warpButtonsRedInDungeon,
								newValue -> config.warpButtonsRedInDungeon = newValue)
						.controller(QuickNavConfigUtils.createBooleanController())
						.build())

				//Manual button selection (only shown while the MANUAL mode is active)
				.groupIf(config.dungeonWarpMode == DungeonWarpMode.MANUAL, protectedButtonsGroup(defaults, config))

				//Buttons
				.group(quickNavButton(defaults.button1, config.button1, 1))
				.group(quickNavButton(defaults.button2, config.button2, 2))
				.group(quickNavButton(defaults.button3, config.button3, 3))
				.group(quickNavButton(defaults.button4, config.button4, 4))
				.group(quickNavButton(defaults.button5, config.button5, 5))
				.group(quickNavButton(defaults.button6, config.button6, 6))
				.group(quickNavButton(defaults.button7, config.button7, 7))
				.group(quickNavButton(defaults.button8, config.button8, 8))
				.group(quickNavButton(defaults.button9, config.button9, 9))
				.group(quickNavButton(defaults.button10, config.button10, 10))
				.group(quickNavButton(defaults.button11, config.button11, 11))
				.group(quickNavButton(defaults.button12, config.button12, 12))
				.group(quickNavButton(defaults.button13, config.button13, 13))
				.group(quickNavButton(defaults.button14, config.button14, 14))
				.build();
	}

	private static OptionGroup quickNavButton(QuickNavConfig.QuickNavItem defaultButton, QuickNavConfig.QuickNavItem button, int index) {
		return OptionGroup.createBuilder()
				.name(Component.translatable("quicknav.config.quickNav.button", index))
				.collapsed(true)
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.render"))
						.binding(defaultButton.render,
								() -> button.render,
								newValue -> button.render = newValue)
						.controller(QuickNavConfigUtils.createBooleanController())
						.build())
				.optionIf(Minecraft.getInstance().level != null, ButtonOption.createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.chooseSkyblockItem"))
						.description(Component.translatable("quicknav.config.quickNav.button.chooseSkyblockItem.@Tooltip"))
						.action(screen -> Minecraft.getInstance().gui.setScreen(new ItemSelectionPopup(screen, item -> {
							if (item == null) return;
							button.itemData.item = item.getItem();
							button.itemData.components = ItemStackComponentizationFixer.componentsAsString(item);
						})))
						.prompt(Component.translatable("quicknav.open"))
						.build())
				.option(Option.<Item>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.item.itemName"))
						.binding(defaultButton.itemData.item,
								() -> button.itemData.item,
								newValue -> button.itemData.item = newValue)
						.controller(ItemController.createBuilder().build())
						.build())
				.option(Option.<Integer>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.item.count"))
						.binding(defaultButton.itemData.count,
								() -> button.itemData.count,
								newValue -> button.itemData.count = newValue)
						.controller(IntegerController.createBuilder().range(1, 99).build())
						.build())
				.option(Option.<String>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.item.components"))
						.description(Component.translatable("quicknav.config.quickNav.button.item.components.@Tooltip"))
						.binding(defaultButton.itemData.components,
								() -> button.itemData.components,
								newValue -> button.itemData.components = newValue)
						.controller(StringController.createBuilder().build())
						.build())
				.option(Option.<String>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.uiTitle"))
						.binding(defaultButton.uiTitle,
								() -> button.uiTitle,
								newValue -> button.uiTitle = newValue)
						.controller(StringController.createBuilder().build())
						.build())
				.option(Option.<String>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.tooltip"))
						.description(Component.translatable("quicknav.config.quickNav.button.tooltip.@Tooltip"))
						.binding(defaultButton.tooltip,
								() -> button.tooltip,
								newValue -> button.tooltip = newValue)
						.controller(StringController.createBuilder().build())
						.build())
				.option(Option.<String>createBuilder()
						.name(Component.translatable("quicknav.config.quickNav.button.clickEvent"))
						.binding(defaultButton.clickEvent,
								() -> button.clickEvent,
								newValue -> button.clickEvent = newValue)
						.controller(StringController.createBuilder().build())
						.build())
				.build();
	}

	private static OptionGroup protectedButtonsGroup(QuickNavConfig defaults, QuickNavConfig config) {
		OptionGroup.Builder builder = OptionGroup.createBuilder()
				.name(Component.translatable("quicknav.config.quickNav.manualSelection"))
				.description(Component.translatable("quicknav.config.quickNav.manualSelection.@Tooltip"))
				.collapsed(true);
		for (int i = 0; i < 14; i++) {
			int buttonIndex = i;
			builder.option(Option.<Boolean>createBuilder()
					.name(Component.translatable("quicknav.config.quickNav.button", i + 1))
					.binding(defaults.isProtectedButton(buttonIndex),
							() -> config.isProtectedButton(buttonIndex),
							newValue -> config.protectedButtons[buttonIndex] = newValue)
					.controller(QuickNavConfigUtils.createBooleanController())
					.build());
		}
		return builder.build();
	}
}
