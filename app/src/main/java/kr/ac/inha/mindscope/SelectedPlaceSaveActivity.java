package kr.ac.inha.mindscope;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import static kr.ac.inha.mindscope.MapsActivity.GEOFENCE_RADIUS_DEFAULT;
import static kr.ac.inha.mindscope.MapsActivity.ID_HOME;
import static kr.ac.inha.mindscope.Tools.ACTION_CLICK_SAVE_BUTTON;

public class SelectedPlaceSaveActivity extends AppCompatActivity {

    private static final String TAG = "SelectedPlaceSave";
    LinearLayout containerPlcaeName;
    LinearLayout containerPlcaeAddress;
    LinearLayout containerPlcaeUserName;
    //    PlaceDbHelper dbHelper;
    EditText editText;
    TextView firstHomeSettingHelpText;
    String oldPlaceUserName;
    private Toolbar toolbar;
    private TextView placeNameTextView;
    private TextView placeAddressTextView;
    private String selectedPlaceName;
    private String selectedPlaceAddress;
    private String selectedPlaceUserName;
    private Double selectedLat;
    private Double selectedLng;
    private Boolean home_setting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_palce_save);

//        dbHelper = new PlaceDbHelper(getApplicationContext());

        containerPlcaeName = findViewById(R.id.container_current_place_name);
        containerPlcaeAddress = findViewById(R.id.container_current_place_address);
        containerPlcaeUserName = findViewById(R.id.container_current_place_user_name);
        placeNameTextView = findViewById(R.id.current_place_name);
        placeAddressTextView = findViewById(R.id.current_place_address);
        editText = findViewById(R.id.current_place_user_name);
        firstHomeSettingHelpText = findViewById(R.id.when_home_setting_help_text);
        Intent intent = getIntent();

        selectedPlaceName = intent.getExtras().getString("name");
        placeNameTextView.setText(selectedPlaceName);
        selectedPlaceAddress = intent.getExtras().getString("address");
        placeAddressTextView.setText(selectedPlaceAddress);
        selectedLat = intent.getExtras().getDouble("lat");
        selectedLng = intent.getExtras().getDouble("lng");
        home_setting = intent.getBooleanExtra("home_setting", false);

        SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        if (home_setting && locationPrefs.getBoolean(ID_HOME + "_isDeleted", true)) {
            editText.setText(getResources().getString(R.string.place_home));
            editText.setEnabled(false);
            firstHomeSettingHelpText.setVisibility(View.VISIBLE);
        } else {
            editText.setEnabled(true);
            firstHomeSettingHelpText.setVisibility(View.INVISIBLE);
        }

        if (intent.getExtras().getInt("editcode") == 2) {
            oldPlaceUserName = selectedPlaceUserName = intent.getExtras().getString("placeusername");
            if (selectedPlaceUserName.equals("HOME"))
                editText.setText(getResources().getString(R.string.place_home));
            else
                editText.setText(selectedPlaceUserName);
        }


        toolbar = findViewById(R.id.toolbar_map_current_save);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Tools.saveApplicationLog(getApplicationContext(), TAG, Tools.ACTION_OPEN_PAGE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.toolbar_save:
                if (editText.getText().toString().equals("")) {
                    Toast.makeText(this, "장소를 입력해주세요!", Toast.LENGTH_SHORT).show();
                } else {
//                    Toast.makeText(this, "장소 저장", Toast.LENGTH_SHORT).show();
                    // setLocation
                    setLocation(editText.getText().toString(), selectedPlaceAddress, selectedLat, selectedLng);
                    Log.d(TAG, "placeUserName, double lat lng, float lat lng: " + editText.getText().toString() + ", " + selectedLat + ", " + selectedLng + ", " + selectedLat.floatValue() + ", " + selectedLng.floatValue());
                    finish();
                    Tools.saveApplicationLog(getApplicationContext(), TAG, ACTION_CLICK_SAVE_BUTTON);
                }
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.toolbar_btn_save, menu);
        return true;
    }

    private void setLocation(String placeUserName, String placeAddress, Double lat, Double lng) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        SharedPreferences.Editor editor = locationPrefs.edit();

        SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = confPrefs.getInt("LOCATIONS_MANUAL", -1);
        int dataSourceId_LocationsList = confPrefs.getInt("LOCATIONS_LIST", -1);
        assert dataSourceId != -1;
        assert dataSourceId_LocationsList != -1;
        long nowTime = System.currentTimeMillis();

        if (oldPlaceUserName != placeUserName && oldPlaceUserName != null) {
            DbMgr.saveMixedData(dataSourceId_LocationsList, nowTime, 1.0f, oldPlaceUserName, placeAddress.replace(' ', '_'), lat.floatValue(), lng.floatValue(), true);
            editor.remove(oldPlaceUserName + "_LAT");
            editor.remove(oldPlaceUserName + "_LNG");
            editor.remove(oldPlaceUserName + "_ADDRESS");
            editor.remove(oldPlaceUserName + "_NAME");
            editor.remove(oldPlaceUserName + "_ENTERED_TIME");
            editor.remove(oldPlaceUserName + "_isDeleted");
            String newLocationList = locationPrefs.getString("locationList", "").replace(" " + oldPlaceUserName, "");
            editor.putString("locationList", newLocationList);
            editor.apply();
        }

        String location_id;
        placeUserName = placeUserName.replace(" ", "");
        if (placeUserName.equals("집") || placeUserName.equalsIgnoreCase("home")) {
            editor.putFloat(ID_HOME + "_LAT", lat.floatValue());
            editor.putFloat(ID_HOME + "_LNG", lng.floatValue());
            editor.putString(ID_HOME + "_ADDRESS", placeAddress);
//            editor.putString(ID_HOME + "_NAME", selectedPlaceName);
            editor.putBoolean(ID_HOME + "_isDeleted", false);
            editor.putString("locationList", String.format("%s %s", locationPrefs.getString("locationList", ""), ID_HOME));
            location_id = ID_HOME;
            DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, ID_HOME, lat.floatValue(), lng.floatValue());
            DbMgr.saveMixedData(dataSourceId_LocationsList, nowTime, 1.0f, ID_HOME, placeAddress.replace(' ', '_'), lat.floatValue(), lng.floatValue(), false);
        } else {
            editor.putFloat(placeUserName + "_LAT", lat.floatValue());
            editor.putFloat(placeUserName + "_LNG", lng.floatValue());
            editor.putString(placeUserName + "_ADDRESS", placeAddress);
//            editor.putString(placeUserName + "_NAME", selectedPlaceName);
            editor.putBoolean(placeUserName + "_isDeleted", false);
            editor.putString("locationList", String.format("%s %s", locationPrefs.getString("locationList", ""), placeUserName));
            location_id = placeUserName;
            DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, placeUserName, lat.floatValue(), lng.floatValue());
            DbMgr.saveMixedData(dataSourceId_LocationsList, nowTime, 1.0f, placeUserName, placeAddress.replace(' ', '_'), lat.floatValue(), lng.floatValue(), false);
        }
        editor.apply();

        LatLng position = new LatLng(lat, lng);

        GeofenceHelper.startGeofence(getApplicationContext(), location_id, position, GEOFENCE_RADIUS_DEFAULT);

        Toast.makeText(getApplicationContext(), placeUserName + " " + getString(R.string.location_set), Toast.LENGTH_SHORT).show();

    }
}