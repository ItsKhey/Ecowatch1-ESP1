package com.example.myecowatch1.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.ui.auth.LoginActivity;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.ui.home.DashboardActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class ProfileFragment extends Fragment {

    /** Words the resident must type exactly to confirm each account action. */
    private static final String DISABLE_CONFIRM_WORD = "Disable";
    private static final String DELETE_CONFIRM_WORD = "Delete";

    private AuthRepository authRepository;
    private ImageView profileImage;
    private TextView residentIdText, profileName, profileUsername, profileEmail, profileAddress;
    private TextView profilePhoneNumber, profileMessengerLink;
    private MaterialButton editProfileBtn, disableAccountBtn, deleteAccountBtn;
    private SwipeRefreshLayout swipeRefreshLayout;
    private User currentUser;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    startCrop(imageUri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        uploadProfileImage(resultUri);
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR) {
                    Throwable cropError = UCrop.getError(result.getData());
                    if (cropError != null) {
                        Toast.makeText(getContext(), cropError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        authRepository = new AuthRepository(requireActivity().getApplication());

        profileImage = view.findViewById(R.id.profileImage);
        residentIdText = view.findViewById(R.id.residentIdText);
        profileName = view.findViewById(R.id.profileName);
        profileUsername = view.findViewById(R.id.profileUsername);
        profileEmail = view.findViewById(R.id.profileEmail);
        profileAddress = view.findViewById(R.id.profileAddress);
        profilePhoneNumber = view.findViewById(R.id.profilePhoneNumber);
        profileMessengerLink = view.findViewById(R.id.profileMessengerLink);
        editProfileBtn = view.findViewById(R.id.editProfileBtn);
        disableAccountBtn = view.findViewById(R.id.disableAccountBtn);
        deleteAccountBtn = view.findViewById(R.id.deleteAccountBtn);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        view.findViewById(R.id.btnBack).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        
        View editImageBtn = view.findViewById(R.id.editProfileImageBtn);
        View.OnClickListener pickImageListener = v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        };
        
        profileImage.setOnClickListener(pickImageListener);
        if (editImageBtn != null) {
            editImageBtn.setOnClickListener(pickImageListener);
        }

        swipeRefreshLayout.setOnRefreshListener(() -> {
            authRepository.fetchMe(new AuthRepository.ApiCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    swipeRefreshLayout.setRefreshing(false);
                }

                @Override
                public void onError(String error) {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getContext(), "Sync failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        authRepository.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                currentUser = user;
                bindUser();
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        editProfileBtn.setOnClickListener(v -> showEditProfileDialog());

        if (disableAccountBtn != null) {
            disableAccountBtn.setOnClickListener(v -> confirmDisableAccount());
        }

        if (deleteAccountBtn != null) {
            deleteAccountBtn.setOnClickListener(v -> confirmDeleteAccount());
        }

        return view;
    }

    /**
     * Paints the cached account onto the three cards.
     *
     * <p>Called from the user observer and again straight after a profile save,
     * so the screen reflects a pasted messenger link without waiting for the
     * next database or network read.
     */
    private void bindUser() {
        User user = currentUser;
        if (user == null) return;

        residentIdText.setText("RESIDENT ID: " + user.getServerId());
        profileName.setText(user.getFirstName() + " " + user.getLastName());
        profileUsername.setText(user.getUsername());
        profileEmail.setText(user.getEmail());
        profileAddress.setText(orPlaceholder(user.getAddress(), "No address set"));
        profilePhoneNumber.setText(orPlaceholder(user.getPhoneNumber(), "No phone number set"));
        profileMessengerLink.setText(orPlaceholder(user.getMessengerLink(), "No Messenger link set"));

        // A stored link is shown on its own line so a long m.me URL stays
        // readable instead of being clipped by the card's width.
        profileMessengerLink.setSingleLine(false);

        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(ServerConfig.absoluteUrl(requireContext(), user.getProfileImageUrl()))
                    .placeholder(R.drawable.pfp)
                    .into(profileImage);
        } else {
            profileImage.setImageResource(R.drawable.pfp);
        }
    }

    private String orPlaceholder(String value, String placeholder) {
        return value == null || value.trim().isEmpty() ? placeholder : value;
    }

    /**
     * Pausing is reversible, so the dialog states plainly that nothing is
     * deleted and that signing in again brings the same account back.
     */
    private void confirmDisableAccount() {
        if (currentUser == null) {
            Toast.makeText(getContext(), "Profile is still loading. Try again in a moment.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showTypeToConfirmDialog(
                "Disable Account?",
                "Your account will be paused. You can restore your profile, reports, "
                        + "history, and settings by logging in again.",
                DISABLE_CONFIRM_WORD,
                this::disableAccount);
    }

    private void disableAccount() {
        setAccountActionInProgress(true);

        authRepository.disableAccount(new AuthRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;
                setAccountActionInProgress(false);
                signOutAndReturnToLogin("Account disabled. Sign in again to restore it.");
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                setAccountActionInProgress(false);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Deletion is permanent, so the confirmation states the consequence in the
     * same words the resident is agreeing to.
     */
    private void confirmDeleteAccount() {
        if (currentUser == null) {
            Toast.makeText(getContext(), "Profile is still loading. Try again in a moment.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showTypeToConfirmDialog(
                "Delete Account Permanently?",
                "Once deletion finishes, you cannot recover this account.",
                DELETE_CONFIRM_WORD,
                this::deleteAccount);
    }

    private void deleteAccount() {
        setAccountActionInProgress(true);

        authRepository.deleteAccount(new AuthRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;
                setAccountActionInProgress(false);
                signOutAndReturnToLogin("Your account was deleted.");
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                setAccountActionInProgress(false);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Builds a confirmation the resident has to finish by typing {@code
     * requiredWord}. The confirm button starts disabled and only unlocks on an
     * exact, case-sensitive match, so a stray tap, a partial word, or a
     * neighbouring key on the keyboard can never reach a destructive action.
     *
     * The button is part of the dialog view rather than an AlertDialog button on
     * purpose: an AlertDialog positive button always dismisses the dialog and
     * cannot be held in a disabled state while the field is being typed.
     */
    private void showTypeToConfirmDialog(String title, String message,
                                         String requiredWord, Runnable onConfirmed) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_type_to_confirm, null);
        TextView titleTv = dialogView.findViewById(R.id.typeToConfirmTitle);
        TextView messageTv = dialogView.findViewById(R.id.typeToConfirmMessage);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.typeToConfirmInputLayout);
        TextInputEditText input = dialogView.findViewById(R.id.typeToConfirmInput);
        MaterialButton confirmBtn = dialogView.findViewById(R.id.typeToConfirmBtn);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.typeToConfirmCancelBtn);

        titleTv.setText(title);
        messageTv.setText(message);
        inputLayout.setHint("Type \"" + requiredWord + "\" to continue");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        confirmBtn.setEnabled(false);
        confirmBtn.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirmed.run();
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // contentEquals is an exact comparison, so "delete", "Delete "
                // and "Delete!" all stay locked.
                confirmBtn.setEnabled(requiredWord.contentEquals(s));
            }
        });

        dialog.show();
        input.requestFocus();
    }

    /**
     * Disables both account actions for the duration of a request so a second
     * tap cannot start a duplicate destructive call.
     */
    private void setAccountActionInProgress(boolean inProgress) {
        if (disableAccountBtn != null) disableAccountBtn.setEnabled(!inProgress);
        if (deleteAccountBtn != null) deleteAccountBtn.setEnabled(!inProgress);
    }

    /**
     * Ends the session and shows the login screen. The dashboard owns this
     * transition so the account lifecycle actions behave exactly like an
     * ordinary logout; the fallback keeps the screen correct if it is ever
     * hosted somewhere else.
     */
    private void signOutAndReturnToLogin(String message) {
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).signOutAndReturnToLogin(message);
            return;
        }

        authRepository.logout();
        if (message != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) getActivity().finish();
    }

    private void showEditProfileDialog() {
        if (currentUser == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_profile, null);
        TextInputEditText editFirstName = dialogView.findViewById(R.id.editFirstName);
        TextInputEditText editLastName = dialogView.findViewById(R.id.editLastName);
        TextInputEditText editUsername = dialogView.findViewById(R.id.editUsername);
        TextInputEditText editEmail = dialogView.findViewById(R.id.editEmail);
        TextInputEditText editAddress = dialogView.findViewById(R.id.editAddress);
        TextInputEditText editPhoneNumber = dialogView.findViewById(R.id.editPhoneNumber);
        TextInputEditText editMessengerLink = dialogView.findViewById(R.id.editMessengerLink);
        TextInputLayout editPhoneNumberLayout = dialogView.findViewById(R.id.editPhoneNumberLayout);
        TextInputLayout editMessengerLinkLayout = dialogView.findViewById(R.id.editMessengerLinkLayout);

        // Pre-fill
        editFirstName.setText(currentUser.getFirstName());
        editLastName.setText(currentUser.getLastName());
        editUsername.setText(currentUser.getUsername());
        editEmail.setText(currentUser.getEmail());
        editAddress.setText(currentUser.getAddress());
        editPhoneNumber.setText(currentUser.getPhoneNumber());
        editMessengerLink.setText(currentUser.getMessengerLink());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Update Profile")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        // Save is wired after show() so a validation failure can keep the dialog
        // open; the default listener always dismisses before it runs.
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String fName = value(editFirstName);
                    String lName = value(editLastName);
                    String username = value(editUsername);
                    String email = value(editEmail);
                    String address = value(editAddress);
                    String phoneNumber = value(editPhoneNumber);
                    String messengerLink = value(editMessengerLink);

                    editPhoneNumberLayout.setError(null);
                    editMessengerLinkLayout.setError(null);

                    if (fName.isEmpty() || lName.isEmpty() || username.isEmpty() || email.isEmpty()) {
                        Toast.makeText(getContext(), "Name, Username and Email are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Mirrors the server constraints so the resident is told here
                    // instead of getting a generic failure toast.
                    if (phoneNumber.isEmpty()) {
                        editPhoneNumberLayout.setError("Phone number is required.");
                        return;
                    }
                    if (!phoneNumber.matches("^[0-9+() .-]{7,25}$")) {
                        editPhoneNumberLayout.setError("Use digits, spaces, and + ( ) - only");
                        return;
                    }
                    if (!messengerLink.isEmpty() && !messengerLink.matches("^https?://\\S+$")) {
                        editMessengerLinkLayout.setError("Enter a link starting with http:// or https://");
                        return;
                    }

                    authRepository.updateProfile(fName, lName, username, email, address,
                            phoneNumber, messengerLink, new AuthRepository.ApiCallback<User>() {
                                @Override
                                public void onSuccess(User result) {
                                    if (result == null) return;
                                    currentUser = result;
                                    if (isAdded()) bindUser();
                                    Toast.makeText(getContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(String error) {
                                    if (!isAdded()) return;
                                    // Detailed error handling to debug "next is not a function"
                                    String msg = error.contains("next") ? "Server configuration error. Please contact admin." : error;
                                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                                }
                            });

                    dialog.dismiss();
                }));

        dialog.show();
    }

    /** Trims a field without throwing on null, which an empty EditText returns. */
    private String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void startCrop(Uri sourceUri) {
        String destinationFileName = "cropped_pfp.jpg";
        UCrop uCrop = UCrop.of(sourceUri, Uri.fromFile(new File(requireContext().getCacheDir(), destinationFileName)));
        uCrop.withAspectRatio(1, 1);
        uCrop.withMaxResultSize(400, 400); // Reduced size for better compatibility
        
        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropGrid(false);
        options.setShowCropFrame(false);
        options.setToolbarTitle("Crop Profile Picture");
        options.setToolbarColor(getResources().getColor(R.color.green, null));
        options.setStatusBarColor(getResources().getColor(R.color.green, null));
        options.setToolbarWidgetColor(getResources().getColor(R.color.white, null));
        options.setActiveControlsWidgetColor(getResources().getColor(R.color.green, null));
        
        uCrop.withOptions(options);

        cropImageLauncher.launch(uCrop.getIntent(requireContext()));
    }

    private void uploadProfileImage(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), uri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
            byte[] imageBytes = baos.toByteArray();

            Toast.makeText(getContext(), "Uploading...", Toast.LENGTH_SHORT).show();
            authRepository.updateProfileImage(imageBytes, new AuthRepository.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(getContext(), "Image updated successfully", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(getContext(), "Upload error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }
}