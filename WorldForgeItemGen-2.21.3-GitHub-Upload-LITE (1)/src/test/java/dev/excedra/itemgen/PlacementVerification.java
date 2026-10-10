package dev.excedra.itemgen;

/** Standalone geometry checks; no server or Paper dependency required. */
public final class PlacementVerification {
    public static void main(String[] args) {
        int checks = 0;
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            var original = new PlacementRotation.Offset(x, 7, z);
            var current = original;
            for (int turn = 0; turn < 4; turn++) {
                current = PlacementRotation.CLOCKWISE_90.apply(current.x(), current.y(), current.z());
            }
            if (!current.equals(original)) throw new AssertionError("Four turns must restore coordinates");
            checks++;
            for (var rotation : PlacementRotation.values()) {
                var result = rotation.apply(x, 7, z);
                if (result.y() != 7 || result.x()*result.x()+result.z()*result.z() != x*x+z*z)
                    throw new AssertionError("Rotation changed elevation or distance");
                checks++;
            }
        }
        if (!PlacementRotation.CLOCKWISE_90.apply(0, 0, -1).equals(new PlacementRotation.Offset(1, 0, 0)))
            throw new AssertionError("North must rotate clockwise to east");
        System.out.println("Placement geometry passed: " + (checks + 1) + " checks");
    }
}
