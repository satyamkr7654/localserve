package com.localserve.location.domain;

public record GeoPoint(double longitude, double latitude) {
    public GeoPoint {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
    }

    /** MongoDB GeoJSON coordinate ordering is longitude, then latitude. */
    public double[] coordinates() { return new double[]{longitude, latitude}; }
}
