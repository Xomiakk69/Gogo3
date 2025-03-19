package com.example.betaforall.api;

import java.util.List;

public class GeoObjectCollection {
    private List<GeoObject> featureMember;

    public List<GeoObject> getFeatureMember() {
        return featureMember;
    }

    public void setFeatureMember(List<GeoObject> featureMember) {
        this.featureMember = featureMember;
    }
}
