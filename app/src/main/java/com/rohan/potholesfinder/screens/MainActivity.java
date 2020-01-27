package com.rohan.potholesfinder.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.rohan.potholesfinder.R;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.ResultReceiver;
import android.widget.TextView;
import android.widget.Toast;

import com.rohan.potholesfinder.LocationService.AdressIntentService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener {


    public static final int LOCATION_INTERVAL = 10000;
    public static final int FASTEST_LOCATION_INTERVAL = 5000;
    public static final int IMAGE_CAPTURE_CODE = 2;
    private static final int FILE_SELECT_CODE = 0;
    private static final String TAG = "MainActivity";


    GoogleApiClient mLocationClient;
    LocationRequest mLocationRequest = new LocationRequest();


    public static final String EXTRA_LATITUDE = "extra_latitude";
    public static final String EXTRA_LONGITUDE = "extra_longitude";
    //
    protected Location currentLocation;
    private AddressResultReceiver resultReceiver;
    private FusedLocationProviderClient fusedLocationProviderClient;

    public static final int FINE_LOCATION_PERMISSION_REQUEST_CODE = 1;

    private TextView textView;
    FirebaseFirestore mFirestore;
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
        //---------------------------
        mLocationClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest.setInterval(LOCATION_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_LOCATION_INTERVAL);


        int priority = LocationRequest.PRIORITY_HIGH_ACCURACY; //by default
        //PRIORITY_BALANCED_POWER_ACCURACY, PRIORITY_LOW_POWER, PRIORITY_NO_POWER are the other priority modes


        mLocationRequest.setPriority(priority);
        mLocationClient.connect();

        //-----------------------------


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        resultReceiver = new AddressResultReceiver(new Handler());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        fab = findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(getPackageManager()) != null)
                    startActivityForResult(intent, IMAGE_CAPTURE_CODE);
            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                try {
                    startActivityForResult(
                            Intent.createChooser(intent, "Select an Image to racognize"),
                            FILE_SELECT_CODE);
                } catch (android.content.ActivityNotFoundException ex) {
                    // Potentially direct the user to the Market with a Dialog
                    Toast.makeText(MainActivity.this, "Please install a File Manager.",
                            Toast.LENGTH_SHORT).show();
                }

                return true;
            }
        });


        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fetchAddress();
            }
        });

    }


    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, FINE_LOCATION_PERMISSION_REQUEST_CODE);
    }

    public void fetchAddress() {

        if (currentLocation != null) {
            startIntentService();

        }


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


    //-----GoogleMapsApi Methods-----//
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "== Error On onConnected() Permission not granted");
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mLocationClient, mLocationRequest, this);

        Log.d(TAG, "Connected to Google API");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Failed to connect to Google API");
    }

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {

            switch (requestCode) {
                case IMAGE_CAPTURE_CODE:

                    Bundle bundle = data.getExtras();
                    if (bundle != null) {
                        Bitmap bitmap = (Bitmap) bundle.get("data");

                        try {
                            //Convert Bitmap to Uri => Process => Verify Label => Upload
                            processImgUri(getImageUri(bitmap));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(this, "null bundle", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case FILE_SELECT_CODE:
                    Uri uri = data.getData();
                    if (uri == null) {
                        Toast.makeText(this, "null uri", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        //Process => Verify Label => Upload
                        processImgUri(uri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }




    private boolean imageLabelsVerified(List<FirebaseVisionImageLabel> labels) {

        //this is a temporary solution to verify our pothole image
        //we will update the logic if we found a better one


        ArrayList<String> requiredLabels = new ArrayList<>();
        //these keywords are found by testing image of a road
        //in different scenarios

        requiredLabels.add("soil");
        requiredLabels.add("asphalt");
        requiredLabels.add("road");
        requiredLabels.add("rock");
        requiredLabels.add("stone");
        requiredLabels.add("concrete");
        requiredLabels.add("sand");
        //can add more related fields

        for (FirebaseVisionImageLabel label : labels) {

            if (requiredLabels.contains(label.getText().toLowerCase())) {
                return true;
            }
        }

        return false;
    }


    private void processImgUri(final Uri uri) throws IOException {
        FirebaseVisionImage image = FirebaseVisionImage.fromFilePath(this, uri);

        FirebaseVisionOnDeviceImageLabelerOptions options = new FirebaseVisionOnDeviceImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.8f)
                .build();

        FirebaseVisionImageLabeler detector = FirebaseVision.getInstance().getOnDeviceImageLabeler(options);


        detector.processImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
            @Override
            public void onSuccess(List<FirebaseVisionImageLabel> firebaseVisionImageLabels) {

                if (imageLabelsVerified(firebaseVisionImageLabels)) {
                    Toast.makeText(MainActivity.this, "Verified ok", Toast.LENGTH_SHORT).show();
                    //Ready To Upload
                    uploadImageUri(uri);


                } else {
                    Toast.makeText(MainActivity.this, "The pic doesn't seems to be of a pothole", Toast.LENGTH_SHORT).show();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("rohan :", "--------FAILED--------------------");
            }
        });

    }

    private void uploadImageUri(final Uri uri) {

        final String currUid = mAuth.getCurrentUser().getUid().toString();
        StorageReference storageRef =
                FirebaseStorage.getInstance().getReference().child("UserReportedPotholes").child(mAuth.getCurrentUser().getEmail() + "_" + System.currentTimeMillis());
        storageRef.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {


                Task<Uri> result = taskSnapshot.getMetadata().getReference().getDownloadUrl();
                result.addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        String photoStringLink = uri.toString();
                        String email = mAuth.getCurrentUser().getEmail();

                        HashMap<String, String> map
                                = new HashMap<>();
                        map.put("emai", email);
                        map.put("uid", currUid);
                        map.put("image", photoStringLink);
                        mFirestore.collection("potholeImages").document().set(map);
                        Toast.makeText(MainActivity.this, "Uploaded", Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Failed To Upload", Toast.LENGTH_SHORT).show();
            }
        });


    }


    public Uri getImageUri(Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(this.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
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













//    private void processImgBitmap(final Bitmap bitmap) {
//
//        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
//        Log.d("rohan :", "--------Line266--------------------");
//        FirebaseVisionOnDeviceImageLabelerOptions options = new FirebaseVisionOnDeviceImageLabelerOptions.Builder()
//                .setConfidenceThreshold(0.8f)
//                .build();
//        Log.d("rohan :", "--------Line270--------------------");
//
//        FirebaseVisionImageLabeler detector = FirebaseVision.getInstance().getOnDeviceImageLabeler(options);
//        detector.processImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
//            @Override
//            public void onSuccess(List<FirebaseVisionImageLabel> firebaseVisionImageLabels) {
//                Log.d("rohan :", "--------onSuccess--------------------");
//                if (imageLabelsVerified(firebaseVisionImageLabels)) {
//                    Toast.makeText(MainActivity.this, "Verified ok", Toast.LENGTH_SHORT).show();
//                    // UPLOAD BITMAP HERE
////                    Uri bitmapUri = getImageUri(MainActivity.this, bitmap);
////                    uploadImageUri(bitmapUri);
//                } else {
//                    Toast.makeText(MainActivity.this, "The pic doesn't seems to be of a pothole", Toast.LENGTH_SHORT).show();
//                }
//
//            }
//        }).addOnFailureListener(new OnFailureListener() {
//            @Override
//            public void onFailure(@NonNull Exception e) {
//                Log.d("rohan :", "--------FAILED--------------------");
//            }
//        });
//    }