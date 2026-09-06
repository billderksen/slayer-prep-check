package com.slayerprep;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Sidebar panel: current task, required item status, and per-style gear sets from the player's own items.
 */
class SlayerPrepPanel extends PluginPanel
{
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final int ICON_SIZE = 32;
	private static final Dimension ICON_DIMENSION = new Dimension(ICON_SIZE, ICON_SIZE);

	private final ItemManager itemManager;
	private final Consumer<String> onGearOptionSelected;
	private final Consumer<String> onStyleSelected;
	private final JLabel taskLabel = new JLabel();
	private final JLabel detailLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JPanel body = new JPanel();

	SlayerPrepPanel(ItemManager itemManager, Consumer<String> onGearOptionSelected, Consumer<String> onStyleSelected)
	{
		this.itemManager = itemManager;
		this.onGearOptionSelected = onGearOptionSelected;
		this.onStyleSelected = onStyleSelected;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new GridLayout(0, 1, 0, 2));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JLabel title = new JLabel("Slayer Prep Check");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		taskLabel.setFont(FontManager.getRunescapeBoldFont());
		taskLabel.setForeground(Color.WHITE);
		detailLabel.setFont(FontManager.getRunescapeSmallFont());
		detailLabel.setForeground(MUTED);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		header.add(title);
		header.add(taskLabel);
		header.add(detailLabel);
		header.add(statusLabel);
		add(header, BorderLayout.NORTH);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(body, BorderLayout.CENTER);
	}

	void update(PrepResult result)
	{
		body.removeAll();
		SlayerTask task = result.getTask();
		if (task == null)
		{
			taskLabel.setText("No Slayer task");
			detailLabel.setText("Get a task from a Slayer master.");
			statusLabel.setText(" ");
		}
		else
		{
			taskLabel.setText(task.getName());
			detailLabel.setText(task.getAmount() + " left" + (task.getLocation() != null ? " • " + task.getLocation() : ""));
			updateStatus(result);
			if (!result.isBankKnown())
			{
				body.add(note("Open your bank once so the plugin knows what you own."));
			}
			body.add(requirementsSection(result));
			for (String n : result.getNotes())
			{
				body.add(note(n));
			}
			body.add(gearSection(result));
		}
		body.revalidate();
		body.repaint();
	}

	private void updateStatus(PrepResult result)
	{
		int toFetch = result.requiredNotCarried().size() + result.gearInBank().size();
		if (result.isReady())
		{
			statusLabel.setText("Ready: everything is with you");
			statusLabel.setForeground(Ownership.CARRIED.getColor());
		}
		else if (result.requiredMissing().isEmpty())
		{
			statusLabel.setText("Not ready: " + toFetch + (toFetch == 1 ? " item" : " items") + " still to fetch");
			statusLabel.setForeground(Ownership.IN_BANK.getColor());
		}
		else
		{
			statusLabel.setText("Not ready: missing " + String.join(", ", result.requiredMissing()));
			statusLabel.setForeground(Ownership.MISSING.getColor());
		}
	}

	private JPanel requirementsSection(PrepResult result)
	{
		JPanel section = section("Required items");
		if (result.getRequirements().isEmpty())
		{
			section.add(muted("No special items needed."));
			return section;
		}
		for (RequirementStatus status : result.getRequirements())
		{
			String badge = status.getOwnership().getLabel() + (status.isRequired() ? "" : " · optional");
			JPanel row = itemRow(result.iconFor(status.getLabel()), status.getLabel(), status.getOwnership().getColor(), badge);
			row.setToolTipText(status.getRequirement().getLabel());
			section.add(row);
		}
		return section;
	}

	private JPanel gearSection(PrepResult result)
	{
		JPanel section = section("Gear from your bank");
		if (result.getGearOptions().size() > 1)
		{
			section.add(gearOptionPicker(result));
		}
		if (result.getGearNote() != null)
		{
			section.add(muted(result.getGearNote()));
		}
		if (result.getGear().isEmpty())
		{
			return section;
		}
		JTabbedPane tabs = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
		tabs.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tabs.setFont(FontManager.getRunescapeSmallFont());
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		for (Map.Entry<String, Map<String, SlotChoice>> style : result.getGear().entrySet())
		{
			tabs.addTab(style.getKey(), styleTab(result, style.getValue()));
			if (style.getKey().equals(result.getSelectedStyle()))
			{
				tabs.setSelectedIndex(tabs.getTabCount() - 1);
			}
		}
		// added after the selection so rebuilding the panel does not fire a change
		tabs.addChangeListener(e ->
		{
			int index = tabs.getSelectedIndex();
			if (index >= 0 && !tabs.getTitleAt(index).equals(result.getSelectedStyle()))
			{
				onStyleSelected.accept(tabs.getTitleAt(index));
			}
		});
		section.add(tabs);
		return section;
	}

	private JComboBox<String> gearOptionPicker(PrepResult result)
	{
		JComboBox<String> picker = new JComboBox<>(result.getGearOptions().toArray(new String[0]));
		picker.setFont(FontManager.getRunescapeSmallFont());
		picker.setSelectedItem(result.getSelectedGearOption());
		picker.setToolTipText("Which monster's wiki gear table to use for this task");
		picker.setAlignmentX(LEFT_ALIGNMENT);
		picker.setMaximumSize(new Dimension(Integer.MAX_VALUE, picker.getPreferredSize().height));
		// added after setSelectedItem so rebuilding the panel does not fire a selection
		picker.addActionListener(e ->
		{
			Object chosen = picker.getSelectedItem();
			if (chosen != null && !chosen.equals(result.getSelectedGearOption()))
			{
				onGearOptionSelected.accept((String) chosen);
			}
		});
		return picker;
	}

	private JPanel styleTab(PrepResult result, Map<String, SlotChoice> slots)
	{
		JPanel tab = new JPanel();
		tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
		tab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tab.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
		for (Map.Entry<String, SlotChoice> slot : slots.entrySet())
		{
			tab.add(slotRow(result, slot.getKey(), slot.getValue()));
		}
		return tab;
	}

	private JPanel slotRow(PrepResult result, String slot, SlotChoice choice)
	{
		String name = choice.getItemName() != null ? choice.getItemName() : "Nothing owned";
		Color color = choice.getItemName() != null ? choice.getOwnership().getColor() : MUTED;
		JPanel row = itemRow(result.iconFor(choice.getItemName()), name, color, slotName(slot));
		if (choice.getNote() != null)
		{
			row.setToolTipText(choice.getNote());
			JLabel nameLabel = (JLabel) ((JPanel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER)).getComponent(0);
			nameLabel.setText(name + " *");
			nameLabel.setToolTipText(choice.getNote());
		}
		if (choice.getNextUpgrade() != null)
		{
			JLabel upgrade = new JLabel("Upgrade: " + choice.getNextUpgrade());
			upgrade.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
			upgrade.setForeground(MUTED);
			upgrade.setToolTipText("Next upgrade from the wiki table");
			JPanel text = (JPanel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER);
			text.add(upgrade);
		}
		return row;
	}

	/** One line: item icon, name in the ownership colour, and a small grey badge on the right. */
	private JPanel itemRow(@Nullable Integer iconId, String name, Color color, String badge)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ICON_SIZE + 8));

		JLabel icon = new JLabel();
		icon.setPreferredSize(ICON_DIMENSION);
		icon.setMinimumSize(ICON_DIMENSION);
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (iconId != null)
		{
			AsyncBufferedImage image = itemManager.getImage(iconId);
			image.addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(color);
		text.add(nameLabel);
		row.add(text, BorderLayout.CENTER);

		JLabel badgeLabel = new JLabel(badge);
		badgeLabel.setFont(FontManager.getRunescapeSmallFont());
		badgeLabel.setForeground(MUTED);
		badgeLabel.setVerticalAlignment(SwingConstants.TOP);
		row.add(badgeLabel, BorderLayout.EAST);
		return row;
	}

	private static String slotName(String slot)
	{
		return "2h".equals(slot) ? "Two-handed" : Character.toUpperCase(slot.charAt(0)) + slot.substring(1);
	}

	private static JPanel section(String heading)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARK_GRAY_COLOR);
		section.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		section.setAlignmentX(LEFT_ALIGNMENT);
		JLabel label = new JLabel(heading);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		section.add(label);
		return section;
	}

	private static JLabel muted(String text)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JComponent note(String text)
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JLabel label = muted(text);
		panel.add(label);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 12));
		return panel;
	}
}
