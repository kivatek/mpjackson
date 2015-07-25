package com.kivatek.mpjackson;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class Team {
    @JsonProperty
    List<byte[]> memberData = new ArrayList<>();
}
