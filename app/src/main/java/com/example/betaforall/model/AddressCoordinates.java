package com.example.betaforall.model;

public class AddressCoordinates {
    private String address;
    private double lat;
    private double lon;

    public AddressCoordinates(String address, double lat, double lon) {
        this.address = address;
        this.lat = lat;
        this.lon = lon;
    }

    public String getAddress() {
        return address;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }
}
