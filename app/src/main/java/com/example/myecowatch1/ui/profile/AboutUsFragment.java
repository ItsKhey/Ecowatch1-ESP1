package com.example.myecowatch1.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myecowatch1.R;

public class AboutUsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about_us, container, false);

        view.findViewById(R.id.btnBack).setOnClickListener(v -> getParentFragmentManager().popBackStack());

        TextView aboutText = view.findViewById(R.id.aboutText);
        String description = "About EcoWatch 🌱\n\n" +
                "EcoWatch is a community-centered environmental monitoring application designed to help residents take an active role in keeping their surroundings clean, safe, and sustainable. It provides a convenient way for community members to report waste-related concerns, monitor the progress of their reports, and stay informed about environmental activities in their area.\n\n" +
                "EcoWatch connects residents, environmental administrators, and community waste-management personnel in one platform, making it easier to identify environmental problems and coordinate appropriate action.\n\n" +
                "What EcoWatch Can Do\n\n" +
                "🌿 Report Environmental Concerns\n" +
                "Residents can submit reports about improper waste disposal, littering, illegal dumping, overflowing garbage, and other waste-related concerns. Reports can include photos, descriptions, locations, and waste categories to help authorities understand and respond to the problem.\n\n" +
                "📍 Location-Based Reporting\n" +
                "EcoWatch can associate reports with their location, helping administrators and waste-management personnel identify where environmental issues are occurring and prioritize areas that need attention.\n\n" +
                "📋 Track Report Status\n" +
                "Residents can monitor the progress of their submitted reports, such as Pending, Accepted/In Progress, Completed, or Denied, providing transparency and allowing residents to know what happened to their concerns.\n\n" +
                "🔔 Community Notifications\n" +
                "The application can keep residents informed about important updates related to their reports and environmental activities, helping maintain communication between the community and environmental personnel.\n\n" +
                "♻️ Support Proper Waste Management\n" +
                "By organizing reports according to waste categories and locations, EcoWatch helps communities identify recurring waste problems and encourages proper disposal and responsible waste-management practices.\n\n" +
                "👥 Connect the Community with Environmental Personnel\n" +
                "EcoWatch creates a communication bridge between residents and the people responsible for addressing environmental concerns. This can help make waste collection and cleanup efforts more organized, responsive, and community-driven.\n\n" +
                "📊 Environmental Monitoring and Records\n" +
                "Administrators can use collected reports and activity records to observe environmental concerns within the community, identify patterns, and make better decisions regarding waste-management activities.\n\n" +
                "Our Purpose\n\n" +
                "EcoWatch aims to promote the idea that protecting the environment is a shared responsibility. Instead of leaving environmental problems unnoticed, residents are given a simple way to speak up and participate in maintaining their community.\n\n" +
                "“See it. Report it. Help protect it.” 🌎\n\n" +
                "Through technology and community participation, EcoWatch strives to build cleaner communities, more responsible waste-management practices, greater environmental awareness, and a stronger culture of environmental responsibility.";
        
        aboutText.setText(description);

        return view;
    }
}
