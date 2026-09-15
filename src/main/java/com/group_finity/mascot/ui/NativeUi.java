package com.group_finity.mascot.ui;

import com.group_finity.mascot.imageset.ShimejiProgramFolder;
import com.group_finity.mascotapp.prefs.MutablePrefs;

import java.util.Collection;

public interface NativeUi {

    void showError(String message);

    void start(MutablePrefs prefs, Runnable onFinish);

    void reload();

    void requestImageSetChooser(Collection<String> currentSelection, ShimejiProgramFolder pf);

    /**
     * Asks the user a yes/no question.
     *
     * @return true when the user answers yes.
     */
    default boolean askYesNo(String title, String message) {
        return false;
    }

}
