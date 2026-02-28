package com.example.skindiseasedetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView mapView;
    private final GeoPoint userLocation = new GeoPoint(13.0827, 80.2707); // Default Chennai

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());
        mapView = new MapView(this);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        setContentView(mapView);

        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });

        setupMap();
        fetchNearbyDermatologists(userLocation);
    }

    private void setupMap() {
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        IMapController mapController = mapView.getController();
        mapController.setZoom(14.0);
        mapController.setCenter(userLocation);

        Marker userMarker = new Marker(mapView);
        userMarker.setPosition(userLocation);
        userMarker.setTitle("You are here");
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(userMarker);
    }

    private void fetchNearbyDermatologists(GeoPoint location) {
        new Thread(() -> {
            try {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                double radius = 2000; // 2 km

                String query = "[out:json];node[\"healthcare:speciality\"=\"dermatology\"](around:" +
                        radius + "," + lat + "," + lon + ");out;";

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://overpass-api.de/api/interpreter?data=" + query)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.body() == null) return;
                String jsonData = response.body().string();
                response.close();

                JSONObject jsonObject = new JSONObject(jsonData);
                JSONArray elements = jsonObject.getJSONArray("elements");

                runOnUiThread(() -> addDermatologistMarkers(elements));

            } catch (IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Network error fetching data!", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void addDermatologistMarkers(JSONArray elements) {
        try {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject obj = elements.getJSONObject(i);
                double lat = obj.getDouble("lat");
                double lon = obj.getDouble("lon");

                String name = "Dermatologist Clinic";
                if (obj.has("tags")) {
                    JSONObject tags = obj.getJSONObject("tags");
                    if (tags.has("name")) name = tags.getString("name");
                }

                final String finalName = name;
                GeoPoint clinicPoint = new GeoPoint(lat, lon);

                Marker marker = new Marker(mapView);
                marker.setPosition(clinicPoint);
                marker.setTitle(finalName + "\nAvailable: 9 AM - 6 PM");
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                marker.setOnMarkerClickListener((clickedMarker, mapView1) -> {
                    drawRoute(userLocation, clinicPoint);
                    Toast.makeText(MapsActivity.this, "Route to " + finalName, Toast.LENGTH_SHORT).show();
                    return false;
                });

                mapView.getOverlays().add(marker);
            }
            mapView.invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawRoute(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            try {
                String url = "https://router.project-osrm.org/route/v1/driving/"
                        + start.getLongitude() + "," + start.getLatitude() + ";"
                        + end.getLongitude() + "," + end.getLatitude()
                        + "?overview=full&geometries=geojson";

                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.body() == null) return;
                String jsonData = response.body().string();
                response.close();

                JSONObject jsonObject = new JSONObject(jsonData);
                JSONArray coordinates = jsonObject
                        .getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<GeoPoint> geoPoints = new ArrayList<>();
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray coord = coordinates.getJSONArray(i);
                    geoPoints.add(new GeoPoint(coord.getDouble(1), coord.getDouble(0)));
                }

                runOnUiThread(() -> {
                    Polyline line = new Polyline();
                    line.setPoints(geoPoints);
                    line.setColor(0xFF0066CC);
                    line.setWidth(8f);
                    mapView.getOverlays().add(line);
                    mapView.invalidate();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        setupMap();
    }
}
