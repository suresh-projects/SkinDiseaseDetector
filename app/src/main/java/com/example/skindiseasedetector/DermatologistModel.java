package com.example.skindiseasedetector;

public class DermatologistModel {
    private String name;
    private String availability;
    private double lat;
    private double lon;
    private String distance;
    private String phone;

    public DermatologistModel(String name, String availability, double lat, double lon, String distance, String phone) {
        this.name = name;
        this.availability = availability;
        this.lat = lat;
        this.lon = lon;
        this.distance = distance;
        this.phone = phone;
    }

    public String getName() { return name; }
    public String getAvailability() { return availability; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getDistance() { return distance; }
    public String getPhone() { return phone; }
}
