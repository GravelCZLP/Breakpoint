package cz.GravelCZLP.Breakpoint.players;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.NotImplementedException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_11_R1.entity.CraftPlayer;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.fijistudios.jordan.FruitSQL;

import cz.GravelCZLP.Breakpoint.Breakpoint;
import cz.GravelCZLP.Breakpoint.Configuration;
import cz.GravelCZLP.Breakpoint.achievements.Achievement;
import cz.GravelCZLP.Breakpoint.achievements.AchievementTranslation;
import cz.GravelCZLP.Breakpoint.achievements.AchievementType;
import cz.GravelCZLP.Breakpoint.achievements.CharacterAchievement;
import cz.GravelCZLP.Breakpoint.equipment.BPArmor;
import cz.GravelCZLP.Breakpoint.equipment.BPEquipment;
import cz.GravelCZLP.Breakpoint.game.CharacterType;
import cz.GravelCZLP.Breakpoint.game.Game;
import cz.GravelCZLP.Breakpoint.game.GameProperties;
import cz.GravelCZLP.Breakpoint.hooks.VaultHooks;
import cz.GravelCZLP.Breakpoint.language.MessageType;
import cz.GravelCZLP.Breakpoint.managers.AfkManager;
import cz.GravelCZLP.Breakpoint.managers.ChatManager;
import cz.GravelCZLP.Breakpoint.managers.DoubleMoneyManager;
import cz.GravelCZLP.Breakpoint.managers.InventoryMenuManager;
import cz.GravelCZLP.Breakpoint.managers.SBManager;
import cz.GravelCZLP.Breakpoint.perks.Perk;
import cz.GravelCZLP.Breakpoint.perks.PerkType;
import cz.GravelCZLP.Breakpoint.players.clans.Clan;
import cz.GravelCZLP.Breakpoint.statistics.PlayerStatistics;
import me.limeth.storageAPI.Column;
import me.limeth.storageAPI.Storage;
import me.limeth.storageAPI.StorageType;
import net.minecraft.server.v1_11_R1.EntityPlayer;
import net.minecraft.server.v1_11_R1.EnumParticle;
import net.minecraft.server.v1_11_R1.PacketPlayOutWorldBorder;
import net.minecraft.server.v1_11_R1.PacketPlayOutWorldBorder.EnumWorldBorderAction;
import net.minecraft.server.v1_11_R1.PacketPlayOutWorldParticles;
import net.minecraft.server.v1_11_R1.WorldBorder;

public class BPPlayer {
	// {{STATIC
	public static LinkedList<BPPlayer> onlinePlayers = new LinkedList<>();

	public static final BPPlayer get(String playerName, boolean create) {
		if (playerName == null) {
			return null;
		}

		for (BPPlayer bpPlayer : onlinePlayers) {
			if (bpPlayer.getOfflinePlayer().getName().equals(playerName)) {
				return bpPlayer;
			}
		}

		if (create) {
			try {
				return createPlayer(playerName);
			} catch (Exception e) {
				Breakpoint.warn("Error when creating player '" + playerName + "': " + e.getMessage());
				e.printStackTrace();
				return null;
			}
		} else {
			Breakpoint.warn("Returning null BPPlayer for " + playerName);

			for (BPPlayer bpPlayer : onlinePlayers) {
				Breakpoint.warn(bpPlayer.getOfflinePlayer().getName());
			}

			return null;
		}
	}

	public static final BPPlayer get(String playerName) {
		return get(playerName, false);
	}

	public static final BPPlayer get(Player player) {
		return get(player.getName());
	}

	private static final BPPlayer createPlayer(String playerName) throws Exception {
		BPPlayer bpPlayer = load(playerName);

		onlinePlayers.add(bpPlayer);

		return bpPlayer;
	}

	public static final void removePlayer(BPPlayer bpPlayer) {
		onlinePlayers.remove(bpPlayer);

		Game game = bpPlayer.getGame();

		if (game != null) {
			game.onPlayerLeaveGame(bpPlayer);
		}
		
		bpPlayer.getScoreboardManager().unregister();
	}

	public static final void removePlayer(String playerName) {
		BPPlayer bpPlayer = BPPlayer.get(playerName);

		if (bpPlayer != null) {
			removePlayer(bpPlayer);
		}
	}

	public static final BPPlayer load(String playerName, StorageType storageType, FruitSQL mySQL) throws Exception {
		Configuration config = Breakpoint.getBreakpointConfig();
		Storage storage = Storage.load(storageType, playerName, getFolder(), mySQL, config.getMySQLTablePlayers());

		Settings settings = Settings.load(storage);
		LobbyInventory lobbyInventory = LobbyInventory.load(storage);
		PlayerStatistics stats = PlayerStatistics.loadPlayerStatistics(storage);
		List<Achievement> achievements = Achievement.loadPlayerAchievements(storage);
		List<Perk> perks = Perk.loadPlayerPerks(storage);

		long timeJoined = System.currentTimeMillis();
		Clan clan = Clan.getByPlayer(playerName);

		BPPlayer bpPlayer = new BPPlayer(playerName, settings, lobbyInventory, stats, achievements, perks, clan,
				timeJoined);

		return bpPlayer;
	}

