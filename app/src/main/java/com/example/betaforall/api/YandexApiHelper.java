package com.example.betaforall.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YandexApiHelper {
    private static final String TAG = "YandexApiHelper";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();


    public interface CoordinatesCallback {
        void onCoordinatesReceived(double latitude, double longitude);
    }
    public interface AddressCallback {
        void onResult(List<String> suggestions, List<String> descriptions);
    }

    public static void fetchAddressAndCoordinates(String query, String apiKey, double userLatitude, double userLongitude, AddressCallback addressCallback, CoordinatesCallback coordinatesCallback) {
        executor.execute(() -> {
            List<String> suggestions = new ArrayList<>();
            List<String> descriptions = new ArrayList<>();

            HttpURLConnection connection = null;
            try {
                // URL encode the query
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");

                // Adding user's coordinates to the query
                String userLocation = userLongitude + "," + userLatitude;
                String apiUrl = "https://geocode-maps.yandex.ru/1.x/?apikey=" + apiKey +
                        "&format=json&geocode=" + encodedQuery + "&ll=" + userLocation;

                URL url = new URL(apiUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Now, use parseJsonResponse to process the JSON response
                    // Pass the existing coordinatesCallback as an argument
                    parseJsonResponse(response.toString(), suggestions, descriptions, coordinatesCallback);

                    // After processing, send the results to the addressCallback
                    addressCallback.onResult(suggestions, descriptions);
                } else {
                    Log.e(TAG, "HTTP Error: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching data: ", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();  // Close the connection
                }
            }
        });
    }




    public static void parseJsonResponse(String jsonResponse, List<String> suggestions, List<String> descriptions,CoordinatesCallback coordinatesCallback) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject geoObjectCollection = jsonObject.getJSONObject("response").getJSONObject("GeoObjectCollection");
            JSONArray featureMember = geoObjectCollection.optJSONArray("featureMember");

            // Проверка на наличие данных в featureMember
            if (featureMember == null || featureMember.length() == 0) {
                Log.e(TAG, "Ответ API пуст или не содержит объектов.");
                return;
            }

            // Логирование для проверки
            Log.d(TAG, "Количество найденных объектов: " + featureMember.length());

            for (int i = 0; i < featureMember.length(); i++) {
                JSONObject geoObject = featureMember.getJSONObject(i).getJSONObject("GeoObject");

                // Логирование каждого найденного адреса
                String address = geoObject.getString("name");
                String description = geoObject.optString("description", "Описание не найдено");

                suggestions.add(address);
                descriptions.add(description);

                JSONObject point = geoObject.getJSONObject("Point");
                String pos = point.getString("pos");

                // Разбиваем строку pos на долготу и широту
                String[] coordinates = pos.split(" ");
                if (coordinates.length == 2) {
                    try {
                        // Долгота (longitude) и широта (latitude)
                        double longitude = Double.parseDouble(coordinates[0]);
                        double latitude = Double.parseDouble(coordinates[1]);

                        Log.d("GeoData", "Latitude: " + latitude + ", Longitude: " + longitude);

                        // Отправляем координаты в callback
                        // Координаты можно передавать в дальнейшем, например, через интерфейс или прямо в UI
                        if (coordinatesCallback != null) {
                            coordinatesCallback.onCoordinatesReceived(latitude, longitude);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Ошибка при разборе координат: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Некорректный формат координат.");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "Ошибка парсинга JSON: " + e.getMessage());
        }
    }

}
