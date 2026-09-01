package net.quicknav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;
import net.quicknav.scheduler.MessageScheduler;
import net.quicknav.scheduler.Scheduler;
import net.quicknav.texture.FallbackedTexture;

/**
 * Main class for the standalone Quick Nav mod. This class will be instantiated by Fabric. Do not instantiate this class.
 */
public class QuickNavMod implements ClientModInitializer {
	public static final String NAMESPACE = "quicknav";
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	@Override
	public void onInitializeClient() {
		FallbackedTexture.init();
		QuickNavConfigManager.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			QuickNavUtils.update();
			Scheduler.INSTANCE.tick();
			MessageScheduler.INSTANCE.tick();
		});
	}
}
