package com.lapentad.dustycv;

public class EffectParameters {
    private float grainIntensity = 5.0f;
    private float halationIntensity = 0.5f;
    private int halationSize = 75;
    private float bloomIntensity = 0.3f;
    private int bloomSize = 45;

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
} 