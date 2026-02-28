package com.example.skindiseasedetector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.skindiseasedetector.databinding.ActivityNearbyDermatologistsBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NearbyDermatologistsActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 200;
    private static final int SEARCH_RADIUS = 60000; // 60 km

    private ActivityNearbyDermatologistsBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private double userLat, userLon;
    private DermatologistAdapter adapter;
    private final List<DermatologistModel> dermatologistList = new ArrayList<>();

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNearbyDermatologistsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ✅ Back button
        binding.btnBack.setOnClickListener(v -> onBackPressed());

        // ✅ RecyclerView setup
        binding.recyclerDerm.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DermatologistAdapter(dermatologistList, new DermatologistAdapter.OnDermActionListener() {
            @Override
            public void onDirectionClick(DermatologistModel derm) {
                openDirections(derm);
            }

            @Override
            public void onCallClick(DermatologistModel derm) {
                callClinic(derm);
            }
        });
        binding.recyclerDerm.setAdapter(adapter);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        checkLocationPermissionAndFetch();
    }

    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            getUserLocation();
        }
    }

    @SuppressLint("MissingPermission")
    private void getUserLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                userLat = location.getLatitude();
                userLon = location.getLongitude();
                fetchNearbyDermatologists();
            } else {
                Toast.makeText(this, "Couldn't get location. Try again!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchNearbyDermatologists() {
        new AsyncTask<Void, Void, List<DermatologistModel>>() {

            @Override
            protected void onPreExecute() {
                progressDialog = new ProgressDialog(NearbyDermatologistsActivity.this);
                progressDialog.setMessage("🔍 Searching nearby dermatologists...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            }

            @Override
            protected List<DermatologistModel> doInBackground(Void... voids) {
                List<DermatologistModel> tempList = new ArrayList<>();
                try {
                    // ✅ Only dermatologists & skin hospitals
                    String urlStr = String.format(
                            "https://overpass-api.de/api/interpreter?data=[out:json];" +
                                    "(" +
                                    "node[\"healthcare:speciality\"~\"dermatology|skin\",i](around:%d,%f,%f);" +
                                    "node[\"name\"~\"dermatologist|skin clinic|skin hospital\",i](around:%d,%f,%f);" +
                                    ");out;",
                            SEARCH_RADIUS, userLat, userLon,
                            SEARCH_RADIUS, userLat, userLon
                    );

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray elements = jsonResponse.getJSONArray("elements");

                    for (int i = 0; i < elements.length(); i++) {
                        JSONObject element = elements.getJSONObject(i);
                        JSONObject tags = element.optJSONObject("tags");

                        if (tags != null) {
                            String name = tags.optString("name", "Unnamed Clinic");

                            // ✅ Filter only dermatology or skin related
                            if (!name.toLowerCase().contains("skin") &&
                                    !name.toLowerCase().contains("derma")) {
                                continue;
                            }

                            String availability = tags.optString("opening_hours", "Availability not listed");
                            String phone = tags.optString("phone", "Not available");
                            double lat = element.optDouble("lat");
                            double lon = element.optDouble("lon");

                            float[] results = new float[1];
                            Location.distanceBetween(userLat, userLon, lat, lon, results);
                            float distanceKm = results[0] / 1000f;

                            tempList.add(new DermatologistModel(
                                    name,
                                    availability,
                                    lat,
                                    lon,
                                    String.format("%.2f", distanceKm),
                                    phone
                            ));
                        }
                    }

                    // ✅ Sort by nearest distance
                    Collections.sort(tempList, Comparator.comparingDouble(
                            d -> Double.parseDouble(d.getDistance()))
                    );

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return tempList;
            }

            @Override
            protected void onPostExecute(List<DermatologistModel> dermatologists) {
                progressDialog.dismiss();

                dermatologistList.clear();
                if (dermatologists != null && !dermatologists.isEmpty()) {
                    dermatologistList.addAll(dermatologists);
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(NearbyDermatologistsActivity.this,
                            "No dermatologists found nearby 😔", Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    private void openDirections(DermatologistModel derm) {
        Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                + derm.getLat() + "," + derm.getLon() +
                "&query=" + Uri.encode(derm.getName()));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        startActivity(intent);
    }

    // ✅ Fixed Step 4: Corrected callClinic() method
    private void callClinic(DermatologistModel derm) {
        String phone = derm.getPhone();
        if (phone == null || phone.equals("Not available")) {
            Toast.makeText(this, "Phone number not available ☹️", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getUserLocation();
        } else {
            Toast.makeText(this, "Location permission denied!", Toast.LENGTH_SHORT).show();
        }
    }
}
