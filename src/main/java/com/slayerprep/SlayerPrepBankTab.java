package com.slayerprep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;

/**
 * A bank tag, opened from a small button in the bank, that shows only the items the current task needs,
 * grouped under text headers: required items, recommended items, then one group per attack style of the
 * chosen gear set. Filtering and item drawing are done by the core Bank Tags plugin; this class supplies the
 * tag and a layout that leaves an empty row above each group, and draws the header text into that row.
 */
@Slf4j
@Singleton
class SlayerPrepBankTab
{
	static final String TAG = "slayer prep";
	private static final int ROW_SIZE = BankTagsPlugin.BANK_ITEMS_PER_ROW;
	private static final int ROW_HEIGHT = BankTagsPlugin.BANK_ITEM_HEIGHT + BankTagsPlugin.BANK_ITEM_Y_PADDING;
	private static final int HEADER_X = BankTagsPlugin.BANK_ITEM_START_X;
	private static final int HEADER_WIDTH = ROW_SIZE * (BankTagsPlugin.BANK_ITEM_WIDTH + BankTagsPlugin.BANK_ITEM_X_PADDING);
	private static final int HEADER_HEIGHT = 16;
	private static final int HEADER_Y_OFFSET = (BankTagsPlugin.BANK_ITEM_HEIGHT - HEADER_HEIGHT) / 2;
	private static final int HEADER_COLOR = 0xff981f;
	private static final int SUB_HEADER_COLOR = 0xc8c8c8;
	private static final String SPECIAL_SLOT = "special";
	private static final String SPECIAL_TITLE = "Special attack";
	private static final int BUTTON_SIZE = 25;
	private static final int BUTTON_X = 380;
	private static final int BUTTON_Y = 5;
	private static final int OP_VIEW_TAB = 2;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private BankTagsPlugin bankTagsPlugin;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private SlayerPrepConfig config;

	private final Set<Integer> taggedIds = new HashSet<>();
	private final List<Widget> headerWidgets = new ArrayList<>();
	/** Row index to header, for the current layout. */
	private Map<Integer, Section> headers = Map.of();
	private Layout layout = new Layout(TAG, new int[0]);
	private Supplier<PrepResult> resultSupplier = () -> PrepResult.NO_TASK;
	private Widget background;
	private Widget icon;
	/** Guards against re-applying our layout more than once per bank session, which could loop. */
	private boolean layoutReapplied;

	void startUp(Supplier<PrepResult> resultSupplier)
	{
		this.resultSupplier = resultSupplier;
		tagManager.registerTag(TAG, taggedIds::contains);
	}

