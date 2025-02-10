package com.asu.hiblatek;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.ImageCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private PreviewView previewView;
    private Slider zoomSlider, brightnessSlider;
    private ImageButton flashButton, captureButton;
    private Camera camera;
    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        zoomSlider = findViewById(R.id.zoomSlider);
        brightnessSlider = findViewById(R.id.brightnessSlider);
        flashButton = findViewById(R.id.flashButton);
        captureButton = findViewById(R.id.captureButton);

        // Check for camera permission and request if necessary
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }

        // Set up flash button
        flashButton.setOnClickListener(v -> {
            if (imageCapture != null) {
                boolean isFlashOn = imageCapture.getFlashMode() == ImageCapture.FLASH_MODE_ON;
                imageCapture.setFlashMode(isFlashOn ? ImageCapture.FLASH_MODE_OFF : ImageCapture.FLASH_MODE_ON);
                flashButton.setImageResource(isFlashOn ? R.drawable.ic_flash_off : R.drawable.ic_flash_on);
            }
        });


        // Set up tap-to-focus
        previewView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && camera != null) {
                MeteringPointFactory factory = previewView.getMeteringPointFactory();
                MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                camera.getCameraControl().startFocusAndMetering(action);
            }
            return true;
        });


        // set up the capture button
        captureButton.setOnClickListener(v -> captureImage());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Set up ImageCapture for flash control
        imageCapture = new ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build();

        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

        // Set up the zoom and brightness sliders
        setupZoomSlider();
        setupBrightnessSlider();
    }

    private void setupZoomSlider() {
        if (camera != null) {
            float maxZoomRatio = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
            zoomSlider.setValueFrom(1.0f);
            zoomSlider.setValueTo(maxZoomRatio);
            zoomSlider.setValue(1.0f);

            zoomSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser && camera != null) {
                    camera.getCameraControl().setZoomRatio(value);
                }
            });
        }
    }

    private void setupBrightnessSlider() {
        if (camera != null) {
            int minExposure = camera.getCameraInfo().getExposureState().getExposureCompensationRange().getLower();
            int maxExposure = camera.getCameraInfo().getExposureState().getExposureCompensationRange().getUpper();

            brightnessSlider.setValueFrom(minExposure);
            brightnessSlider.setValueTo(maxExposure);
            brightnessSlider.setValue(0); // default to 0 for normal exposure

            brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser && camera != null) {
                    camera.getCameraControl().setExposureCompensationIndex((int) value);
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    private void captureImage() {
        // Create a file to save the image
        File photoFile = new File(getExternalFilesDir(null), generateFileName());

        // Set up the output options
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // Capture the image
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Image captured successfully, return the image URI
                Uri savedUri = Uri.fromFile(photoFile);

                Intent resultIntent = new Intent();
                resultIntent.putExtra("imageUri", savedUri);
                setResult(RESULT_OK, resultIntent);
                finish(); // Close the CameraActivity
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                // Handle the error
                exception.printStackTrace();
            }
        });
    }

    private String generateFileName() {
        // Generate a unique file name for the image
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
        return "IMG_" + sdf.format(new Date()) + ".jpg";
    }
}
