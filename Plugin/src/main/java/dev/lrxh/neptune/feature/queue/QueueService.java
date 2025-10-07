package dev.lrxh.neptune.feature.queue;

import dev.lrxh.api.events.QueueJoinEvent;
import dev.lrxh.api.kit.IKit;
import dev.lrxh.api.queue.IQueueEntry;
import dev.lrxh.api.queue.IQueueService;
import dev.lrxh.neptune.API;
import dev.lrxh.neptune.Neptune;
import dev.lrxh.neptune.configs.impl.MessagesLocale;
import dev.lrxh.neptune.configs.impl.SettingsLocale;
import dev.lrxh.neptune.game.kit.Kit;
import dev.lrxh.neptune.game.kit.impl.KitRule;
import dev.lrxh.neptune.profile.data.ProfileState;
import dev.lrxh.neptune.profile.impl.Profile;
import dev.lrxh.neptune.providers.clickable.Replacement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class QueueService implements IQueueService {

    private static QueueService instance;

    private final Map<Kit, Queue<QueueEntry>> kitQueues = new HashMap<>();

    public static QueueService get() {
        if (instance == null)
            instance = new QueueService();
        return instance;
    }

    public void add(QueueEntry queueEntry, boolean add) {
        UUID playerUUID = queueEntry.getUuid();
        Kit kit = queueEntry.getKit();

        if (get(playerUUID) != null && !SettingsLocale.MULTIPLE_QUEUE.getBoolean())
            return;

        Profile profile = API.getProfile(playerUUID);
        if (!profile.hasState(ProfileState.IN_LOBBY))
            return;
        if (profile.getGameData().getParty() != null)
            return;
        if (queueEntry.getKit().is(KitRule.HIDDEN))
            return;

        kitQueues.computeIfAbsent(kit, k -> new ConcurrentLinkedQueue<>()).offer(queueEntry);

        if (add) {
            QueueJoinEvent event = new QueueJoinEvent(queueEntry);
            Bukkit.getScheduler().runTask(Neptune.get(), () -> Bukkit.getPluginManager().callEvent(event));
            if (event.isCancelled())
                return;
            profile.setState(ProfileState.IN_QUEUE);
            kit.addQueue();
            MessagesLocale.QUEUE_JOIN.send(playerUUID,
                    new Replacement("<kit>", kit.getDisplayName()),
                    new Replacement("<maxPing>", String.valueOf(profile.getSettingData().getMaxPing())));
        }
    }

    public List<IQueueEntry> removeAll(UUID playerUUID) {
        List<IQueueEntry> removedEntries = new ArrayList<>();

        for (Queue<QueueEntry> queue : kitQueues.values()) {
            Iterator<QueueEntry> iterator = queue.iterator();
            while (iterator.hasNext()) {
                QueueEntry entry = iterator.next();
                if (entry.getUuid().equals(playerUUID)) {
                    iterator.remove();
                    removedEntries.add(entry);
                    entry.getKit().removeQueue();
                }
            }
        }

        return removedEntries.isEmpty() ? null : removedEntries;
    }

    public boolean has(Kit kit, UUID playerUUID) {
        for (Queue<QueueEntry> entryQueue : getAllQueues().values()) {
            for (QueueEntry entry : entryQueue) {
                if (entry.getKit() == kit && entry.getUuid() == playerUUID) {
                    return true;
                }
            }
        }
        return false;
    }

    public QueueEntry remove(Kit kit, UUID playerUUID) {
        for (Queue<QueueEntry> queue : getAllQueues().values()) {
            for (QueueEntry entry : queue) {
                if (entry.getKit() == kit && entry.getUuid() == playerUUID) {
                    queue.remove(entry);
                    kit.removeQueue();
                    return entry;
                }
            }
        }
        return null;
    }

    public void remove(QueueEntry queueEntry) {
        removeAll(queueEntry.getUuid());
    }

    public QueueEntry poll(Kit kit) {
        Queue<QueueEntry> queue = kitQueues.get(kit);
        if (queue == null || queue.isEmpty())
            return null;

        List<QueueEntry> entries = new ArrayList<>(queue);
        QueueEntry entry = entries.get(new Random().nextInt(entries.size()));
        removeAll(entry.getUuid());
        return entry;
    }

    public QueueEntry get(UUID uuid) {
        for (Queue<QueueEntry> queue : kitQueues.values()) {
            for (QueueEntry entry : queue) {
                if (entry.getUuid().equals(uuid))
                    return entry;
            }
        }
        return null;
    }

    public List<QueueEntry> getPlayerQueues(Player player) {
        List<QueueEntry> entries = new ArrayList<>();
        for (Queue<QueueEntry> queue : kitQueues.values()) {
            for (QueueEntry entry : queue) {
                if (entry.getUuid().equals(player.getUniqueId()))
                    entries.add(entry);
            }
        }
        return entries;
    }

    public int getQueueSize() {
        return QueueService.get().getAllQueues().values().stream()
                .mapToInt(Queue::size)
                .sum();
    }

    public Map<Kit, Queue<QueueEntry>> getAllQueues() {
        return kitQueues;
    }

    public Map<IKit, Queue<IQueueEntry>> getQueues() {
        return kitQueues.entrySet().stream().collect(
                HashMap::new,
                (map, entry) -> map.put(
                        entry.getKey(),
                        entry.getValue().stream().map(
                                e -> (IQueueEntry) e).collect(Collectors.toCollection(LinkedList::new))),
                HashMap::putAll);
    }
}
