package kr.ac.inha.mindscope;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener, OnItemClick {

    private static final String TAG = "MapsActivity";

    private static int AUTOCOMPLETE_REQUEST_CODE = 1;
    private GoogleMap mMap;
    LocationManager locationManager;
    Toolbar toolbar;


    ListView listView;
    ImageButton currentLocationBtn;

    private Marker currentGeofenceMarker;
    private MapsActivity.StoreLocation currentStoringLocation;
    private Circle geoLimits;

    private static final Float ZOOM_LEVLE = 16f;

    //region Constants
    public static final String ID_HOME = "HOME";
    public static final String ID_DORM = "DORM";
    public static final String ID_UNIV = "UNIV";
    public static final String ID_LIBRARY = "LIBRARY";
    public static final String ID_ADDITIONAL = "ADDITIONAL";

    public boolean checkForFirstStartStep1;

    private String TITLE_HOME;
    private String TITLE_DORM;
    private String TITLE_UNIV;
    private String TITLE_LIBRARY;
    private String TITLE_ADDITIONAL;

    PlaceDbHelper placeDbHelper;

    // autocomplte place selected
    LatLng selectedLatLng;
    String selectedName;
    String selectedAddress;

    List<String[]> placeList;

    private FusedLocationProviderClient fusedLocationProviderClient;


    public static final int GEOFENCE_RADIUS_DEFAULT = 100;
    static final MapsActivity.StoreLocation[] ALL_LOCATIONS = new MapsActivity.StoreLocation[]
            {
                    new MapsActivity.StoreLocation(ID_HOME, null),
                    new MapsActivity.StoreLocation(ID_DORM, null),
                    new MapsActivity.StoreLocation(ID_UNIV, null),
                    new MapsActivity.StoreLocation(ID_LIBRARY, null),
                    new MapsActivity.StoreLocation(ID_ADDITIONAL, null)
            };

    //region Internal classes
    static class StoreLocation {
        LatLng mLatLng;
        String mId;
        int rad;

        StoreLocation(String id, LatLng latlng/*, int radius*/) {
            mLatLng = latlng;
            mId = id;
            //rad = radius;
        }

        LatLng getmLatLng() {
            return mLatLng;
        }

        String getmId() {
            return mId;
        }

        int getRadius() {
            return rad;
        }

        void setRadius(int radius) {
            rad = radius;
        }
    }
    //endregion


    LinearLayout loadingLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        //첫번째 실행의 경우, INTRO 및 장소설정
        SharedPreferences firstStartMap = getSharedPreferences("firstStartMap", MODE_PRIVATE);
        int firstMap = firstStartMap.getInt("FirstMap", 0);

        if (firstMap != 1) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.map_help_contents));
            builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    // 다른 기능 필요하면 여기서 작업
                }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();

            int infoFirst = 1;
            SharedPreferences a = getSharedPreferences("firstStartMap", MODE_PRIVATE);
            SharedPreferences.Editor editor = a.edit();
            editor.putInt("FirstMap", infoFirst);
            editor.apply();
        }

        TITLE_HOME = getString(R.string.set_home_location);


        placeDbHelper = new PlaceDbHelper(getApplicationContext());
        updateListView();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                PlaceInfo placeInfo = (PlaceInfo) listView.getItemAtPosition(i);
//                Toast.makeText(MapsActivity.this, "work okay", Toast.LENGTH_SHORT).show();
                Log.i(TAG, placeInfo.placeName + " by click");
                changeMarkerPlaceInfo(placeInfo);
            }
        });

        currentLocationBtn = (ImageButton) findViewById(R.id.current_location_btn);
        loadingLayout = findViewById(R.id.loading_frame);
        loadingLayout.setVisibility(View.VISIBLE);
        loadingLayout.bringToFront();
        Tools.disable_touch(this);


        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);


        currentLocationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getCurrentLocation();
            }
        });

        // GPS setting check
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent intent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            startActivity(intent);
            finish();
        }

        toolbar = (Toolbar) findViewById(R.id.toolbar_map);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null)
            mapFragment.getMapAsync(this);


        String apiKey = getResources().getString(R.string.google_maps_key);
        // Place API
        Places.initialize(getApplicationContext(), apiKey);
        PlacesClient placesclient = Places.createClient(this);

        AutocompleteSupportFragment autocompleteSupportFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.frg_places_autocomplete);

        autocompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));

        autocompleteSupportFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                // TODO: Get info about the selected palce
