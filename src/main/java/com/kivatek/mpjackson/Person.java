package com.kivatek.mpjackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.lang.reflect.Field;

public class Person {

    @JsonProperty
    public int id = 0;

    @JsonProperty
    public String firstName = "";

    @JsonProperty
    public String familyName = "";

    @JsonProperty
    public int age = 0;

}
