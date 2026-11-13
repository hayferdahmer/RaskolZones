package ru.raskol.zones.territory.rules;

public record RuleSpecificity(int rank) implements Comparable<RuleSpecificity> {
    public static final int MIN_RANK = 0;
    public static final int MAX_RANK = 1_000;

    public RuleSpecificity {
        if (rank < MIN_RANK || rank > MAX_RANK) {
            throw new IllegalArgumentException("Специфичность должна находиться в диапазоне 0..1000");
        }
    }

    @Override
    public int compareTo(RuleSpecificity other) {
        return Integer.compare(rank, other.rank);
    }
}
