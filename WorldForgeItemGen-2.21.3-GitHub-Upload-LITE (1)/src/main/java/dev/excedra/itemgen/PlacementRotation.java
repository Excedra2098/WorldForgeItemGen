package dev.excedra.itemgen;

/** Clockwise quarter-turns around the fragment's local origin. */
enum PlacementRotation {
    NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;

    record Offset(int x, int y, int z) {}

    Offset apply(int x, int y, int z) {
        return switch (this) {
            case NONE -> new Offset(x, y, z);
            case CLOCKWISE_90 -> new Offset(-z, y, x);
            case CLOCKWISE_180 -> new Offset(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> new Offset(z, y, -x);
        };
    }
}
