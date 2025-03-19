package com.example.betaforall.api;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeocodeApi {
    @GET("1.x/")
    Call<GeocodeResponse> getCoordinatesFromAddress(
            @Query("geocode") String address,
            @Query("format") String format,
            @Query("apikey") String apiKey);
}
