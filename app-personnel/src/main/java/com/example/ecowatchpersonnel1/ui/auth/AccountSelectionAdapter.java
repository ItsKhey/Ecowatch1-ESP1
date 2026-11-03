package com.example.ecowatchpersonnel1.ui.auth;

import com.example.ecowatchpersonnel1.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AccountSelectionAdapter extends RecyclerView.Adapter<AccountSelectionAdapter.ViewHolder> {

    public static class AccountRow {
        public final String username;
        public final Object setting; // Replace Object with your specific Biometric model class if applicable

        public AccountRow(String username, Object setting) {
            this.username = username;
            this.setting = setting;
        }
    }

    public interface Listener {
        void onAccountChosen(AccountRow row);
        void onAccountRemoved(String username, int remaining);
    }

    private final Context context;
    private final String type;
    private final Listener listener;
    private final List<AccountRow> items = new ArrayList<>();

    public AccountSelectionAdapter(Context context, String type, Listener listener) {
        this.context = context;
        this.type = type;
        this.listener = listener;
    }

    public void submit(List<String> rememberedUsernames, List<?> biometricSettings) {
        items.clear();

        // Add biometric accounts first
        if (biometricSettings != null) {
            for (Object setting : biometricSettings) {
                // If setting stores username, adjust getter call accordingly
                items.add(new AccountRow("", setting));
            }
        }

        // Add remaining remembered accounts
        if (rememberedUsernames != null) {
            for (String username : rememberedUsernames) {
                items.add(new AccountRow(username, null));
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_account_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AccountRow row = items.get(position);
        holder.tvEmail.setText(row.username);

        if (row.setting != null) {
            // Fingerprint / Biometric row
            holder.tvAccountTag.setText("Fingerprint login");
            holder.tvAccountTag.setVisibility(View.VISIBLE);
            holder.tvRemoveAccount.setVisibility(View.GONE);
        } else {
            // Remembered account row
            holder.tvAccountTag.setVisibility(View.GONE);
            holder.tvRemoveAccount.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAccountChosen(row);
            }
        });

        holder.tvRemoveAccount.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                AccountRow removedItem = items.remove(currentPos);
                notifyItemRemoved(currentPos);
                if (listener != null) {
                    listener.onAccountRemoved(removedItem.username, getItemCount());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfilePicture;
        TextView tvEmail;
        TextView tvAccountTag;
        TextView tvRemoveAccount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePicture = itemView.findViewById(R.id.ivProfilePicture);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvAccountTag = itemView.findViewById(R.id.tvAccountTag);
            tvRemoveAccount = itemView.findViewById(R.id.tvRemoveAccount);
        }
    }
}