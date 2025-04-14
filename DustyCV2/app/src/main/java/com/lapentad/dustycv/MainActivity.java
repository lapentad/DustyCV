package com.lapentad.dustycv;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Core;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.graphics.Matrix;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.Color;

public class MainActivity extends AppCompatActivity {

    private Bitmap originalBitmap;
    private Bitmap processedBitmap;
    private ImageView imageView;
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private Matrix matrix = new Matrix();
    private CropOverlayView cropOverlayView;
    private boolean isCropping = false;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnResize;

    static {
        if (!OpenCVLoader.initDebug()) {
            throw new RuntimeException("OpenCV initialization failed");
        } else {
            Log.i("OpenCV", "OpenCV loaded successfully.");
        }
    }

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    try {
                        originalBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                        imageView.setImageBitmap(originalBitmap);
                        processedBitmap = null; // reset processed if user picks new image
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    try {
                        // Get the output file from the crop activity
                        File filesDir = getFilesDir();
                        File[] files = filesDir.listFiles((dir, name) -> name.startsWith("cropped_"));
                        if (files != null && files.length > 0) {
                            // Sort by last modified to get the most recent
                            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                            File croppedFile = files[0];
                            
                            Uri croppedUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", croppedFile);
                            originalBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), croppedUri);
                            imageView.setImageBitmap(originalBitmap);
                            processedBitmap = null; // reset processed if user crops image
                            
                            // Clean up temporary files
                            for (File file : files) {
                                file.delete();
                            }
                            // Clean up temp crop files
                            File[] tempFiles = filesDir.listFiles((dir, name) -> name.startsWith("temp_crop_"));
                            if (tempFiles != null) {
                                for (File file : tempFiles) {
                                    file.delete();
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error loading cropped image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        com.google.android.material.floatingactionbutton.FloatingActionButton btnChoose = findViewById(R.id.btnChoose);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnProcess = findViewById(R.id.btnProcess);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnShare = findViewById(R.id.btnShare);
        btnResize = findViewById(R.id.btnResize);
        imageView = findViewById(R.id.touchImageView);
        cropOverlayView = findViewById(R.id.cropOverlayView);

        // Setup scale gesture detector
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        // Setup touch listener for the image
        imageView.setOnTouchListener((v, event) -> {
            if (isCropping) {
                return cropOverlayView.onTouchEvent(event);
            }
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });

        btnChoose.setOnClickListener(v -> chooseImage());
        btnProcess.setOnClickListener(v -> {
            if (originalBitmap != null) {
                processedBitmap = applyFilmLook2(originalBitmap);
                imageView.setImageBitmap(processedBitmap);
                // Reset scale and center the image
                scaleFactor = 1.0f;
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
        // Reset scale and center the image
        scaleFactor = 1.0f;
        matrix.reset();
        imageView.setImageMatrix(matrix);
        processedBitmap = null;
        btnResize.setImageResource(android.R.drawable.ic_menu_crop);
    }

    private Bitmap applyFilmLook2(Bitmap bitmap) {
        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);
        // Keep original RGBA format
        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR);

        // Analyze image brightness
        double brightness = getImageBrightness(src);
        double effectStrength = getEffectStrength(brightness);

        // Apply effects with dynamic strength
        addHalation(src, effectStrength);
        applyToneCurve(src, effectStrength);
        addSoftBloom(src, effectStrength);
        addGrain(src, effectStrength * 5);

        // No need to convert back, keep original format
        Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGBA);
        Bitmap result = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(src, result);

        src.release();
        return result;
    }

    private double getImageBrightness(Mat img) {
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
        Scalar mean = Core.mean(gray);
        gray.release();
        return mean.val[0]; // Average brightness
    }

    private double getEffectStrength(double brightness) {
        // Normalize brightness to 0-1 range
        double normalizedBrightness = brightness / 255.0;
        
        // Use a stronger sigmoid function for more pronounced effects
        return 0.4 + (0.5 / (1.0 + Math.exp(-12.0 * (normalizedBrightness - 0.5))));
    }
    
    private void applyToneCurve(Mat img, double strength) {
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2Lab);
        // Create LUT for tone curve (must be CV_8UC1 and continuous)
        Mat lut = new Mat(1, 256, CvType.CV_8UC1);
        for (int i = 0; i < 256; i++) {
            double x = i / 255.0;
            
            // Film print characteristic curve
            double y = 0.0;
            if (x < 0.2) {
                // Rich shadows with detail
                y = x * 0.7;
            } else if (x < 0.5) {
                // Smooth midtones
                y = 0.14 + (x - 0.2) * 0.8;
            } else if (x < 0.8) {
                // Gentle highlight rolloff
                y = 0.38 + (x - 0.5) * 0.9;
            } else {
                // Protected highlights
                y = 0.65 + (x - 0.8) * 0.7;
            }
            
            // Apply strength with film-like response
            double finalY = x * (1.0 - strength) + y * strength;
            lut.put(0, i, (int)(finalY * 255));
        }

// Split image into channels
        List<Mat> channels = new ArrayList<>();
        Core.split(img, channels);

// Convert channels to float
        Mat aFloat = new Mat();
        Mat bFloat = new Mat();
        channels.get(1).convertTo(aFloat, CvType.CV_32F);
        channels.get(2).convertTo(bFloat, CvType.CV_32F);

// Get brightness for mask
        Mat lFloat = new Mat();
        channels.get(0).convertTo(lFloat, CvType.CV_32F);
        Core.normalize(lFloat, lFloat, 0, 1, Core.NORM_MINMAX);

// Create warm adjustments
        Mat warmA = new Mat();
        Mat warmB = new Mat();
        Core.multiply(lFloat, new Scalar(2.0), warmA);  // Slight red in highlights
        Core.multiply(lFloat, new Scalar(5.0), warmB);  // Yellow boost in highlights

        Core.add(aFloat, warmA, aFloat);
        Core.add(bFloat, warmB, bFloat);

// Clip values
        Core.min(aFloat, new Scalar(255), aFloat);
        Core.max(aFloat, new Scalar(0), aFloat);
        Core.min(bFloat, new Scalar(255), bFloat);
        Core.max(bFloat, new Scalar(0), bFloat);

// Back to 8U
        aFloat.convertTo(channels.get(1), CvType.CV_8U);
        bFloat.convertTo(channels.get(2), CvType.CV_8U);

// Release temp mats
        lFloat.release();
        warmA.release();
        warmB.release();
        aFloat.release();
        bFloat.release();

// Apply LUT only to L channel (brightness)
        Core.LUT(channels.get(0), lut, channels.get(0));

// Merge and convert back
        Core.merge(channels, img);
        for (Mat ch : channels) ch.release();
        Imgproc.cvtColor(img, img, Imgproc.COLOR_Lab2BGR);
    }

    private void addGrain(Mat img, double strength) {
        Mat noise = new Mat(img.size(), CvType.CV_8UC1);
        Core.randn(noise, 128, 20); // Increased noise intensity

        Mat noiseBGR = new Mat();
        Imgproc.cvtColor(noise, noiseBGR, Imgproc.COLOR_GRAY2BGR);

        // Stronger grain effect
        Core.addWeighted(img, 1.0, noiseBGR, 0.08 * strength, 0, img);
        
        noise.release();
        noiseBGR.release();
    }
    private void addHalation(Mat img, double strength) {
        // Adjustable parameters
        int brightnessThreshold = 200;    // Higher value = only brighter areas affected (0-255)
        int blurKernelSize = 75;          // Larger value = more spread out glow
        double redIntensity = 255.0;      // Red color intensity (0-255)
        double effectStrength = 0.5;      // Overall effect strength (0.0-1.0)
        double warmTone = 0.4;            // Additional warm tone (0.0-1.0)
        
        // Edge detection parameters
        int edgeThreshold1 = 150;         // Lower threshold for edge detection
        int edgeThreshold2 = 255;         // Higher threshold for edge detection
        double edgeBlend = 0.2;           // How much to blend edge detection with brightness (0.0-1.0)
        
        // Color settings (BGR format)
        Scalar redColor = new Scalar(0, 0, redIntensity);         // Pure red
        Scalar warmColor = new Scalar(0, warmTone * 50, 50);     // Warm tint

        // Step 1: Create edge detection mask
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
        
        // Apply Gaussian blur to reduce noise
        Mat blurredGray = new Mat();
        Imgproc.GaussianBlur(gray, blurredGray, new Size(3, 3), 0);
        
        // Detect edges using Canny
        Mat edges = new Mat();
        Imgproc.Canny(blurredGray, edges, edgeThreshold1, edgeThreshold2);
        
        // Dilate edges to make them more visible
        Mat dilatedEdges = new Mat();
        Imgproc.dilate(edges, dilatedEdges, new Mat(), new Point(-1, -1), 2);

        // Step 2: Create brightness mask
        Mat thresholdMask = new Mat();
        Imgproc.threshold(gray, thresholdMask, brightnessThreshold, 255, Imgproc.THRESH_BINARY);

        // Step 3: Combine edge and brightness masks
        Mat combinedMask = new Mat();
        Core.addWeighted(dilatedEdges, edgeBlend, thresholdMask, 1.0 - edgeBlend, 0, combinedMask);

        // Step 4: Apply Gaussian blur to create the halation effect
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(combinedMask, blurred, new Size(blurKernelSize, blurKernelSize), 0);

        // Step 5: Create color layers
        Mat redLayer = new Mat(img.size(), img.type());
        redLayer.setTo(redColor);
        
        Mat warmLayer = new Mat(img.size(), img.type());
        warmLayer.setTo(warmColor);

        // Step 6: Apply the halation effect
        // Convert blurred mask to 3 channels and same type as color layers
        Mat mask3Channel = new Mat();
        List<Mat> channels = new ArrayList<>();
        Mat channel = new Mat();
        blurred.convertTo(channel, CvType.CV_32F);
        channels.add(channel);
        channels.add(channel);
        channels.add(channel);
        Core.merge(channels, mask3Channel);
        
        // Normalize the mask
        Core.normalize(mask3Channel, mask3Channel, 0, 1, Core.NORM_MINMAX, CvType.CV_32F);

        // Convert color layers to same type as mask
        Mat redLayerFloat = new Mat();
        redLayer.convertTo(redLayerFloat, CvType.CV_32F);
        
        Mat warmLayerFloat = new Mat();
        warmLayer.convertTo(warmLayerFloat, CvType.CV_32F);

        // Create the halation effect by blending
        Mat halation = new Mat();
        Core.multiply(redLayerFloat, mask3Channel, halation, 1.0, CvType.CV_32F);
        
        // Add warm tone
        Mat warmEffect = new Mat();
        Core.multiply(warmLayerFloat, mask3Channel, warmEffect, 1.0, CvType.CV_32F);
        Core.add(halation, warmEffect, halation);

        // Convert halation back to original type
        Mat halationConverted = new Mat();
        halation.convertTo(halationConverted, img.type());

        // Step 7: Blend with original image
        Core.addWeighted(img, 1.0, halationConverted, effectStrength * strength, 0, img);

        // Clean up
        gray.release();
        blurredGray.release();
        edges.release();
        dilatedEdges.release();
        thresholdMask.release();
        combinedMask.release();
        blurred.release();
        redLayer.release();
        warmLayer.release();
        mask3Channel.release();
        redLayerFloat.release();
        warmLayerFloat.release();
        halation.release();
        warmEffect.release();
        halationConverted.release();
        channel.release();
        for (Mat ch : channels) {
            ch.release();
        }
    }

    private void addSoftBloom(Mat img, double strength) {
        // Convert to Lab for better highlight detection
        Mat lab = new Mat();
        Imgproc.cvtColor(img, lab, Imgproc.COLOR_BGR2Lab);
        
        // Split channels
        List<Mat> channels = new ArrayList<>();
        Core.split(lab, channels);
        
        // Create highlight mask from L channel
        Mat highlightMask = new Mat();
        Imgproc.threshold(channels.get(0), highlightMask, 200, 255, Imgproc.THRESH_BINARY);
        
        // Apply Gaussian blur with odd kernel size
        Mat bloom = new Mat();
        Imgproc.GaussianBlur(highlightMask, bloom, new Size(45, 45), 0);
        
        // Normalize bloom
        Core.normalize(bloom, bloom, 0, 1, Core.NORM_MINMAX, CvType.CV_32F);
        
        // Create 3-channel bloom mask
        Mat bloomMask = new Mat();
        List<Mat> bloomChannels = new ArrayList<>();
        bloomChannels.add(bloom);
        bloomChannels.add(bloom);
        bloomChannels.add(bloom);
        Core.merge(bloomChannels, bloomMask);
        
        // Convert original image to float
        Mat imgFloat = new Mat();
        img.convertTo(imgFloat, CvType.CV_32F);
        
        // Create bloom effect
        Mat bloomEffect = new Mat();
        Core.multiply(imgFloat, bloomMask, bloomEffect);
        
        // Blend with original
        Core.addWeighted(imgFloat, 1.0, bloomEffect, strength * 0.3, 0, imgFloat);
        
        // Convert back to original type
        imgFloat.convertTo(img, img.type());
        
        // Clean up
        lab.release();
        highlightMask.release();
        bloom.release();
        bloomMask.release();
        imgFloat.release();
        bloomEffect.release();
        for (Mat ch : channels) {
            ch.release();
        }
        for (Mat ch : bloomChannels) {
            ch.release();
        }
    }

    private void protectHighlights(Mat img) {
        // Create a highlight mask
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
        
        // Find bright areas
        Mat highlightMask = new Mat();
        Imgproc.threshold(gray, highlightMask, 220, 255, Imgproc.THRESH_BINARY);
        
        // Dilate the mask to protect areas around highlights
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.dilate(highlightMask, highlightMask, kernel);
        
        // Convert mask to 3 channels
        Mat mask3Channel = new Mat();
        List<Mat> channels = new ArrayList<>();
        channels.add(highlightMask);
        channels.add(highlightMask);
        channels.add(highlightMask);
        Core.merge(channels, mask3Channel);
        
        // Normalize the mask
        Core.normalize(mask3Channel, mask3Channel, 0, 1, Core.NORM_MINMAX, CvType.CV_32F);
        
        // Create a copy of the original image
        Mat original = new Mat();
        img.copyTo(original);
        
        // Blend the original highlights back
        Core.multiply(img, mask3Channel, img, 1.0, CvType.CV_32F);
        Core.multiply(original, new Scalar(1, 1, 1), original, 1.0, CvType.CV_32F);
        Core.addWeighted(img, 1.0, original, 1.0, 0, img);
        
        // Clean up
        gray.release();
        highlightMask.release();
        mask3Channel.release();
        original.release();
        kernel.release();
        for (Mat ch : channels) {
            ch.release();
        }
    }

    private void shareImage(Bitmap bitmap) {
        try {
            // Create a file in the app's cache directory
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "shared_image.png");
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
            e.printStackTrace();
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show();
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f)); // Limit scale
            matrix.setScale(scaleFactor, scaleFactor);
            // Center the scaled image
            float scaledWidth = imageView.getDrawable().getIntrinsicWidth() * scaleFactor;
            float scaledHeight = imageView.getDrawable().getIntrinsicHeight() * scaleFactor;
            float dx = (imageView.getWidth() - scaledWidth) / 2;
            float dy = (imageView.getHeight() - scaledHeight) / 2;
            matrix.postTranslate(dx, dy);
            imageView.setImageMatrix(matrix);
            return true;
        }
    }
}
