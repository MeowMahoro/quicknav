package net.quicknav.quicknav;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import java.time.Duration;
import java.util.Locale;

import net.quicknav.QuickNavConfig;
import net.quicknav.QuickNavConfigManager;
import net.quicknav.QuickNavConstants;
import net.quicknav.QuickNavMod;
import net.quicknav.QuickNavUtils;
import net.quicknav.gui.AbstractPopupScreen;
import net.quicknav.mixins.accessors.AbstractContainerScreenAccessor;
import net.quicknav.mixins.accessors.PopupScreenAccessor;
import net.quicknav.scheduler.MessageScheduler;
import net.quicknav.texture.FallbackedTexture;

import org.jspecify.annotations.Nullable;

@Environment(value = EnvType.CLIENT)
public class QuickNavButton extends AbstractWidget {
	private static final long TOGGLE_DURATION = 1000;
	@SuppressWarnings("unchecked")
	private static final @Nullable FallbackedTexture<Identifier>[] TAB_TEXTURES = new FallbackedTexture[14];
	@SuppressWarnings("unchecked")
	private static final @Nullable FallbackedTexture<Identifier>[] TAB_TEXTURES_SELECTED = new FallbackedTexture[14];

	private static final Tooltip DUNGEON_DISABLED_TOOLTIP = Tooltip.create(Component.translatable("quicknav.quickNav.disabledInDungeon"));

	private final int index;
	private final boolean toggled;
	private final String command;
	private final ItemStack icon;
	protected final Tooltip tooltip;

	private boolean temporaryToggled = false;
	private long toggleTime;
	private boolean showingDungeonDisabledTooltip = false;

	// Stores whether the button is currently rendering in front of the main inventory background.
	private boolean renderInFront;
	private int alpha = 255;

	/**
	 * Checks if the current tab is a top tab based on its index.
	 *
	 * @return true if the index is less than 7, false otherwise.
	 */
	private boolean isTopTab() {
		return index < 7;
	}

	public boolean toggled() {
		return toggled || temporaryToggled;
	}

	public void setRenderInFront(boolean renderInFront) {
		this.renderInFront = renderInFront;
	}

	public float getAlpha() {
		return alpha / 255f;
	}

