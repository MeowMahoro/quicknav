package net.quicknav;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.azureaaron.dandelion.api.ConfigManager;
import net.azureaaron.dandelion.api.ConfigType;
import net.azureaaron.dandelion.api.DandelionConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import net.quicknav.scheduler.Scheduler;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class QuickNavConfigManager {
	private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("quicknav.json");
	private static final ConfigManager<QuickNavConfig> CONFIG_MANAGER = ConfigManager.create(QuickNavConfig.class, CONFIG_FILE, UnaryOperator.identity());

	public static QuickNavConfig get() {
		return CONFIG_MANAGER.instance();
	}

	/**
	 * This method is caller sensitive and can only be called by the mod initializer.
	 */
	public static void init() {
		CONFIG_MANAGER.load();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(literal(QuickNavMod.NAMESPACE)
				.then(configLiteral("config"))
				.then(configLiteral("options"))
		));
	}

	public static Screen createGUI(@Nullable Screen parent) {
		return createGUI(parent, "");
	}

	public static Screen createGUI(@Nullable Screen parent, String search) {
		return DandelionConfigScreen.create(CONFIG_MANAGER, (defaults, config, builder) -> builder
				.title(Component.translatable("quicknav.config.title"))
				.category(QuickNavCategory.create(defaults, config))
				.search(search)
		).generateScreen(parent, ConfigType.MOUL_CONFIG);
	}

	/**
	 * Registers a command argument to open the config.
	 *
	 * @return the command builder
	 */
	private static LiteralArgumentBuilder<FabricClientCommandSource> configLiteral(String name) {
		return literal(name).executes(Scheduler.queueOpenScreenCommand(() -> createGUI(null)))
				.then(argument("search", StringArgumentType.greedyString()).executes(ctx -> Scheduler.queueOpenScreen(createGUI(null, ctx.getArgument("search", String.class)))));
	}
}
