package net.quicknav;

import net.azureaaron.dandelion.api.controllers.BooleanController;
import net.azureaaron.dandelion.api.controllers.BooleanController.BooleanStyle;

public class QuickNavConfigUtils {
	public static BooleanController createBooleanController() {
		return BooleanController.createBuilder()
				.coloured(true)
				.booleanStyle(BooleanStyle.YES_NO)
				.build();
	}
}