	/** Must run on the client thread. */
	void shutDown()
	{
		if (isActive())
		{
			bankTagsService.closeBankTag();
		}
		tagManager.unregisterTag(TAG);
		removeButton();
		hideHeaders();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			layoutReapplied = false;
			headerWidgets.clear(); // the previous bank interface's children are gone
			createButton();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}
		// the core remembers the open tag between bank sessions but not our layout; put it back once
		if (isActive() && !layoutReapplied && bankTagsService.getActiveLayout() != layout)
		{
			layoutReapplied = true;
			refresh(resultSupplier.get());
			relayout();
		}
		syncButton();
		drawHeaders();
	}

	/**
	 * Rebuild the tag contents from the last known bank contents and the current task result.
	 * Must run on the client thread. Relayouts the bank if our tag is showing and the contents changed.
	 *
	 * @return true if the bank was relaid out with a new layout
	 */
	boolean refresh(PrepResult result)
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return false;
		}
		Map<String, List<Integer>> bankIdsByName = indexBank(bank);
		List<Section> sections = new ArrayList<>();
		List<String> missing = result.requiredMissing();
		sections.add(new Section("Required items", requirementSection(result, bankIdsByName, true), false, !missing.isEmpty()));
		if (!missing.isEmpty())
		{
			sections.add(new Section("Missing: " + String.join(", ", missing), List.of(), true, true));
		}
		sections.add(new Section("Recommended items", requirementSection(result, bankIdsByName, false), false, false));
		result.getGear().forEach((style, slots) ->
		{
			if (config.bankTabSelectedStyleOnly() && result.getSelectedStyle() != null && !style.equals(result.getSelectedStyle()))
			{
				return;
			}
			sections.add(new Section(style, styleSection(slots, bankIdsByName, false), false, false));
			sections.add(new Section(SPECIAL_TITLE, styleSection(slots, bankIdsByName, true), true, false));
		});
		Map<Integer, Section> newHeaders = new LinkedHashMap<>();
		int[] positions = layoutSections(sections, newHeaders);
		if (Arrays.equals(positions, layout.getLayout()))
		{
			return false;
		}
		headers = newHeaders;
		layout = new Layout(TAG, positions);
		taggedIds.clear();
		for (int id : positions)
		{
			if (id >= 0)
			{
				taggedIds.add(id);
			}
		}
		if (isActive())
		{
			relayout();
			return true;
		}
		return false;
	}

	/**
	 * Re-open our tag with the current layout. Deferred to the next client tick: opening a tag rebuilds the bank
	 * with a script, and this may be called while the bank's own build script is still running (scripts are
	 * not reentrant).
	 */
	private void relayout()
	{
		clientThread.invokeLater(() ->
		{
			if (isActive() && bankOpen())
			{
				bankTagsPlugin.openTag(TAG, layout, 0);
			}
		});
	}

	private boolean bankOpen()
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return items != null && !items.isHidden();
	}

	private Map<String, List<Integer>> indexBank(ItemContainer bank)
	{
		Map<String, List<Integer>> byName = new HashMap<>();
		for (Item item : bank.getItems())
		{
			if (item.getId() < 0)
			{
				continue;
			}
			String name = ItemMatcher.normalize(itemManager.getItemComposition(item.getId()).getName());
			byName.computeIfAbsent(name, k -> new ArrayList<>()).add(item.getId());
		}
		return byName;
	}

	private static List<Integer> requirementSection(PrepResult result, Map<String, List<Integer>> bankIdsByName, boolean required)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (RequirementStatus status : result.getRequirements())
		{
			if (status.isRequired() != required)
			{
				continue;
			}
			bankIdsByName.forEach((name, bankIds) ->
			{
				if (status.getRequirement().matchesAny(name))
				{
					ids.addAll(bankIds);
				}
			});
		}
		return new ArrayList<>(ids);
	}

	/** Bank ids of the chosen items of one style: either the worn gear, or only the special attack weapon. */
	private static List<Integer> styleSection(Map<String, SlotChoice> slots, Map<String, List<Integer>> bankIdsByName, boolean special)
	{
		List<Integer> ids = new ArrayList<>();
		for (Map.Entry<String, SlotChoice> slot : slots.entrySet())
		{
			SlotChoice choice = slot.getValue();
			if (choice.getItemName() == null || SPECIAL_SLOT.equals(slot.getKey()) != special)
			{
				continue;
			}
			List<Integer> bankIds = bankIdsByName.get(ItemMatcher.normalize(choice.getItemName()));
			if (bankIds != null)
			{
				ids.add(bankIds.get(0));
			}
		}
		return ids;
	}

	/**
	 * Each non-empty section gets an empty bank row for its header, then its items padded to whole rows.
	 * -1 is an empty slot. Header row indexes are collected into {@code headers}.
	 */
	private static int[] layoutSections(List<Section> sections, Map<Integer, Section> headers)
	{
		List<Integer> positions = new ArrayList<>();
		for (Section section : sections)
		{
			if (section.ids.isEmpty() && !section.force)
			{
				continue;
			}
			headers.put(positions.size() / ROW_SIZE, section);
			for (int i = 0; i < ROW_SIZE; i++)
			{
				positions.add(-1);
			}
			positions.addAll(section.ids);
			while (positions.size() % ROW_SIZE != 0)
			{
				positions.add(-1);
			}
		}
		return positions.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Draw the group headers into the bank item container; called after every bank rebuild. Widgets are reused. */
	private void drawHeaders()
	{
		if (!isActive())
		{
			hideHeaders();
			return;
		}
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null)
		{
			return;
		}
		int i = 0;
		for (Map.Entry<Integer, Section> header : headers.entrySet())
		{
			Widget text;
			if (i < headerWidgets.size())
			{
				text = headerWidgets.get(i);
			}
			else
			{
				text = container.createChild(-1, WidgetType.TEXT);
				text.setOriginalWidth(HEADER_WIDTH);
				text.setOriginalHeight(HEADER_HEIGHT);
				text.setTextShadowed(true);
				headerWidgets.add(text);
			}
			Section section = header.getValue();
			text.setFontId(section.sub ? FontID.PLAIN_11 : FontID.PLAIN_12);
			text.setTextColor(section.sub ? SUB_HEADER_COLOR : HEADER_COLOR);
			text.setOriginalX(HEADER_X);
			text.setOriginalY(header.getKey() * ROW_HEIGHT + HEADER_Y_OFFSET);
			text.setText(section.title);
			text.setHidden(false);
			text.revalidate();
			i++;
		}
		for (; i < headerWidgets.size(); i++)
		{
			headerWidgets.get(i).setHidden(true);
		}
	}

	private void hideHeaders()
	{
		for (Widget w : headerWidgets)
		{
			w.setHidden(true);
		}
	}

	private static class Section
	{
		private final String title;
		private final List<Integer> ids;
		/** A sub-section of the section above it, drawn with a smaller, muted header. */
		private final boolean sub;
		/** Show the header row even when the section has no items (e.g. a "Missing: ..." line). */
		private final boolean force;

		Section(String title, List<Integer> ids, boolean sub, boolean force)
		{
			this.title = title;
			this.ids = ids;
			this.sub = sub;
			this.force = force;
		}
	}

	private boolean isActive()
	{
		return TAG.equals(bankTagsService.getActiveTag());
	}

	private boolean bankTagsEnabled()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof BankTagsPlugin)
			{
				return pluginManager.isPluginEnabled(plugin);
			}
		}
		return false;
	}

	private void toggle(ScriptEvent event)
	{
		if (event.getOp() != OP_VIEW_TAB)
		{
			return;
		}
		if (!bankTagsEnabled())
		{
			client.addChatMessage(ChatMessageType.CONSOLE, "", "Slayer Prep: enable the Bank Tags plugin to use the bank tab.", null);
			return;
		}
		boolean open = !isActive();
		if (open)
		{
			refresh(resultSupplier.get());
		}
		clientThread.invokeLater(() ->
		{
			if (!bankOpen())
			{
				return;
			}
			if (open)
			{
				bankTagsPlugin.openTag(TAG, layout, 0);
			}
			else
			{
				bankTagsService.closeBankTag();
				hideHeaders();
			}
			syncButton();
		});
		client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	private void createButton()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}
		removeButton();
		background = parent.createChild(-1, WidgetType.GRAPHIC);
		background.setOriginalWidth(BUTTON_SIZE);
		background.setOriginalHeight(BUTTON_SIZE);
		background.setOriginalX(BUTTON_X);
		background.setOriginalY(BUTTON_Y);
		background.setSpriteId(SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
		background.setName("Slayer prep");
		background.setAction(1, "View tab");
		background.setOnOpListener((JavaScriptCallback) this::toggle);
		background.setHasListener(true);
		background.revalidate();

		icon = parent.createChild(-1, WidgetType.GRAPHIC);
		icon.setOriginalWidth(BUTTON_SIZE - 6);
		icon.setOriginalHeight(BUTTON_SIZE - 6);
		icon.setOriginalX(BUTTON_X + 3);
		icon.setOriginalY(BUTTON_Y + 3);
		icon.setItemId(ItemID.SLAYER_HELM);
		icon.setItemQuantity(1);
		icon.setItemQuantityMode(ItemQuantityMode.NEVER);
		icon.revalidate();
		syncButton();
	}

	private void syncButton()
	{
		if (background == null)
		{
			return;
		}
		background.setSpriteId(isActive()
			? SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
			: SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
		background.revalidate();
	}

	private void removeButton()
	{
		if (background != null)
		{
			background.setHidden(true);
			background = null;
		}
		if (icon != null)
		{
			icon.setHidden(true);
			icon = null;
		}
	}
}