//                Toast.makeText(MapsActivity.this, place.getName() + ", " + place.getId(), Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Place: " + place.getName() + ", " + place.getAddress() + ", " + place.getLatLng().latitude + ", " + place.getLatLng().longitude);
                selectedLatLng = new LatLng(place.getLatLng().latitude, place.getLatLng().longitude);
                selectedAddress = place.getAddress();
                selectedName = place.getName();
                mMap.animateCamera(CameraUpdateFactory.newLatLng(selectedLatLng));

            }

            @Override
            public void onError(@NonNull Status status) {
                Log.i(TAG, "An error occurred: " + status);
            }
        });

        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);

//        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
//                .build(this);
//        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);


    }

    public void updateListView() {
        PlaceDBAdapter adapter = new PlaceDBAdapter(this, R.layout.item_place_list, placeDbHelper.getPlaceData(), this);
        listView = findViewById(R.id.place_list);
        listView.setEmptyView(findViewById(android.R.id.empty));
        listView.setAdapter(adapter);

    }

    @Override
    protected void onResume() {
        updateListView();
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (requestCode == RESULT_OK) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                Log.i(TAG, "이거 Place: " + place.getName() + ", " + place.getId());
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                Status status = Autocomplete.getStatusFromIntent(data);
                Log.i(TAG, "이거2 " + status.getStatusMessage());
            } else if (resultCode == RESULT_CANCELED) {
                Status status = Autocomplete.getStatusFromIntent(data);
                Log.i(TAG, "이거3 " + status);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.clear();

        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerClickListener(this);

        for (MapsActivity.StoreLocation location : ALL_LOCATIONS) {
            if (getLocationData(getApplicationContext(), location) != null) {
                addLocationMarker(Objects.requireNonNull(getLocationData(getApplicationContext(), location)));
            }
        }

        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 20, this);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    @Override
    public void onMapClick(LatLng latLng) {
        markerForGeofence(latLng);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        removeLocation(marker.getTitle());
        drawGeofence(marker, Integer.parseInt(marker.getSnippet()));
        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        loadingLayout.setVisibility(View.GONE);
        Tools.enable_touch(this);

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, ZOOM_LEVLE));

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void drawGeofence(Marker marker, int radius) {
        if (geoLimits != null) {
            geoLimits.remove();
        }

        CircleOptions circleOptions = new CircleOptions()
                .center(marker.getPosition())
                .strokeColor(Color.argb(50, 70, 70, 70))
                .strokeWidth(1)
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(radius);

        geoLimits = mMap.addCircle(circleOptions);
    }

    private void markerForGeofence(LatLng latLng) {
        MarkerOptions optionsMarker = new MarkerOptions()
                .snippet(String.valueOf(GEOFENCE_RADIUS_DEFAULT))
                .position(latLng)
                .title("Current Location test");
        if (mMap != null) {
            if (currentGeofenceMarker != null) {
                currentGeofenceMarker.remove();
            }
            currentGeofenceMarker = mMap.addMarker(optionsMarker);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));
            drawGeofence(currentGeofenceMarker, GEOFENCE_RADIUS_DEFAULT);
        }
    }

    private void addLocationMarker(StoreLocation location) {
        Drawable iconDrawable;
        String location_title;
        switch (location.getmId()) {
            case ID_HOME:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.home);
                location_title = TITLE_HOME;
                break;
            case ID_DORM:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.dormitory);
                location_title = TITLE_DORM;
                break;
            case ID_UNIV:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.university);
                location_title = TITLE_UNIV;
                break;
            case ID_LIBRARY:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.library);
                location_title = TITLE_LIBRARY;
                break;
            case ID_ADDITIONAL:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.additional);
                location_title = TITLE_ADDITIONAL;
                break;
            default:
                iconDrawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher_no_bg);
                location_title = "My location";
                break;
        }

        assert iconDrawable != null;
        Bitmap iconBmp = ((BitmapDrawable) iconDrawable).getBitmap();
        mMap.addMarker(new MarkerOptions()
                .title(location_title)
                .snippet(String.valueOf(GEOFENCE_RADIUS_DEFAULT))
                .position(location.getmLatLng())
                .icon(BitmapDescriptorFactory.fromBitmap(iconBmp)));
    }

    // place info marker
    private void changeMarkerPlaceInfo(PlaceInfo placeInfo) {
        // 누른 listview 에 따라서 마커 그리기
        LatLng latLng = new LatLng(placeInfo.placeLat, placeInfo.placeLng);
        mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .snippet(String.valueOf(GEOFENCE_RADIUS_DEFAULT))
                .title(placeInfo.placeUserName));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, ZOOM_LEVLE));
    }

    @Override
    public void onClick(PlaceInfo placeInfo, int code) {
        if (placeInfo != null) {
            if (code == 1) {
                PlaceDbHelper dbHelper = new PlaceDbHelper(getApplicationContext());
                dbHelper.deletePlaceData(placeInfo.placeName);
                Log.i(TAG, placeInfo.placeName + " is deleted.");
                updateListView();
            } else {
                PlaceDbHelper dbHelper = new PlaceDbHelper(getApplicationContext());

                Intent intent = new Intent(MapsActivity.this, SelectedPlaceSaveActivity.class);
                intent.putExtra("name", placeInfo.placeName);
                intent.putExtra("address", placeInfo.placeAddress);
                intent.putExtra("lat", placeInfo.placeLat);
                intent.putExtra("lng", placeInfo.placeLng);
                intent.putExtra("placeusername", placeInfo.placeUserName);
                intent.putExtra("editcode", PlaceDBAdapter.EDIT_CODE);
                startActivity(intent);


            }
        }

    }


    static StoreLocation getLocationData(Context con, StoreLocation location) {
        SharedPreferences locationPrefs = con.getSharedPreferences("UserLocations", MODE_PRIVATE);
        float lat = locationPrefs.getFloat(location.getmId() + "_LAT", 0);
        float lng = locationPrefs.getFloat(location.getmId() + "_LNG", 0);
        if (lat != 0 && lng != 0) {
            return new StoreLocation(location.getmId(), new LatLng(lat, lng));
        }
        return null;

    }

    private void setLocation(String locationText, StoreLocation location) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        SharedPreferences.Editor editor = locationPrefs.edit();
        editor.putFloat(location.getmId() + "_LAT", (float) location.getmLatLng().latitude);
        editor.putFloat(location.getmId() + "_LNG", (float) location.getmLatLng().longitude);
        editor.apply();
        Toast.makeText(getApplicationContext(), locationText + getString(R.string.location_set), Toast.LENGTH_SHORT).show();
        currentStoringLocation.setRadius(GEOFENCE_RADIUS_DEFAULT);

        String location_id = currentStoringLocation.getmId();
        LatLng position = currentStoringLocation.getmLatLng();
        int radius = currentStoringLocation.getRadius();

        GeofenceHelper.startGeofence(getApplicationContext(), location_id, position, radius);

        SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = confPrefs.getInt("LOCATIONS_MANUAL", -1);
        assert dataSourceId != -1;
        long nowTime = System.currentTimeMillis();
        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, location.getmId(), (float) location.getmLatLng().latitude, (float) location.getmLatLng().longitude);

        onMapReady(mMap);
        drawGeofence(currentGeofenceMarker, currentStoringLocation.getRadius());
    }

    private void removeLocation(String markerTitle) {
        if (markerTitle.equals(TITLE_HOME))
            displayRemoveDialog(ID_HOME);
        else if (markerTitle.equals(TITLE_DORM))
            displayRemoveDialog(ID_DORM);
        else if (markerTitle.equals(TITLE_UNIV))
            displayRemoveDialog(ID_UNIV);
        else if (markerTitle.equals(TITLE_LIBRARY))
            displayRemoveDialog(ID_LIBRARY);
        else if (markerTitle.equals(TITLE_ADDITIONAL))
            displayRemoveDialog(ID_ADDITIONAL);
    }

    public void displayRemoveDialog(final String locationId) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.location_remove_confirmation));
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                GeofenceHelper.removeGeofence(getApplicationContext(), locationId);
                SharedPreferences.Editor editor = locationPrefs.edit();
                editor.remove(locationId + "_LAT");
                editor.remove(locationId + "_LNG");
                editor.remove(locationId + "_ENTERED_TIME");
                editor.apply();
                Toast.makeText(MapsActivity.this, getString(R.string.location_removed), Toast.LENGTH_SHORT).show();
                onMapReady(mMap);
                drawGeofence(currentGeofenceMarker, GEOFENCE_RADIUS_DEFAULT);
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        builder.show();
    }

