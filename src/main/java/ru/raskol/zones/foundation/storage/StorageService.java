package ru.raskol.zones.foundation.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface StorageService extends AutoCloseable {
    CompletionStage<Void> initialize();

    <T> CompletionStage<T> transaction(SqlWork<T> work);

    default CompletionStage<Void> closeAsync() {
        return CompletableFuture.runAsync(this::close);
    }

    @Override
    void close();
}
