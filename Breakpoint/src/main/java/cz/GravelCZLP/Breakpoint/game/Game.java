package cz.GravelCZLP.Breakpoint.game;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_11_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import cz.GravelCZLP.Breakpoint.Breakpoint;
import cz.GravelCZLP.Breakpoint.achievements.AchievementType;
import cz.GravelCZLP.Breakpoint.game.ctf.CTFGame;
import cz.GravelCZLP.Breakpoint.game.ctf.CTFProperties;
import cz.GravelCZLP.Breakpoint.game.ctf.Team;
import cz.GravelCZLP.Breakpoint.game.cw.CWGame;
import cz.GravelCZLP.Breakpoint.game.dm.DMGame;
import cz.GravelCZLP.Breakpoint.language.MessageType;
import cz.GravelCZLP.Breakpoint.managers.InventoryMenuManager;
import cz.GravelCZLP.Breakpoint.managers.PlayerManager;
import cz.GravelCZLP.Breakpoint.managers.SBManager;
import cz.GravelCZLP.Breakpoint.maps.BPMapPalette;
import cz.GravelCZLP.Breakpoint.maps.CurrentMapRenderer;
import cz.GravelCZLP.Breakpoint.maps.MapManager;
import cz.GravelCZLP.Breakpoint.maps.SizeRenderer;
import cz.GravelCZLP.Breakpoint.players.BPPlayer;

@SuppressWarnings("deprecation")
public abstract class Game {
	// {{STATIC

