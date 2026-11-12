package ru.raskol.zones.territory.geometry;

import java.util.List;

public record CuboidGeometry(
        double minX,
        double maxX,
        int minY,
        int maxY,
        double minZ,
        double maxZ
) implements TerritoryGeometry {
    public CuboidGeometry {
        validateHorizontalBounds(minX, maxX, "X");
        validateHorizontalBounds(minZ, maxZ, "Z");
        if (minY > maxY) {
            throw new IllegalArgumentException("minY не может быть больше maxY");
        }
    }

    @Override
    public String shapeType() {
        return "CUBOID";
    }

    @Override
    public List<Point2> vertices() {
        return List.of();
    }

    @Override
    public boolean contains(double x, int y, double z) {
        return y >= minY && y <= maxY
                && x >= minX && x <= maxX
                && z >= minZ && z <= maxZ;
    }

    private static void validateHorizontalBounds(double minimum, double maximum, String axis) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum > maximum
                || Math.abs(minimum) > 30_000_000
                || Math.abs(maximum) > 30_000_000) {
            throw new IllegalArgumentException("Некорректные границы территории по оси " + axis);
        }
    }
}
