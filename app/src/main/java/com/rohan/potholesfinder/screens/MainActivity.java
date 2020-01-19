package com.rohan.potholesfinder.screens;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.rohan.potholesfinder.R;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.rohan.potholesfinder.LocationService.AdressIntentService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class MainActivity extends AppCompatActivity {


    protected Location currentLocation;
    private AddressResultReceiver resultReceiver;
    private FusedLocationProviderClient fusedLocationProviderClient;

    public static final int FINE_LOCATION_PERMISSION_REQUEST_CODE = 1;

    private TextView textView;

    FirebaseAuth mAuth;
    FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text);
        if (!hasPermissions()) {
            requestPermissions();
        }
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        resultReceiver = new AddressResultReceiver(new Handler());

        mAuth = FirebaseAuth.getInstance();
        fab = findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, CameraXactivity.class));
            }
        });

    }


    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_PERMISSION_REQUEST_CODE);
    }

    public void fetchAddress(View view) {
        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {

                        currentLocation = location;

                        if (!Geocoder.isPresent()) {
                            Toast.makeText(MainActivity.this, "No GeoCoder Available", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        startIntentService();

                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        textView.setText(e.toString());
                    }
                });
    }

    protected void startIntentService() {
        Intent intent = new Intent(this, AdressIntentService.class);
        intent.putExtra("location", currentLocation);
        intent.putExtra("receiver", resultReceiver);
        startService(intent);
    }

    private void setAddress(String address) {
        textView.setText(address);
    }


    class AddressResultReceiver extends ResultReceiver {

        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);

            if (resultData == null) {
                setAddress("result data is null");
                return;
            }
            String address = resultData.getString("data_key");
            if (address == null) {
                address = "null";
            }
            setAddress(address);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_signout:
                userSignOut();

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void userSignOut() {
        mAuth.signOut();
        startActivity(new Intent(MainActivity.this, LoginActivity.class));

    }
}
