package dev.lrxh.neptune.feature.queue.tasks;

import dev.lrxh.neptune.API;
import dev.lrxh.neptune.configs.impl.MessagesLocale;
import dev.lrxh.neptune.feature.queue.QueueEntry;
import dev.lrxh.neptune.feature.queue.QueueService;
import dev.lrxh.neptune.profile.impl.Profile;
import dev.lrxh.neptune.providers.clickable.Replacement;
import dev.lrxh.neptune.utils.tasks.NeptuneRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;

public class QueueMessageTask extends NeptuneRunnable {
    @Override
    public void run() {
        if (!MessagesLocale.QUEUE_REPEAT_TOGGLE.getBoolean())
            return;
        HashMap<Profile, List<QueueEntry>> queues = new HashMap<>();
        for (Queue<QueueEntry> queue : QueueService.get().getAllQueues().values()) {
            for (QueueEntry queueEntry : queue) {
                Profile profile = API.getProfile(queueEntry.getUuid());
                queues.computeIfAbsent(profile, p -> new ArrayList<>()).add(queueEntry);
            }
        }
        queues.forEach((profile, entries) -> {
            MessagesLocale.QUEUE_REPEAT.send(profile.getPlayerUUID(),
                    new Replacement("<kit>",
                            String.join(", ", entries.stream().map(entry -> entry.getKit().getDisplayName()).toList())),
                    new Replacement("<maxPing>", String.valueOf(profile.getSettingData().getMaxPing())));
        });
    }
}
