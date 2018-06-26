package xyz.drjest.playerlocations;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

public final class PlayerLocations extends JavaPlugin implements Listener, Runnable {
	private PluginDescriptionFile pdfFile;
	private HashMap<String, Object> pluginOptions = new HashMap<>();
	private int _updateTaskId = 0;
	private HashMap<String, String> _mapNameMapping = new HashMap<>();
	private ConcurrentHashMap<String, PlayerState> _offlinePlayers = new ConcurrentHashMap<>();
	private Gson gson = new Gson();
	
	@EventHandler
	public void playerJoin(PlayerJoinEvent event) {
		_offlinePlayers.remove(event.getPlayer().getName());
		run();
	}
	
	@EventHandler
	public void playerQuit(PlayerQuitEvent event) {
		_offlinePlayers.put(event.getPlayer().getName(), new PlayerState(event.getPlayer()));
		run();
	}
	
	@Override
	public void onEnable() {
		pdfFile = getDescription();
	    Logger.getLogger(pdfFile.getName()).log(Level.INFO, pdfFile.getName() + " version " + pdfFile.getVersion() + " enabled");
	    loadOptions();
	    initMapNameMapping();
	    if((boolean) pluginOptions.get("saveOfflinePlayers")) {
	    	loadOfflinePlayers();	
	    }
	    int updateInterval = (int) pluginOptions.get("updateInterval") / 100;
	    _updateTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, this, 0L, updateInterval);
	    getConfig().options().copyDefaults(true);
	    saveConfig();
	    getServer().getPluginManager().registerEvents(this, this);
	}

	private void loadOfflinePlayers() {
	    if ((pluginOptions.get("offlinePlayersFile") != null)) {
	    	String filename = (String) pluginOptions.get("offlinePlayersFile");
	    	try {
				JsonReader reader = new JsonReader(new FileReader(filename));
				Type type = new TypeToken<ConcurrentHashMap<String,PlayerState>>() {}.getType();   
				_offlinePlayers = gson.fromJson(reader, type);
			} catch (FileNotFoundException e) {
				Logger.getLogger(getDescription().getName()).log(Level.INFO, "NO_OFFLINE_PLAYERS_FOUND");
			}
	    }
	}

	@Override
	public void onDisable() {
	    getServer().getScheduler().cancelTask(_updateTaskId);
		if((boolean) pluginOptions.get("saveOfflinePlayers")) {
			saveOfflinePlayers();
		}
	    Logger.getLogger(getDescription().getName()).log(Level.INFO, pdfFile.getName() + " disabled");
	}
	
	private void saveOfflinePlayers() {
	    if ((pluginOptions.get("offlinePlayersFile") != null) && (boolean) pluginOptions.get("saveOfflinePlayers")) {
	    	String opt = (String) pluginOptions.get("offlinePlayersFile");
	    	try (Writer writer = new FileWriter(opt);) {
	    	    gson.toJson(_offlinePlayers, writer);
	    	} catch (IOException e) {
				Logger.getLogger(getDescription().getName()).log(Level.INFO, "UNABLE_TO_SAVE_OFFLINE_PLAYERS");
			}
	    }
    	run();
	}
	
	@Override
	public void run() {
		HashMap<String, PlayerState> players = new HashMap<>();
		for(Player p: getServer().getOnlinePlayers()) {
			PlayerState player = new PlayerState(p);
			player.world = _mapNameMapping.get(player.world);
	        if(
	        		player.status == 4 || 
	        		( player.status == 5 && (boolean) pluginOptions.get("showVanishedPlayers") ) ||
	        		( player.status == 6 && (boolean) pluginOptions.get("showSneakingPlayers") ) ||
	        		( player.status == 7 && (boolean) pluginOptions.get("showInvisiblePlayers") ) ||
	        		( player.status == 8 && (boolean) pluginOptions.get("showSpectatorPlayers") )
	        ) {
		        players.putIfAbsent(p.getName(), player);
	        }
		}
		if( (boolean) pluginOptions.get("saveOfflinePlayers") ) {
			_offlinePlayers.forEach((k,v) -> {
				PlayerState s = new PlayerState(v);
				s.status = 5;
				s.world = _mapNameMapping.get(v.world);
				players.putIfAbsent(k, s);
			});
		}
	    if ((pluginOptions.get("outputFile") != null)) {
	    	String optFile = (String) pluginOptions.get("outputFile");
	    	HashMap<String, Object> opt = new HashMap<>();
	    	HashMap<String, Object> options = new HashMap<>();
	    	options.put("updateInterval", (int) pluginOptions.get("updateInterval"));
	    	opt.put("options", options);
	    	opt.put("players", players);
	    	try (Writer writer = new FileWriter(optFile)) {
	    	    gson.toJson(opt, writer);
	    	} catch (IOException e) {
	    		Logger.getLogger(getDescription().getName()).log(Level.INFO, "UNABLE_TO_SAVE_OUTPUT");
			}
	    }
	}
	
	private void loadOptions() {
		pluginOptions.put("outputFile", getConfig().getString("outputFile").replace("_DATA_FOLDER_", this.getDataFolder().getAbsolutePath()));
		pluginOptions.put("updateInterval", getConfig().getInt("updateInterval"));
		pluginOptions.put("offlinePlayersFile", getConfig().getString("offlinePlayersFile").replace("_DATA_FOLDER_", this.getDataFolder().getAbsolutePath()));
		pluginOptions.put("saveOfflinePlayers", getConfig().getBoolean("saveOfflinePlayers"));
		pluginOptions.put("tagMessages", getConfig().getBoolean("tagMessages"));
		pluginOptions.put("showVanishedPlayers", getConfig().getBoolean("showVanishesPlayers"));
		pluginOptions.put("showSneakingPlayers", getConfig().getBoolean("showSneakingPlayers"));
		pluginOptions.put("showInvisiblePlayers", getConfig().getBoolean("showInvisiblePlayers"));
		pluginOptions.put("showSpectatorPlayers", getConfig().getBoolean("showSpectatorPlayers"));
	}

	private void initMapNameMapping() {
		ConfigurationSection mappingSection = getConfig().getConfigurationSection("Mapping");
		if(mappingSection != null) {
			Map<String, Object> configMap = mappingSection.getValues(false);
		    for (Map.Entry<String, Object> entry : configMap.entrySet()) {
		    	_mapNameMapping.put((String)entry.getKey(), (String)entry.getValue());
		    }
		}
	    List<World> serverWorlds = getServer().getWorlds();
	    for (World w : serverWorlds) {
	    	_mapNameMapping.putIfAbsent(w.getName(), w.getName());
	    }
	    getConfig().createSection("Mapping", _mapNameMapping);
	}
	
	private static int getPlayerStatus(Player p) {
        for (MetadataValue value : p.getMetadata("vanished")) {
            if (value.asBoolean()) {
            	return 5;
            }
        }
        if(p.isSneaking()) {
        	return 6;
        }
        else if(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
        	return 7;
        }
        else if(p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
        	return 8;
        }
        else {
        	return 4;
        }
	}
	
	private static class PlayerState {
		public int status = 0; // 0: UNKNOWN, 4: ONLINE, 5: OFFLINE, 6: SNEAKING, 7: INVISIBLE, 8: SPECTATOR
	    public String world;
	    public int x;
	    public int y;
	    public int z;
	    
	    public PlayerState(Player p) {
	    	Location loc = p.getLocation();
		    world = loc.getWorld().getName();
		    x = loc.getBlockX();
		    y = loc.getBlockY();
		    z = loc.getBlockZ();
		    status = getPlayerStatus(p);
	    }
	    
	    public PlayerState(PlayerState s) {
	    	world = s.world;
	    	x = s.x;
	    	y = s.y;
	    	z = s.z;
	    	status = s.status;
	    }
	    
		@Override 
	    public String toString() {
	    	return "Status: " + status + "\n World: " + world + "\n X: " + x + "\n Y: " + y + "\n Z: " + z;
	    }
	}
}
