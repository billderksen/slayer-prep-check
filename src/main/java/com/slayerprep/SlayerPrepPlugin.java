package com.slayerprep;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Slayer Prep Check",
	description = "Shows required items and the best gear set from your bank for your current Slayer task",
	tags = {"slayer", "ironman", "task", "gear", "bank"}
)
@PluginDependency(BankTagsPlugin.class)
public class SlayerPrepPlugin extends Plugin
{
	private static final String BANK_KEY = "bank";
	private static final String GROUP_BANK_KEY = "groupBank";
	private static final String GEAR_OPTION_KEY_PREFIX = "gearOption.";
	private static final String STYLE_KEY_PREFIX = "style.";
	private static final Type BANK_TYPE = new TypeToken<Map<String, Integer>>()
	{
	}.getType();
	/** SLAYER_TARGET value used for boss tasks, from [proc,helper_slayer_current_assignment]. */
	private static final int BOSS_TASK_ID = 98;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private SlayerPrepConfig config;

	@Inject
	private SlayerPrepOverlay overlay;

	@Inject
	private SlayerPrepBankTab bankTab;

	@Inject
	private EventBus eventBus;

	private SlayerPrepPanel panel;
	private NavigationButton navButton;

	private GearData gearData;
	private GearAdvisor advisor;
	private Set<String> gearItemNames = Collections.emptySet();
	private List<ItemMatcher> requirementMatchers = Collections.emptyList();

	private ItemSnapshot snapshot = ItemSnapshot.empty();
	/** Normalised item name to the id last seen in a container, for icons of owned items. */
	private final Map<String, Integer> ownedIds = new HashMap<>();
	private boolean bankKnown;
	private SlayerTask task;
	private boolean loginFlag;
	private boolean bankTabEnabled;

	@Getter
	private volatile PrepResult result = PrepResult.NO_TASK;

	@Override
	protected void startUp()
	{
		loadData();
		panel = new SlayerPrepPanel(itemManager, this::selectGearOption, this::selectStyle);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Slayer Prep Check")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		if (config.showPanel())
		{
			clientToolbar.addNavigation(navButton);
		}
		if (config.showOverlay())
		{
			overlayManager.add(overlay);
		}
		if (config.bankTab())
		{
			enableBankTab();
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loginFlag = true;
		}
		publish(PrepResult.NO_TASK);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		disableBankTab();
		navButton = null;
		panel = null;
		snapshot = ItemSnapshot.empty();
		bankKnown = false;
		task = null;
		loginFlag = false;
		result = PrepResult.NO_TASK;
	}

	private void enableBankTab()
	{
		if (bankTabEnabled)
		{
			return;
		}
		bankTabEnabled = true;
		eventBus.register(bankTab);
		clientThread.invoke(() ->
		{
			bankTab.startUp(this::getResult);
			if (bankTabEnabled && client.getGameState() == GameState.LOGGED_IN)
			{
				bankTab.refresh(result);
			}
		});
	}

	private void disableBankTab()
	{
		if (!bankTabEnabled)
		{
			return;
		}
		bankTabEnabled = false;
		eventBus.unregister(bankTab);
		clientThread.invoke(bankTab::shutDown);
	}

