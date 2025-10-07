package dev.lrxh.api.queue;

import dev.lrxh.api.kit.IKit;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public interface IQueueService {
    int getQueueSize();

    Map<IKit, Queue<IQueueEntry>> getQueues();

    List<IQueueEntry> removeAll(UUID playerUUID);

    IQueueEntry get(UUID playerUUID);
}
