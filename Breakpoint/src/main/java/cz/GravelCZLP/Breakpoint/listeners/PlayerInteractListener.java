package cz.GravelCZLP.Breakpoint.listeners;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import cz.GravelCZLP.Breakpoint.Breakpoint;
import cz.GravelCZLP.Breakpoint.Configuration;
import cz.GravelCZLP.Breakpoint.achievements.Achievement;
import cz.GravelCZLP.Breakpoint.game.Game;
import cz.GravelCZLP.Breakpoint.game.GameType;
import cz.GravelCZLP.Breakpoint.game.MapPoll;
import cz.GravelCZLP.Breakpoint.game.ctf.CTFGame;
import cz.GravelCZLP.Breakpoint.game.ctf.CTFProperties;
import cz.GravelCZLP.Breakpoint.game.ctf.FlagManager;
import cz.GravelCZLP.Breakpoint.game.ctf.Team;
import cz.GravelCZLP.Breakpoint.language.MessageType;
import cz.GravelCZLP.Breakpoint.managers.AbilityManager;
import cz.GravelCZLP.Breakpoint.managers.DoubleMoneyManager;
import cz.GravelCZLP.Breakpoint.managers.GameManager;
import cz.GravelCZLP.Breakpoint.managers.PlayerManager;
import cz.GravelCZLP.Breakpoint.managers.ShopManager;
import cz.GravelCZLP.Breakpoint.perks.Perk;
import cz.GravelCZLP.Breakpoint.players.BPPlayer;
import cz.GravelCZLP.Breakpoint.players.CooldownType;
import cz.GravelCZLP.Breakpoint.players.Settings;

public class PlayerInteractListener implements Listener {
	Breakpoint plugin;

	public PlayerInteractListener(Breakpoint p) {
		this.plugin = p;
	}

