package com.github.cinnaio.transportation.model;

import java.util.UUID;

public class GarageVehicle {
    private int id;
    private UUID ownerUuid;
    private String ownerName;
    private String identityCode;
    private String model;
    private String modelId;
    private String statsOriginal; // JSON string
    private String statsExtended; // JSON string
    private boolean inGarage;
    private boolean frozen;
    private boolean destroyed;

    public GarageVehicle(int id, UUID ownerUuid, String ownerName, String identityCode, String model, String modelId, String statsOriginal, String statsExtended, boolean inGarage, boolean frozen, boolean destroyed) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.identityCode = identityCode;
        this.model = model;
        this.modelId = modelId;
        this.statsOriginal = statsOriginal;
        this.statsExtended = statsExtended;
        this.inGarage = inGarage;
        this.frozen = frozen;
        this.destroyed = destroyed;
    }

    public int getId() { return id; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getIdentityCode() { return identityCode; }
    public void setIdentityCode(String identityCode) { this.identityCode = identityCode; }
    public String getModel() { return model; }
    public String getModelId() { return modelId; }
    public String getStatsOriginal() { return statsOriginal; }
    public String getStatsExtended() { return statsExtended; }
    public void setStatsExtended(String statsExtended) { this.statsExtended = statsExtended; }
    public boolean isInGarage() { return inGarage; }
    public void setInGarage(boolean inGarage) { this.inGarage = inGarage; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
    public boolean isDestroyed() { return destroyed; }
    public void setDestroyed(boolean destroyed) { this.destroyed = destroyed; }
}
