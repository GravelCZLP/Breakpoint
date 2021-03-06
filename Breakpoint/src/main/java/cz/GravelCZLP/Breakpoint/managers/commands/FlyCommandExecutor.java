package cz.GravelCZLP.Breakpoint.managers.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import cz.GravelCZLP.Breakpoint.language.MessageType;
import cz.GravelCZLP.Breakpoint.managers.VIPManager;
import cz.GravelCZLP.Breakpoint.players.BPPlayer;

public class FlyCommandExecutor implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {
			return true;
		}

		Player player = (Player) sender;
		BPPlayer bpPlayer = BPPlayer.get(player);

		boolean b = player.hasPermission("Breakpoint.fly");

		if (!b) {
			player.sendMessage(MessageType.COMMAND_FLY_VIPPLUSSONLY.getTranslation().getValue());
			return true;
		}

		if (!bpPlayer.isInLobby()) {
			player.sendMessage(MessageType.COMMAND_FLY_NOTLOBBY.getTranslation().getValue());
			return true;
		}

		if (VIPManager.isFarFromSpawnToUseFly(player)) {
			player.sendMessage(MessageType.COMMAND_FLY_TOOFAR.getTranslation().getValue());
			return true;
		}

		boolean value = !player.getAllowFlight();
		player.setAllowFlight(value);

		if (value) {
			player.sendMessage(MessageType.COMMAND_FLY_ENABLED.getTranslation().getValue());
		} else {
			player.sendMessage(MessageType.COMMAND_FLY_DISABLED.getTranslation().getValue());
		}

		return true;
	}
}
