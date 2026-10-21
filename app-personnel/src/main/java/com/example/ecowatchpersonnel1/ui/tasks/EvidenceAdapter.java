package com.example.ecowatchpersonnel1.ui.tasks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.PersonnelEvidence;
import com.example.ecowatchpersonnel1.data.remote.ServerConfig;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * The evidence strip for one phase: the photos already on the server, plus the
 * add tile when the server's permissions allow another upload.
 *
 * <p>The phase is fixed per adapter, so the "add" callback does not have to
 * guess which BEFORE or AFTER bucket a new photo belongs to.
 */
public class EvidenceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_ADD = 1;

    public interface Listener {
        void onAddPhoto(String phase);

        void onRemovePhoto(String phase, PersonnelEvidence evidence);
    }

    private final String phase;
    private final Listener listener;
    private final List<PersonnelEvidence> items = new ArrayList<>();

    private boolean canAdd;
    private boolean canRemove;

    public EvidenceAdapter(String phase, Listener listener) {
        this.phase = phase;
        this.listener = listener;
    }

    public void submit(List<PersonnelEvidence> evidence, boolean canAdd, boolean canRemove) {
        items.clear();
        if (evidence != null) {
            items.addAll(evidence);
        }
        this.canAdd = canAdd;
        this.canRemove = canRemove;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == items.size()) {
            return TYPE_ADD;
        }
        return TYPE_PHOTO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            return new AddViewHolder(inflater.inflate(R.layout.item_add_photo, parent, false));
        }
        return new PhotoViewHolder(
                inflater.inflate(R.layout.item_photo_thumbnail, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD) {
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAddPhoto(phase);
            });
            return;
        }

        PhotoViewHolder photoHolder = (PhotoViewHolder) holder;
        PersonnelEvidence evidence = items.get(position);

        Glide.with(photoHolder.thumbnail.getContext())
                .load(ServerConfig.absoluteUrl(photoHolder.thumbnail.getContext(),
                        evidence.imageUrl))
                .placeholder(R.drawable.bg_photo_thumbnail)
                .error(R.drawable.bg_photo_thumbnail)
                .centerCrop()
                .into(photoHolder.thumbnail);

        photoHolder.removeBtn.setVisibility(canRemove ? View.VISIBLE : View.GONE);
        photoHolder.removeBtn.setOnClickListener(v -> {
            if (listener != null) listener.onRemovePhoto(phase, evidence);
        });
    }

    @Override
    public int getItemCount() {
        return items.size() + (canAdd ? 1 : 0);
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView thumbnail;
        final View removeBtn;

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