	public static final Game loadGame(YamlConfiguration yml, String name) {
		try {
			String rawType = yml.getString(name + ".type");
			GameType type = GameType.valueOf(rawType);

			String[] rawSignLoc = yml.getString(name + ".signLoc", "world,0,0,0").split(",");
			Location signLoc = new Location(Bukkit.getWorld(rawSignLoc[0]), Integer.parseInt(rawSignLoc[1]),
					Integer.parseInt(rawSignLoc[2]), Integer.parseInt(rawSignLoc[3]));

			return type.loadGame(yml, name, signLoc);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// }}

	public static final int defaultMapSeconds = 9 * 60;
	private final GameType type;
	private final GameListener listener;
	private String name;
	private Location signLoc;
	private LinkedList<? extends BPMap> maps;
	protected final List<BPPlayer> players;
	private int activeMapId, mapSecondsLeft;
	protected final short votingMapId, currentMapMapId, playerAmountRendererMapId;
	private Integer countdownTaskId = null;
	private boolean active, roundEnded;
	private MapPoll mapPoll;
	private final SizeRenderer playerAmountRenderer;
	private final CurrentMapRenderer currentMapRenderer;
	private String firstBloodPlayerName, lastBloodPlayerName;
	public boolean noPlayers;

	public Game(GameType type, String name, Location signLoc, LinkedList<? extends BPMap> maps) {
		if (type == null || name == null || type.getListenerClass() == null || name.length() <= 0 || signLoc == null
				|| maps == null) {
			throw new IllegalArgumentException();
		}

		Class<? extends GameListener> listenerClass = type.getListenerClass();

		try {
			this.listener = listenerClass.getConstructor(Game.class).newInstance(this);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Cannot create a new instance of GameListener. (Game: " + name + ")");
		}

		this.type = type;
		this.signLoc = signLoc.getBlock().getLocation();
		this.name = name;
		this.maps = maps;
		this.players = new ArrayList<>();

		this.votingMapId = MapManager.getNextFreeId(5);

		this.currentMapMapId = MapManager.getNextFreeId();
		this.currentMapRenderer = new CurrentMapRenderer();
		MapView cmmv = Bukkit.getMap(this.currentMapMapId);
		this.currentMapRenderer.set(cmmv);

		this.playerAmountRendererMapId = MapManager.getNextFreeId();
		System.out.println(playerAmountRendererMapId);
		this.playerAmountRenderer = new SizeRenderer(BPMapPalette.getColor(BPMapPalette.DARK_BROWN, 0),
				BPMapPalette.getColor(BPMapPalette.WHITE, 2), 0);
		MapView rtsmv = Bukkit.getMap(this.playerAmountRendererMapId);
		this.playerAmountRenderer.set(rtsmv);
		this.active = isPlayable(true);
	}

	public Game(GameType type, String name, Location signLoc) {
		this(type, name, signLoc, new LinkedList<BPMap>());
	}

	public void start() {
		changeMap(getRandomMapWithCapacity(getPlayers().size()));
		startCountdown();

		startExtra();

		setActive(true);
	}

	// {{Requests
	public abstract void spawn(BPPlayer bpPlayer);

	public abstract void reset(BPPlayer bpPlayer);

	public abstract void updateProgressObjective(BPPlayer bpPlayer);

	public abstract void updateProgressObjectiveHeader(BPPlayer bpPlayer);

	public abstract void showInGameMenu(BPPlayer bpPlayer);

	protected abstract void endRoundExtra();

	protected abstract void changeMapExtra();

	protected abstract void saveExtra(YamlConfiguration yml);

	protected abstract void startExtra();
	// }}

	public abstract void onCommand(CommandSender sender, String[] args);

	public void onPlayerLeaveGame(BPPlayer bpPlayer) {
		this.players.remove(bpPlayer);
		bpPlayer.setGame(null);
		bpPlayer.setGameProperties(null);
		updatePlayerAmountRenderer();

		SBManager sbm = bpPlayer.getScoreboardManager();

		SBManager.updateLobbyObjectives();
		sbm.updateSidebarObjective();
		sbm.getProgressObj().unregister();
		sbm.setProgressObj(null);
		
		Breakpoint.getInstance().getNametagAPIHook().updateNametag(bpPlayer);
	}

	// {{Saving
	public final void save(YamlConfiguration yml) {
		String mapPath = this.name + ".maps";
		yml.set(this.name, null);

		yml.set(this.name + ".type", this.type.name());
		yml.set(this.name + ".signLoc", this.signLoc.getWorld().getName() + "," + this.signLoc.getBlockX() + ","
				+ this.signLoc.getBlockY() + "," + this.signLoc.getBlockZ());

		saveExtra(yml);

		for (BPMap map : this.maps) {
			if (map.isPlayable()) {
				map.save(yml, mapPath);
			}
		}
	}
	// }}

	public void updateLobbyMaps(BPPlayer bpPlayer) {
		Player player = bpPlayer.getPlayer();

		if (Bukkit.getMap(this.currentMapMapId) == null || Bukkit.getMap(this.playerAmountRendererMapId) == null) {
			return;
		}

		player.sendMap(Bukkit.getMap(this.currentMapMapId));
		player.sendMap(Bukkit.getMap(this.playerAmountRendererMapId));
	}

	public boolean isPlayable(boolean skipActive) {
		if (!skipActive && !isActive()) {
			return false;
		}

		return this.type != null && this.name != null && this.name.length() > 0 && this.signLoc != null
				&& getPlayableMaps().size() > 0;
	}

	public boolean isPlayable() {
		return isPlayable(false);
	}

	public void join(BPPlayer bpPlayer) throws Exception {

		if (this.noPlayers) {
			return;
		}

		Player player = bpPlayer.getPlayer();

		player.setGameMode(GameMode.ADVENTURE);
		player.setFlying(false);
		player.setAllowFlight(false);
		InventoryMenuManager.saveLobbyMenu(bpPlayer);
		this.players.add(bpPlayer);
		bpPlayer.setGame(this);
		bpPlayer.getPlayer().sendMessage(MessageType.LOBBY_GAME_JOIN.getTranslation().getValue(getName()));
		updatePlayerAmountRenderer();
		bpPlayer.setPlayerListName();

		if (bpPlayer.getGame() instanceof CTFGame) {
			CTFGame game = (CTFGame) bpPlayer.getGame();
			player.teleport(game.teamSelectionLocation);
		}
		if (bpPlayer.getGame() instanceof DMGame) {
			DMGame game = (DMGame) bpPlayer.getGame();
			player.teleport(game.characterSelectionLocation);
		}
		if (bpPlayer.getGame() instanceof CWGame) {
			CWGame game = (CWGame) bpPlayer.getGame();
			player.teleport(game.teamSelectionLocation);
		}

		player.getInventory().clear();

		SBManager sbm = bpPlayer.getScoreboardManager();
		updateProgressObjective(bpPlayer);
		sbm.updateSidebarObjective();
		SBManager.updateLobbyObjectives();
	}

	public void updatePlayerAmountRenderer() {
		this.playerAmountRenderer.setSize(this.players.size());
		MapManager.updateMapForNotPlayingPlayers(this.playerAmountRendererMapId);
	}

	public void second() {
		setMapSecondsLeft(getMapSecondsLeft() - 1);

		if (getMapSecondsLeft() > 0) {
			scheduleNextSecond();
		} else {
			endRound();
			Bukkit.getScheduler().runTaskLater(Breakpoint.getInstance(), new Runnable() {
				@Override
				public void run() {
					scheduleMapPoll();
				}
			}, 10L);
		}

		updateProgressHeaderTime();
	}

	public void updateProgressHeaderTime() {
		for (BPPlayer bpPlayer : this.players) {
			updateProgressObjectiveHeader(bpPlayer);
		}
	}

	public void scheduleMapPoll() {
		final Game game = this;
		Bukkit.getScheduler().scheduleSyncDelayedTask(Breakpoint.getInstance(), new Runnable() {
			@Override
			public void run() {
				setMapPoll(new MapPoll(game));
			}
		}, 20L * 10);
	}

	public void endRound() {
		endRoundExtra();
		setRoundEnded(true);
		PlayerManager.clearHotBars();
		awardLastKiller();
		for (BPPlayer bpPlayer : players) {
			Breakpoint.getInstance().getNametagAPIHook().updateNametag(bpPlayer);
		}
	}

	public void awardLastKiller() {
		if (getLastBloodPlayerName() == null) {
			return;
		}

		Player player = Bukkit.getPlayerExact(getLastBloodPlayerName());

		if (player == null) {
			return;
		}

		BPPlayer bpPlayer = BPPlayer.get(player);

		if (Bukkit.getOnlinePlayers().size() >= Bukkit.getMaxPlayers() / 2) {
			bpPlayer.checkAchievement(AchievementType.LAST_BLOOD);
		}

		Location playerLoc = player.getLocation();
		String playerPVPName = bpPlayer.getPVPName();
		broadcast(ChatColor.DARK_RED + "" + ChatColor.BOLD + "> LAST BLOOD! " + playerPVPName + ChatColor.DARK_RED
				+ ChatColor.BOLD + " <", false);
		PlayerManager.spawnRandomlyColoredFirework(playerLoc);
	}

	public void broadcastDeathMessage(String victim, String killer) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			BPPlayer bpPlayer = BPPlayer.get(player);

			if (bpPlayer.isInGame()) {
				if (bpPlayer.getSettings().hasDeathMessages()) {
					player.sendMessage(MessageType.PVP_KILLINFO_KILLED.getTranslation().getValue(victim, killer));
				}
			}
		}
	}

