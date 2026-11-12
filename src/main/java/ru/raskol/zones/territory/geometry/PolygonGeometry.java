package ru.raskol.zones.territory.geometry;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PolygonGeometry(List<Point2> vertices, int minY, int maxY) implements TerritoryGeometry {
    private static final double EPSILON = 1.0E-9;
    private static final int MAX_VERTICES = 2_048;

    public PolygonGeometry {
        Objects.requireNonNull(vertices, "vertices");
        vertices = List.copyOf(vertices);
        if (minY > maxY) {
            throw new IllegalArgumentException("minY не может быть больше maxY");
        }
        if (vertices.size() < 3 || vertices.size() > MAX_VERTICES) {
            throw new IllegalArgumentException("Полигон должен содержать от 3 до "
                    + MAX_VERTICES + " вершин");
        }
        if (vertices.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Полигон содержит пустую вершину");
        }
        validateVertices(vertices);
    }

    @Override
    public String shapeType() {
        return "POLYGON";
    }

    @Override
    public double minX() {
        return vertices.stream().mapToDouble(Point2::x).min().orElseThrow();
    }

    @Override
    public double maxX() {
        return vertices.stream().mapToDouble(Point2::x).max().orElseThrow();
    }

    @Override
    public double minZ() {
        return vertices.stream().mapToDouble(Point2::z).min().orElseThrow();
    }

    @Override
    public double maxZ() {
        return vertices.stream().mapToDouble(Point2::z).max().orElseThrow();
    }

    @Override
    public boolean contains(double x, int y, double z) {
        if (y < minY || y > maxY || x < minX() || x > maxX()
                || z < minZ() || z > maxZ()) {
            return false;
        }

        boolean inside = false;
        for (int current = 0, previous = vertices.size() - 1;
             current < vertices.size(); previous = current++) {
            Point2 a = vertices.get(previous);
            Point2 b = vertices.get(current);
            if (onSegment(a, b, x, z)) {
                return true;
            }
            boolean crosses = (a.z() > z) != (b.z() > z)
                    && x < (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static void validateVertices(List<Point2> points) {
        Set<Point2> unique = new HashSet<>(points);
        if (unique.size() != points.size()) {
            throw new IllegalArgumentException("Полигон не должен содержать повторяющиеся вершины");
        }

        double twiceArea = 0;
        for (int i = 0; i < points.size(); i++) {
            Point2 current = points.get(i);
            Point2 next = points.get((i + 1) % points.size());
            if (distanceSquared(current, next) <= EPSILON * EPSILON) {
                throw new IllegalArgumentException("Соседние вершины полигона совпадают");
            }
            twiceArea += current.x() * next.z() - next.x() * current.z();
        }
        if (!Double.isFinite(twiceArea) || Math.abs(twiceArea) <= EPSILON) {
            throw new IllegalArgumentException("Площадь полигона должна быть больше нуля");
        }

        int size = points.size();
        for (int first = 0; first < size; first++) {
            int firstNext = (first + 1) % size;
            for (int second = first + 1; second < size; second++) {
                int secondNext = (second + 1) % size;
                if (first == second || firstNext == second || secondNext == first) {
                    continue;
                }
                if (segmentsIntersect(points.get(first), points.get(firstNext),
                        points.get(second), points.get(secondNext))) {
                    throw new IllegalArgumentException("Контур полигона самопересекается");
                }
            }
        }
    }

    private static boolean segmentsIntersect(Point2 a, Point2 b, Point2 c, Point2 d) {
        double abC = orientation(a, b, c);
        double abD = orientation(a, b, d);
        double cdA = orientation(c, d, a);
        double cdB = orientation(c, d, b);

        if (((abC > EPSILON && abD < -EPSILON) || (abC < -EPSILON && abD > EPSILON))
                && ((cdA > EPSILON && cdB < -EPSILON) || (cdA < -EPSILON && cdB > EPSILON))) {
            return true;
        }
        return (Math.abs(abC) <= EPSILON && onSegment(a, b, c.x(), c.z()))
                || (Math.abs(abD) <= EPSILON && onSegment(a, b, d.x(), d.z()))
                || (Math.abs(cdA) <= EPSILON && onSegment(c, d, a.x(), a.z()))
                || (Math.abs(cdB) <= EPSILON && onSegment(c, d, b.x(), b.z()));
    }

    private static double orientation(Point2 a, Point2 b, Point2 c) {
        return (b.x() - a.x()) * (c.z() - a.z())
                - (b.z() - a.z()) * (c.x() - a.x());
    }

    private static boolean onSegment(Point2 a, Point2 b, double x, double z) {
        double cross = (x - a.x()) * (b.z() - a.z()) - (z - a.z()) * (b.x() - a.x());
        if (Math.abs(cross) > EPSILON) {
            return false;
        }
        return x >= Math.min(a.x(), b.x()) - EPSILON
                && x <= Math.max(a.x(), b.x()) + EPSILON
                && z >= Math.min(a.z(), b.z()) - EPSILON
                && z <= Math.max(a.z(), b.z()) + EPSILON;
    }

    private static double distanceSquared(Point2 a, Point2 b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }
}