	public static final BPPlayer load(String playerName) throws Exception {
		StorageType storageType = Breakpoint.getBreakpointConfig().getStorageType();
		FruitSQL mySQL = Breakpoint.getMySQL();

		return load(playerName, storageType, mySQL);
	}

	public static final void saveOnlinePlayersData() throws IOException {
		for (BPPlayer bpPlayer : onlinePlayers) {
			bpPlayer.trySave();
		}
	}

	public static File getFile(String playerName) {
		return new File(getFolder(), playerName + ".yml");
	}

	public static File getFolder() {
		return new File("plugins/Breakpoint/players/");
	}

	public static List<String> getPlayerNames(StorageType storageType) {
		if (storageType == null) {
			return null;
		}

		if (storageType == StorageType.YAML) {
			File folder = getFolder();
			File[] files = folder.listFiles();
			List<String> list = new LinkedList<>();

			if (files == null) {
				return null;
			}

			for (File file : files) {
				String name = FilenameUtils.removeExtension(file.getName());

				list.add(name);
			}

			return list;
		} else if (storageType == StorageType.MYSQL) {
			return Storage.queryKeyColumn(Breakpoint.getMySQL(),
					Breakpoint.getBreakpointConfig().getMySQLTablePlayers(), String.class);
		} else {
			throw new NotImplementedException("Storage Type " + storageType.toString() + " is not implemented yet.");
		}
	}