	/**
	 * Constructs a new QuickNavButton with the given parameters.
	 *
	 * @param index   the index of the button.
	 * @param toggled the toggled state of the button.
	 * @param command the command to execute when the button is clicked.
	 * @param icon    the icon to display on the button.
	 * @param tooltip the tooltip to show when hovered
	 */
	public QuickNavButton(int index, boolean toggled, String command, ItemStack icon, String tooltip) {
		super(0, 0, 26, 32, Component.empty());
		this.index = index;
		this.toggled = toggled;
		this.command = command;
		this.icon = icon;
		this.toggleTime = 0;
		if (tooltip == null || tooltip.isEmpty()) {
			this.tooltip = null;
			return;
		}
		Tooltip tip;
		try {
			setTooltip(tip = Tooltip.create(ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, QuickNavMod.GSON.fromJson(tooltip, JsonElement.class)).getOrThrow().getFirst()));
		} catch (Exception _) {
			setTooltip(tip = Tooltip.create(Component.literal(tooltip)));
		}
		this.tooltip = tip;
		setTooltipDelay(Duration.ofMillis(100));
	}

	private void updateCoordinates() {
		Screen screen = Minecraft.getInstance().gui.screen();
		while (screen instanceof PopupScreen || screen instanceof AbstractPopupScreen) {
			if (screen instanceof PopupScreen) {
				if (!(screen instanceof PopupScreenAccessor popup)) {
					throw new IllegalStateException(
							"Current PopupScreen does not support AccessorPopupBackground"
					);
				}
				screen = popup.getUnderlyingScreen();
			} else if (screen instanceof AbstractPopupScreen abstractPopupScreen) {
				screen = abstractPopupScreen.backgroundScreen;
			}
		}
		if (screen instanceof AbstractContainerScreen<?> handledScreen) {
			var accessibleScreen = (AbstractContainerScreenAccessor) handledScreen;
			int x = accessibleScreen.getX();
			int y = accessibleScreen.getY();
			int h = accessibleScreen.getImageHeight();
			if (handledScreen instanceof ContainerScreen) h--; // they messed up the height on these.
			int w = accessibleScreen.getImageWidth();
			this.setX(x + this.index % 7 * 25 + w / 2 - 176 / 2);
			this.setY(this.index < 7 ? y - 28 : y + h - 4);
		}
	}

	/**
	 * Handles click events. If the button is not currently toggled,
	 * it sets the toggled state to true and sends a message with the command after cooldown.
	 */
	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		if (isDungeonDisabled()) {
			// Prevent accidentally warping out of the current dungeon instance.
			Minecraft client = Minecraft.getInstance();
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 1.0f));
			if (client.player != null) {
				client.player.sendSystemMessage(QuickNavConstants.PREFIX.get().append(Component.translatable("quicknav.quickNav.disabledInDungeon").withStyle(ChatFormatting.RED)));
			}
			return;
		}
		if (!this.temporaryToggled) {
			this.temporaryToggled = true;
			this.toggleTime = System.currentTimeMillis();
			if (command == null || command.isEmpty()) {
				Minecraft.getInstance().player.sendSystemMessage(QuickNavConstants.PREFIX.get().append(Component.literal("Quick Nav button index " + (index + 1) + " has no command!").withStyle(ChatFormatting.RED)));
			} else {
				MessageScheduler.INSTANCE.sendMessageAfterCooldown(command, true);
			}
			this.alpha = 0;
		}
	}

	/**
	 * Suppresses the normal click sound for buttons disabled inside dungeons, since the warning
	 * sound is already played in {@link #onClick(MouseButtonEvent, boolean)}.
	 */
	@Override
	public void playDownSound(SoundManager soundManager) {
		if (isDungeonDisabled()) return;
		super.playDownSound(soundManager);
	}

	/**
	 * Checks whether this button should be disabled while the player is inside a dungeon.
	 * <p>
	 * A button is disabled if the player is in a dungeon and either the "disable warp buttons"
	 * mode is enabled (the button's command starts with {@code /warp}) or the button has been
	 * manually set to be disabled in dungeons in the config.
	 *
	 * @return whether this button is currently disabled inside a dungeon
	 */
	public boolean isDungeonDisabled() {
		QuickNavConfig config = QuickNavConfigManager.get();
		if (!QuickNavUtils.isInDungeon()) return false;

		String cmd = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
		if (config.disableWarpButtonsInDungeon && (cmd.equals("/warp") || cmd.startsWith("/warp "))) return true;

		return config.getButton(index).disableInDungeon;
	}

	/**
	 * As of 1.21.8, vanilla's creative inventory tabs aren't tab navigable due to them not being proper GUI buttons and instead they're
	 * manually drawn and the click logic is manual as well. If that ever changes, this should be adjusted to match the new vanilla behaviour.
	 */
	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
		return null;
	}

	/**
	 * Renders the button on screen. This includes both its texture and its icon.
	 * The method first updates the coordinates of the button,
	 * then calculates appropriate values for rendering based on its current state,
	 * and finally draws both the background and icon of the button on screen.
	 *
	 * @param graphics the context in which to render the button
	 * @param mouseX  the x-coordinate of the mouse cursor
	 * @param mouseY  the y-coordinate of the mouse cursor
	 */
	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.updateCoordinates();

		// Note that this changes the return value of `toggled()`, so do not call it after this point.
		// Instead, use `renderInFront` to determine whether the button is currently rendering in front of the main inventory background.
		if (this.temporaryToggled && System.currentTimeMillis() - this.toggleTime >= TOGGLE_DURATION) {
			this.temporaryToggled = false; // Reset toggled state
		}
		//"animation"
		if (alpha < 255) {
			alpha = Math.min(alpha + 10, 255);
		}

		boolean dungeonDisabled = isDungeonDisabled();
		updateDungeonDisabledTooltip(dungeonDisabled);

		Identifier tabTexture = getTexture();

		// Render the button texture, always with full alpha if it's not rendering in front
		if (dungeonDisabled) {
			// Render with a red tint to signal that the button is disabled inside dungeons
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, tabTexture, this.getX(), this.getY(), this.width, this.height, ARGB.color(alpha, 0xFF5555));
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, tabTexture, this.getX(), this.getY(), this.width, this.height, renderInFront ? ARGB.color(alpha, -1) : -1);
		}
		// Render the button icon
		int yOffset = this.index < 7 ? 1 : -1;
		graphics.item(this.icon, this.getX() + 5, this.getY() + 8 + yOffset);

		this.handleCursor(graphics);
	}

	/**
	 * Switches the tooltip to the dungeon disabled warning while the button is disabled inside a dungeon.
	 */
	private void updateDungeonDisabledTooltip(boolean dungeonDisabled) {
		if (dungeonDisabled == showingDungeonDisabledTooltip) return;
		showingDungeonDisabledTooltip = dungeonDisabled;
		setTooltip(dungeonDisabled ? DUNGEON_DISABLED_TOOLTIP : tooltip);
	}

	private Identifier getTexture() {
		var textures = renderInFront ? TAB_TEXTURES_SELECTED : TAB_TEXTURES;
		FallbackedTexture<Identifier> texture = textures[index];
		if (texture != null) return texture.get();
		// Construct the texture identifier based on the index and toggled state
		return (textures[index] = FallbackedTexture.ofGuiSprite(
				QuickNavMod.id("quick_nav/tab_" + (isTopTab() ? "top" : "bottom") + "_" + (renderInFront ? "selected" : "unselected") + "_" + (index % 7 + 1)),
				Identifier.withDefaultNamespace("container/creative_inventory/tab_" + (isTopTab() ? "top" : "bottom") + "_" + (renderInFront ? "selected" : "unselected") + "_" + (index % 7 + 1))
		)).get();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
