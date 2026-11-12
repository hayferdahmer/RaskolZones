package ru.raskol.zones.foundation.storage;

import java.util.concurrent.CompletionStage;

public interface StorageService extends AutoCloseable {
    CompletionStage<Void> initialize();
    <T> CompletionStage<T> transaction(SqlWork<T> work);
    @Override
    void close();
}
