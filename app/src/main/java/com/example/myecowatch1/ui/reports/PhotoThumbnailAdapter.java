package com.example.myecowatch1.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoThumbnailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_ADD = 1;

    private List<File> photos = new ArrayList<>();
    private int maxPhotos = 10;
    private OnPhotoActionListener listener;

    public interface OnPhotoActionListener {
        void onRemovePhoto(int position);
        void onAddPhoto();
    }

    public void setOnPhotoActionListener(OnPhotoActionListener listener) {
        this.listener = listener;
    }

    public void setPhotos(List<File> photos) {
        this.photos = new ArrayList<>(photos);
        notifyDataSetChanged();
    }

    public void addPhoto(File photo) {
        if (photos.size() < maxPhotos) {
            photos.add(photo);
            notifyItemInserted(photos.size() - 1);
            // Notify add button change (may need to hide if max reached)
            if (photos.size() >= maxPhotos) {
                notifyItemChanged(photos.size());
            }
        }
    }

    public void removePhoto(int position) {
        if (position >= 0 && position < photos.size()) {
            photos.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, photos.size() - position);
            // Re-show add button if it was hidden
            if (photos.size() == maxPhotos - 1) {
                notifyItemChanged(photos.size());
            }
        }
    }

    public List<File> getPhotos() {
        return new ArrayList<>(photos);
    }

    public int getPhotoCount() {
        return photos.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == photos.size() && photos.size() < maxPhotos) {
            return TYPE_ADD;
        }
        return TYPE_PHOTO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            View view = inflater.inflate(R.layout.item_add_photo, parent, false);
            return new AddViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_photo_thumbnail, parent, false);
            return new PhotoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD) {
            AddViewHolder addHolder = (AddViewHolder) holder;
            addHolder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAddPhoto();
            });
        } else {
            PhotoViewHolder photoHolder = (PhotoViewHolder) holder;
            File photo = photos.get(position);

            Glide.with(photoHolder.itemView.getContext())
                    .load(photo)
                    .centerCrop()
                    .into(photoHolder.thumbnail);

            photoHolder.removeBtn.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onRemovePhoto(pos);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        if (photos.size() < maxPhotos) {
            return photos.size() + 1; // +1 for the "add" button
        }
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView thumbnail;
        View removeBtn;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.photoThumbnail);
            removeBtn = itemView.findViewById(R.id.removePhotoBtn);
        }
    }

    static class AddViewHolder extends RecyclerView.ViewHolder {
        AddViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
