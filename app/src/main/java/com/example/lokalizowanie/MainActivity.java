package com.example.lokalizowanie;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Adres serwera na komputerze
    private static final String SERVER_URL = "http://tubyloIP:8080/";

    // Elementy interfejsu
    private TextView tvStatus;
    private Switch swEnable;

    // Obiekty do lokalizacji GPS
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Połączenie zmiennych z widokami z XML
        tvStatus = findViewById(R.id.tvStatus);
        swEnable = findViewById(R.id.swEnable);

        // Utworzenie klienta GPS
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Ustawienia jak często i jak dokładnie pobierać lokalizację
        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                30000   // co 30 sekund
        ).build();

        // Co ma się stać gdy telefon dostanie nową lokalizację
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {

                for (Location location : result.getLocations()) {

                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    String text = "Lat: " + lat + "\nLon: " + lon;
                    tvStatus.setText(text);

                    // Wysyłanie danych do komputera
                    sendDataToServer(lat, lon);
                }
            }
        };

        // Reakcja na włącznik
        swEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {

                if (checkPermission()) {
                    startTracking();
                } else {
                    requestPermission();
                    swEnable.setChecked(false);
                }

            } else {
                stopTracking();
            }
        });
    }

    // ================= GPS =================

    private void startTracking() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );

        Toast.makeText(this, "GPS uruchomiony", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        tvStatus.setText("Zatrzymano GPS");
    }

    // ================= INTERNET =================

    private void sendDataToServer(double lat, double lon) {

        // Android nie pozwala na internet w głównym wątku,
        // dlatego tworzymy nowy wątek

        new Thread(() -> {

            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);

                String json = "{ \"latitude\": " + lat + ", \"longitude\": " + lon + " }";

                byte[] data = json.getBytes(StandardCharsets.UTF_8);

                OutputStream output = connection.getOutputStream();
                output.write(data);
                output.close();

                int code = connection.getResponseCode();
                Log.d("SERVER", "Wysłano: " + code);

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Błąd połączenia z serwerem",
                                Toast.LENGTH_SHORT).show()
                );
            }

        }).start();
    }

    // ================= PERMISSION =================

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }
}
