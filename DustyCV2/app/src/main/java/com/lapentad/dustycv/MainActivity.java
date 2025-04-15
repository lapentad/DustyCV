package com.lapentad.dustycv;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.graphics.Matrix;

import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;

public class MainActivity extends AppCompatActivity {

    private Bitmap originalBitmap;
    private Bitmap processedBitmap;
    private ImageView imageView;
    private final Matrix matrix = new Matrix();
    private CropOverlayView cropOverlayView;
    private boolean isCropping = false;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnResize;
    private EffectParameters effectParameters = new EffectParameters();

    private static final int PERMISSION_REQUEST_CODE = 100;

    static {
        try {
            // Use the new initialization method
            if (!OpenCVLoader.initLocal()) {
                Log.e("OpenCV", "OpenCV initialization failed");
            } else {
                Log.i("OpenCV", "OpenCV loaded successfully.");
            }
        } catch (Exception e) {
            Log.e("OpenCV", "Error initializing OpenCV: " + e.getMessage());
        }
    }

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    try {
                            // Use ImageDecoder for API 28+
                        assert uri != null;
                        originalBitmap = android.graphics.ImageDecoder.decodeBitmap(
                                android.graphics.ImageDecoder.createSource(getContentResolver(), uri)
                            );
                            if (originalBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                            Bitmap temp = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            originalBitmap.recycle();
                            originalBitmap = temp;
                        }
                        
                        imageView.setImageBitmap(originalBitmap);
                        processedBitmap = null; // reset processed if user picks new image
                    } catch (IOException e) {
                        Log.e("MainActivity", "Error loading image: " + e.getMessage());
                        Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> mediaAccessLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK) {
                // User granted selected photos access
                Toast.makeText(this, "Selected photos access granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Selected photos access denied", Toast.LENGTH_SHORT).show();
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check and request permissions immediately
        if (hasNoPermissions()) {
            requestPermissions();
        }

        com.google.android.material.floatingactionbutton.FloatingActionButton btnChoose = findViewById(R.id.btnChoose);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnProcess = findViewById(R.id.btnProcess);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnShare = findViewById(R.id.btnShare);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnSettings = findViewById(R.id.btnSettings);
        btnResize = findViewById(R.id.btnResize);
        imageView = findViewById(R.id.touchImageView);
        cropOverlayView = findViewById(R.id.cropOverlayView);

        btnChoose.setOnClickListener(v -> {
            if (hasNoPermissions()) {
                requestPermissions();
            } else {
                chooseImage();
            }
        });
        btnProcess.setOnClickListener(v -> {
            if (originalBitmap != null) {
                processedBitmap = applyFilmLook(originalBitmap);
                imageView.setImageBitmap(processedBitmap);
                // Reset scale and center the image
                matrix.reset();
                // Calculate center position
                float dx = (imageView.getWidth() - processedBitmap.getWidth()) / 2f;
                float dy = (imageView.getHeight() - processedBitmap.getHeight()) / 2f;
                matrix.postTranslate(dx, dy);
                imageView.setImageMatrix(matrix);
            }
        });
        btnShare.setOnClickListener(v -> {
            if (processedBitmap != null) {
                shareImage(processedBitmap);
            }
        });
        btnSettings.setOnClickListener(v -> {
            EffectsSettingsDialog dialog = EffectsSettingsDialog.newInstance(effectParameters, parameters -> {
                effectParameters = parameters;
                if (originalBitmap != null) {
                    processedBitmap = applyFilmLook(originalBitmap);
                    imageView.setImageBitmap(processedBitmap);
                }
            });
            dialog.show(getSupportFragmentManager(), "effects_settings");
        });
        btnResize.setOnClickListener(v -> {
            if (originalBitmap != null) {
                if (!isCropping) {
                    startCropping();
                } else {
                    applyCrop();
                }
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void chooseImage() {
        if (hasNoPermissions()) {
            requestPermissions();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Picture"));
    }

    private void startCropping() {
        isCropping = true;
        cropOverlayView.setVisibility(View.VISIBLE);
        cropOverlayView.setImageBitmap(originalBitmap);
        btnResize.setImageResource(android.R.drawable.ic_menu_save);
    }

    private void applyCrop() {
        isCropping = false;
        cropOverlayView.setVisibility(View.GONE);
        originalBitmap = cropOverlayView.getCroppedBitmap();
        imageView.setImageBitmap(originalBitmap);
        
        // Reset and center the image
        if (imageView instanceof com.ortiz.touchview.TouchImageView) {
            com.ortiz.touchview.TouchImageView touchImageView = (com.ortiz.touchview.TouchImageView) imageView;
            touchImageView.resetZoom();
            touchImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        
        processedBitmap = null;
        btnResize.setImageResource(android.R.drawable.ic_menu_crop);
    }

    private Bitmap applyFilmLook(Bitmap bitmap) {
        return ImageProcessor.applyFilmLook(bitmap, effectParameters);
    }

    private void shareImage(Bitmap bitmap) {
        try {
            // Create a file in the app's cache directory
            File cachePath = new File(getCacheDir(), "images");
            if (!cachePath.exists() && !cachePath.mkdirs()) {
                Log.e("MainActivity", "Failed to create cache directory");
                Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create unique filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "shared_image_" + timestamp + ".png";
            File file = new File(cachePath, filename);

            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();

            // Create a content URI for the file
            Uri contentUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                file
            );

            // Create and launch the share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share image via"));
        } catch (IOException e) {
            Log.e("MainActivity", "Error sharing image: " + e.getMessage());
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasNoPermissions() {
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
            new String[]{android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},
            PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, request selected photos access
                Intent intent = new Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA);
                mediaAccessLauncher.launch(intent);
            } else {
                Toast.makeText(this, "Permission denied. Some features may not work.", 
                    Toast.LENGTH_SHORT).show();
            }
        }
    }
}
