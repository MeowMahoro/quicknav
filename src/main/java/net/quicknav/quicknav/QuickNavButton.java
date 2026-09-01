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
	private static final long DOUBLE_CLICK_TIME = 1000;
	@SuppressWarnings("unchecked")
	private static final @Nullable FallbackedTexture<Identifier>[] TAB_TEXTURES = new FallbackedTexture[14];
	@SuppressWarnings("unchecked")
	private static final @Nullable FallbackedTexture<Identifier>[] TAB_TEXTURES_SELECTED = new FallbackedTexture[14];

	private static final Tooltip CONFIRM_TOOLTIP = Tooltip.create(Component.translatable("quicknav.quickNav.confirm"));

	private final int index;
	private final boolean toggled;
	private final String command;
	private final ItemStack icon;
	protected final Tooltip tooltip;

	private boolean temporaryToggled = false;
	private long toggleTime;
	private boolean showingConfirmTooltip = false;
	private long lastClicked = 0;

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
	 * Handles click events. If the button requires a double click (see
	 * {@link #requiresDoubleClick()}) and this is the first click, only the click time is
	 * recorded and the command is not executed. Otherwise, if the button is not currently
	 * toggled, it sets the toggled state to true and sends a message with the command after
	 * cooldown.
	 */
	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		if (requiresDoubleClick() && !isDoubleClick()) {
			lastClicked = System.currentTimeMillis();
			if (Minecraft.getInstance().player != null) {
				Minecraft.getInstance().player.sendSystemMessage(QuickNavConstants.PREFIX.get().append(Component.translatable("quicknav.quickNav.confirmChat")));
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
	 * Plays a confirmation chime on the first click of a button that requires a double click,
	 * so the player knows they have to click again to activate it.
	 */
	@Override
	public void playDownSound(SoundManager soundManager) {
		if (requiresDoubleClick() && !isDoubleClick()) {
			soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.0f));
			return;
		}
		super.playDownSound(soundManager);
	}

	/**
	 * Checks whether this button runs a warp command ({@code /warp ...}). Only these commands
	 * are protected by the automatic dungeon warp protection; other commands such as
	 * {@code /hub} are always executed with a single click.
	 *
	 * @return whether the button's command is a warp command
	 */
	public boolean isWarpCommand() {
		String cmd = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
		return cmd.equals("/warp") || cmd.startsWith("/warp ");
	}

	/**
	 * Checks whether this button currently requires a double click to activate.
	 * <p>
	 * A button requires a double click while the player is inside a dungeon, depending on the
	 * configured {@link DungeonWarpMode}:
	 * <ul>
	 *   <li>{@link DungeonWarpMode#AUTO}: every {@code /warp} button is protected.</li>
	 *   <li>{@link DungeonWarpMode#MANUAL}: only the buttons selected by the player are protected.</li>
	 *   <li>{@link DungeonWarpMode#DISABLED}: no button is ever protected.</li>
	 * </ul>
	 * Outside dungeons every button is unrestricted.
	 *
	 * @return whether this button needs a double click in the current context
	 */
	protected boolean requiresDoubleClick() {
		QuickNavConfig config = QuickNavConfigManager.get();
		if (!QuickNavUtils.isInDungeon()) return false;
		return switch (config.dungeonWarpMode) {
			case AUTO -> isWarpCommand();
			case MANUAL -> config.isProtectedButton(index);
			case DISABLED -> false;
		};
	}

	/**
	 * @return whether a warp-protected button should be rendered red inside a dungeon
	 */
	private boolean shouldRenderRedInDungeon() {
		QuickNavConfig config = QuickNavConfigManager.get();
		return config.warpButtonsRedInDungeon && requiresDoubleClick();
	}

	private boolean isDoubleClick() {
		long now = System.currentTimeMillis();
		return now - lastClicked < DOUBLE_CLICK_TIME;
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

		updateConfirmTooltip();

		Identifier tabTexture = getTexture();

		// Render the button texture, always with full alpha if it's not rendering in front
		if (shouldRenderRedInDungeon()) {
			// Render with a red tint to signal that the button is warp-protected inside dungeons
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
	 * Switches the tooltip to the confirmation hint while the button requires a double click,
	 * and back to the normal tooltip once it no longer does.
	 */
	private void updateConfirmTooltip() {
		if (toggled()) return;
		if (!requiresDoubleClick()) {
			if (showingConfirmTooltip) {
				showingConfirmTooltip = false;
				setTooltip(tooltip);
			}
			return;
		}
		if (isDoubleClick() == showingConfirmTooltip) return;
		showingConfirmTooltip = !showingConfirmTooltip;
		setTooltip(showingConfirmTooltip ? CONFIRM_TOOLTIP : tooltip);
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