	@SuppressWarnings("deprecation")
	public void onPlayerUseItem(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
			Player player = event.getPlayer();
			BPPlayer bpPlayer = BPPlayer.get(player);
			ItemStack item = player.getItemInHand();
			ItemStack itemClone = item.clone();
			Material mat = item.getType();
			short durability = item.getDurability();

			if (bpPlayer.isInGame()) {
				Game game = bpPlayer.getGame();

				if (game.votingInProgress()) {
					voting(event, bpPlayer);
				}

				if (mat == Material.INK_SACK && durability == 1) {
					if (player.getHealth() < 20) {
						if (!bpPlayer.hasCooldown(CooldownType.HEAL.getPath(), 0.5, true)) {
							Location loc = player.getLocation();
							World world = loc.getWorld();

							player.setItemInHand(PlayerManager.decreaseItem(item));

							if (player.hasPotionEffect(PotionEffectType.HEAL)) {
								player.removePotionEffect(PotionEffectType.HEAL);
							}

							player.addPotionEffect(new PotionEffect(PotionEffectType.HEAL, 1, 2), true);

							world.playSound(loc, Sound.ENTITY_GENERIC_EAT, 1F, 1F);
						}
					}
				} else if (mat == Material.POTION && bpPlayer.isInGame()) {
					if (!bpPlayer.hasCooldown(CooldownType.POTION_RAW.getPath() + durability, 1, true)) {
						player.getInventory().setItem(player.getInventory().getHeldItemSlot(), itemClone);
					} else {
						event.setCancelled(true);
						player.updateInventory();
					}
				} else {
					game.getListener().onPlayerRightClickItem(event, bpPlayer, item);
				}
			} else if (bpPlayer.isInLobby()) {
				if (mat == Material.NAME_TAG) {
					GameMode gamemode = player.getGameMode();
					if (gamemode != GameMode.CREATIVE) {
						event.setCancelled(true);
						bpPlayer.setAchievementViewTarget(bpPlayer);
						bpPlayer.setAchievementViewPage(0);
						Achievement.showAchievementMenu(bpPlayer);
					}
				} else if (mat == Material.REDSTONE_COMPARATOR) {
					GameMode gamemode = player.getGameMode();
					if (gamemode != GameMode.CREATIVE) {
						event.setCancelled(true);
						Settings.showSettingsMenu(bpPlayer);
					}
				} else if (mat == Material.EXP_BOTTLE) {
					GameMode gamemode = player.getGameMode();
					if (gamemode != GameMode.CREATIVE) {
						event.setCancelled(true);
						Perk.showPerkMenu(bpPlayer);
						bpPlayer.getPlayer().updateInventory();
					}
				}
			}
		} else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
			Player player = event.getPlayer();
			BPPlayer bpPlayer = BPPlayer.get(player);
			ItemStack item = player.getItemInHand();
			Material mat = item.getType();
			if (mat == Material.BLAZE_ROD) {
				if (!bpPlayer.hasCooldown(CooldownType.BLAZE_ROD_MAGE.getPath(), 2, true)) {
					Location eyeLoc = player.getEyeLocation();
					AbilityManager.launchFireball(player, eyeLoc, AbilityManager.getDirection(player));
				}
			} else if (mat == Material.STICK) {
				if (!bpPlayer.hasCooldown(CooldownType.STICK_MAGE.getPath(), 2, true)) {
					Location eyeLoc = player.getEyeLocation();
					AbilityManager.launchSmallFireball(player, eyeLoc, AbilityManager.getDirection(player));
				}
			} else if (mat == Material.FEATHER) {
				if (!bpPlayer.hasCooldown(CooldownType.FEATHER_MAGE.getPath(), 3, true)) {
					AbilityManager.launchPlayer(player);
				}
			} else {
				Game game = bpPlayer.getGame();

				if (game != null) {
					game.getListener().onPlayerLeftClickItem(event, bpPlayer, item);
				}
			}
		}
	}
	
	public void voting(PlayerInteractEvent event, BPPlayer bpPlayer) {
		Game game = bpPlayer.getGame();
		Player player = bpPlayer.getPlayer();
		MapPoll mapPoll = game.getMapPoll();
		PlayerInventory inv = player.getInventory();
		int mapId = inv.getHeldItemSlot();
		if (mapPoll.isIdCorrect(mapId)) {
			String playerName = player.getName();
			if (!mapPoll.hasVoted(playerName)) {
				boolean b = player.hasPermission("Breakpoint.game.votetwo");

				int strength = b ? 2 : 1;
				mapPoll.vote(playerName, mapId, strength);
				PlayerManager.clearHotBar(inv);
				player.updateInventory();
				String mapName = mapPoll.maps[mapId];
				player.sendMessage(MessageType.VOTING_VOTE.getTranslation().getValue(mapName));
			}
		}
	}

	@EventHandler
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		Entity entity = event.getRightClicked();
		if (entity instanceof ItemFrame) {
			Player player = event.getPlayer();
			boolean b = player.hasPermission("Breakpoint.interact");
			if (!b) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (this.plugin.hasEvent()) {
			this.plugin.evtm.onPlayerInteract(event);
		}

		boolean cont = onPlayerBlockInteract(event);

		if (!cont) {
			return;
		}

		onPlayerUseItem(event);
	}

	public boolean onPlayerBlockInteract(PlayerInteractEvent event) {
		Action action = event.getAction();

		if (action == Action.RIGHT_CLICK_BLOCK) {
			Player player = event.getPlayer();
			BPPlayer bpPlayer = BPPlayer.get(player);

			if (bpPlayer.isInGame()) {
				Game game = bpPlayer.getGame();

				game.getListener().onPlayerRightClickBlock(event, bpPlayer);
			} else {
				Block block = event.getClickedBlock();
				Material type = block.getType();

				if (type == Material.WALL_SIGN || type == Material.SIGN_POST) {
					Sign sign = (Sign) block.getState();
					Location loc = block.getLocation();
					Game clickedGame = GameManager.getGame(loc);

					if (clickedGame != null) {
						if (clickedGame.isPlayable()) {
							try {
								clickedGame.join(bpPlayer);
							} catch (Exception e) {
								player.sendMessage(e.getMessage());
							}
						} else {
							player.sendMessage(
									MessageType.LOBBY_GAME_NOTREADY.getTranslation().getValue(clickedGame.getName()));
						}
					} else {
						String[] lines = sign.getLines();

						if (ShopManager.isShop(lines)) {
							ShopManager.buyItem(bpPlayer, sign, lines);
						}
					}

					event.setCancelled(true);
					return false;
				}
			}
		} else if (action == Action.PHYSICAL) {
			Block block = event.getClickedBlock();
			Material mat = block.getType();
			if (mat == Material.STONE_PLATE) {
				Block below = block.getRelative(BlockFace.DOWN);
				Material belowMat = below.getType();
				Player player = event.getPlayer();
				BPPlayer bpPlayer = BPPlayer.get(player);

				if (!bpPlayer.isInGame()) {
					Configuration config = Breakpoint.getBreakpointConfig();

					if (belowMat == Material.EMERALD_BLOCK) {
						bpPlayer.teleport(config.getShopLocation());
					} else if (belowMat == Material.QUARTZ_BLOCK) {
						bpPlayer.teleport(config.getLobbyLocation());
					} else if (belowMat == Material.REDSTONE_BLOCK) {
						bpPlayer.teleport(config.getStaffListLocation());
					} else if (belowMat == Material.GOLD_BLOCK) {
						bpPlayer.teleport(config.getVipInfoLocation());
					}
				} else {
					Game game = bpPlayer.getGame();

					game.getListener().onPlayerPhysicallyInteractWithBlock(event, bpPlayer, below);
				}
			}
		}
		return true;
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		if (!(player.hasPermission("Breakpoint.build") && player.getGameMode() == GameMode.CREATIVE)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();
		if (!(player.hasPermission("Breakpoint.build") && player.getGameMode() == GameMode.CREATIVE)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent event) {
		event.setCancelled(true);
		Location loc = event.getLocation();
		World world = loc.getWorld();
		world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), event.getYield(), false, false);
	}

	@EventHandler
	public void onPickup(PlayerPickupItemEvent e) {
		BPPlayer bpPlayer = BPPlayer.get(e.getPlayer());
		if (bpPlayer.isInGame()) {
			if (bpPlayer.getGame().getType() == GameType.CTF) {
				ItemStack item = e.getItem().getItemStack();
				if (item.getType() == Material.SPECKLED_MELON) {
					if (item.getItemMeta().getDisplayName() == "melounBoost") {
						e.setCancelled(true);
						e.getItem().remove();
						e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0F, 2.0F);
						Color color = Color.WHITE;
						CTFProperties props = (CTFProperties) bpPlayer.getGameProperties();
						color = props.getTeam().getColor();
						e.getPlayer().addPotionEffect(
								new PotionEffect(PotionEffectType.SPEED, 20 * 3, 3, false, true, color));
					}
				}
			}
		}
	}

	@EventHandler
	public void onHangingBreak(HangingBreakEvent event) {
		if (event.isCancelled()) {
			return;
		}
		if (event.getCause() == RemoveCause.ENTITY) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent event) {
		Inventory inv = event.getInventory();
		
		BPPlayer bpPlayer = BPPlayer.get(event.getPlayer().getName());
		
		if (bpPlayer.isInGame()) {
			return;
		}
		
		if (bpPlayer.isInLobby()) {
			if (inv.getType() != InventoryType.CHEST ) {
				return;
			}
		}
		
		if (inv.getType() != InventoryType.PLAYER && !bpPlayer.getPlayer().hasPermission("Breakpoint.interact")) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void onBlockBurn(BlockBurnEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void onBlockSpread(BlockSpreadEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void onBlockIgnite(BlockIgniteEvent event) {
		IgniteCause ic = event.getCause();
		if (ic != IgniteCause.FLINT_AND_STEEL) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerTeleport(PlayerTeleportEvent event) {
		Player player = event.getPlayer();

		BPPlayer bpPlayer = BPPlayer.get(player);
		Game game = bpPlayer.getGame();

		if (game != null) {
			game.getListener().onPlayerTeleport(event, bpPlayer);
		}
	}

	@EventHandler
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent e) {
		for (int i = 0; i < e.getChunk().getEntities().length; i++) {
			if (e.getChunk().getEntities()[i].getType() == EntityType.ENDER_CRYSTAL) {
				e.setCancelled(true);
				return;
			}
		}
	}
	
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent e) {
		Entity[] entities = e.getChunk().getEntities();
		for (Entity ent : entities) {
			if (ent instanceof Item) {
				Item i = (Item) ent;
				ItemStack itemstack = i.getItemStack();
				if (itemstack.getType() == Material.SPECKLED_MELON && !DoubleMoneyManager.isDoubleXP()) {
					ent.remove();
				}
			}
		} 
	}
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		BPPlayer bpPlayer = BPPlayer.get(e.getPlayer());
		
		Game game = bpPlayer.getGame();
		
		if (game != null) {
			game.getListener().onPlayerMove(bpPlayer, e.getFrom(), e.getTo(), e);
		}
	}
	
	@EventHandler
	public void onSprint(PlayerToggleSprintEvent e) {
		BPPlayer bpPlayer = BPPlayer.get(e.getPlayer());
		Game game = bpPlayer.getGame();
		
		if (game != null) {
			game.getListener().onPlayerToggleSprint(bpPlayer, e);
		}
	}
	
	@EventHandler
	public void onEnderCrystalDie(EntityDeathEvent e) {
		if (e.getEntityType() == EntityType.ENDER_CRYSTAL) {
			EnderCrystal crystal = (EnderCrystal) e.getEntity();
			for (Game g : GameManager.getGames()) {
				if (g.getType() == GameType.CTF) {
					CTFGame game = (CTFGame) g;
					FlagManager flm = game.getFlagManager();
					if (flm.isTeamFlag(crystal)) {
						Team t = flm.getFlagTeam(crystal);
						flm.spawnFlag(flm.getFlagLocation(t), t);
					}
				}
			}
		}
	}
}