	@Provides
	SlayerPrepConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerPrepConfig.class);
	}

	private void loadData()
	{
		Map<String, List<String>> aliases = Collections.emptyMap();
		try
		{
			gearData = GearData.load(gson);
			aliases = GearData.loadAliases(gson);
			gearItemNames = gearData.allItemNames();
			log.debug("Loaded gear data generated {} with {} item names", gearData.getGenerated(), gearItemNames.size());
		}
		catch (IOException | RuntimeException e)
		{
			log.error("Failed to load bundled gear data; gear advice will be unavailable", e);
			gearData = null;
			gearItemNames = Collections.emptySet();
		}
		advisor = new GearAdvisor(aliases);
		requirementMatchers = TaskRequirements.allMatchers();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				loginFlag = true;
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				// another account may log in next; forget this account's bank until it is reloaded
				snapshot = ItemSnapshot.empty();
				ownedIds.clear();
				bankKnown = false;
				task = null;
				publish(PrepResult.NO_TASK);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!loginFlag)
		{
			return;
		}
		loginFlag = false;
		if (!bankKnown)
		{
			loadBankFromProfile();
		}
		syncContainer(InventoryID.INV);
		syncContainer(InventoryID.WORN);
		updateTask();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varpId = event.getVarpId();
		int varbitId = event.getVarbitId();
		if (varpId == VarPlayerID.SLAYER_COUNT
			|| varpId == VarPlayerID.SLAYER_AREA
			|| varpId == VarPlayerID.SLAYER_TARGET
			|| varbitId == VarbitID.SLAYER_TARGET_BOSSID)
		{
			clientThread.invokeLater(this::updateTask);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.WORN || id == InventoryID.BANK || id == InventoryID.INV_GROUP_TEMP)
		{
			syncContainer(id);
			recompute();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SlayerPrepConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("showPanel".equals(event.getKey()))
		{
			clientToolbar.removeNavigation(navButton);
			if (config.showPanel())
			{
				clientToolbar.addNavigation(navButton);
			}
		}
		else if ("showOverlay".equals(event.getKey()))
		{
			overlayManager.remove(overlay);
			if (config.showOverlay())
			{
				overlayManager.add(overlay);
			}
		}
		else if ("bankTab".equals(event.getKey()))
		{
			disableBankTab();
			if (config.bankTab())
			{
				enableBankTab();
			}
		}
		clientThread.invokeLater(this::recompute);
	}

	private void syncContainer(int containerId)
	{
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return;
		}
		Map<String, Integer> items = readContainer(container);
		if (containerId == InventoryID.INV)
		{
			snapshot = snapshot.withInventory(items);
		}
		else if (containerId == InventoryID.WORN)
		{
			snapshot = snapshot.withWorn(items);
		}
		else if (containerId == InventoryID.BANK)
		{
			Map<String, Integer> relevant = relevantItems(items);
			snapshot = snapshot.withBank(relevant);
			bankKnown = true;
			configManager.setRSProfileConfiguration(SlayerPrepConfig.GROUP, BANK_KEY, gson.toJson(relevant));
			log.debug("Bank snapshot updated: {} relevant items", relevant.size());
		}
		else if (containerId == InventoryID.INV_GROUP_TEMP)
		{
			Map<String, Integer> relevant = relevantItems(items);
			snapshot = snapshot.withGroupBank(relevant);
			configManager.setRSProfileConfiguration(SlayerPrepConfig.GROUP, GROUP_BANK_KEY, gson.toJson(relevant));
			log.debug("Group storage snapshot updated: {} relevant items", relevant.size());
		}
	}

	private Map<String, Integer> relevantItems(Map<String, Integer> items)
	{
		return items.entrySet().stream()
			.filter(e -> isRelevant(e.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/** Item names to quantities; must run on the client thread because of the item composition lookup. */
	private Map<String, Integer> readContainer(ItemContainer container)
	{
		Map<String, Integer> items = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			String name = itemManager.getItemComposition(item.getId()).getName();
			items.merge(name, item.getQuantity(), Integer::sum);
			ownedIds.put(ItemMatcher.normalize(name), item.getId());
		}
		return items;
	}

	private boolean isRelevant(String itemName)
	{
		String normalized = ItemMatcher.normalize(itemName);
		if (gearItemNames.contains(normalized) || gearItemNames.contains(normalized.replaceAll(" \\d+$", "")))
		{
			return true;
		}
		if (normalized.contains("slayer helmet"))
		{
			return true;
		}
		return requirementMatchers.stream().anyMatch(m -> m.matches(itemName));
	}

	private void loadBankFromProfile()
	{
		Map<String, Integer> bank = loadSnapshot(BANK_KEY);
		if (bank != null)
		{
			snapshot = snapshot.withBank(bank);
			bankKnown = true;
			log.debug("Restored bank snapshot with {} items", bank.size());
		}
		Map<String, Integer> groupBank = loadSnapshot(GROUP_BANK_KEY);
		if (groupBank != null)
		{
			snapshot = snapshot.withGroupBank(groupBank);
			log.debug("Restored group storage snapshot with {} items", groupBank.size());
		}
	}

	@Nullable
	private Map<String, Integer> loadSnapshot(String key)
	{
		String json = configManager.getRSProfileConfiguration(SlayerPrepConfig.GROUP, key);
		if (json == null)
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, BANK_TYPE);
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Ignoring corrupt {} snapshot", key, e);
			return null;
		}
	}

	/** Mirrors the core Slayer plugin's task detection. Must run on the client thread. */
	private void updateTask()
	{
		SlayerTask current = readTask();
		boolean changed = current != null && (task == null || !Objects.equals(task.getName(), current.getName()));
		task = current;
		recompute();
		if (changed && config.chatOnNewTask())
		{
			announceTask();
		}
	}

	private SlayerTask readTask()
	{
		int amount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (amount <= 0)
		{
			return null;
		}
		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		int taskDBRow;
		if (taskId == BOSS_TASK_ID)
		{
			List<Integer> bossRows = client.getDBRowsByValue(
				DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				0,
				client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (bossRows.isEmpty())
			{
				return null;
			}
			taskDBRow = (Integer) client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
		}
		else
		{
			List<Integer> taskRows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
			if (taskRows.isEmpty())
			{
				return null;
			}
			taskDBRow = taskRows.get(0);
		}
		String taskName = (String) client.getDBTableField(taskDBRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];

		String location = null;
		int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		if (areaId > 0)
		{
			List<Integer> areaRows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
			if (!areaRows.isEmpty())
			{
				location = (String) client.getDBTableField(areaRows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
			}
		}
		return new SlayerTask(taskName, amount, location);
	}

	private void announceTask()
	{
		List<RequirementStatus> notCarried = result.requiredNotCarried();
		if (notCarried.isEmpty())
		{
			return;
		}
		String list = notCarried.stream()
			.map(r -> r.getLabel() + " (" + r.getOwnership().getLabel().toLowerCase() + ")")
			.collect(Collectors.joining(", "));
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Slayer Prep: " + task.getName() + " needs " + list, null);
	}

	private void recompute()
	{
		if (task == null)
		{
			publish(PrepResult.NO_TASK);
			if (bankTabEnabled)
			{
				bankTab.refresh(PrepResult.NO_TASK);
			}
			return;
		}
		List<RequirementStatus> statuses = new ArrayList<>();
		boolean kourendElite = client.getVarbitValue(VarbitID.KOUREND_DIARY_ELITE_COMPLETE) == 1;
		for (Requirement requirement : TaskRequirements.forTask(task.getName(), kourendElite))
		{
			statuses.add(status(requirement));
		}
		List<GearOption> options = gearData == null ? Collections.emptyList() : gearData.optionsFor(task.getName());
		GearOption selected = selectedOption(options);
		Map<String, Map<String, SlotChoice>> gear = selected == null ? Collections.emptyMap() : adviseGear(selected.getTask());
		List<String> labels = options.stream().map(GearOption::getLabel).collect(Collectors.toList());
		publish(new PrepResult(task, statuses, gear, labels, selected == null ? null : selected.getLabel(),
			gearNote(selected), perkNotes(), icons(statuses, gear), selectedStyle(gear), bankKnown));
		if (bankTabEnabled)
		{
			bankTab.refresh(result);
		}
	}

	/** Called from the panel when the player switches style tab; remembered per task. */
	void selectStyle(String style)
	{
		SlayerTask current = result.getTask();
		if (current == null)
		{
			return;
		}
		configManager.setConfiguration(SlayerPrepConfig.GROUP, STYLE_KEY_PREFIX + taskKey(current.getName()), style);
		clientThread.invokeLater(this::recompute);
	}

	@Nullable
	private String selectedStyle(Map<String, Map<String, SlotChoice>> gear)
	{
		if (gear.isEmpty())
		{
			return null;
		}
		String stored = configManager.getConfiguration(SlayerPrepConfig.GROUP, STYLE_KEY_PREFIX + taskKey(task.getName()));
		return stored != null && gear.containsKey(stored) ? stored : gear.keySet().iterator().next();
	}

	/** Called from the panel when the player picks another gear source. */
	void selectGearOption(String label)
	{
		SlayerTask current = result.getTask();
		if (current == null)
		{
			return;
		}
		configManager.setConfiguration(SlayerPrepConfig.GROUP, gearOptionKey(current.getName()), label);
		clientThread.invokeLater(this::recompute);
	}

	@Nullable
	private GearOption selectedOption(List<GearOption> options)
	{
		if (options.isEmpty())
		{
			return null;
		}
		String stored = configManager.getConfiguration(SlayerPrepConfig.GROUP, gearOptionKey(task.getName()));
		return options.stream()
			.filter(o -> o.getLabel().equals(stored))
			.findFirst()
			.orElse(options.get(0));
	}

	private static String gearOptionKey(String taskName)
	{
		return GEAR_OPTION_KEY_PREFIX + taskKey(taskName);
	}

	private static String taskKey(String taskName)
	{
		return taskName.toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}

	@Nullable
	private static String gearNote(@Nullable GearOption selected)
	{
		if (selected == null)
		{
			return "No wiki equipment table for this task.";
		}
		switch (selected.getKind())
		{
			case VARIANT:
				return "Wiki table for " + selected.getLabel() + ", which counts for this task.";
			case GENERIC:
				return "No task-specific wiki table; showing generic slayer gear.";
			default:
				return null;
		}
	}

	/** Icon ids for everything shown: the owned item's real id when known, else the wiki's id for the name. */
	private Map<String, Integer> icons(List<RequirementStatus> statuses, Map<String, Map<String, SlotChoice>> gear)
	{
		Map<String, Integer> icons = new HashMap<>();
		for (RequirementStatus status : statuses)
		{
			addIcon(icons, status.getItemName());
			status.getRequirement().getAlternatives().forEach(m -> addIcon(icons, m.getName()));
		}
		for (Map<String, SlotChoice> slots : gear.values())
		{
			for (SlotChoice choice : slots.values())
			{
				addIcon(icons, choice.getItemName());
				addIcon(icons, choice.getNextUpgrade());
			}
		}
		return icons;
	}

	private void addIcon(Map<String, Integer> icons, @Nullable String itemName)
	{
		if (itemName == null)
		{
			return;
		}
		String key = ItemMatcher.normalize(itemName);
		Integer id = ownedIds.get(key);
		if (id == null && gearData != null)
		{
			// "A / B" upgrade labels: use the first alternative
			String first = itemName.contains(" / ") ? itemName.substring(0, itemName.indexOf(" / ")) : itemName;
			id = gearData.itemId(first).orElse(null);
		}
		if (id != null)
		{
			icons.put(key, id);
		}
	}

	private RequirementStatus status(Requirement requirement)
	{
		Ownership best = Ownership.MISSING;
		String bestName = null;
		for (ItemMatcher matcher : requirement.getAlternatives())
		{
			Ownership ownership = snapshot.find(matcher);
			if (ownership.ordinal() < best.ordinal())
			{
				best = ownership;
				bestName = snapshot.findName(matcher).orElse(null);
			}
		}
		return new RequirementStatus(requirement, best, bestName);
	}

	private Map<String, Map<String, SlotChoice>> adviseGear(GearData.GearTask gearTask)
	{
		Map<String, Map<String, SlotChoice>> advised = advisor.advise(gearTask, snapshot);
		Map<String, Map<String, SlotChoice>> ordered = new LinkedHashMap<>();
		for (String style : GearAdvisor.orderStyles(advised.keySet(), config.styleOrder()))
		{
			ordered.put(style, advised.get(style));
		}
		return ordered;
	}

	/** Slayer reward unlocks that let the Slayer helmet replace a task item, read from the game. */
	private List<String> perkNotes()
	{
		List<String> notes = new ArrayList<>();
		String name = task.getName().toLowerCase();
		if (name.contains("gargoyle") && client.getVarbitValue(VarbitID.SLAYER_AUTOKILL_GARGOYLES) == 1)
		{
			notes.add("Gargoyle Smasher unlocked: a Slayer helmet finishes gargoyles without a hammer.");
		}
		if (name.contains("rockslug") && client.getVarbitValue(VarbitID.SLAYER_AUTOKILL_ROCKSLUGS) == 1)
		{
			notes.add("Slug Salter unlocked: a Slayer helmet finishes rockslugs without salt.");
		}
		if (name.equals("lizards") && client.getVarbitValue(VarbitID.SLAYER_AUTOKILL_DESERTLIZARDS) == 1)
		{
			notes.add("Reptile Freezer unlocked: a Slayer helmet finishes desert lizards without an ice cooler.");
		}
		if (name.contains("zygomite") && client.getVarbitValue(VarbitID.SLAYER_AUTOKILL_ZYGOMITES) == 1)
		{
			notes.add("Shroom Sprayer unlocked: a Slayer helmet finishes zygomites without fungicide spray.");
		}
		return notes;
	}

	private void publish(PrepResult newResult)
	{
		result = newResult;
		SlayerPrepPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.update(newResult));
		}
	}
}
