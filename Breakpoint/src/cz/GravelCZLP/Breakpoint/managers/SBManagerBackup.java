package cz.GravelCZLP.Breakpoint.managers;

import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import cz.GravelCZLP.Breakpoint.Breakpoint;
import cz.GravelCZLP.Breakpoint.game.Game;
import cz.GravelCZLP.Breakpoint.language.MessageType;
import cz.GravelCZLP.Breakpoint.players.BPPlayer;

@SuppressWarnings("deprecation")
public class SBManagerBackup
{
	private final BPPlayer bpPlayer;
	private final Scoreboard sb;
	protected Objective lobbyObj, voteObj, rankObj, progressObj;

	public SBManagerBackup(BPPlayer bpPlayer)
	{
		this.bpPlayer = bpPlayer;
		sb = Bukkit.getScoreboardManager().getNewScoreboard();
		init();
		bpPlayer.getPlayer().setScoreboard(sb);
		
	}
	
	private void init()
	{
		initLobbyObj();
		initVoteObj();
		initRankObj();
	}
	
	private void initLobbyObj()
	{
		lobbyObj = sb.registerNewObjective("LOBBY", "dummy");
		lobbyObj.setDisplayName(MessageType.SCOREBOARD_LOBBY_HEADER.getTranslation().getValue());
	}
	
	private void initRankObj()
	{
		rankObj = sb.registerNewObjective("RANK", "dummy");
		rankObj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
	}

	private void initVoteObj()
	{
		voteObj = sb.registerNewObjective("VOTE", "dummy");
		voteObj.setDisplayName(MessageType.MAP_VOTING_HEADER.getTranslation().getValue());
	}
	
	public void initProgressObj()
	{
		progressObj = sb.registerNewObjective("PROGRESS", "dummy");
		progressObj.setDisplayName("Undefined");
	}
	
	public void unregister()
	{
		for(Objective obj : sb.getObjectives())
			obj.unregister();
		
		lobbyObj = rankObj = voteObj = progressObj = null;
		
		sb.resetScores(getPlayer().getOfflinePlayer());;
	}
	
	public static void updateLobbyObjectives()
	{
		for(BPPlayer bpPlayer : BPPlayer.onlinePlayers)
			if(bpPlayer.isInLobby())
				bpPlayer.getScoreboardManager().updateLobbyObjective();
	}
	
	public void updateLobbyObjective()
	{
		for(Game game : GameManager.getGames())
		{
			String name = game.getName();
			int players = game.getPlayers().size();
			OfflinePlayer fakePlayer = Bukkit.getOfflinePlayer(ChatColor.YELLOW + name);
			Score score = lobbyObj.getScore(fakePlayer);
			
			score.setScore(players);
		}
	}
	
	public void updateOnlinePlayerRanks()
	{
		try
		{
			synchronized(BPPlayer.onlinePlayers)
			{
				for(BPPlayer bpPlayer : BPPlayer.onlinePlayers)
					updatePlayerRank(bpPlayer);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			Breakpoint.warn("Error when updating player ranks in tab: " + e.getClass());
		}
	}
	
	public void updatePlayerRank(BPPlayer bpPlayer)
	{
		Player player = bpPlayer.getPlayer();
		String playerName = player.getName();
		int rank = StatisticsManager.isUpdating() ? 0 : StatisticsManager.getRank(playerName);
		String tag = bpPlayer.getTag();
		
		setNameRank(tag, rank);
	}
	
	private void setNameRank(String playerName, int rank)
	{
		OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
		Score score = rankObj.getScore(player);
		score.setScore(rank);
	}

	public void updateVoteOptions(Map<String, Integer> votes)
	{
		for (Entry<String, Integer> entry : votes.entrySet())
		{
			String name = entry.getKey();
			int voted = entry.getValue();
			if (name != null)
			{
				Score score = voteObj.getScore(Bukkit.getOfflinePlayer(ChatColor.AQUA + name));
				score.setScore(voted);
			}
		}
	}

	public void restartVoteObj()
	{
		voteObj.unregister();
		initVoteObj();
	}

	public void updateSidebarObjective()
	{
		Game game = bpPlayer.getGame();
		
		if(game != null)
			if (!game.votingInProgress())
				progressObj.setDisplaySlot(DisplaySlot.SIDEBAR);
			else
				voteObj.setDisplaySlot(DisplaySlot.SIDEBAR);
		else
			lobbyObj.setDisplaySlot(DisplaySlot.SIDEBAR);
	}

	public static String formatTime(int timeLeft)
	{
		if (timeLeft <= 0)
			return "0:00";
		
		int seconds = timeLeft;
		int minutes = 0;
		
		while (seconds >= 60)
		{
			seconds -= 60;
			minutes++;
		}
		
		String sMinutes, sSeconds;
		
		if (minutes < 10)
			sMinutes = "0" + minutes;
		else
			sMinutes = Integer.toString(minutes);
		
		if (seconds < 10)
			sSeconds = "0" + seconds;
		else
			sSeconds = Integer.toString(seconds);
		
		return sMinutes + ":" + sSeconds;
	}
	
	public Scoreboard getScoreboard()
	{
		return sb;
	}

	public BPPlayer getPlayer()
	{
		return bpPlayer;
	}

	public Objective getProgressObj()
	{
		return progressObj;
	}

	public void setProgressObj(Objective progressObj)
	{
		this.progressObj = progressObj;
	}
}
