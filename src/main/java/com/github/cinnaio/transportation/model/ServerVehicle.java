package com.github.cinnaio.transportation.model;

public class ServerVehicle {
    private int id;
    private String name;
    private String model;
    private String statsOriginal;
    private String statsExtended;
    private double price;

    public ServerVehicle(int id, String name, String model, String statsOriginal, String statsExtended, double price) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.statsOriginal = statsOriginal;
        this.statsExtended = statsExtended;
        this.price = price;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getModel() { return model; }
    public String getStatsOriginal() { return statsOriginal; }
    public String getStatsExtended() { return statsExtended; }
    public double getPrice() { return price; }
}
