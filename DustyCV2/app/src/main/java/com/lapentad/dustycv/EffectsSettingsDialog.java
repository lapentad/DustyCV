package com.lapentad.dustycv;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.slider.Slider;

public class EffectsSettingsDialog extends DialogFragment {
    private EffectParameters parameters;
    private OnEffectsAppliedListener listener;

    public interface OnEffectsAppliedListener {
        void onEffectsApplied(EffectParameters parameters);
    }

    public static EffectsSettingsDialog newInstance(EffectParameters parameters, OnEffectsAppliedListener listener) {
        EffectsSettingsDialog dialog = new EffectsSettingsDialog();
        dialog.parameters = parameters;
        dialog.listener = listener;
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_effects_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Slider grainIntensitySlider = view.findViewById(R.id.sliderGrainIntensity);
        Slider halationIntensitySlider = view.findViewById(R.id.sliderHalationIntensity);
        Slider halationSizeSlider = view.findViewById(R.id.sliderHalationSize);
        Slider bloomIntensitySlider = view.findViewById(R.id.sliderBloomIntensity);
        Slider bloomSizeSlider = view.findViewById(R.id.sliderBloomSize);
        Button applyButton = view.findViewById(R.id.btnApply);

        TextView textGrainIntensity = view.findViewById(R.id.textGrainIntensity);
        TextView textHalationIntensity = view.findViewById(R.id.textHalationIntensity);
        TextView textHalationSize = view.findViewById(R.id.textHalationSize);
        TextView textBloomIntensity = view.findViewById(R.id.textBloomIntensity);
        TextView textBloomSize = view.findViewById(R.id.textBloomSize);

        // Set initial values
        grainIntensitySlider.setValue(parameters.getGrainIntensity());
        halationIntensitySlider.setValue(parameters.getHalationIntensity());
        halationSizeSlider.setValue(parameters.getHalationSize());
        bloomIntensitySlider.setValue(parameters.getBloomIntensity());
        bloomSizeSlider.setValue(parameters.getBloomSize());

        // Update text views with initial values
        updateTextView(textGrainIntensity, R.string.grain_intensity_format, parameters.getGrainIntensity());
        updateTextView(textHalationIntensity, R.string.halation_intensity_format, parameters.getHalationIntensity());
        updateTextView(textHalationSize, R.string.halation_size_format, parameters.getHalationSize());
        updateTextView(textBloomIntensity, R.string.bloom_intensity_format, parameters.getBloomIntensity());
        updateTextView(textBloomSize, R.string.bloom_size_format, parameters.getBloomSize());

        // Add listeners
        grainIntensitySlider.addOnChangeListener((slider, value, fromUser) -> {
            parameters.setGrainIntensity(value);
            updateTextView(textGrainIntensity, R.string.grain_intensity_format, value);
        });
        
        halationIntensitySlider.addOnChangeListener((slider, value, fromUser) -> {
            parameters.setHalationIntensity(value);
            updateTextView(textHalationIntensity, R.string.halation_intensity_format, value);
        });
        
        halationSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            parameters.setHalationSize((int) value);
            updateTextView(textHalationSize, R.string.halation_size_format, (int) value);
        });
        
        bloomIntensitySlider.addOnChangeListener((slider, value, fromUser) -> {
            parameters.setBloomIntensity(value);
            updateTextView(textBloomIntensity, R.string.bloom_intensity_format, value);
        });
        
        bloomSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            parameters.setBloomSize((int) value);
            updateTextView(textBloomSize, R.string.bloom_size_format, (int) value);
        });

        applyButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEffectsApplied(parameters);
            }
            dismiss();
        });
    }

    private void updateTextView(TextView textView, int formatResId, float value) {
        String format = getString(formatResId);
        textView.setText(String.format(format, value));
    }

    private void updateTextView(TextView textView, int formatResId, int value) {
        String format = getString(formatResId);
        textView.setText(String.format(format, value));
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
} 