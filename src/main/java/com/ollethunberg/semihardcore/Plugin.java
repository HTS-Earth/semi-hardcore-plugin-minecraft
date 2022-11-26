package com.ollethunberg.semihardcore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;
import org.postgresql.Driver;

public class Plugin extends JavaPlugin implements Listener {
  private static final Logger LOGGER = Logger.getLogger("semi-hardcore");
  Connection connection;
  SQLHelper sqlHelper;
  FileConfiguration config;

  public void onEnable() {
    loadConfig();
    LOGGER.info("semi-hardcore enabled");
    LOGGER.info("jdbc:postgresql://" + config.getString("database.ip") + ":" + config.getInt("database.port") + "/"
        + config.getString("database.database") + "?stringtype=unspecified");
    try {
      DriverManager.registerDriver(new Driver());
      connection = DriverManager.getConnection(
          "jdbc:postgresql://" + config.getString("database.ip") + ":" + config.getInt("database.port") + "/"
              + config.getString("database.database") + "?stringtype=unspecified",
          config.getString("database.username"), config.getString("database.password"));
      sqlHelper = new SQLHelper(connection);
      getServer().getPluginManager().registerEvents(this, this);

    } catch (SQLException e) {
      LOGGER.severe("Could not connect to database");
      e.printStackTrace();
    }
  }

  // Command handler
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (sender instanceof Player) {
      Player player = (Player) sender;
      if (cmd.getName().equalsIgnoreCase("deaths")) {
        try {
          ResultSet rs = sqlHelper.query("SELECT * FROM player_bans WHERE player_id = ?",
              player.getUniqueId().toString());
          int deaths = 0;
          while (rs.next()) {
            deaths++;
          }
          player.sendMessage("You have died " + deaths + " times");
          return true;
        } catch (SQLException e) {
          e.printStackTrace();
        }
      } else if (cmd.getName().equalsIgnoreCase("semihardcore")) {
        // check if the sender has the permission
        if (sender.hasPermission("semihardcore.admin")) {
          if (args.length == 0) {
            sender.sendMessage("Usage: /semihardcore <reload | unban>");
            return true;
          }

          if (args[0].equalsIgnoreCase("reload")) {
            loadConfig();
            sender.sendMessage("Config reloaded");
            return true;
          } else if (args[0].equalsIgnoreCase("unban")) {
            if (args.length == 1) {
              sender.sendMessage("Usage: /semihardcore unban <player>");
              return true;
            }
            try {
              // delete from player_bans, only delete the latest ban
              sqlHelper.update(
                  "DELETE FROM player_bans WHERE id = (select pb.id from player_bans as pb inner join player as p on p.uid=pb.player_id where LOWER(p.player_name) = LOWER(?) ORDER BY pb.banned_date DESC LIMIT 1)",
                  args[1]);
              sender.sendMessage("§aPlayer unbanned: " + args[1]);
              return true;
            } catch (SQLException e) {
              sender.sendMessage("§cThere was an error unbanning the player");
              e.printStackTrace();
            }
          }

        }
      }
    } else {
      sender.sendMessage("You must be a player to use this command");
    }
    return true;

  }

  public String getBanReasonMessage(String reason, Timestamp bannedUntil) {
    switch (reason) {
      case "death":
        return "§c§lYou are dead\n§cThis is a semi-hardcore server which means that you will be temporary banned from the server until you are revived.\n§aYou will be revived at "
            + bannedUntil.toString() + "\n§l§6Don't want to wait? Visit §a§l§n§ofedcraft.com/shop";
      case "cheat":
        return "You have been banned from the server for cheating.";
      case "spam":
        return "You have been banned from the server for spamming.";
      default:
        return "You have been banned from the server for other reasons.";

    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onPlayerJoin(PlayerLoginEvent event) {
    String isPlayerInDatabaseSQL = "SELECT EXISTS ( SELECT FROM player WHERE uid = ? );";
    try {
      ResultSet rs = sqlHelper.query(isPlayerInDatabaseSQL, event.getPlayer().getUniqueId().toString());
      if (rs.next() && rs.getBoolean("exists")) {
        LOGGER.info("Player " + event.getPlayer().getName() + " is in database: " + rs.getBoolean(1));
        String playerBannedUntil = "SELECT banned_date + (banned_minutes * interval '1 minute') as banned_until, player_id, ban_reason FROM player_bans WHERE player_id = ? order by banned_date DESC;";
        ResultSet rsPlayerBannedUntil = sqlHelper.query(playerBannedUntil, event.getPlayer().getUniqueId().toString());
        if (!rsPlayerBannedUntil.next()) {
          return;
        }
        if (rsPlayerBannedUntil.getTimestamp("banned_until").after(new java.util.Date())) {

          event.setKickMessage(getBanReasonMessage(rsPlayerBannedUntil.getString("ban_reason"),
              rsPlayerBannedUntil.getTimestamp("banned_until")));
          event.setResult(Result.KICK_BANNED);
        }
      } else {
        // NationsPlus plugin takes care of inserting new players to the database.

      }

    } catch (SQLException e) {
      LOGGER.info("Could not check if player is in database");
      e.printStackTrace();
    }
  }

  // event handler when player dies
  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    // get the player who died
    String victimUid = event.getEntity().getPlayer().getUniqueId().toString();
    // get the player who killed the player
    String killerUid = "";
    if (event.getEntity().getKiller() != null) {
      killerUid = event.getEntity().getKiller().getUniqueId().toString();
    }

    // Add the stats to the victim and the killer
    String addDeathToVictimSQL = "UPDATE player SET deaths = deaths + 1, balance=0 WHERE uid = ?;";
    String addDeathToKillerSQL = "UPDATE player SET kills = kills + 1 WHERE uid = ?;";
    try {
      sqlHelper.update(addDeathToVictimSQL, victimUid);
      String updateBalanceSQL = "UPDATE player SET balance = 0 WHERE uid = ?;";
      sqlHelper.update(updateBalanceSQL, victimUid);
      if (!killerUid.equals("")) { // Sometimes, the killer can be null, for example if they are killed by /kill
                                   // command
        sqlHelper.update(addDeathToKillerSQL, killerUid);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    // Ban the victim for 48 hours for death
    String banVictimSQL = "INSERT INTO player_bans (player_id, banned_date, banned_minutes, ban_reason) VALUES (?, ?, ?, ?);";
    try {
      sqlHelper.update(banVictimSQL, victimUid, new Timestamp(new java.util.Date().getTime()),
          config.getInt("deathban.duration") * 60, "death");
      // Kick player
      event.getEntity().getPlayer().kickPlayer(getBanReasonMessage("death",
          new Timestamp(new java.util.Date().getTime() + (config.getInt("deathban.duration") * 60 * 60 * 1000))));
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public void onDisable() {
    getLogger().info("Nations plus disabled");
    try {
      connection.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public void loadConfig() {
    getConfig().options().copyDefaults(true);
    saveConfig();
    config = getConfig();
  }
}
