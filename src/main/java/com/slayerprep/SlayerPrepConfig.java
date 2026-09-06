package com.slayerprep;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerPrepConfig.GROUP)
public interface SlayerPrepConfig extends Config
{
	String GROUP = "slayerprepcheck";

	@ConfigItem(
		keyName = "showPanel",
		name = "Show side panel",
		description = "Show the Slayer Prep Check panel in the sidebar",
		position = 1
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show a small overlay listing required items that are missing or still in the bank",
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankTab",
		name = "Bank tab button",
		description = "Add a button to the bank that shows only the items for your task (needs the Bank Tags plugin)",
		position = 3
	)
	default boolean bankTab()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankTabSelectedStyleOnly",
		name = "Bank tab: selected style only",
		description = "Show only the attack style selected in the panel in the bank tab, instead of every style",
		position = 4
	)
	default boolean bankTabSelectedStyleOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatOnNewTask",
		name = "Chat message on new task",
		description = "Send a game message listing required items you are not carrying when you get a new task",
		position = 5
	)
	default boolean chatOnNewTask()
	{
		return true;
	}

	@ConfigItem(
		keyName = "styleOrder",
		name = "Style order",
		description = "Comma-separated order of attack style tabs, e.g. Melee, Ranged, Magic",
		position = 6
	)
	default String styleOrder()
	{
		return "Melee, Ranged, Magic";
	}
}
