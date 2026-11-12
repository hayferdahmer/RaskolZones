package ru.raskol.zones.territory.geometry;

import java.util.List;

public sealed interface TerritoryGeometry permits CuboidGeometry, PolygonGeometry {
    String shapeType();

    int minY();

    int maxY();

    double minX();

    double maxX();

    double minZ();

    double maxZ();

    List<Point2> vertices();

    boolean contains(double x, int y, double z);
}
