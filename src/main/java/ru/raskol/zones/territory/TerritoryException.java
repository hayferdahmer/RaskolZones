package ru.raskol.zones.territory;

public final class TerritoryException extends RuntimeException {
    public enum Code {
        NOT_FOUND,
        WORLD_NOT_FOUND,
        PARENT_NOT_FOUND,
        PARENT_WORLD_MISMATCH,
        PARENT_ARCHIVED,
        HIERARCHY_CYCLE,
        TYPE_NOT_FOUND,
        VERSION_CONFLICT,
        DATA_CORRUPTION
    }

    private final Code code;

    public TerritoryException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public TerritoryException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
