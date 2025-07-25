package dev.lrxh.neptune;

import com.github.retrooper.packetevents.PacketEvents;
import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import com.jonahseguin.drink.provider.spigot.UUIDProvider;
import dev.lrxh.neptune.cache.Cache;
import dev.lrxh.neptune.cache.EntityCache;
import dev.lrxh.neptune.cache.EntityCacheRunnable;
import dev.lrxh.neptune.cache.ItemCache;
import dev.lrxh.neptune.commands.FollowCommand;
import dev.lrxh.neptune.commands.LeaveCommand;
import dev.lrxh.neptune.configs.ConfigService;
import dev.lrxh.neptune.configs.impl.ScoreboardLocale;
import dev.lrxh.neptune.configs.impl.SettingsLocale;
import dev.lrxh.neptune.feature.cosmetics.CosmeticService;
import dev.lrxh.neptune.feature.cosmetics.command.CosmeticsCommand;
import dev.lrxh.neptune.feature.divisions.DivisionService;
import dev.lrxh.neptune.feature.hotbar.HotbarService;
import dev.lrxh.neptune.feature.hotbar.listener.ItemListener;
import dev.lrxh.neptune.feature.leaderboard.LeaderboardService;
import dev.lrxh.neptune.feature.leaderboard.command.LeaderboardCommand;
import dev.lrxh.neptune.feature.leaderboard.task.LeaderboardTask;
import dev.lrxh.neptune.feature.party.command.PartyCommand;
import dev.lrxh.neptune.feature.queue.command.QueueCommand;
import dev.lrxh.neptune.feature.queue.command.QueueMenuCommand;
import dev.lrxh.neptune.feature.queue.command.QuickQueueCommand;
import dev.lrxh.neptune.feature.queue.tasks.QueueCheckTask;
import dev.lrxh.neptune.feature.queue.tasks.QueueMessageTask;
import dev.lrxh.neptune.feature.settings.Setting;
import dev.lrxh.neptune.feature.settings.command.SettingProvider;
import dev.lrxh.neptune.feature.settings.command.SettingsCommand;
import dev.lrxh.neptune.game.arena.Arena;
import dev.lrxh.neptune.game.arena.ArenaService;
import dev.lrxh.neptune.game.arena.command.ArenaProvider;
import dev.lrxh.neptune.game.arena.command.StandaloneArenaProvider;
import dev.lrxh.neptune.game.arena.impl.StandAloneArena;
import dev.lrxh.neptune.game.arena.procedure.ArenaProcedureListener;
import dev.lrxh.neptune.game.duel.command.DuelCommand;
import dev.lrxh.neptune.game.kit.Kit;
import dev.lrxh.neptune.game.kit.KitService;
import dev.lrxh.neptune.game.kit.command.KitEditorCommand;
import dev.lrxh.neptune.game.kit.command.KitProvider;
import dev.lrxh.neptune.game.kit.command.StatsCommand;
import dev.lrxh.neptune.game.kit.procedure.KitProcedureListener;
import dev.lrxh.neptune.game.match.MatchService;
import dev.lrxh.neptune.game.match.commands.MatchHistoryCommand;
import dev.lrxh.neptune.game.match.commands.SpectateCommand;
import dev.lrxh.neptune.game.match.listener.BlockTracker;
import dev.lrxh.neptune.game.match.listener.MatchListener;
import dev.lrxh.neptune.game.match.tasks.ArenaBoundaryCheckTask;
import dev.lrxh.neptune.game.match.tasks.XPBarRunnable;
import dev.lrxh.neptune.main.MainCommand;
import dev.lrxh.neptune.profile.ProfileService;
import dev.lrxh.neptune.profile.listener.ProfileListener;
import dev.lrxh.neptune.providers.database.DatabaseService;
import dev.lrxh.neptune.providers.hider.EntityHider;
import dev.lrxh.neptune.providers.hider.listeners.BukkitListener;
import dev.lrxh.neptune.providers.hider.listeners.PacketInterceptor;
import dev.lrxh.neptune.providers.listeners.LobbyListener;
import dev.lrxh.neptune.providers.placeholder.PlaceholderImpl;
import dev.lrxh.neptune.providers.scoreboard.ScoreboardAdapter;
import dev.lrxh.neptune.utils.ServerUtils;
import dev.lrxh.neptune.utils.menu.MenuListener;
import dev.lrxh.neptune.utils.menu.MenuRunnable;
import dev.lrxh.neptune.utils.tasks.TaskScheduler;
import fr.mrmicky.fastboard.FastManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Getter
public final class Neptune extends JavaPlugin {
    private static Neptune instance;
    private Cache cache;
    private boolean placeholder = false;
    private EntityHider entityHider;
    @Setter
    private boolean allowJoin;
    @Setter
    private boolean allowMatches;

