package com.asu.hiblatek;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final String SHARED_PREF_DISCLAIMER_LABEL = "hiblatek.disclaimer";
    private static final int CAMERA_REQUEST = 1888;
    private static final int GALLERY_REQUEST = 2888;
    private final String TAG = "com.asu.hiblatek";
    private LinearLayout llMain;
    private TableLayout tlResult;
    private TextView tvWarps;
    private TextView tvWefts;
    private TextView tvTotal;
    private Button btNew;
    private ImageView imageView;
    private Uri mImageUri;
    private List<Bitmap> imageList = null;
    private int index = 0;

    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS)
                Log.i(TAG, "OpenCV loaded successfully");
            else
                super.onManagerConnected(status);
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        llMain = findViewById(R.id.llMain);
        tlResult = findViewById(R.id.tlResult);
        btNew = findViewById(R.id.btNew);
        imageView = findViewById(R.id.imageView1);
        tvWarps = findViewById(R.id.tvWarps);
        tvWefts = findViewById(R.id.tvWefts);
        tvTotal = findViewById(R.id.tvTotal);

        displayMenu(true);

        Button btnCapture = this.findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(v -> {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photo;
            try
            {
                // place where to store camera taken picture
                photo = createTemporaryFile("picture", ".jpg");
                photo.delete();
            }
            catch(Exception e)
            {
                Log.v(TAG, "Can't create file to take picture!");
                Toast.makeText(getApplicationContext(), "Please check SD card. Cannot take a photo.", Toast.LENGTH_SHORT).show();
                return;
            }

            mImageUri = FileProvider.getUriForFile(getApplicationContext(),
                    getApplicationContext().getApplicationContext().getPackageName() + ".provider",
                    photo);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
            //start camera intent
            startActivityForResult(cameraIntent, CAMERA_REQUEST);
        });

        Button btnGallery = findViewById(R.id.btnGallery);
        btnGallery.setOnClickListener(view -> {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            startActivityForResult(photoPickerIntent, GALLERY_REQUEST);
        });

        imageView.setOnClickListener(view -> {
            if (imageList != null && imageList.size() > 0) {
                index = ++index % imageList.size();
                imageView.setImageBitmap(imageList.get(index));
            }
        });

        btNew.setOnClickListener(view -> {
            displayMenu(true);
        });

        displayDisclaimer(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // delete all the created temporary files
        deleteTempFiles(new File(getExternalFilesDir(null)+"/.temp/"));
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (CAMERA_REQUEST == requestCode) {
                Bitmap b = grabImage(imageView);
                Log.v(TAG, mImageUri.getEncodedPath());
                processImage(b);
            } else if (GALLERY_REQUEST == requestCode) {
                Uri selectedImage = data.getData();
                try {
                    Bitmap b = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), selectedImage);
                    processImage(b);
                } catch (IOException e) {
                    Log.i(TAG, "Error: " + e);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_disclaimer:
                displayDisclaimer(true);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private File createTemporaryFile(String part, String ext) throws Exception
    {
        File tempDir = new File(getExternalFilesDir(null)+"/.temp/");
        Log.v(TAG, tempDir.getAbsolutePath());
        if(!tempDir.exists())
        {
            tempDir.mkdirs();
        }
        return File.createTempFile(part, ext, tempDir);
    }

    public Bitmap grabImage(ImageView imageView)
    {
        this.getContentResolver().notifyChange(mImageUri, null);
        ContentResolver cr = this.getContentResolver();
        Bitmap bitmap;
        try
        {
            bitmap = android.provider.MediaStore.Images.Media.getBitmap(cr,    mImageUri);
            imageView.setImageBitmap(bitmap);
            return bitmap;
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Failed to load", e);
            return null;
        }
    }

    /**
     * Runs the {@code FiberCounter} class to the bitmap image.
     * @param b the bitmap image
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void processImage(Bitmap b) {
        imageView.setImageBitmap(b);
        FiberCounter fc = new FiberCounter(b, getApplicationContext());
        imageList = fc.start();

        FiberCounter.Count c = fc.getCount();
        tvWarps.setText(c.vertical + "");
        tvWefts.setText(c.horizontal + "");
        tvTotal.setText(c.getTotal() + "");
        displayMenu(false);
    }

    private void displayMenu(boolean isMain) {
        if (isMain) {
            llMain.setVisibility(View.VISIBLE);
            tlResult.setVisibility(View.GONE);
            imageView.setImageDrawable(getResources().getDrawable(R.drawable.hiblatekbg, getTheme()));
        }
        else {
            llMain.setVisibility(View.GONE);
            tlResult.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Deletes temporary files.
     * @param file
     * @return
     */
    private boolean deleteTempFiles(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteTempFiles(f);
                    } else {
                        f.delete();
                    }
                }
            }
        }
        return file.delete();
    }

    /**
     * Displays the disclaimer.
     * @param required
     */
    private void displayDisclaimer(boolean required) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        try {
            boolean wasShown = pref.getBoolean(SHARED_PREF_DISCLAIMER_LABEL, false);
            if (!wasShown || required) {
                // display the Disclaimer first
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Disclaimer");
                builder.setMessage(
                        "Please note that the current version of the HiblaTek app is a prototype intended solely for testing purposes to ensure the accuracy and functionality of the application. This version is not the final product and may contain errors or bugs that could affect its performance.\n\n" +
                                "Furthermore, please be aware that the features, design, and functionality of the final version of HiblaTek may differ significantly from what is currently available in this prototype. Changes and improvements may be made to the app without prior notice.\n\n" +
                                "By using the HiblaTek app, you agree to the terms and conditions outlined in this disclaimer. If you do not agree with any part of this disclaimer, we kindly request that you refrain from using the app until the final version is released.\n\n" +
                                "Thank you for your understanding and support as we work towards delivering a reliable and fully functional HiblaTek app."
                );
                builder.setIcon(R.drawable.information);
                // Accept (Positive) action
                builder.setPositiveButton(required ? "OK" : "Accept", null);
                if (!required)
                    // Close (Negative) action
                    builder.setNegativeButton("Close Application", (dialogInterface, i) -> finish());
                // Create the AlertDialog
                AlertDialog alertDialog = builder.create();
                // set other dialog properties
                alertDialog.setCancelable(false);
                alertDialog.show();

                // setup the Accept action
                Button posBtn = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                posBtn.setOnClickListener(view -> {
                    pref.edit()
                            .putBoolean(SHARED_PREF_DISCLAIMER_LABEL, true)
                            .apply();
                    alertDialog.cancel();
                });
            }

        } catch (Exception e) { }
    }
}