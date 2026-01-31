package com.example.lokalizowanie;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.util.*;
import android.view.View;
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
    private Button btnLogin;
    private View layoutMainContent;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    // Zmienne do przechowywania danych kursu
    private String currentCourseNumber = "";
    private String currentVehicleName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        swEnable = findViewById(R.id.swEnable);
        btnLogin = findViewById(R.id.btnLogin);
        layoutMainContent = findViewById(R.id.layoutMainContent);

        // Obsługa przycisku logowania
        btnLogin.setOnClickListener(v -> showLoginDialog());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000).build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location location : result.getLocations()) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    String text = "Lat: " + lat + "\nLon: " + lon +
                            "\nKurs: " + currentCourseNumber +
                            "\nPojazd: " + currentVehicleName;
                    tvStatus.setText(text);

                    sendDataToServer(lat, lon);
                }
            }
        };

        swEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Najpierw sprawdzamy uprawnienia
                if (checkPermission()) {
                    // Jeśli są uprawnienia, pytamy o dane kursu
                    showTripDetailsDialog();
                } else {
                    // Jeśli brak uprawnień, cofamy switch i prosimy o nie
                    swEnable.setChecked(false);
                    requestPermission();
                }
            } else {
                stopTracking();
            }
        });
    }

    // --- LOGOWANIE ---
    private void showLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logowanie");

        // Prosty layout dla logowania
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputUser = new EditText(this);
        inputUser.setHint("Login");
        layout.addView(inputUser);

        final EditText inputPass = new EditText(this);
        inputPass.setHint("Hasło");
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputPass);

        builder.setView(layout);

        builder.setPositiveButton("Zaloguj", (dialog, which) -> {
            // Tutaj możesz dodać sprawdzanie poprawności loginu/hasła
            String user = inputUser.getText().toString();
            String pass = inputPass.getText().toString();

            if (!user.isEmpty() && !pass.isEmpty()) {
                // Pomyślne logowanie
                btnLogin.setVisibility(View.GONE); // Ukryj przycisk logowania
                layoutMainContent.setVisibility(View.VISIBLE); // Pokaż resztę
                Toast.makeText(MainActivity.this, "Zalogowano pomyślnie", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Podaj login i hasło", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Anuluj", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    // --- DANE KURSU I POJAZDU ---
    private void showTripDetailsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rozpocznij kurs");
        builder.setCancelable(false); // Użytkownik musi wybrać jedną z opcji

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Pole: Numer kursu (max 4 cyfry)
        final EditText inputCourse = new EditText(this);
        inputCourse.setHint("Nr kursu (max 4 cyfry)");
        inputCourse.setInputType(InputType.TYPE_CLASS_NUMBER);
        // Ograniczenie długości do 4 znaków
        inputCourse.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });
        layout.addView(inputCourse);

        // Pole: Nazwa pojazdu
        final EditText inputVehicle = new EditText(this);
        inputVehicle.setHint("Nazwa pojazdu");
        inputVehicle.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(inputVehicle);

        builder.setView(layout);

        builder.setPositiveButton("Start", (dialog, which) -> {
            String course = inputCourse.getText().toString().trim();
            String vehicle = inputVehicle.getText().toString().trim();

            if (course.isEmpty() || vehicle.isEmpty()) {
                Toast.makeText(MainActivity.this, "Wypełnij wszystkie pola!", Toast.LENGTH_SHORT).show();
                swEnable.setChecked(false); // Cofnij włączenie switcha
            } else {
                currentCourseNumber = course;
                currentVehicleName = vehicle;
                startTracking();
            }
        });

        builder.setNegativeButton("Anuluj", (dialog, which) -> {
            swEnable.setChecked(false); // Cofnij włączenie switcha
        });

        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Po nadaniu uprawnień włączamy switch, co wywoła listener i pokaże dialog z danymi
                swEnable.setChecked(true);
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
        Toast.makeText(this, "GPS uruchomiony\nKurs: " + currentCourseNumber + ", Pojazd: " + currentVehicleName, Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        tvStatus.setText("Zatrzymano GPS");
    }

    // ================= INTERNET =================

    private void sendDataToServer(double lat, double lon) {
        networkExecutor.execute(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Content-Type", "application/json");

                // Dodano wysyłanie nr kursu i pojazdu w JSON
                String json = "{ " +
                        "\"latitude\": " + lat + ", " +
                        "\"longitude\": " + lon + ", " +
                        "\"courseNumber\": \"" + currentCourseNumber + "\", " +
                        "\"vehicleName\": \"" + currentVehicleName + "\"" +
                        " }";

                byte[] data = json.getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }

                int code = connection.getResponseCode();
                Log.d("SERVER", "Wysłano: " + code);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Błąd wysyłania: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
}