//    //region Buttons click listeners
//    public void setHomeClick(View view) {
//        currentStoringLocation = new LocationsSettingActivity.StoreLocation(ID_HOME, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
//        displayDialog(TITLE_HOME, currentStoringLocation);
//        //setLocation(TITLE_HOME, currentStoringLocation);
//    }
//
//    public void setDormClick(View view) {
//        currentStoringLocation = new LocationsSettingActivity.StoreLocation(ID_DORM, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
//        displayDialog(TITLE_DORM, currentStoringLocation);
//        //setLocation(TITLE_DORM, currentStoringLocation);
//    }
//
//    public void setUnivClick(View view) {
//        currentStoringLocation = new LocationsSettingActivity.StoreLocation(ID_UNIV, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
//        displayDialog(TITLE_UNIV, currentStoringLocation);
//        //setLocation(TITLE_UNIV, currentStoringLocation);
//    }
//
//    public void setLibraryClick(View view) {
//        currentStoringLocation = new LocationsSettingActivity.StoreLocation(ID_LIBRARY, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
//        displayDialog(TITLE_LIBRARY, currentStoringLocation);
//        //setLocation(TITLE_LIBRARY, currentStoringLocation);
//    }
//
//    public void setAdditionalPlaceClick(View view) {
//        currentStoringLocation = new LocationsSettingActivity.StoreLocation(ID_ADDITIONAL, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
//        displayDialog(TITLE_ADDITIONAL, currentStoringLocation);
//        //setLocation(TITLE_LIBRARY, currentStoringLocation);
//    }
//    //endregion


    public void displayDialog(final String locationText, final StoreLocation location) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.location_set_confirmation, locationText));
        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setLocation(locationText, location);
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    @Override
    public void onBackPressed() {
        //
    }

    //    @Override
//    public void onBackPressed() {
//        Intent intent = new Intent(MapsActivity.this, MainActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        startActivity(intent);
//    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.toolbar_map_current_save_btn:
//                Toast.makeText(this, "장소 저장 액티비티 실행", Toast.LENGTH_SHORT).show();
                // Call 장소 저장 액티비티
                if (selectedLatLng != null) {
                    Intent intent = new Intent(MapsActivity.this, SelectedPlaceSaveActivity.class);
                    intent.putExtra("name", selectedName);
                    intent.putExtra("address", selectedAddress);
                    intent.putExtra("lat", selectedLatLng.latitude);
                    intent.putExtra("lng", selectedLatLng.longitude);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "장소 검색 후 사용해주세요!", Toast.LENGTH_SHORT).show();
                }

                return true;
            case android.R.id.home:
                Intent intent = new Intent(MapsActivity.this, MainActivity.class);
                finish();
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.toolbar_map_current_save_btn, menu);
        return true;
    }

    private void getCurrentLocation() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        onLocationChanged(location);
                    }
                });
    }

}