	public static void updateTable(FruitSQL mySQL) {
		try {
			if (mySQL == null) {
				return;
			}

			List<Column> columns = getRequiredMySQLColumns();
			String tableName = Breakpoint.getBreakpointConfig().getMySQLTablePlayers();

			Storage.createTable(mySQL, tableName, "VARCHAR(16)", columns.toArray(new Column[columns.size()]));
			addMissingColumns(mySQL, tableName, columns);
		} catch (Exception e) {
			Breakpoint.warn("Error when updating the MySQL player table: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static void addMissingColumns(FruitSQL mySQL, String table, List<Column> available) throws SQLException {
		List<Column> missing = getMissingColumns(mySQL, table, available);

		if (missing.size() <= 0) {
			return;
		}

		StringBuilder sb = new StringBuilder();

		for (Column column : missing) {
			sb.append("ALTER TABLE `").append(table).append("` ADD ").append(column.toString()).append("; ");
		}

		mySQL.execute(sb.toString());
	}

	private static List<Column> getMissingColumns(FruitSQL mySQL, String table, List<Column> available)
			throws SQLException {
		List<Column> missing = new LinkedList<>();
		ResultSetMetaData rsmd = mySQL.getMetaData(table);
		int size = rsmd.getColumnCount();

		for (Column column : available) {
			boolean isMissing = true;

			for (int i = 1; i <= size; i++) {
				String name = rsmd.getColumnName(i);

				if (name.equals(column.getName())) {
					isMissing = false;
					break;
				}
			}

			if (isMissing) {
				missing.add(column);
			}
		}

		return missing;
	}

	public static List<Column> getRequiredMySQLColumns() {
		List<Column> columns = new LinkedList<>();

		columns.addAll(PlayerStatistics.getRequiredMySQLColumns());
		columns.addAll(Perk.getRequiredMySQLColumns());
		columns.addAll(Settings.getRequiredMySQLColumns());
		columns.addAll(Achievement.getRequiredMySQLColumns());
		columns.addAll(LobbyInventory.getRequiredMySQLColumns());

		return columns;
	}
	// }}

	// Main
	private final String name;
	private Settings settings;
	private final LobbyInventory lobbyInventory;
	private PlayerStatistics statistics;
	private List<Achievement> achievements;
	private List<Perk> perks;
	private Clan bpClan;
	private Game game;
	private GameProperties gameProperties;
	private SBManager scoreboardManager;

	// Chat
	private String lastMessage;

	// Others
	private final HashMap<String, Long> cooldowns = new HashMap<>();
	private final HashMap<BPPlayer, Long> lastTimeDamagedBy = new HashMap<>();
	private boolean leaveAfterDeath = false;
	private ItemStack[] quickChatInventoryContents = new ItemStack[0];
	private long spawnTime = 0, lastTimeKilled = 0, timeJoined = 0;
	private int armorWoreSince = 0, achievementViewPage = 0, afkSecondsToKick = AfkManager.defSTK, multikills = 0,
			killedThisLife = 0;
	private BPPlayer achievementViewTarget = null, lastTimeKilledBy = null;
	private Location afkPastLocation = null, shopItemLocation = null, singleTeleportLocation = null;
	private CharacterType queueCharacter = null;
	
	private BPPlayer(String name, Settings settings, LobbyInventory lobbyInventory, PlayerStatistics statistics,
			List<Achievement> achievements, List<Perk> perks, Clan bpClan, long timeJoined) {
		this.settings = settings;
		this.lobbyInventory = lobbyInventory;
		this.name = name;
		this.statistics = statistics;
		this.achievements = achievements;
		this.perks = perks;
		this.bpClan = bpClan;
		this.timeJoined = timeJoined;

		VaultHooks vh = Breakpoint.getInstance().getVaultHooks();
		
		if (vh.isHooked()) {
			if (!vh.getEconomy().hasAccount(getPlayer())) {
				vh.getEconomy().createPlayerAccount(getPlayer());
			}
			
			int currentEmeralds = getMoney();
			int vaultMoney = (int) vh.getEconomy().getBalance(getPlayer());
			
			if (vaultMoney != currentEmeralds) {
				vh.getEconomy().withdrawPlayer(getPlayer(), vaultMoney);
				vh.getEconomy().depositPlayer(getPlayer(), currentEmeralds);
			}
		}
		
		if (isOnline()) {
			this.scoreboardManager = new SBManager(this);
		} else {
			this.scoreboardManager = null;
		}
	}

	public void save(StorageType storageType, FruitSQL mySQL) throws IOException, SQLException {
		if (hasDefaultData()) {
			System.out.println("Data of player " + this.name + " have not been saved because of default values.");
			return;
		}

		Storage storage = new Storage(this.name);
		File folder = getFolder();

		this.settings.save(storage);
		this.lobbyInventory.save(storage);
		this.statistics.savePlayerStatistics(storage);
		Achievement.savePlayerAchievements(storage, this.achievements);
		Perk.savePlayerPerks(storage, this.perks);

		storage.save(storageType, folder, mySQL, Breakpoint.getBreakpointConfig().getMySQLTablePlayers());
	}

	public void trySave(StorageType storageType, FruitSQL mySQL) {
		try {
			save(storageType, mySQL);
		} catch (SQLException | IOException e) {
			Breakpoint.warn("Error when saving player '" + getName() + "': " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void save() throws IOException, SQLException {
		save(Breakpoint.getBreakpointConfig().getStorageType(), Breakpoint.getMySQL());
	}

	public void trySave() {
		try {
			save();
		} catch (SQLException | IOException e) {
			Breakpoint.warn("Error when saving player '" + getName() + "': " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void deleteFile() {
		getFile().delete();
	}

	public File getFile() {
		return getFile(this.name);
	}

	public String getName() {
		return this.name;
	}

	public boolean hasDefaultData() {
		return this.settings.areDefault() && this.lobbyInventory.isEmpty() && this.statistics.areDefault();
	}

	public void setDefaultData() {
		statistics.setDefault();
		for (int i = 0; i < perks.size(); i++) {
			perks.remove(i);
		}
		settings.setDeathMessages(true);
		settings.setExtraSounds(true);
		settings.setShowEnchantments(true);
		for (int i = 0; i < achievements.size(); i++) {
			achievements.remove(i);
		}
		
	}
	
	public void leaveGame() {
		this.game.onPlayerLeaveGame(this);

		this.game = null;
		this.gameProperties = null;
	}

	public boolean isInLobby() {
		return this.game == null && this.gameProperties == null;
	}

	// {{CHAT
	public void setPlayerListName() {
		Breakpoint.getInstance().getNametagAPIHook().updateNametag(this);
	}

	public String getNameTagPrefix() {
		StringBuilder sb = new StringBuilder();
		String prefix = "";
		String gamePrefix = this.gameProperties != null ? this.gameProperties.getTagPrefix() : null;
		if (getPlayer().hasPermission("Breakpoint.admin")) {
			prefix = ChatManager.tagPrefixAdmin;
		} else if (getPlayer().hasPermission("Breakpoint.moderator")) {
			prefix = ChatManager.tagPrefixModerator;
		} else if (getPlayer().hasPermission("Breakpoint.helper")) {
			prefix = ChatManager.tagPrefixHelper;
		} else if (getPlayer().hasPermission("Breakpoint.yt")) {
			prefix = ChatManager.tagPrefixYT;
		} else if (getPlayer().hasPermission("Breakpoint.sponsor")) {
			prefix = ChatManager.tagPrefixSponsor;
		} else if (getPlayer().hasPermission("Breakpoint.vipplus")) {
			prefix = ChatManager.tagPrefixVIPPlus;
		} else if (getPlayer().hasPermission("Breakpoint.vip")) {
			prefix = ChatManager.tagPrefixVIP;
		}
		sb.append(prefix).append(gamePrefix != null ? gamePrefix : "" + ChatColor.ITALIC);
		return sb.toString();
	} 
	
	public String getTagPrefix(boolean brackets) {
		String gamePrefix = this.gameProperties != null ? this.gameProperties.getTagPrefix() : null;
		boolean vip = getPlayer().hasPermission("Breakpoint.vip") || getPlayer().hasPermission("Breakpoint.vipplus");
		boolean staff = getPlayer().hasPermission("Breakpoint.admin") || getPlayer().hasPermission("Breakpoint.moderator") || getPlayer().hasPermission("Breakpoint.helper");
		boolean sponsor = staff ? false : getPlayer().hasPermission("Breakpoint.sponsor");
		boolean yt = staff ? false : getPlayer().hasPermission("Breakpoint.yt");

		return getTagPrefix(gamePrefix, vip, sponsor, yt, brackets);
	}

	public String getTagSuffix() {
		if (getPlayer().isOp()) {
			return ChatColor.DARK_PURPLE + "[OP]";
		}
		return "";
	}

	public String getTag(boolean brackets, boolean cut) {
		Player player = getPlayer();
		String playerName = player.getName();
		String prefix = getTagPrefix(brackets);
		String suffix = getTagSuffix();
		String tag = prefix + playerName + suffix;

		if (cut && tag.length() > 16) {
			return tag.substring(0, 16);
		} else {
			return tag;
		}
	}

	public String getTag() {
		return getTag(false, true);
	}

	private static String getTagPrefix(String gamePrefix, boolean vip, boolean sponsor, boolean yt,boolean brackets) {
		StringBuilder builder = new StringBuilder();
		String prefix = "";

		if (yt) {
			prefix = (brackets ? brackets(ChatManager.tagPrefixYT) : ChatManager.tagPrefixYT) + ChatColor.WHITE + " ";
		} else if (sponsor) {
			prefix = (brackets ? brackets(ChatManager.tagPrefixSponsor) : ChatManager.tagPrefixSponsor)
					+ ChatColor.WHITE + " ";
		} else if (vip) {
			prefix = (brackets ? brackets(ChatManager.tagPrefixVIP) : ChatManager.tagPrefixVIP) + ChatColor.WHITE + " ";
		}
		builder.append(prefix).append(gamePrefix != null ? gamePrefix : "" + ChatColor.ITALIC);

		return builder.length() > 16 ? builder.substring(0, 16) : builder.toString();
	}

	public static String brackets(String string) {
		return ChatColor.DARK_GRAY + "[" + string + ChatColor.DARK_GRAY + "]";
	}

	public void sendClanMessage(String message) {
		if (this.bpClan == null) {
			return;
		}

		for (Player member : this.bpClan.getOnlinePlayers()) {
			member.sendMessage(ChatColor.GOLD + "" + ChatColor.ITALIC + this.name + ": " + message);
		}
	}

	public void sendStaffMessage(String message) {
		if (!getPlayer().hasPermission("Breakpoint.admin")
				|| getPlayer().hasPermission("Breakpoint.moderator")
				|| getPlayer().hasPermission("Breakpoint.helper")
				) {
			return;
		}

		for (Player target : Bukkit.getOnlinePlayers()) {

			if (target.hasPermission("Breakpoint.admin")
					|| target.hasPermission("Breakpoint.moderator")
					|| target.hasPermission("Breakpoint.helper")
					) {
				target.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.ITALIC + this.name + ": " + message);
			}
		}
	}

	/**
	 * @param player
	 * @return Chat name without admin's prefix
	 */
	public String getPVPName() {
		Player player = getPlayer();
		String prefix = getPVPPrefix();
		String playerName = player.getName();
		return prefix + playerName;
	}

	public String getPVPPrefix() {
		Clan clan = getClan();
		String prefix = getRawPVPPrefix();
		String clanName = clan != null ? ChatColor.GRAY + clan.getColoredName() + " " + ChatColor.WHITE : "";// getChatPrefix(player);
		String gamePrefix = this.gameProperties != null ? this.gameProperties.getChatPrefix() : "" + ChatColor.ITALIC;
		return clanName + prefix + gamePrefix;
	}

	public String getChatName() {
		Player player = getPlayer();
		String prefix = getChatPrefix();
		String playerName = player.getName();
		return prefix + playerName;
	}

	public String getChatPrefix() {
		Player player = getPlayer();
		Clan clan = getClan();
		String prefix = getPrefix(player);
		String clanName = clan != null ? ChatColor.GRAY + clan.getColoredName() + " " + ChatColor.WHITE : "";// getChatPrefix(player);
		String gamePrefix = this.gameProperties != null ? this.gameProperties.getChatPrefix() : "" + ChatColor.ITALIC;
		return clanName + prefix + gamePrefix;
	}

	public String getPrefix(Player player) {
		if (player.hasPermission("Breakpoint.admin")) {
			return ChatManager.prefixAdmin + " ";
		} else if (player.hasPermission("Breakpoint.developer")) {
			return ChatManager.prefixDeveloper + " ";
		} else if (player.hasPermission("Breakpoint.moderator")) {
			return ChatManager.prefixModerator + " ";
		} else if (player.hasPermission("Breakpoint.helper")) {
			return ChatManager.prefixHelper + " ";
		} else if (player.hasPermission("Breakpoint.sponsor")) {
			return ChatManager.prefixSponsor + " " + "";
		} else if (player.hasPermission("Breakpoint.yt")) {
			return ChatManager.prefixYT + " ";
		} else if (player.hasPermission("Breakpoint.vipplus")) {
			return ChatManager.prefixVIPPlus + " ";
		} else if (player.hasPermission("Breakpoint.vip")) {
			return ChatManager.prefixVIP + " ";
		} else {
			return "";
		}
	}

	public String getRawPVPPrefix() {
		Player player = getPlayer();
		if (player.hasPermission("Breakpoint.admin")) {
			return ChatManager.prefixAdmin + " ";
		} else if (player.hasPermission("Breakpoint.developer")) {
			return ChatManager.prefixDeveloper + " ";
		} else if (player.hasPermission("Breakpoint.moderator")) {
			return ChatManager.prefixModerator + " ";
		} else if (player.hasPermission("Breakpoint.helper")) {
			return ChatManager.prefixHelper + " ";
		} else if (player.hasPermission("Breakpoint.sponsor")) {
			return ChatManager.prefixSponsor + " ";
		} else if (player.hasPermission("Breakpoint.yt")) {
			return ChatManager.prefixYT + " ";
		} else if (player.hasPermission("Breakpoint.vipplus")) {
			return ChatManager.prefixVIPPlus + " ";
		} else if (player.hasPermission("Breakpoint.vip")) {
			return ChatManager.prefixVIP + " ";
		} else {
			return "";
		}
	}
	// }}

	public boolean hasCooldown(String path, double seconds, boolean setCooldown) {
		long now = System.currentTimeMillis();

		if (this.cooldowns.containsKey(path)) {
			long lastTimeUsed = this.cooldowns.get(path);
			boolean hasCooldown = lastTimeUsed >= now - seconds * 1000;

			if (hasCooldown) {
				return true;
			}
		}

		if (setCooldown) {
			this.cooldowns.put(path, now);
		}
		return false;
	}

	public void removeCooldown(String path) {
		this.cooldowns.remove(path);
	}

	public void clearCooldowns() {
		this.cooldowns.clear();
	}

	public void reset() {
		setMultikills(0);
		setKilledThisLife(0);
		setQueueCharacter(null);

		if (this.game != null) {
			this.game.reset(this);
			this.game.onPlayerLeaveGame(this);
		}

		setGame(null);
		setGameProperties(null);

		setPlayerListName();
	}

	public void sendWarnLowHealth(int i) {
		EntityPlayer nmsPlayer = ((CraftPlayer) Bukkit.getPlayer(this.name)).getHandle();
		WorldBorder playerWorldBorder = nmsPlayer.world.getWorldBorder();
		PacketPlayOutWorldBorder worldBorder = new PacketPlayOutWorldBorder(playerWorldBorder,
				EnumWorldBorderAction.SET_WARNING_BLOCKS);
		try {
			Field f = worldBorder.getClass().getDeclaredField("i");
			f.setAccessible(true);
			f.setInt(worldBorder, i);
			f.setAccessible(!f.isAccessible());
		} catch (Exception e) {
			e.printStackTrace();
		}
		nmsPlayer.playerConnection.sendPacket(worldBorder);
	}

	public void colorArmor() {
		Player player = getPlayer();
		PlayerInventory inv = player.getInventory();
		ItemStack[] armor = inv.getArmorContents();
		BPEquipment[] contents = getLobbyInventory().getContents();
		for (int i = 0; i < 4; i++) {
			if (!(contents[i] instanceof BPArmor) || armor[i] == null) {
				continue;
			}
			BPArmor bpArmor = ((BPArmor) contents[i]).clone();
			bpArmor.colorArmor(armor[i]);
		}
		inv.setArmorContents(armor);
	}

	public void equipArmor() {
		Player player = getPlayer();

		PlayerInventory pi = player.getInventory();
		ItemStack[] armor = getWornArmor();
		armor[2] = applyPerks(armor[2]);
		pi.setArmorContents(armor);
	}

	public ItemStack applyPerks(ItemStack is) {
		for (Perk perk : getEnabledPerks()) {
			is = perk.getType().applyToItemStack(is);
		}

		return is;
	}

	public ItemStack[] getWornArmor() {
		int playingSince = getArmorWoreSince();
		ItemStack[] armor = new ItemStack[4];
		int decreaseMinutesBy = (int) (System.currentTimeMillis() / (1000 * 60) - playingSince);
		BPEquipment[] contents = getLobbyInventory().getContents();

		for (int i = 0; i < 4; i++) {
			if (contents[i] == null) {
				continue;
			}

			BPEquipment bpEquipment = contents[i].clone();
			bpEquipment.decreaseMinutesLeft(decreaseMinutesBy);

			if (bpEquipment.hasExpired()) {
				continue;
			}

			armor[i] = bpEquipment.getItemStack();
		}

		for (int i = 0; i < 4; i++) {
			if (armor[i] == null) {
				ItemStack is = new ItemStack(BPArmor.getMaterial(i));
				ItemMeta im = is.getItemMeta();
				im.setDisplayName(MessageType.SHOP_ITEM_ARMOR_NOCOLOR.getTranslation().getValue());
				is.setItemMeta(im);
				armor[i] = is;
			}
		}

		return armor;
	}

	public void updateArmorMinutesLeft() {
		int playingSince = getArmorWoreSince();
		int decreaseMinutesBy = (int) (System.currentTimeMillis() / (1000 * 60) - playingSince);
		BPEquipment[] contents = getLobbyInventory().getContents();

		for (int i = 0; i < 4; i++) {
			if (contents[i] != null) {
				BPEquipment bpEquipment = contents[i];
				bpEquipment.decreaseMinutesLeft(decreaseMinutesBy);
				if (!bpEquipment.hasExpired()) {
					contents[i] = bpEquipment;
					continue;
				}
			}
			contents[i] = null;
		}

		getLobbyInventory().setContents(contents);
	}

	public boolean hasSpaceInLobbyInventory() {
		BPEquipment[] contents = getLobbyInventory().getContents();
		boolean b = getPlayer().hasPermission("Breakpoint.vipSlots") 
				|| getPlayer().hasPermission("Breakpoint.vip") 
				|| getPlayer().hasPermission("Breakpoint.vipplus");
		int size = b ? 24 : 12;
		for (int i = 0; i < size; i++) {
			if (contents[4 + i] == null) {
				return true;
			}
		}
		return false;
	}

	public int getLobbyInventorySpaceSlot() {
		BPEquipment[] contents = getLobbyInventory().getContents();
		boolean b = getPlayer().hasPermission("Breakpoint.vipSlots") 
				|| getPlayer().hasPermission("Breakpoint.vip") 
				|| getPlayer().hasPermission("Breakpoint.vipplus");
		int size = b ? 24 : 12;
		for (int i = 0; i < size; i++) {
			if (contents[4 + i] == null) {
				return 4 + i;
			}
		}
		return -1;
	}

	@SuppressWarnings("deprecation")
	public void purify() {
		Player player = getPlayer();

		clearInventory();
		player.setHealth(((Damageable) player).getMaxHealth());
		player.setFoodLevel(15);
		player.setSaturation(Float.MAX_VALUE);
		for (PotionEffect pe : player.getActivePotionEffects()) {
			player.removePotionEffect(pe.getType());
		}
	}

	public void teleport(Location loc) {
		Chunk chunk = loc.getWorld().getChunkAt(loc);
		Player player = getPlayer();

		if (!chunk.isLoaded()) {
			chunk.load();
		}
		
		player.teleport(loc);
	}

	public void clearInventory() {
		Player player = getPlayer();
		PlayerInventory pi = player.getInventory();
		pi.clear();
		pi.setArmorContents(new ItemStack[] { null, null, null, null });
	}

	public void spawn() {
		Player player = getPlayer();

		if (player.isDead()) {
			return;
		}

		if (isInGame()) {
			this.game.spawn(this);
		} else {
			Configuration config = Breakpoint.getBreakpointConfig();

			purify();
			teleport(config.getLobbyLocation());
			InventoryMenuManager.showLobbyMenu(this);
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true), true);
		}
	}

	public int addMoney(int amount, boolean inform, boolean allowMultiplication) {
		Player player = getPlayer();
		boolean positive = amount >= 0;
		boolean multiply = positive && DoubleMoneyManager.isDoubleXP() && allowMultiplication;

		int byWhat = 2;

		if (getPlayer().hasPermission("Breakpoint.admin") 
				|| getPlayer().hasPermission("Breakpoint.moderator") 
				|| getPlayer().hasPermission("Breakpoint.helper")) {
			byWhat = 8;
		}
		if (multiply && !getPlayer().hasPermission("Breakpoint.admin") 
				|| !getPlayer().hasPermission("Breakpoint.moderator") 
				|| !getPlayer().hasPermission("Breakpoint.helper")) {
			if (getPlayer().hasPermission("Breakpoint.vipplus")) {
				byWhat = 6;
			} else if (getPlayer().hasPermission("Breakpoint.vip")) {
				byWhat = 4;
			} else {
				byWhat = 2;
			}
		}
		this.statistics.increaseMoney(amount);

		if (inform) {
			if (player != null) {
				MessageType msgType = positive ? MessageType.OTHER_EMERALDS_INCREASE
						: MessageType.OTHER_EMERALDS_DECREASE;

				player.sendMessage(msgType.getTranslation().getValue(amount, multiply ? byWhat + "x" : ""));
			}
		}

		return this.statistics.getMoney();
	}

	public boolean isInGameWith(BPPlayer bpPlayer) {
		if (!isInGame()) {
			return false;
		}

		return this.game.equals(bpPlayer.getGame());
	}

	public boolean isInGame() {
		return this.game != null && this.gameProperties != null;
	}

	public boolean isPlaying() {
		if (!isInGame()) {
			return false;
		}

		return this.gameProperties.isPlaying();
	}

	public Player getPlayer() {
		return Bukkit.getPlayerExact(this.name);
	}

	@SuppressWarnings("deprecation")
	public OfflinePlayer getOfflinePlayer() {
		return Bukkit.getOfflinePlayer(this.name);
	}

	public Game getGame() {
		return this.game;
	}

	public void setGame(Game game) {
		this.game = game;
	}

	public PlayerStatistics getStatistics() {
		return this.statistics;
	}

	public void setStatistics(PlayerStatistics statistics) {
		this.statistics = statistics;
	}

	public int getMaxEquippedPerks() {
		boolean b = getPlayer().hasPermission("Breakpoint.game.moreperks");
		return b ? 3 : 1;
	}

	public int getPerkInventoryRows() {
		int disabled = 0, enabled = 0;

		for (Perk perk : this.perks) {
			if (perk.isEnabled()) {
				enabled++;
			} else {
				disabled++;
			}
		}

		int disabledRows = (int) Math.ceil(disabled / 9.0);
		int enabledRows = (int) Math.ceil(enabled / 9.0);

		return 1 + disabledRows + enabledRows;
	}

	public Perk getPerk(PerkType type) {
		for (Perk perk : this.perks) {
			if (perk.getType() == type) {
				return perk;
			}
		}

		return null;
	}

	public Perk getOrAddPerk(PerkType type) {
		Perk perk = getPerk(type);

		if (perk != null) {
			return perk;
		}

		perk = new Perk(type, 0, false);

		this.perks.add(perk);

		return perk;
	}

	public LinkedList<Perk> getDisabledPerks() {
		return getPerks(false);
	}

	public LinkedList<Perk> getEnabledPerks() {
		return getPerks(true);
	}

	private LinkedList<Perk> getPerks(boolean enabled) {
		LinkedList<Perk> list = new LinkedList<>();

		for (Perk perk : this.perks) {
			if (perk.isEnabled() == enabled) {
				list.add(perk);
			}
		}

		return list;
	}

	public void decreasePerkLives(boolean notice) {
		for (Perk perk : getEnabledPerks()) {
			perk.decreaseLivesLeft();

			if (perk.hasExpired()) {
				this.perks.remove(perk);

				if (notice) {
					getPlayer().sendMessage(
							MessageType.PERK_NOTICE_BROKEN.getTranslation().getValue(perk.getType().getName()));
				}
			}
		}
	}

	public void checkAchievement(AchievementType ac) {
		if (!hasAchievement(ac)) {
			giveAchievement(ac);
		}
	}

	public void checkAchievement(AchievementType ac, CharacterType ct) {
		if (!hasAchievement(ac, ct)) {
			giveAchievement(ac, ct);
		}
	}

	public void giveAchievement(AchievementType ac) {
		AchievementTranslation att = ac.getTranslation();
		String propName = att.getName();

		if (propName.equals("")) {
			propName = ac.name();
		}

		setAchievement(ac, true);

		Player player = getPlayer();

		if (player == null) {
			return;
		}

		String desc = att.getDesc();
		Location loc = player.getLocation();

		player.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 16F, 4F);
		player.sendMessage(MessageType.ACHIEVEMENT_GET.getTranslation().getValue(propName));
		player.sendMessage(ChatColor.LIGHT_PURPLE + desc);
	}

	public void giveAchievement(AchievementType ac, CharacterType ct) {
		String propName = ac.getName(ct);

		setAchievement(ac, ct, true);

		Player player = getPlayer();

		if (player == null) {
			return;
		}

		String desc = ac.getDescription(ct);
		Location loc = player.getLocation();

		player.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 16F, 4F);
		player.sendMessage(MessageType.ACHIEVEMENT_GET.getTranslation().getValue(propName));
		player.sendMessage(ChatColor.LIGHT_PURPLE + desc);
	}

	public boolean hasAchievement(AchievementType type) {
		for (Achievement ac : this.achievements) {
			if (ac.getType() == type) {
				return ac.isAchieved();
			}
		}

		return false;
	}

	public boolean hasAchievement(AchievementType type, CharacterType ct) {
		for (Achievement ac : this.achievements) {
			if (ac.getType() == type) {
				CharacterAchievement cac = (CharacterAchievement) ac;

				if (cac.getCharacterType() == ct) {
					return cac.isAchieved();
				}
			}
		}

		return false;
	}

	private Achievement getAchievement(AchievementType type) {
		for (Achievement ac : this.achievements) {
			if (ac.getType() == type) {
				return ac;
			}
		}

		return null;
	}

	private Achievement getAchievement(AchievementType type, CharacterType ct) {
		for (Achievement ac : this.achievements) {
			if (ac.getType() == type) {
				CharacterAchievement cac = (CharacterAchievement) ac;

				if (cac.getCharacterType() == ct) {
					return ac;
				}
			}
		}

		return null;
	}

	public void setAchievement(AchievementType type, boolean value) {
		Achievement ac = getAchievement(type);

		ac.setAchieved(value);
	}

	public void setAchievement(AchievementType type, CharacterType ct, boolean value) {
		Achievement ac = getAchievement(type, ct);

		ac.setAchieved(value);
	}

	public List<Achievement> getAchievements() {
		return this.achievements;
	}

	public void setAchievements(List<Achievement> achievements) {
		this.achievements = achievements;
	}

	public Settings getSettings() {
		return this.settings;
	}

	public void setSettings(Settings settings) {
		this.settings = settings;
	}

	public LobbyInventory getLobbyInventory() {
		return this.lobbyInventory;
	}

	public int getAchievementViewPage() {
		return this.achievementViewPage;
	}

	public void setAchievementViewPage(int achievementViewPage) {
		this.achievementViewPage = achievementViewPage;
	}

	public BPPlayer getAchievementViewTarget() {
		return this.achievementViewTarget;
	}

	public void setAchievementViewTarget(BPPlayer achievementViewTarget) {
		this.achievementViewTarget = achievementViewTarget;
	}

	public int getAfkSecondsToKick() {
		return this.afkSecondsToKick;
	}

	public void setAfkSecondsToKick(int afkSecondsToKick) {
		this.afkSecondsToKick = afkSecondsToKick;
	}

	public void clearAfkSecondsToKick() {
		this.afkSecondsToKick = AfkManager.defSTK;
	}

	public Location getAfkPastLocation() {
		return this.afkPastLocation;
	}

	public void setAfkPastLocation(Location afkPastLocation) {
		this.afkPastLocation = afkPastLocation;
	}

	public int getArmorWoreSince() {
		return this.armorWoreSince;
	}

	public void setArmorWoreSince(int armorWoreSince) {
		this.armorWoreSince = armorWoreSince;
	}

	public void setArmorWoreSince() {
		this.armorWoreSince = (int) (System.currentTimeMillis() / (1000 * 60));
	}

	public Clan getClan() {
		return this.bpClan;
	}

	public void setClan(Clan bpClan) {
		this.bpClan = bpClan;
	}

	public int getMoney() {
		return this.statistics.getMoney();
	}

	public void setMoney(int money) {
		this.statistics.setMoney(money);
	}

	public Location getShopItemLocation() {
		return this.shopItemLocation;
	}

	public void setShopItemLocation(Location shopItemLocation) {
		this.shopItemLocation = shopItemLocation;
	}

	public void setScoreboardManager(SBManager scoreboardManager) {
		this.scoreboardManager = scoreboardManager;
	}
	
	public long getSpawnTime() {
		return this.spawnTime;
	}

	public void setSpawnTime(long spawnTime) {
		this.spawnTime = spawnTime;
	}

	public CharacterType getQueueCharacter() {
		return this.queueCharacter;
	}

	public void setQueueCharacter(CharacterType queueCharacter) {
		this.queueCharacter = queueCharacter;
	}

	public long getLastTimeKilled() {
		return this.lastTimeKilled;
	}

	public void setLastTimeKilled(long lastTimeKilled) {
		this.lastTimeKilled = lastTimeKilled;
	}

	public void setLastTimeKilled() {
		this.lastTimeKilled = System.currentTimeMillis();
	}

	public int getMultikills() {
		return this.multikills;
	}

	public void setMultikills(int multikills) {
		this.multikills = multikills;
	}

	public int getKilledThisLife() {
		return this.killedThisLife;
	}

	public void setKilledThisLife(int killedThisLife) {
		this.killedThisLife = killedThisLife;
	}

	public ItemStack[] getQuickChatInventoryContents() {
		return this.quickChatInventoryContents;
	}

	public void setQuickChatInventoryContents(ItemStack[] quickChatInventoryContents) {
		this.quickChatInventoryContents = quickChatInventoryContents;
	}

	public HashMap<String, Long> getCooldowns() {
		return this.cooldowns;
	}

	public String getLastMessage() {
		return this.lastMessage;
	}

	public void setLastMessage(String lastMessage) {
		this.lastMessage = lastMessage;
	}

	public long getTimeJoined() {
		return this.timeJoined;
	}

	public void setTimeJoined(long timeJoined) {
		this.timeJoined = timeJoined;
	}

	public Location getSingleTeleportLocation() {
		return this.singleTeleportLocation;
	}

	public void setSingleTeleportLocation(Location singleTeleportLocation) {
		this.singleTeleportLocation = singleTeleportLocation;
	}

	public boolean isLeaveAfterDeath() {
		return this.leaveAfterDeath;
	}

	public void setLeaveAfterDeath(boolean leaveAfterDeath) {
		this.leaveAfterDeath = leaveAfterDeath;
	}

	public BPPlayer getLastTimeKilledBy() {
		return this.lastTimeKilledBy;
	}

	public void setLastTimeKilledBy(BPPlayer lastTimeKilledBy) {
		this.lastTimeKilledBy = lastTimeKilledBy;
	}

	public GameProperties getGameProperties() {
		return this.gameProperties;
	}

	public void setGameProperties(GameProperties gameProperties) {
		this.gameProperties = gameProperties;
	}

	public SBManager getScoreboardManager() {
		return this.scoreboardManager;
	}

	public boolean isOnline() {
		return getOfflinePlayer().isOnline();
	}

	public HashMap<BPPlayer, Long> getLastTimeDamagedBy() {
		return this.lastTimeDamagedBy;
	}

	public List<Perk> getPerks() {
		return this.perks;
	}

	public void setPerks(List<Perk> perks) {
		this.perks = perks;
	}
	
	public void sendParticles() {
		Runnable run = new Runnable() {
			public void run() {
				Player p = getPlayer();
				
				Location loc = p.getLocation();
				int radius = 2;
				
				for (double y = 0; y < 1.85; y += 0.05) {
					double x = radius * Math.cos(y);
					double z = radius * Math.sin(y);
					PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(EnumParticle.END_ROD, false, (float) (loc.getX() + x), (float) (loc.getY() + y), (float) (loc.getZ() + z), 0, 0, 0, 0, 1, 0);
					for (Player p1 : Bukkit.getOnlinePlayers()) {
						CraftPlayer cp = (CraftPlayer) p1;
						EntityPlayer ep = cp.getHandle();
						ep.playerConnection.sendPacket(packet);
					}
				}
			}
		};
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Breakpoint.getInstance(), run, 0L, 5L);
		
	}
}
