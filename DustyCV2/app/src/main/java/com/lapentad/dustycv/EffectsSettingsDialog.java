package com.lapentad.dustycv;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.slider.Slider;

import java.util.Objects;

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

        Spinner filmTypeSpinner = view.findViewById(R.id.spinnerFilmType);
        ImageButton btnFilmInfo = view.findViewById(R.id.btnFilmInfo);
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

        // Set up film info button
        btnFilmInfo.setOnClickListener(v -> showFilmInfoDialog());

        // Set up film type spinner
        String[] filmTypes = {
            getString(R.string.film_type_neutral),
            getString(R.string.film_type_kodak_50d),
            getString(R.string.film_type_kodak_200t),
            getString(R.string.film_type_kodak_250d),
            getString(R.string.film_type_kodak_500t)
        };
        
        ArrayAdapter<String> filmTypeAdapter = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, filmTypes);
        filmTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filmTypeSpinner.setAdapter(filmTypeAdapter);
        
        // Set initial selection based on current film type
        filmTypeSpinner.setSelection(parameters.getFilmType().ordinal());
        
        // Set spinner listener
        filmTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EffectParameters.FilmType selectedType = EffectParameters.FilmType.values()[position];
                parameters.setFilmType(selectedType);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Set initial values for other controls
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

    private void showFilmInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View infoView = getLayoutInflater().inflate(R.layout.dialog_film_info, null);
        
        builder.setView(infoView);
        
        AlertDialog dialog = builder.create();
        
        // Set up close button
        Button btnClose = infoView.findViewById(R.id.btnCloseInfo);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
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
            Objects.requireNonNull(dialog.getWindow()).setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
} 