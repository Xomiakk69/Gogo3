package com.example.betaforall.ui.home;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.betaforall.R;
import com.example.betaforall.api.YandexApiHelper;
import com.example.betaforall.api.YandexApiHelper.AddressCallback;
import com.example.betaforall.api.YandexApiHelper.CoordinatesCallback;
import com.example.betaforall.databinding.FragmentHomeBinding;
import com.example.betaforall.model.DeliveryLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.runtime.image.ImageProvider;



import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private AutoCompleteTextView etAddressIn, etAddressFor;
    private DatabaseReference databaseReference;
    private static final String TAG = "HomeFragment";
    private FusedLocationProviderClient fusedLocationClient;
    private MapObjectCollection mapObjects;
    private boolean isFirstLocationUpdate = true;
    private MapView mapView;
    View view;
    LinearLayout slidingLayout;
    private PlacemarkMapObject userLocationMarker;
    private PlacemarkMapObject addressInMarker;
    private PlacemarkMapObject addressForMarker; // новый маркер для "for"
    private Handler handler = new Handler(Looper.getMainLooper());

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private double userLat = 0, userLon = 0;
    private static final String YANDEX_API_KEY = "4d8b6f35-8d14-4eb8-93cc-3c0a8672de3b";
    private Runnable searchRunnable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        MapKitFactory.initialize(requireContext());
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        mapView = root.findViewById(R.id.mapview);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        mapObjects = mapView.getMap().getMapObjects();
        view = root.findViewById(R.id.view);

        // Инициализация полей ввода
        etAddressIn = binding.etAddressIn;
        etAddressFor = binding.etAddressFor;

        // Инициализация Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference("deliveryLocations");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        // Запрос разрешений на получение геолокации
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            getUserLocation();
        }

        // Кнопка создания заказа
        Button btnCreateOrder = root.findViewById(R.id.btn_create_order);
        btnCreateOrder.setOnClickListener(v -> {
            slidingLayout.setVisibility(View.VISIBLE);
            // Скрываем клавиатуру
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getActivity().getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
            }
            // Анимация для slidingLayout
            ObjectAnimator animation = ObjectAnimator.ofFloat(slidingLayout, "translationY", 600);
            animation.setDuration(100); // Duration of the slide-up animation
            animation.start();
            // Убираем фокус с полей ввода
            etAddressIn.clearFocus();
            etAddressFor.clearFocus();
            view.setVisibility(View.GONE);
            createOrder();
        });

        // Настройка авто-заполнения для адресов
        setupAutoComplete(etAddressIn);
        setupAutoComplete(etAddressFor);

        slidingLayout = root.findViewById(R.id.sliding_layout);

        etAddressIn.setOnFocusChangeListener((v, hasFocus) -> {
            if (slidingLayout.getTranslationY() == 0) {
                return;
            }

            if (hasFocus) {
                if (slidingLayout.getTranslationY() > 0) {
                    ObjectAnimator animation = ObjectAnimator.ofFloat(slidingLayout, "translationY", 0f);
                    animation.setDuration(500); // Duration of the slide-up animation
                    animation.start();
                    view.setVisibility(View.VISIBLE);
                }
            }
        });

        view.setOnClickListener(onClickListener -> {
            // Показываем slidingLayout
            slidingLayout.setVisibility(View.VISIBLE);
            // Скрываем клавиатуру
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getActivity().getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
            }
            // Анимация для slidingLayout
            ObjectAnimator animation = ObjectAnimator.ofFloat(slidingLayout, "translationY", 600);
            animation.setDuration(100); // Duration of the slide-up animation
            animation.start();
            // Убираем фокус с полей ввода
            etAddressIn.clearFocus();
            etAddressFor.clearFocus();
            view.setVisibility(View.GONE);
        });

        setupUserLocationMarker();
        setupAddressMarkers(); // Инициализация маркеров для адресов
        requestUserLocationUpdates();

        return root;
    }
    @SuppressLint("MissingPermission")
    private void getUserLocation() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        userLat = location.getLatitude();
                        userLon = location.getLongitude();
                        Log.d(TAG, "Координаты получены: " + userLat + ", " + userLon);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Ошибка получения местоположения", e));
    }
    @SuppressLint("MissingPermission")
    private void requestUserLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000); // Update every 5 seconds

        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    Location location = locationResult.getLastLocation();
                    updateUserLocation(location);
                }
            }
        }, Looper.getMainLooper());
    }
    private void updateUserLocation(Location location) {
        Point userPoint = new Point(location.getLatitude(), location.getLongitude());
        userLocationMarker.setGeometry(userPoint);

        if (isFirstLocationUpdate) {
            mapView.getMap().move(new CameraPosition(userPoint, 15, 0, 0)); // Move camera to the user's location
            isFirstLocationUpdate = false;
        }
    }



    private void setupUserLocationMarker() {
        userLocationMarker = mapObjects.addPlacemark();
        Bitmap resizedBitmap = resizeMarkerImage(50, 50);
        userLocationMarker.setIcon(ImageProvider.fromBitmap(resizedBitmap));
    }

    private void setupAddressMarkers() {
        // Создание маркеров для "in" и "for"
        addressInMarker = mapObjects.addPlacemark();
        addressForMarker = mapObjects.addPlacemark();

        Bitmap markerBitmap = resizeMarkerImage(50, 50); // маркер с одинаковым размером
        addressInMarker.setIcon(ImageProvider.fromBitmap(markerBitmap));
        addressForMarker.setIcon(ImageProvider.fromBitmap(markerBitmap));
    }

    private Bitmap resizeMarkerImage(int width, int height) {
        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pointgogo);
        return Bitmap.createScaledBitmap(originalBitmap, width, height, false);
    }

    private void updateAddressMarker(final PlacemarkMapObject marker, final double lat, final double lon) {
        // Ensure this code runs on the UI thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Update the marker's position on the map
                    marker.setGeometry(new Point(lat, lon));
                }
            });
        } else {
            // If it's already the main thread, update directly
            marker.setGeometry(new Point(lat, lon));
        }
    }



    private void setupAutoComplete(AutoCompleteTextView autoCompleteTextView) {
        Log.d(TAG, "setupAutoComplete: инициализирован для " + autoCompleteTextView.getId());

        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedAddress = (String) parent.getItemAtPosition(position);
            autoCompleteTextView.setText(selectedAddress);

            // После выбора адреса из списка, останавливаем дальнейшие запросы
            handler.removeCallbacks(searchRunnable);

            // Получаем выбранный адрес и отправляем запрос для получения координат
            YandexApiHelper.fetchAddressAndCoordinates(
                    selectedAddress,  // выбранный адрес
                    YANDEX_API_KEY,  // ваш API ключ
                    userLat,  // широта пользователя
                    userLon,
                    new AddressCallback() {
                        @Override
                        public void onResult(List<String> addressList, List<String> descriptionList) {
                            Log.d(TAG, "Адреса получены: " + addressList);
                        }
                    },
                    new CoordinatesCallback() {
                        @Override
                        public void onCoordinatesReceived(double lat, double lon) {
                            if (autoCompleteTextView == etAddressIn) {
                                updateAddressMarker(addressInMarker, lat, lon); // Обновление маркера для адреса "in"
                            } else if (autoCompleteTextView == etAddressFor) {
                                updateAddressMarker(addressForMarker, lat, lon); // Обновление маркера для адреса "for"
                            }
                        }
                    });
        });

        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    if (s.length() > 2) {
                        YandexApiHelper.fetchAddressAndCoordinates(
                                s.toString(),  // строка запроса
                                YANDEX_API_KEY,  // ваш API ключ
                                userLat,  // широта пользователя
                                userLon,
                                new AddressCallback() {
                                    @Override
                                    public void onResult(List<String> addressList, List<String> descriptionList) {
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                // Проверка на пустоту списка перед доступом
                                                if (addressList != null && !addressList.isEmpty() && descriptionList != null && !descriptionList.isEmpty()) {
                                                    List<String> combinedList = new ArrayList<>();
                                                    for (int i = 0; i < addressList.size(); i++) {
                                                        if (i < descriptionList.size()) {
                                                            combinedList.add(addressList.get(i) + " - " + descriptionList.get(i));
                                                        } else {
                                                            combinedList.add(addressList.get(i) + " - Описание не найдено");
                                                        }
                                                    }
                                                    ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, combinedList);
                                                    autoCompleteTextView.setAdapter(adapter);
                                                    autoCompleteTextView.showDropDown();
                                                }
                                            });
                                        }
                                    }
                                },
                                new CoordinatesCallback() {
                                    @Override
                                    public void onCoordinatesReceived(double lat, double lon) {
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                Log.d(TAG, "Получены координаты: " + lat + ", " + lon);
                                            });
                                        }
                                    }
                                });
                    }
                };
                handler.postDelayed(searchRunnable, 1000); // задержка для предотвращения частых запросов
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void createOrder() {
        String addressIn = etAddressIn.getText().toString();
        String addressFor = etAddressFor.getText().toString();

        if (addressIn.isEmpty() || addressFor.isEmpty()) {
            Toast.makeText(requireContext(), "Пожалуйста, заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = databaseReference.push().getKey();
        if (id != null) {
            DeliveryLocation order = new DeliveryLocation(id, addressIn, addressFor, "В ожидании курьера", "0");
            databaseReference.child(id).setValue(order)
                    .addOnSuccessListener(aVoid -> Toast.makeText(requireContext(), "Заказ создан", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(requireContext(), "Ошибка при создании заказа", Toast.LENGTH_SHORT).show());
        }
    }
    @Override
    public void onStart() {
        super.onStart();
        MapKitFactory.getInstance().onStart();
        mapView.onStart();
    }

    @Override
    public void onStop() {
        mapView.onStop();
        MapKitFactory.getInstance().onStop();
        super.onStop();
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
