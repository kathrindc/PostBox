//
// MIT License
//
// Copyright (c) 2022 Joel Strasser
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package at.joestr.postbox;

import at.joestr.postbox.commands.CommandPostBox;
import at.joestr.postbox.commands.CommandPostBoxOpen;
import at.joestr.postbox.commands.CommandPostBoxOpenOther;
import at.joestr.postbox.commands.CommandPostBoxSend;
import at.joestr.postbox.commands.CommandPostBoxUpdate;
import at.joestr.postbox.configuration.AppConfiguration;
import at.joestr.postbox.configuration.CurrentEntries;
import at.joestr.postbox.configuration.DatabaseConfiguration;
import at.joestr.postbox.configuration.LanguageConfiguration;
import at.joestr.postbox.configuration.Updater;
import at.joestr.postbox.event.InventoryClickListener;
import at.joestr.postbox.event.InventoryCloseListener;
import at.joestr.postbox.event.PlayerJoinListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.apache.commons.lang3.tuple.Triple;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.enginehub.squirrelid.resolver.HttpRepositoryService;
import org.enginehub.squirrelid.resolver.ProfileService;

public class PostBoxPlugin extends JavaPlugin implements Listener {

  private static final Logger LOG = Logger.getLogger(PostBoxPlugin.class.getName());
  public static PostBoxPlugin instance = null;

  private NamespacedKey namespacedKey;
  private HashMap<String, TabExecutor> commandMap;
  private Updater updater;
  private LuckPerms luckPermsApi;
  private ProfileService profileService;
  private ArrayList<Triple<UUID, Inventory, UUID>> inventoryMappings = new ArrayList<>();

  @Override
  public void onLoad() {
    super.onLoad();
    instance = this;
  }

  @Override
  public void onEnable() {
    super.onEnable();

    Metrics metrics = new Metrics(this, 16667);

    this.commandMap = new HashMap<>();

    this.loadAppConfiguration();
    this.loadLanguageConfiguration();
    try {
      this.loadDatabase();
    } catch (SQLException ex) {
      this.getLogger().log(Level.SEVERE, "Error whilst loading database!", ex);
      this.getServer().getPluginManager().disablePlugin(this);
    }
    this.loadExternalPluginIntegrations();
    this.loadProfileService();

    this.updater
      = new Updater(
        AppConfiguration.getInstance().getBool(CurrentEntries.CONF_UPDATER_ENABLED.toString()),
        AppConfiguration.getInstance()
          .getBool(CurrentEntries.CONF_UPDATER_DOWNLOADTOPLUGINUPDATEFOLDER.toString()),
        this.getDescription().getVersion(),
        AppConfiguration.getInstance()
          .getString(CurrentEntries.CONF_UPDATER_TARGETURL.toString()),
        AppConfiguration.getInstance()
          .getString(CurrentEntries.CONF_UPDATER_POMPROPERTIESFILE.toString()),
        AppConfiguration.getInstance()
          .getString(CurrentEntries.CONF_UPDATER_CLASSIFIER.toString()),
        Bukkit.getUpdateFolderFile());

    this.namespacedKey = new NamespacedKey(instance, "PostBox_c765a011bcd709a9c8d417be90a7f090");

    this.commandMap.put("postbox", new CommandPostBox());
    this.commandMap.put("postbox-open", new CommandPostBoxOpen());
    this.commandMap.put("postbox-openother", new CommandPostBoxOpenOther());
    this.commandMap.put("postbox-send", new CommandPostBoxSend());
    this.commandMap.put("postbox-update", new CommandPostBoxUpdate());

    this.registerCommands();
    this.registerListeners();
  }

  @Override
  public void onDisable() {
    super.onDisable();

    DatabaseConfiguration.getInstance().getConnectionSource().closeQuietly();
  }

  private void registerCommands() {
    this.commandMap.forEach(
      (s, e) -> {
        PluginCommand pluginCommand = getCommand(s);
        if (pluginCommand == null) {
          return;
        }
        pluginCommand.setExecutor(e);
        pluginCommand.setTabCompleter(e);
      });
  }

  private void registerListeners() {
    this.getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
    this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
    this.getServer().getPluginManager().registerEvents(new InventoryCloseListener(), this);
  }

  private void loadAppConfiguration() {
    InputStream bundledConfig = this.getClass().getResourceAsStream("config.yml");
    File externalConfig = new File(this.getDataFolder(), "config.yml");

    try {
      AppConfiguration.getInstance(externalConfig, bundledConfig);
    } catch (IOException ex) {
      this.getLogger().log(Level.SEVERE, "Error whilst intialising the plugin configuration!", ex);
      this.getServer().getPluginManager().disablePlugin(this);
    }
  }

  private void loadLanguageConfiguration() {
    Map<String, InputStream> bundledLanguages = new HashMap<>();
    bundledLanguages.put("en.yml", this.getClass().getResourceAsStream("languages/en.yml"));
    bundledLanguages.put("de.yml", this.getClass().getResourceAsStream("languages/de.yml"));
    File externalLanguagesFolder = new File(this.getDataFolder(), "languages");

    try {
      LanguageConfiguration.getInstance(
        externalLanguagesFolder, bundledLanguages, new Locale("en"));
    } catch (IOException ex) {
      this.getLogger()
        .log(Level.SEVERE, "Error whilst intialising the language configuration!", ex);
      this.getServer().getPluginManager().disablePlugin(this);
    }
  }

  private void loadDatabase() throws SQLException {
    try {
      DatabaseConfiguration.getInstance(
        AppConfiguration.getInstance().getString(CurrentEntries.CONF_JDBCURI.toString()));
    } catch (ClassNotFoundException ex) {
      Logger.getLogger(PostBoxPlugin.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  private void loadExternalPluginIntegrations() {
    try {
      this.luckPermsApi = LuckPermsProvider.get();
    } catch (IllegalStateException ex) {
      LOG.log(
        Level.WARNING,
        "LuckPerms API not found. Using conventional methods for resolving player names!");
    }
  }

  private void loadProfileService() {
    this.profileService = HttpRepositoryService.forMinecraft();
  }

  public static PostBoxPlugin getInstance() {
    return instance;
  }

  public HashMap<String, TabExecutor> getCommandMap() {
    return commandMap;
  }

  public Updater getUpdater() {
    return updater;
  }

  public LuckPerms getLuckPermsApi() {
    return luckPermsApi;
  }

  public ProfileService getProfileService() {
    return profileService;
  }

  public ArrayList<Triple<UUID, Inventory, UUID>> getInventoryMappings() {
    return inventoryMappings;
  }

  public NamespacedKey getNamespacedKey() {
    return namespacedKey;
  }
}
