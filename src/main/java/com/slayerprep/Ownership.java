package com.slayerprep;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.ColorScheme;

/** Where an item is, best first. */
@Getter
@RequiredArgsConstructor
public enum Ownership
{
	CARRIED("With you", ColorScheme.PROGRESS_COMPLETE_COLOR),
	IN_BANK("In bank", ColorScheme.PROGRESS_INPROGRESS_COLOR),
	IN_GROUP_BANK("In group bank", new Color(115, 170, 255)),
	MISSING("Missing", ColorScheme.PROGRESS_ERROR_COLOR);

	private final String label;
	private final Color color;
}
