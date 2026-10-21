package com.example.myecowatch1.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders every photo of a report as a horizontally scrollable strip.
 *
 * <p>Each entry is either a local {@link java.io.File} (a photo not yet uploaded,
 * so it is the only copy available) or a remote URL string. Glide handles both, so
 * the caller only has to decide which source applies.
 */
public class ReportDetailPhotoAdapter extends RecyclerView.Adapter<ReportDetailPhotoAdapter.PhotoViewHolder> {

    /** Sources are {@link java.io.File} for local copies, {@link String} for remote URLs. */
    private final List<Object> photos = new ArrayList<>();
    private OnPhotoClickListener listener;

    public interface OnPhotoClickListener {
        void onPhotoClick(int position, Object source);
    }

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.listener = listener;
    }

    public void setPhotos(List<Object> sources) {
        photos.clear();
        if (sources != null) {
            for (Object source : sources) {
                if (source != null) {
                    photos.add(source);
                }
            }
        }
        notifyDataSetChanged();
    }

    public Object getPhoto(int position) {
        return (position >= 0 && position < photos.size()) ? photos.get(position) : null;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_detail_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Object source = photos.get(position);

        Glide.with(holder.itemView.getContext())
                .load(source)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.image);

        // The badge is only useful when there is more than one photo, where it
        // marks the position within the set.
        if (photos.size() > 1) {
            holder.badge.setText(String.valueOf(position + 1));
            holder.badge.setVisibility(View.VISIBLE);
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION && listener != null) {
                listener.onPhotoClick(current, photos.get(current));
            }
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView image;
        final TextView badge;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.detailPhotoImage);
            badge = itemView.findViewById(R.id.detailPhotoBadge);
        }
    }
}
