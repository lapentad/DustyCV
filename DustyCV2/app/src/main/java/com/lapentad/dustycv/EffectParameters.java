package com.lapentad.dustycv;

public class EffectParameters {
    // Film effect parameters
    private float grainIntensity = 5.0f;
    private float halationIntensity = 0.5f;
    private int halationSize = 75;
    private float bloomIntensity = 0.3f;
    private int bloomSize = 45;
    
    // Film simulation type
    public enum FilmType {
        NEUTRAL,       // Neutral film (no specific stock)
        KODAK_50D,     // Kodak Vision3 50D - Daylight, relatively low contrast
        KODAK_200T,    // Kodak Vision3 200T - Tungsten, low contrast
        KODAK_250D,    // Kodak Vision3 250D - Daylight, high contrast
        KODAK_500T     // Kodak Vision3 500T - Tungsten, low contrast for night scenes
    }
    
    private FilmType filmType = FilmType.KODAK_50D;
    
    // Color tint parameters (RGB channels adjustment)
    private float redTint = 1.0f;    // Default is no tint adjustment (1.0)
    private float greenTint = 1.0f;
    private float blueTint = 1.0f;
    
    public float getGrainIntensity() {
        return grainIntensity;
    }

    public void setGrainIntensity(float grainIntensity) {
        this.grainIntensity = grainIntensity;
    }

    public float getHalationIntensity() {
        return halationIntensity;
    }

    public void setHalationIntensity(float halationIntensity) {
        this.halationIntensity = halationIntensity;
    }

    public int getHalationSize() {
        return halationSize;
    }

    public void setHalationSize(int halationSize) {
        this.halationSize = halationSize;
    }

    public float getBloomIntensity() {
        return bloomIntensity;
    }

    public void setBloomIntensity(float bloomIntensity) {
        this.bloomIntensity = bloomIntensity;
    }

    public int getBloomSize() {
        return bloomSize;
    }

    public void setBloomSize(int bloomSize) {
        this.bloomSize = bloomSize;
    }
    
    public FilmType getFilmType() {
        return filmType;
    }
    
    public void setFilmType(FilmType filmType) {
        this.filmType = filmType;
        
        // Adjust tint values based on film type
        switch (filmType) {
            case KODAK_50D:
                // Daylight balanced, slightly warmer shadows
                redTint = 1.05f;
                greenTint = 1.0f;
                blueTint = 0.95f;
                break;
            case KODAK_200T:
                // Tungsten balanced, warm overall look
                redTint = 1.1f;
                greenTint = 1.0f;
                blueTint = 0.9f;
                break;
            case KODAK_250D:
                // Daylight balanced, high contrast, slightly cooler
                redTint = 1.0f;
                greenTint = 1.0f;
                blueTint = 1.05f;
                break;
            case KODAK_500T:
                // Tungsten balanced, higher grain, slightly cyan shadows
                redTint = 0.95f;
                greenTint = 1.05f;
                blueTint = 1.1f;
                break;
            case NEUTRAL:
            default:
                // Reset to neutral
                redTint = 1.0f;
                greenTint = 1.0f;
                blueTint = 1.0f;
                break;
        }
    }
    
    public float getRedTint() {
        return redTint;
    }
    
    public void setRedTint(float redTint) {
        this.redTint = redTint;
    }
    
    public float getGreenTint() {
        return greenTint;
    }
    
    public void setGreenTint(float greenTint) {
        this.greenTint = greenTint;
    }
    
    public float getBlueTint() {
        return blueTint;
    }
    
    public void setBlueTint(float blueTint) {
        this.blueTint = blueTint;
    }
    
    // Get contrast value based on film type
    public float getFilmContrast() {
        switch (filmType) {
            case KODAK_50D:
                return 0.9f;    // Relatively low contrast
            case KODAK_200T:
                return 0.8f;    // Low contrast
            case KODAK_250D:
                return 1.2f;    // High contrast
            case KODAK_500T:
                return 0.8f;    // Low contrast
            case NEUTRAL:
            default:
                return 1.0f;    // Normal contrast
        }
    }
    
    // Get grain adjustment based on film type
    public float getFilmGrainMultiplier() {
        switch (filmType) {
            case KODAK_50D:
                return 0.7f;    // Fine grain
            case KODAK_200T:
                return 1.0f;    // Medium grain
            case KODAK_250D:
                return 1.0f;    // Medium grain
            case KODAK_500T:
                return 1.5f;    // More noticeable grain
            case NEUTRAL:
            default:
                return 1.0f;    // Normal grain
        }
    }
} 