package com.example.ecowatchpersonnel1.utils;

import android.content.Context;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.WasteScale;

/**
 * Turns a waste scale into the label shown to the cleaner.
 *
 * <p>The server always speaks the canonical vocabulary — "Small-Scale Waste",
 * "Medium-Scale Waste", "Large-Scale Waste" — because that is what it stores
 * and what it dispatches against. The labels shown in the app may be
 * translated, so the two are kept apart: the canonical value travels on the
 * wire and this class only decides what the cleaner reads.
 */
public final class WasteScaleDisplay {

    private WasteScaleDisplay() {
    }

    /**
     * The translated label for a canonical scale.
     *
     * <p>Anything unrecognised falls back to the small scale, which is the only
     * size a report could have carried before the field existed.
     */
    public static String labelFor(Context context, String rawScale) {
        String scale = WasteScale.canonical(rawScale);
        if (context == null) {
            return scale;
        }
        if (WasteScale.MEDIUM.equals(scale)) {
            return context.getString(R.string.waste_scale_medium);
        }
        if (WasteScale.LARGE.equals(scale)) {
            return context.getString(R.string.waste_scale_large);
        }
        return context.getString(R.string.waste_scale_small);
    }
}