	public void broadcastDeathMessage(String victim) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			BPPlayer bpPlayer = BPPlayer.get(player);

			if (bpPlayer.isInGame()) {
				if (bpPlayer.getSettings().hasDeathMessages()) {
					player.sendMessage(MessageType.PVP_KILLINFO_DIED.getTranslation().getValue(victim));
				}
			}
		}
	}

	public void startCountdown() {
		if (getCountdownTaskId() != null) {
			Bukkit.getScheduler().cancelTask(getCountdownTaskId());
		}

		setMapSecondsLeft(defaultMapSeconds);
		scheduleNextSecond();
	}

	public void scheduleNextSecond() {
		Breakpoint plugin = Breakpoint.getInstance();

		setCountdownTaskId(plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			@Override
			public void run() {
				second();
			}
		}, 20L));
	}

	public int getRandomMapWithCapacity(int players) {
		ArrayList<Integer> ids = new ArrayList<>();
		for (int i = 0; i < getMaps().size(); i++) {
			BPMap map = getMaps().get(i);

			if (!map.isPlayable()) {
				continue;
			}

			if (map.isPlayableWith(players)) {
				ids.add(i);
			}
		}
		if (ids.size() > 0) {
			return ids.get(new Random().nextInt(ids.size()));
		} else {
			return -1;
		}
	}

	public void changeMap(int mapId) {
		setActiveMapId(mapId);
		BPMap map = getCurrentMap();
		String mapName = map.getName();

		map.setLastTimePlayed(System.currentTimeMillis());
		spawnPlayers();
		updateCurrentMapRenderer();
		setFirstBloodPlayerName(null);
		setLastBloodPlayerName(null);

		this.noPlayers = false;

		for (World world : Bukkit.getWorlds()) {
			world.setGameRuleValue("doDaylightCycle", "false");
		}

		for (BPPlayer bpPlayer : players) {
			Breakpoint.getInstance().getNametagAPIHook().updateNametag(bpPlayer);
		}
		
		setRoundEnded(false);
		startCountdown();

		changeMapExtra();

		broadcast(MessageType.MAP_CHANGE.getTranslation().getValue(mapName), true);
	}

	public void updateCurrentMapRenderer() {
		BPMap map = getCurrentMap();
		this.currentMapRenderer.setCurrentMap(map);
		if (Bukkit.getMap(this.currentMapMapId) != null) {
			MapManager.updateMapForNotPlayingPlayers(this.currentMapMapId);
		}
	}

	public void spawnPlayers() {
		for (BPPlayer bpPlayer : this.players) {
			bpPlayer.spawn();
		}
	}

	public void broadcastCombo(String playerName, Location loc, ChatColor color, String decor, String name) {
		broadcast(color + "" + decor + " " + ChatColor.BOLD + name + "! - " + playerName + color + " " + decor, false);
		PlayerManager.spawnRandomlyColoredFirework(loc);
	}

	public void broadcast(String string, boolean prefix) {
		for (BPPlayer bpPlayer : this.players) {
			if (bpPlayer.isPlaying()) {
				Player player = bpPlayer.getPlayer();

				player.sendMessage((prefix ? ChatColor.DARK_GRAY + "[" + ChatColor.LIGHT_PURPLE + ChatColor.BOLD
						+ "Breakpoint" + ChatColor.DARK_GRAY + "] " : "") + ChatColor.YELLOW + string);
			}
		}
		Bukkit.getConsoleSender().sendMessage("[Breakpoint] [" + this.name + "] " + string);
	}

	public void broadcast(String string) {
		broadcast(string, false);
	}

	public void broadcastTitle(String title, Team t) {
		for (BPPlayer bpPlayer : this.players) {
			if (bpPlayer.isPlaying() && ((CTFProperties) bpPlayer.getGameProperties()).getTeam() == t) {
				((CraftPlayer) bpPlayer.getPlayer()).sendTitle(title, "");
			}
		}
	}

	public void broadcastTitle(String title, String subtitle, Team t) {
		for (BPPlayer bpPlayer : this.players) {
			if (bpPlayer.isPlaying() && ((CTFProperties) bpPlayer.getGameProperties()).getTeam() == t) {
				((CraftPlayer) bpPlayer.getPlayer()).sendTitle(title, subtitle);
			}
		}
	}

	public BPMap getMapByName(String mapName) {
		for (BPMap map : getMaps()) {
			if (mapName.equals(map.getName())) {
				return map;
			}
		}
		return null;
	}

	public boolean votingInProgress() {
		if (getMapPoll() != null) {
			return getMapPoll().getVoting();
		}
		return false;
	}

	public boolean isMapActive(int index) {
		BPMap map = this.maps.get(index);
		return isMapActive(map);
	}

	public boolean isMapActive(BPMap map) {
		return getCurrentMap().equals(map);
	}

	public BPMap getCurrentMap() {
		int id = getActiveMapId();
		if (id == -1) {
			id = 0;
		}

		return getMaps().get(id);
	}

	public GameType getType() {
		return this.type;
	}

	public LinkedList<? extends BPMap> getPlayableMaps() {
		LinkedList<BPMap> playableMaps = new LinkedList<>();

		for (BPMap map : this.maps) {
			if (map.isPlayable()) {
				playableMaps.add(map);
			}
		}

		return playableMaps;
	}

	public LinkedList<? extends BPMap> getMaps() {
		return this.maps;
	}

	public void setMaps(LinkedList<BPMap> maps) {
		this.maps = maps;
	}

	public List<BPPlayer> getPlayers() {
		return this.players;
	}

	public int getActiveMapId() {
		return this.activeMapId;
	}

	public void setActiveMapId(int activeMapId) {
		this.activeMapId = activeMapId;
	}

	public int getMapSecondsLeft() {
		return this.mapSecondsLeft;
	}

	public void setMapSecondsLeft(int mapSecondsLeft) {
		this.mapSecondsLeft = mapSecondsLeft;
	}

	public Integer getCountdownTaskId() {
		return this.countdownTaskId;
	}

	public void setCountdownTaskId(Integer countdownTaskId) {
		this.countdownTaskId = countdownTaskId;
	}

	public boolean hasRoundEnded() {
		return this.roundEnded;
	}

	public void setRoundEnded(boolean roundEnded) {
		this.roundEnded = roundEnded;
	}

	public MapPoll getMapPoll() {
		return this.mapPoll;
	}

	public void setMapPoll(MapPoll mapPoll) {
		this.mapPoll = mapPoll;
	}

	public String getFirstBloodPlayerName() {
		return this.firstBloodPlayerName;
	}

	public void setFirstBloodPlayerName(String firstBloodPlayerName) {
		this.firstBloodPlayerName = firstBloodPlayerName;
	}

	public String getLastBloodPlayerName() {
		return this.lastBloodPlayerName;
	}

	public void setLastBloodPlayerName(String lastBloodPlayerName) {
		this.lastBloodPlayerName = lastBloodPlayerName;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Location getSignLocation() {
		return this.signLoc;
	}

	public void setSignLocation(Location signLoc) {
		this.signLoc = signLoc;
	}

	public boolean isActive() {
		return this.active;
	}

	private void setActive(boolean active) {
		this.active = active;
	}

	public short getVotingMapId() {
		return this.votingMapId;
	}

	public GameListener getListener() {
		return this.listener;
	}
}
