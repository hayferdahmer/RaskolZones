package ru.raskol.zones.territory.geometry;

public record Point2(double x, double z) {
    public Point2 {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Координаты вершины должны быть конечными числами");
        }
        if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
            throw new IllegalArgumentException("Координата вершины выходит за пределы мира Minecraft");
        }
    }
}
