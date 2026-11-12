package ru.raskol.zones.territory.geometry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TerritoryGeometryTest {
    @Test
    void cuboidUsesInclusiveHorizontalAndVerticalBounds() {
        var geometry = new CuboidGeometry(-10, 10, -64, 320, -20, 20);

        assertTrue(geometry.contains(-10, -64, -20));
        assertTrue(geometry.contains(10, 320, 20));
        assertFalse(geometry.contains(10.01, 100, 0));
        assertFalse(geometry.contains(0, 321, 0));
    }

    @Test
    void polygonDetectsInteriorAndBoundaryButRejectsExterior() {
        var geometry = new PolygonGeometry(List.of(
                new Point2(0, 0),
                new Point2(10, 0),
                new Point2(10, 10),
                new Point2(0, 10)
        ), -32, 128);

        assertTrue(geometry.contains(5, 0, 5));
        assertTrue(geometry.contains(0, 0, 5));
        assertFalse(geometry.contains(11, 0, 5));
        assertFalse(geometry.contains(5, 129, 5));
    }

    @Test
    void rejectsSelfIntersectingPolygon() {
        assertThrows(IllegalArgumentException.class, () -> new PolygonGeometry(List.of(
                new Point2(0, 0),
                new Point2(8, 8),
                new Point2(0, 8),
                new Point2(8, 0),
                new Point2(10, 3)
        ), 0, 100));
    }

    @Test
    void rejectsRepeatedVerticesAndInvalidVerticalRange() {
        assertThrows(IllegalArgumentException.class, () -> new PolygonGeometry(List.of(
                new Point2(0, 0),
                new Point2(10, 0),
                new Point2(10, 10),
                new Point2(0, 0)
        ), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new CuboidGeometry(
                0, 10, 20, 10, 0, 10
        ));
    }
}
