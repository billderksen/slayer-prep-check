package com.slayerprep;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Small overlay listing required task items that are missing or still in the bank. Hidden when everything is carried.
 */
public class SlayerPrepOverlay extends OverlayPanel
{
	private final SlayerPrepPlugin plugin;

	@Inject
	SlayerPrepOverlay(SlayerPrepPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		PrepResult result = plugin.getResult();
		if (result.getTask() == null)
		{
			return null;
		}
		List<RequirementStatus> notCarried = result.requiredNotCarried();
		List<String> gearInBank = result.gearInBank();
		if (notCarried.isEmpty() && gearInBank.isEmpty())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Slayer prep: " + result.getTask().getName())
			.build());
		for (RequirementStatus status : notCarried)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(status.getLabel())
				.right(status.getOwnership().getLabel())
				.rightColor(status.getOwnership().getColor())
				.build());
		}
		if (!gearInBank.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(result.getSelectedStyle() + " gear not with you")
				.right(String.valueOf(gearInBank.size()))
				.rightColor(Ownership.IN_BANK.getColor())
				.build());
		}
		return super.render(graphics);
	}
}
