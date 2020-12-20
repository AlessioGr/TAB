package me.neznamy.tab.shared.features.bossbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.Shared;
import me.neznamy.tab.shared.config.Configs;
import me.neznamy.tab.shared.cpu.TabFeature;
import me.neznamy.tab.shared.cpu.UsageType;
import me.neznamy.tab.shared.features.PlaceholderManager;
import me.neznamy.tab.shared.features.interfaces.CommandListener;
import me.neznamy.tab.shared.features.interfaces.JoinEventListener;
import me.neznamy.tab.shared.features.interfaces.Loadable;
import me.neznamy.tab.shared.features.interfaces.WorldChangeListener;
import me.neznamy.tab.shared.placeholders.ServerPlaceholder;

/**
 * Class for handling bossbar feature
 */
public class BossBar implements Loadable, JoinEventListener, WorldChangeListener, CommandListener{

	public List<String> defaultBars;
	public Map<String, List<String>> perWorld;
	public Map<String, BossBarLine> lines = new HashMap<String, BossBarLine>();
	private String toggleCommand;
	public List<String> announcements = new ArrayList<String>();
	public boolean remember_toggle_choice;
	public List<String> bossbar_off_players;
	public boolean permToToggle;
	private List<String> disabledWorlds;
	public long announceEndTime;
	private boolean hiddenByDefault;

	public BossBar() {
		disabledWorlds = Configs.config.getStringList("disable-features-in-"+Shared.platform.getSeparatorType()+"s.bossbar", Arrays.asList("disabled" + Shared.platform.getSeparatorType()));
		toggleCommand = Configs.bossbar.getString("bossbar-toggle-command", "/bossbar");
		defaultBars = Configs.bossbar.getStringList("default-bars", new ArrayList<String>());
		permToToggle = Configs.bossbar.getBoolean("permission-required-to-toggle", false);
		hiddenByDefault = Configs.bossbar.getBoolean("hidden-by-default", false);
		perWorld = Configs.bossbar.getConfigurationSection("per-world");
		for (Object bar : Configs.bossbar.getConfigurationSection("bars").keySet()){
			lines.put(bar+"", BossBarLine.fromConfig(bar+""));
		}
		for (String bar : new ArrayList<String>(defaultBars)) {
			if (lines.get(bar) == null) {
				Shared.errorManager.startupWarn("BossBar \"&e" + bar + "&c\" is defined as default bar, but does not exist! &bIgnoring.");
				defaultBars.remove(bar);
			}
		}
		for (Entry<String, List<String>> entry : perWorld.entrySet()) {
			List<String> bars = entry.getValue();
			for (String bar : new ArrayList<String>(bars)) {
				if (lines.get(bar) == null) {
					Shared.errorManager.startupWarn("BossBar \"&e" + bar + "&c\" is defined as per-world bar in world &e" + entry.getKey() + "&c, but does not exist! &bIgnoring.");
					bars.remove(bar);
				}
			}
		}
		remember_toggle_choice = Configs.bossbar.getBoolean("remember-toggle-choice", false);
		if (remember_toggle_choice) {
			bossbar_off_players = Configs.getPlayerData("bossbar-off");
		}
		if (bossbar_off_players == null) bossbar_off_players = new ArrayList<String>();
		((PlaceholderManager) Shared.featureManager.getFeature("placeholders")).registerPlaceholder(new ServerPlaceholder("%countdown%", 100) {

			@Override
			public String get() {
				return "" + (announceEndTime - System.currentTimeMillis()) / 1000;
			}
		});
	}
	
	@Override
	public void load() {
		for (TabPlayer p : Shared.getPlayers()) {
			onJoin(p);
		}
		Shared.cpu.startRepeatingMeasuredTask(1000, "refreshing bossbar permissions", getFeatureType(), UsageType.REPEATING_TASK, new Runnable() {
			public void run() {
				for (TabPlayer p : Shared.getPlayers()) {
					if (!p.hasBossbarVisible() || isDisabledWorld(disabledWorlds, p.getWorldName())) continue;
					for (BossBarLine bar : p.getActiveBossBars().toArray(new BossBarLine[0])) {
						if (!bar.isConditionMet(p)) {
							bar.remove(p);
							p.getActiveBossBars().remove(bar);
						}
					}
					showBossBars(p, defaultBars);
					showBossBars(p, perWorld.get(p.getWorldName()));
				}
			}
		});
	}
	
	@Override
	public void unload() {
		for (TabPlayer p : Shared.getPlayers()) {
			for (me.neznamy.tab.api.bossbar.BossBar line : p.getActiveBossBars().toArray(new me.neznamy.tab.api.bossbar.BossBar[0])) {
				p.removeBossBar(line);
			}
		}
		lines.clear();
	}
	
	@Override
	public void onJoin(TabPlayer connectedPlayer) {
		connectedPlayer.setBossbarVisible(!bossbar_off_players.contains(connectedPlayer.getName()) && !hiddenByDefault);
		detectBossBarsAndSend(connectedPlayer);
	}
	
	@Override
	public void onWorldChange(TabPlayer p, String from, String to) {
		for (me.neznamy.tab.api.bossbar.BossBar line : p.getActiveBossBars().toArray(new me.neznamy.tab.api.bossbar.BossBar[0])) {
			p.removeBossBar(line);
		}
		detectBossBarsAndSend(p);
	}
	
	@Override
	public boolean onCommand(TabPlayer sender, String message) {
		if (message.equalsIgnoreCase(toggleCommand)) {
			Shared.command.execute(sender, new String[] {"bossbar"});
			return true;
		}
		return false;
	}
	
	public void detectBossBarsAndSend(TabPlayer p) {
		p.getActiveBossBars().clear();
		if (isDisabledWorld(disabledWorlds, p.getWorldName()) || !p.hasBossbarVisible()) return;
		showBossBars(p, defaultBars);
		showBossBars(p, announcements);
		showBossBars(p, perWorld.get(p.getWorldName()));
	}
	
	private void showBossBars(TabPlayer p, List<String> bars) {
		if (bars == null) return;
		for (String defaultBar : bars) {
			BossBarLine bar = lines.get(defaultBar);
			if (bar.isConditionMet(p) && !p.getActiveBossBars().contains(bar)) {
				bar.create(p);
				p.getActiveBossBars().add(bar);
			}
		}
	}
	
	/**
	 * Returns name of the feature displayed in /tab cpu
	 * @return name of the feature displayed in /tab cpu
	 */
	@Override
	public TabFeature getFeatureType() {
		return TabFeature.BOSSBAR;
	}
	
	public BossBarLine getLine(UUID id) {
		for (BossBarLine line : lines.values()) {
			if (line.uuid == id) return line;
		}
		return null;
	}
}