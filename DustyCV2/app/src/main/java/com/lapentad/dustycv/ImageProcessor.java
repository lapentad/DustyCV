package com.lapentad.dustycv;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ImageProcessor {
    private static final String TAG = "ImageProcessor";

    public static Bitmap applyFilmLook(Bitmap bitmap, EffectParameters parameters) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "Invalid bitmap provided to applyFilmLook");
            return null;
        }

        // Ensure bitmap is in a valid format
        Bitmap workingBitmap = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            workingBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }

        try {
            Mat src = new Mat();
            Utils.bitmapToMat(workingBitmap, src);
            
            // Keep original RGBA format
            Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR);

            // Analyze image brightness
            double brightness = getImageBrightness(src);
            double effectStrength = getEffectStrength(brightness);

            // Apply effects with dynamic strength and parameters
            addHalation(src, effectStrength, parameters);
            applyToneCurve(src, effectStrength);
            addSoftBloom(src, effectStrength, parameters);
            addGrain(src, effectStrength * 5, parameters);

            // No need to convert back, keep original format
            Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGBA);
            Bitmap result = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(src, result);

            src.release();
            
            // Clean up if we created a new bitmap
            if (workingBitmap != bitmap) {
                workingBitmap.recycle();
            }
            
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error in applyFilmLook: " + e.getMessage());
            // Clean up if we created a new bitmap
            if (workingBitmap != bitmap) {
                workingBitmap.recycle();
            }
            return bitmap; // Return original bitmap if processing fails
        }
    }

    private static double getImageBrightness(Mat img) {
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
        Scalar mean = Core.mean(gray);
        gray.release();
        return mean.val[0]; // Average brightness
    }

    private static double getEffectStrength(double brightness) {
        // Normalize brightness to 0-1 range
        double normalizedBrightness = brightness / 255.0;
        
        // Use a stronger sigmoid function for more pronounced effects
        return 0.4 + (0.5 / (1.0 + Math.exp(-12.0 * (normalizedBrightness - 0.5))));
    }

    private static void applyToneCurve(Mat img, double strength) {
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2Lab);
        Mat lut = getLut(strength);

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

    private static Mat getLut(double strength) {
        Mat lut = new Mat(1, 256, CvType.CV_8UC1);
        for (int i = 0; i < 256; i++) {
            double x = i / 255.0;

            // Film print characteristic curve
            double y;
            if (x < 0.2) {
                // Rich shadows with detail
                y = x * 0.7;
            } else if (x < 0.5) {
                // Smooth mid tones
                y = 0.14 + (x - 0.2) * 0.8;
            } else if (x < 0.8) {
                // Gentle highlight roll off
                y = 0.38 + (x - 0.5) * 0.9;
            } else {
                // Protected highlights
                y = 0.65 + (x - 0.8) * 0.7;
            }

            // Apply strength with film-like response
            double finalY = x * (1.0 - strength) + y * strength;
            lut.put(0, i, (int)(finalY * 255));
        }
        return lut;
    }

    private static void addGrain(Mat img, double strength, EffectParameters parameters) {
        Mat noise = new Mat(img.size(), CvType.CV_8UC1);
        Core.randn(noise, 128, 20); // Increased noise intensity

        Mat noiseBGR = new Mat();
        Imgproc.cvtColor(noise, noiseBGR, Imgproc.COLOR_GRAY2BGR);

        // Apply grain effect with intensity from parameters
        Core.addWeighted(img, 1.0, noiseBGR, 0.08 * strength * parameters.getGrainIntensity() / 5.0, 0, img);
        
        noise.release();
        noiseBGR.release();
    }

    private static void addHalation(Mat img, double strength, EffectParameters parameters) {
        // Adjustable parameters
        int brightnessThreshold = 200;    // Higher value = only brighter areas affected (0-255)
        int blurKernelSize = parameters.getHalationSize() | 1;  // Ensure odd number
        double redIntensity = 255.0;      // Red color intensity (0-255)
        double effectStrength = parameters.getHalationIntensity();  // Use parameter for strength
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

    private static void addSoftBloom(Mat img, double strength, EffectParameters parameters) {
        // Convert to Lab for better highlight detection
        Mat lab = new Mat();
        Imgproc.cvtColor(img, lab, Imgproc.COLOR_BGR2Lab);
        
        // Split channels
        List<Mat> channels = new ArrayList<>();
        Core.split(lab, channels);
        
        // Create highlight mask from L channel
        Mat highlightMask = new Mat();
        Imgproc.threshold(channels.get(0), highlightMask, 200, 255, Imgproc.THRESH_BINARY);
        
        // Apply Gaussian blur with size from parameters (ensure odd number)
        int bloomSize = parameters.getBloomSize() | 1;
        Mat bloom = new Mat();
        Imgproc.GaussianBlur(highlightMask, bloom, new Size(bloomSize, bloomSize), 0);
        
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
        
        // Blend with original using intensity from parameters
        double effectStrength = strength * parameters.getBloomIntensity();
        Core.addWeighted(imgFloat, 1.0 - effectStrength, bloomEffect, effectStrength, 0, imgFloat);
        
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
} 