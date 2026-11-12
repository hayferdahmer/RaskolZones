package ru.raskol.zones.foundation.storage;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlWork<T> {
    T execute(Connection connection) throws SQLException;
}
