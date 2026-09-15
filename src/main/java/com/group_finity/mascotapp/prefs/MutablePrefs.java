package com.group_finity.mascotapp.prefs;

import com.group_finity.mascot.MascotPrefProvider;

import java.util.LinkedHashMap;
import java.util.Map;

public class MutablePrefs implements MascotPrefProvider, Cloneable {

    public double Scaling = 1;
    public double Opacity = 1;
    public String InteractiveWindows = "";
    public String InteractiveWindowsBlacklist = "";
    public boolean LogicalAnchors = false;
    public boolean AsymmetryNameScheme = false;
    public boolean PixelArtScaling = false;
    public boolean HqxScaling = false;
    public boolean FixRelativeGlobalSound = false;

    public boolean Breeding = true;
    public boolean Transients = true;
    public boolean Transformation = true;
    public boolean Throwing = true;
    public boolean Sounds = true;
    public boolean Multiscreen = true;

    public boolean TranslateBehaviorNames = true;
    public boolean AlwaysShowShimejiChooser = true;
    public boolean AlwaysShowInformationScreen = false;
    public boolean IgnoreImagesetProperties = true;

    public String ShimejiEENameOverride = "";
    public String InformationDismissed = "";
    public String Environment = "";

    /** disabled behaviors per image set, slash separated names (like the original) */
    public Map<String, String> DisabledBehaviours = new LinkedHashMap<>();

    // virtual desktop (window mode) settings
    public String WindowSize = "900x600";
    public String Background = "#FFFFFF";
    public String BackgroundMode = "centre";
    public String BackgroundImage = "";

    @Override public boolean isIEMovementAllowed() { return Throwing; }
    @Override public boolean isBreedingAllowed() { return Breeding; }
    @Override public boolean isTransientBreedingAllowed() { return Transients; }
    @Override public boolean isTransformationAllowed() { return Transformation; }
    @Override public boolean isSoundAllowed() { return Sounds; }
    @Override public boolean isMultiscreenAllowed() { return Multiscreen; }

    @Override
    public boolean isBehaviorEnabled(String imageSet, String behaviorName) {
        var disabled = DisabledBehaviours.get(imageSet);
        if (disabled == null || disabled.isBlank() || behaviorName == null) {
            return true;
        }
        for (String name : disabled.split("/")) {
            if (behaviorName.equals(name.trim())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public MutablePrefs clone() {
        try {
            var cloned = (MutablePrefs) super.clone();
            cloned.DisabledBehaviours = new LinkedHashMap<>(DisabledBehaviours);
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
