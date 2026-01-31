package com.example.lokalizowanie;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.*;
import android.util.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.*;

import java.io.OutputStream;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private static final String SERVER_URL = "http://192.168.188.18:8080/android";

    private TextView tvStatus;
    private Switch swEnable;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        swEnable = findViewById(R.id.swEnable);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000).build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location location : result.getLocations()) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    String text = "Lat: " + lat + "\nLon: " + lon;
                    tvStatus.setText(text);

                    sendDataToServer(lat, lon);
                }
            }
        };

        swEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkPermission()) {
                    startTracking();
                } else {
                    swEnable.setChecked(false);
                    requestPermission();
                }
            } else {
                stopTracking();
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                swEnable.setChecked(true);
                startTracking();
            } else {
                Toast.makeText(this, "Wymagane uprawnienie lokalizacji!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    private void startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        Toast.makeText(this, "GPS uruchomiony", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        tvStatus.setText("Zatrzymano GPS");
    }

    // ================= INTERNET (NAPRAWIONE) =================

    private void sendDataToServer(double lat, double lon) {
        // Używamy Executora zamiast new Thread() dla lepszej wydajności
        networkExecutor.execute(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                // Ustawienie timeoutów, żeby aplikacja nie wisiała w nieskończoność
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Content-Type", "application/json");

                String json = "{ \"latitude\": " + lat + ", \"longitude\": " + lon + " }";
                byte[] data = json.getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }

                int code = connection.getResponseCode();
                Log.d("SERVER", "Wysłano: " + code);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        // Toast pokazujemy tylko raz na jakiś czas, żeby nie spamować użytkownika,
                        // ale tutaj zostawiam prostą wersję
                        Toast.makeText(MainActivity.this, "Błąd wysyłania: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
}