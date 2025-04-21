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

            // Apply film type color adjustments
            applyFilmColorBalance(src, parameters);
            
            // Apply effects with dynamic strength and parameters
            addHalation(src, effectStrength, parameters);
            applyToneCurve(src, effectStrength, parameters);
            addSoftBloom(src, effectStrength, parameters);
            
            // Apply grain with film-specific adjustments
            float grainMultiplier = parameters.getFilmGrainMultiplier();
            addGrain(src, effectStrength * grainMultiplier * 4.0, parameters);

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

    private static void applyFilmColorBalance(Mat img, EffectParameters parameters) {
        // Split the image into color channels
        List<Mat> channels = new ArrayList<>();
        Core.split(img, channels);
        
        // Get the color adjustments from parameters
        float redTint = parameters.getRedTint();
        float greenTint = parameters.getGreenTint();
        float blueTint = parameters.getBlueTint();
        
        // Apply the film-specific color adjustments to each channel
        Mat adjustedB = new Mat();
        Mat adjustedG = new Mat();
        Mat adjustedR = new Mat();
        
        // OpenCV uses BGR order
        Core.multiply(channels.get(0), new Scalar(blueTint), adjustedB);
        Core.multiply(channels.get(1), new Scalar(greenTint), adjustedG);
        Core.multiply(channels.get(2), new Scalar(redTint), adjustedR);
        
        // Merge the adjusted channels back
        channels.set(0, adjustedB);
        channels.set(1, adjustedG);
        channels.set(2, adjustedR);
        Core.merge(channels, img);
        
        // Clean up
        adjustedB.release();
        adjustedG.release();
        adjustedR.release();
        for (Mat ch : channels) {
            ch.release();
        }
    }

    private static void applyToneCurve(Mat img, double strength, EffectParameters parameters) {
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2Lab);
        
        // Get film-specific contrast
        float filmContrast = parameters.getFilmContrast();
        
        Mat lut = getLut(strength, filmContrast);

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

        // Create warm adjustments based on film type
        Mat warmA = new Mat();
        Mat warmB = new Mat();
        
        float redTint = parameters.getRedTint();
        float blueTint = parameters.getBlueTint();
        
        // Adjust a and b channels based on film type (a: red-green, b: yellow-blue)
        float aAdjust = (redTint - 1.0f) * 10.0f;
        float bAdjust = (1.0f - blueTint) * 10.0f;
        
        Core.multiply(lFloat, new Scalar(aAdjust), warmA);  // Adjust red/green
        Core.multiply(lFloat, new Scalar(bAdjust), warmB);  // Adjust yellow/blue

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

    private static Mat getLut(double strength, float contrastFactor) {
        Mat lut = new Mat(1, 256, CvType.CV_8UC1);
        for (int i = 0; i < 256; i++) {
            double x = i / 255.0;

            // Film print characteristic curve (with contrast adjustment)
            double y;
            if (x < 0.2) {
                // Rich shadows with detail
                y = x * (0.7 * contrastFactor);
            } else if (x < 0.5) {
                // Smooth mid tones
                y = 0.14 + (x - 0.2) * (0.8 * contrastFactor);
            } else if (x < 0.8) {
                // Gentle highlight roll off
                y = 0.38 + (x - 0.5) * (0.9 * contrastFactor);
            } else {
                // Protected highlights
                y = 0.65 + (x - 0.8) * (0.7 * contrastFactor);
            }

            // Apply strength with film-like response
            double finalY = x * (1.0 - strength) + y * strength;
            lut.put(0, i, (int)(finalY * 255));
        }
        return lut;
    }

    private static void addGrain(Mat img, double strength, EffectParameters parameters) {
        // Calculate the image area
        double imageArea = img.rows() * img.cols();
        
        // Define a base area for normalization (e.g., 1 megapixel)
        double baseArea = 1_000_000.0;
        
        // Calculate the scaling factor based on the image area
        double scaleFactor = Math.sqrt(imageArea / baseArea);
        
        // Get parameters and adjust for image size
        float grainIntensity = parameters.getGrainIntensity();
        
        // Create grayscale version of the image for grain modulation
        Mat luminance = new Mat();
        Imgproc.cvtColor(img, luminance, Imgproc.COLOR_BGR2GRAY);
        
        // Create grain with adjustable intensity - lower standard deviation for more subtle effect
        Mat noise = new Mat(img.size(), CvType.CV_8UC1);
        double stdDev = 15 + (grainIntensity / 15.0) * 15;  // Range from 15 to 30
        Core.randn(noise, 128, stdDev);
        
        // Optional: For very high grain settings, add some structure
        if (grainIntensity > 10.0) {
            // Create structured grain for higher intensities
            Mat structuredNoise = new Mat(img.size(), CvType.CV_8UC1);
            Core.randn(structuredNoise, 128, stdDev * 1.5);
            
            // Threshold to create clumps
            Mat thresholdedNoise = new Mat();
            Imgproc.threshold(structuredNoise, thresholdedNoise, 170, 255, Imgproc.THRESH_BINARY);
            
            // Blend with base noise
            double structureFactor = (grainIntensity - 10.0) / 5.0; // 0.0 to 1.0
            Core.addWeighted(noise, 1.0 - structureFactor * 0.5, thresholdedNoise, structureFactor * 0.5, 0, noise);
            
            structuredNoise.release();
            thresholdedNoise.release();
        }
        
        // Create luminance-dependent masks (simplified from previous version)
        Mat shadowMask = new Mat();
        Mat highlightMask = new Mat();
        
        // Use adaptive thresholds for more natural transitions
        Imgproc.threshold(luminance, shadowMask, 60, 1.0, Imgproc.THRESH_BINARY_INV);
        Imgproc.threshold(luminance, highlightMask, 200, 1.0, Imgproc.THRESH_BINARY);
        
        // Convert to floating point for calculations
        shadowMask.convertTo(shadowMask, CvType.CV_32F);
        highlightMask.convertTo(highlightMask, CvType.CV_32F);
        
        // Normalize noise to -1 to 1 range for proper overlay
        Mat normalizedNoise = new Mat();
        noise.convertTo(normalizedNoise, CvType.CV_32F);
        Core.subtract(normalizedNoise, new Scalar(128), normalizedNoise);
        Core.divide(normalizedNoise, new Scalar(128), normalizedNoise);
        
        // Split image into channels
        List<Mat> channels = new ArrayList<>();
        Core.split(img, channels);
        
        // CRITICAL: Use much more subtle grain intensity factor
        // This is the main fix for the "burning" effect
        double baseIntensity = 0.02 * strength * Math.min(grainIntensity, 10) / Math.sqrt(scaleFactor);
        
        // Apply grain to each channel identically (monochrome grain)
        for (int i = 0; i < channels.size(); i++) {
            Mat channel = channels.get(i);
            Mat channelF = new Mat();
            channel.convertTo(channelF, CvType.CV_32F);
            
            // Apply grain with luminance modulation (more in shadows, less in highlights)
            Mat grainMask = new Mat(channelF.size(), CvType.CV_32F);
            
            // Base grain amount
            double shadowGrainAmount = baseIntensity * 1.5; // More grain in shadows
            double midtoneGrainAmount = baseIntensity;      // Normal grain in midtones
            double highlightGrainAmount = baseIntensity * 0.7; // Less grain in highlights
            
            // Create shadow grain component
            Mat shadowGrain = new Mat();
            Core.multiply(normalizedNoise, new Scalar(shadowGrainAmount), shadowGrain);
            Core.multiply(shadowGrain, shadowMask, shadowGrain);
            
            // Create highlight grain component
            Mat highlightGrain = new Mat();
            Core.multiply(normalizedNoise, new Scalar(highlightGrainAmount), highlightGrain);
            Core.multiply(highlightGrain, highlightMask, highlightGrain);
            
            // Create midtone grain component (areas that are neither shadows nor highlights)
            Mat midtoneMask = new Mat(shadowMask.size(), CvType.CV_32F, new Scalar(1.0));
            Core.subtract(midtoneMask, shadowMask, midtoneMask);
            Core.subtract(midtoneMask, highlightMask, midtoneMask);
            
            Mat midtoneGrain = new Mat();
            Core.multiply(normalizedNoise, new Scalar(midtoneGrainAmount), midtoneGrain);
            Core.multiply(midtoneGrain, midtoneMask, midtoneGrain);
            
            // Combine all grain components
            Mat combinedGrain = new Mat(channelF.size(), CvType.CV_32F, new Scalar(0));
            Core.add(combinedGrain, shadowGrain, combinedGrain);
            Core.add(combinedGrain, midtoneGrain, combinedGrain);
            Core.add(combinedGrain, highlightGrain, combinedGrain);
            
            // Apply grain using overlay blend mode simulation for more natural look
            // This prevents the burning effect by preserving image contrast
            Mat result = new Mat();
            
            // Overlay blend mode: if grain < 0, result = 2 * channel * grain
            //                      if grain >= 0, result = 1 - 2 * (1 - channel) * (1 - grain)
            // Simplified for small grain values to: channel + channel * grain
            Core.multiply(channelF, combinedGrain, result);
            Core.add(channelF, result, channelF);
            
            // Convert back to 8-bit and update channel
            channelF.convertTo(channel, CvType.CV_8UC1);
            
            // Ensure we don't exceed valid pixel range
            Core.min(channel, new Scalar(255), channel);
            Core.max(channel, new Scalar(0), channel);
            
            // Update the channel
            channels.set(i, channel);
            
            // Clean up
            channelF.release();
            shadowGrain.release();
            midtoneGrain.release();
            highlightGrain.release();
            midtoneMask.release();
            combinedGrain.release();
            result.release();
        }
        
        // Merge channels back
        Core.merge(channels, img);
        
        // Clean up
        luminance.release();
        noise.release();
        normalizedNoise.release();
        shadowMask.release();
        highlightMask.release();
        
        // Release channel mats
        for (Mat ch : channels) {
            ch.release();
        }
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
        
        // Create highlight mask from L channel (lower threshold for more highlights)
        Mat highlightMask = new Mat();
        Imgproc.threshold(channels.get(0), highlightMask, 180, 255, Imgproc.THRESH_BINARY);
        
        // Dilate highlights to make them more pronounced
        Mat dilated = new Mat();
        Imgproc.dilate(highlightMask, dilated, new Mat(), new Point(-1, -1), 2);
        
        // First pass: Small blur for detail
        Mat bloomPass1 = new Mat();
        Imgproc.GaussianBlur(dilated, bloomPass1, new Size(15, 15), 0);
        
        // Second pass: Larger blur for spread
        Mat bloomPass2 = new Mat();
        Imgproc.GaussianBlur(bloomPass1, bloomPass2, new Size(45, 45), 0);
        
        // Normalize bloom
        Core.normalize(bloomPass2, bloomPass2, 0, 1, Core.NORM_MINMAX, CvType.CV_32F);
        
        // Create Mats filled with scalar values for multiplication
        Mat greenScalar = new Mat(bloomPass2.size(), bloomPass2.type(), new Scalar(0.8));
        Mat blueScalar = new Mat(bloomPass2.size(), bloomPass2.type(), new Scalar(0.6));
        
        // Create 3-channel bloom mask with warm tint (yellow/orange)
        Mat bloomMask = new Mat();
        List<Mat> bloomChannels = new ArrayList<>();
        bloomChannels.add(bloomPass2);
        Mat greenChannel = new Mat();
        Core.multiply(bloomPass2, greenScalar, greenChannel);
        bloomChannels.add(greenChannel);  // Reduce green
        Mat blueChannel = new Mat();
        Core.multiply(bloomPass2, blueScalar, blueChannel);
        bloomChannels.add(blueChannel);   // Reduce blue (warmer tint)
        Core.merge(bloomChannels, bloomMask);
        
        // Convert original image to float
        Mat imgFloat = new Mat();
        img.convertTo(imgFloat, CvType.CV_32F);
        
        // Create bloom effect with direct addition (stronger effect)
        Mat bloomEffect = new Mat();
        Core.multiply(imgFloat, bloomMask, bloomEffect);
        
        // Blend with original using intensity from parameters
        double effectStrength = strength * parameters.getBloomIntensity() * 2.5;  // Increased multiplier
        Core.addWeighted(imgFloat, 1.0, bloomEffect, effectStrength, 0, imgFloat);
        // Convert back to original type
        imgFloat.convertTo(img, img.type());

        // Clean up
        lab.release();
        highlightMask.release();
        dilated.release();
        bloomPass1.release();
        bloomPass2.release();
        bloomMask.release();
        imgFloat.release();
        bloomEffect.release();
        greenScalar.release();
        blueScalar.release();
        greenChannel.release();
        blueChannel.release();
        for (Mat ch : channels) ch.release();
        for (Mat ch : bloomChannels) ch.release();
    }
}