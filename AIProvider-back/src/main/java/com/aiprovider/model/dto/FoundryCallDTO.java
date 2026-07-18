package com.aiprovider.model.dto;

import java.util.ArrayList;
import java.util.List;

public class FoundryCallDTO {
    private String address;
    private String signature;
    private List<String> arguments = new ArrayList<>();

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public List<String> getArguments() { return arguments; }
    public void setArguments(List<String> arguments) { this.arguments = arguments; }
}
