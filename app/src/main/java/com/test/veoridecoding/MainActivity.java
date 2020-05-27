package com.test.veoridecoding;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.location.replay.ReplayRouteLocationEngine;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMapClickListener, PermissionsListener, ProgressChangeListener {
    Toolbar toolbar;
    Button startnavigation;
    MapView mapView;
    MapboxMap mapboxMap;
    // variables for adding location layer
    PermissionsManager permissionsManager;
    LocationComponent locationComponent;
    // variables for calculating and drawing a route
    DirectionsRoute currentRoute;
    NavigationMapRoute navigationMapRoute;
    MapboxNavigation navigation;
    ReplayRouteLocationEngine replayEngine;
    boolean showResults = false;
    RouteProgress routeProgress;
    long startTime=0;
    Location currentLocation, sourceLocation;
    NumberFormat formatter = NumberFormat.getNumberInstance();
    String currentLocationName = "";
    String sourceLocationName = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token));

        MapboxNavigationOptions mapboxNavigationOptions = MapboxNavigationOptions.builder()
                .isDebugLoggingEnabled(true)
                .build();
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        replayEngine = new ReplayRouteLocationEngine();
        navigation = new MapboxNavigation(MainActivity.this, getString(R.string.access_token), mapboxNavigationOptions, replayEngine);
        navigation.addProgressChangeListener(MainActivity.this);

        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(MainActivity.this);

        //setting the status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0A436F"));
        }
    }

    private void setupUI() {
        startnavigation = findViewById(R.id.startnavigationbutton);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        startnavigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTime = System.currentTimeMillis();
                sourceLocation = locationComponent.getLastKnownLocation();
                sourceLocationName = getLocationStringName(sourceLocation);
                showResults = true;
                replayEngine.assign(currentRoute);
                navigation.setLocationEngine(replayEngine);
                NavigationLauncherOptions.Builder optionsBuilder = NavigationLauncherOptions.builder()
                        .shouldSimulateRoute(true);
                CameraPosition initialPosition = new CameraPosition.Builder()
                        .target(new LatLng(locationComponent.getLastKnownLocation().getLatitude(), locationComponent.getLastKnownLocation().getLongitude()))
                        .zoom(16)
                        .build();
                optionsBuilder.initialMapCameraPosition(initialPosition);
                optionsBuilder.directionsRoute(currentRoute);
                navigation.startNavigation(currentRoute);
                NavigationLauncher.startNavigation(MainActivity.this, optionsBuilder.build());

            }
        });
    }

    private String getLocationStringName(Location metLocation) {
        String locationname = "";
        try {
            Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(metLocation.getLatitude(), metLocation.getLongitude(), 1);
            if(addresses.size()>0){
               locationname = addresses.get(0).getThoroughfare();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return locationname;

    }


    @SuppressWarnings( {"MissingPermission"})
    @Override
    public boolean onMapClick(@NonNull LatLng point) {

        Point destinationPoint = Point.fromLngLat(point.getLongitude(), point.getLatitude());
        Point originPoint = null;
        if (locationComponent.getLastKnownLocation() != null) {
            originPoint = Point.fromLngLat(locationComponent.getLastKnownLocation().getLongitude(),
                    locationComponent.getLastKnownLocation().getLatitude());
        }

        GeoJsonSource source = Objects.requireNonNull(mapboxMap.getStyle()).getSourceAs("destination-source-id");
        if (source != null) {
            source.setGeoJson(Feature.fromGeometry(destinationPoint));
        }

        getRoute(originPoint, destinationPoint);
        startnavigation.setEnabled(true);
        return true;
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(getString(R.string.navigation_guidance_day), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                enableLocationComponent(style);

                addDestinationIconSymbolLayer(style);

                mapboxMap.addOnMapClickListener(MainActivity.this);
                setupUI();
            }
        });
    }

    private void addDestinationIconSymbolLayer(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addImage("destination-icon-id",
                BitmapFactory.decodeResource(this.getResources(), R.drawable.mapbox_marker_icon_default));
        GeoJsonSource geoJsonSource = new GeoJsonSource("destination-source-id");
        loadedMapStyle.addSource(geoJsonSource);
        SymbolLayer destinationSymbolLayer = new SymbolLayer("destination-symbol-layer-id", "destination-source-id");
        destinationSymbolLayer.withProperties(
                iconImage("destination-icon-id"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        );
        loadedMapStyle.addLayer(destinationSymbolLayer);
    }

    private void getRoute(Point origin, Point destination) {

        assert Mapbox.getAccessToken() != null;
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @SuppressLint("LogNotTimber")
                    @Override
                    public void onResponse(@NotNull Call<DirectionsResponse> call, @NotNull Response<DirectionsResponse> response) {
// You can get the generic HTTP info about the response
                        Log.d("MainActivity", "Response code: " + response.code());

                        if (response.body() == null) {
                            Log.e("MainActivity", "No routes found, make sure you set the right user and access token.");
                            return;
                        } else if (response.body().routes().size() < 1) {
                            Log.e("MainActivity", "No routes found");
                            return;
                        }

                        currentRoute = response.body().routes().get(0);
                        if (navigationMapRoute != null) {
                            navigationMapRoute.removeRoute();
                        } else {
                            navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute);
                        }
                        navigationMapRoute.addRoute(currentRoute);

                    }

                    @SuppressLint("LogNotTimber")
                    @Override
                    public void onFailure(@NotNull Call<DirectionsResponse> call, @NotNull Throwable throwable) {
                        Log.e("MainActivity", "Error: " + throwable.getMessage());
                    }



                });

    }


    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
// Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
// Activate the MapboxMap LocationComponent to show user location
// Adding in LocationComponentOptions is also an optional parameter
            locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(this, loadedMapStyle);
            locationComponent.setLocationComponentEnabled(true);
// Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }


    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationComponent(Objects.requireNonNull(mapboxMap.getStyle()));
            setupUI();
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if(showResults){
            navigation.stopNavigation();
            navigation.removeProgressChangeListener(MainActivity.this);
            showResults = false;
            showProgress();
        }
    }

    private void showProgress() {
        Location currLoc = getLoc();
        currentLocationName = "";
        double distanceTravelled = routeProgress.distanceTraveled();
        long endTime = System.currentTimeMillis();
        double seconds = (float)(endTime - startTime)/1000;
        final String secondsStr = formatter.format(seconds) +" seconds";
        final String distance = formatter.format(distanceTravelled/1000);
        currentLocationName = getLocationStringName(currLoc);
        String summary = "Summary of Your Trip:\nYou traveled "+distance+" miles in "+secondsStr+" from "+sourceLocationName+" to "+currentLocationName;
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(summary);
        alertDialogBuilder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {

                    }
                });


        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress1) {
        currentLocation = location;
        routeProgress = routeProgress1;
    }

    public RouteProgress getRouteProgress(){
        return routeProgress;
    }
    public Location getLoc(){
        return currentLocation;
    }
}