    public static Neptune get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        allowJoin = false;
        loadManager();
        allowJoin = true;
        allowMatches = true;
    }

    private void loadManager() {
        loadExtensions();
        if (!isEnabled()) return;

        ConfigService.get().load();

        ArenaService.get().load();
        KitService.get().load();
        this.cache = new Cache();
        HotbarService.get().load();

        new DatabaseService();
        if (!isEnabled()) return;
        CosmeticService.get().load();

        DivisionService.get().load();

        LeaderboardService.get();

        registerListeners();
        loadCommandManager();
        loadTasks();
        loadWorlds();
        initAPIs();

        if (ScoreboardLocale.ENABLED_SCOREBOARD.getBoolean()) {
            new FastManager(this, new ScoreboardAdapter());
        }

        ServerUtils.info("Loaded Successfully");
    }

    private void initAPIs() {
        entityHider = new EntityHider(this, EntityHider.Policy.BLACKLIST);

        PacketEvents.getAPI().getEventManager().registerListener(new PacketInterceptor());
        PacketEvents.getAPI().init();
    }

    private void registerListeners() {
        Arrays.asList(
                new ProfileListener(),
                new MatchListener(),
                new LobbyListener(),
                new ItemListener(),
                new EntityCache(),
                new ItemCache(),
                new BukkitListener(),
                new MenuListener(),
                new ArenaProcedureListener(),
                new KitProcedureListener(),
                new BlockTracker()
        ).forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
    }

    private void loadExtensions() {
        placeholder = loadExtension("PlaceholderAPI");
        if (placeholder) {
            ServerUtils.info("Placeholder API found, loading expansion.");
            new PlaceholderImpl(this).register();
        }
    }

    private boolean loadExtension(String pluginName) {
        Plugin plugin = getServer().getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    private void loadWorlds() {
        for (World world : getServer().getWorlds()) {
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setDifficulty(Difficulty.HARD);
        }
    }

    private void loadTasks() {
        new QueueCheckTask().start(20L);
        new QueueMessageTask().start(100L);
        new LeaderboardTask().start(SettingsLocale.LEADERBOARD_UPDATE_TIME.getInt());
        new EntityCacheRunnable().start(400L);
        new ArenaBoundaryCheckTask().start(20L);
        new MenuRunnable().start(2L);
        new XPBarRunnable().start(2L);
    }

    private void loadCommandManager() {
        CommandService drink = Drink.get(this);
        drink.bind(Kit.class).toProvider(new KitProvider());
        drink.bind(Arena.class).toProvider(new ArenaProvider());
        drink.bind(StandAloneArena.class).toProvider(new StandaloneArenaProvider());
        drink.bind(UUID.class).toProvider(new UUIDProvider());
        drink.bind(Setting.class).toProvider(new SettingProvider());

        drink.register(new KitEditorCommand(), "kiteditor").setDefaultCommandIsHelp(true);
        drink.register(new StatsCommand(), "stats").setDefaultCommandIsHelp(true);
        drink.register(new PartyCommand(), "party", "p");
        drink.register(new FollowCommand(), "follow");
        drink.register(new QueueCommand(), "queue").registerSub(new QueueMenuCommand());
        drink.register(new DuelCommand(), "duel", "1v1").setDefaultCommandIsHelp(true);
        drink.register(new LeaveCommand(), "leave", "forfeit", "spawn", "l");
        drink.register(new LeaderboardCommand(), "leaderboard", "lbs", "lb", "leaderboard").setDefaultCommandIsHelp(true);
        drink.register(new SettingsCommand(), "settings").setDefaultCommandIsHelp(true);
        drink.register(new SpectateCommand(), "spec", "spectate");
        drink.register(new MainCommand(), "neptune");
        drink.register(new CosmeticsCommand(), "cosmetics");
        drink.register(new MatchHistoryCommand(), "matchhistory");
        drink.register(new QuickQueueCommand(), "quickqueue");
        drink.registerCommands();
    }

    @Override
    public void onDisable() {
        stopService(KitService.get(), KitService::stop);
        stopService(ArenaService.get(), ArenaService::stop);
        stopService(MatchService.get(), MatchService::stopAllGames);
        stopService(TaskScheduler.get(), TaskScheduler::stopAllTasks);
        stopService(ProfileService.get(), ProfileService::saveAll);
        stopService(cache, Cache::save);
    }

    public <T> void stopService(T service, Consumer<T> consumer) {
        Optional.ofNullable(service).ifPresent(consumer);
    }
}