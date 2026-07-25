package xyz.nucleoid.leukocyte.client.util;

import java.util.*;

public class FaceMerger {

    public static class Face implements Comparable<Face> {
        public final int plane;
        public final int planeValue;
        public int u1, v1, u2, v2;

        public Face(int plane, int planeValue, int u1, int v1, int u2, int v2) {
            this.plane = plane;
            this.planeValue = planeValue;
            this.u1 = Math.min(u1, u2);
            this.v1 = Math.min(v1, v2);
            this.u2 = Math.max(u1, u2);
            this.v2 = Math.max(v1, v2);
        }

        public int getWidth() { return u2 - u1; }
        public int getHeight() { return v2 - v1; }
        public int getArea() { return getWidth() * getHeight(); }

        @Override
        public int compareTo(Face other) {
            if (this.plane != other.plane) return this.plane - other.plane;
            if (this.planeValue != other.planeValue) return this.planeValue - other.planeValue;
            if (this.u1 != other.u1) return this.u1 - other.u1;
            return this.v1 - other.v1;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Face)) return false;
            Face f = (Face) o;
            return plane == f.plane && planeValue == f.planeValue &&
                   u1 == f.u1 && v1 == f.v1 && u2 == f.u2 && v2 == f.v2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(plane, planeValue, u1, v1, u2, v2);
        }
    }

    public static List<Face> extractAndMergeBoundaryFaces(VoxelGrid grid) {
        Set<Face> boundaryFaces = extractBoundaryFaces(grid);
        List<Face> mergedFaces = mergeFacesOnPlanes(new ArrayList<>(boundaryFaces));
        return mergedFaces;
    }

    private static Set<Face> extractBoundaryFaces(VoxelGrid grid) {
        Set<Face> faces = new HashSet<>();
        for (int x = grid.getMinX(); x <= grid.getMaxX(); x++) {
            for (int y = grid.getMinY(); y <= grid.getMaxY(); y++) {
                for (int z = grid.getMinZ(); z <= grid.getMaxZ(); z++) {
                    if (!grid.isOccupied(x, y, z)) continue;
                    if (!grid.isOccupied(x - 1, y, z)) faces.add(new Face(0, x, y, z, y + 1, z + 1));
                    if (!grid.isOccupied(x + 1, y, z)) faces.add(new Face(0, x + 1, y, z, y + 1, z + 1));
                    if (!grid.isOccupied(x, y - 1, z)) faces.add(new Face(1, y, x, z, x + 1, z + 1));
                    if (!grid.isOccupied(x, y + 1, z)) faces.add(new Face(1, y + 1, x, z, x + 1, z + 1));
                    if (!grid.isOccupied(x, y, z - 1)) faces.add(new Face(2, z, x, y, x + 1, y + 1));
                    if (!grid.isOccupied(x, y, z + 1)) faces.add(new Face(2, z + 1, x, y, x + 1, y + 1));
                }
            }
        }
        return faces;
    }

    private static List<Face> mergeFacesOnPlanes(List<Face> faces) {
        Map<String, List<Face>> groups = new HashMap<>();
        for (Face face : faces) {
            String key = face.plane + ":" + face.planeValue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(face);
        }
        List<Face> merged = new ArrayList<>();
        for (List<Face> group : groups.values()) {
            merged.addAll(mergeFacesOnSamePlane(group));
        }
        return merged;
    }

    private static List<Face> mergeFacesOnSamePlane(List<Face> faces) {
        if (faces.isEmpty()) return faces;
        faces.sort(Comparator.comparingInt((Face f) -> f.u1).thenComparingInt(f -> f.v1));
        List<Face> working = new ArrayList<>(faces);

        boolean merged = true;
        while (merged && !working.isEmpty()) {
            merged = false;
            List<Face> nextWorking = new ArrayList<>();
            boolean[] processed = new boolean[working.size()];

            for (int i = 0; i < working.size(); i++) {
                if (processed[i]) continue;
                Face current = working.get(i);
                Face mergedFace = null;
                int mergedIdx = -1;

                for (int j = i + 1; j < working.size(); j++) {
                    if (processed[j]) continue;
                    Face candidate = working.get(j);
                    Face newFace = tryMergeFaces(current, candidate);
                    if (newFace != null) {
                        mergedFace = newFace;
                        mergedIdx = j;
                        merged = true;
                        break;
                    }
                }

                if (mergedFace != null) {
                    processed[i] = true;
                    processed[mergedIdx] = true;
                    nextWorking.add(mergedFace);
                } else {
                    processed[i] = true;
                    nextWorking.add(current);
                }
            }
            working = nextWorking;
        }
        return working;
    }

    private static Face tryMergeFaces(Face f1, Face f2) {
        if (f1.plane != f2.plane || f1.planeValue != f2.planeValue) return null;

        if (f1.v1 == f2.v1 && f1.v2 == f2.v2) {
            if (f1.u2 == f2.u1) return new Face(f1.plane, f1.planeValue, f1.u1, f1.v1, f2.u2, f2.v2);
            if (f2.u2 == f1.u1) return new Face(f1.plane, f1.planeValue, f2.u1, f1.v1, f1.u2, f2.v2);
        }

        if (f1.u1 == f2.u1 && f1.u2 == f2.u2) {
            if (f1.v2 == f2.v1) return new Face(f1.plane, f1.planeValue, f1.u1, f1.v1, f2.u2, f2.v2);
            if (f2.v2 == f1.v1) return new Face(f1.plane, f1.planeValue, f2.u1, f2.v1, f1.u2, f1.v2);
        }

        return null;
    }
}
