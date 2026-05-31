package com.delfino.expensetracker.dto.expense;

import jakarta.validation.constraints.Size;

/**
 * DTO for setting or updating the store associated with an expense. Keeps the
 * persistence entity out of the API contract.
 */
public class StoreRequest {

    /** If > 0 the client wants to update an existing store by its database ID. */
    private long id;

    @Size(max = 100)
    private String name;

    @Size(max = 200)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 2)
    private String country;

    @Size(max = 20)
    private String postalCode;

    private String phoneNumber;
    private String website;
    private Double latitude;
    private Double longitude;
    private String sourceId;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
}

