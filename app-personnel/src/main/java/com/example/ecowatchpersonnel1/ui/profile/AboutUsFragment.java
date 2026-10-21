package com.example.ecowatchpersonnel1.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ecowatchpersonnel1.R;

/**
 * What EcoWatch Personnel is, and what a cleaner can do with it.
 *
 * <p>Written for the field side of the system rather than copied from the
 * resident app: the cleaner is not filing reports, they are working on the ones
 * residents filed, and the copy says so. Text comes from a string resource so
 * it can be translated alongside the rest of the app.
 */
public class AboutUsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about_us, container, false);

        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        }

        TextView aboutText = view.findViewById(R.id.aboutText);
        if (aboutText != null) {
            aboutText.setText(R.string.about_body);
        }

        return view;
    }
}