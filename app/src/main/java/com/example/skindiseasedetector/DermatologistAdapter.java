package com.example.skindiseasedetector;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DermatologistAdapter extends RecyclerView.Adapter<DermatologistAdapter.ViewHolder> {

    private final List<DermatologistModel> dermatologistList;
    private final OnDermActionListener listener;

    // ✅ Interface definition (this fixes the “cannot resolve symbol” error)
    public interface OnDermActionListener {
        void onDirectionClick(DermatologistModel derm);
        void onCallClick(DermatologistModel derm);
    }

    public DermatologistAdapter(List<DermatologistModel> dermatologistList, OnDermActionListener listener) {
        this.dermatologistList = dermatologistList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dermatologist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DermatologistModel derm = dermatologistList.get(position);

        holder.txtName.setText("👨‍⚕️ " + derm.getName());
        holder.txtAvailability.setText("⏰ " + derm.getAvailability());
        holder.txtDistance.setText("📍 " + derm.getDistance() + " km away");

        holder.btnDirection.setOnClickListener(v -> listener.onDirectionClick(derm));
        holder.btnCall.setOnClickListener(v -> listener.onCallClick(derm));
    }

    @Override
    public int getItemCount() {
        return dermatologistList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtAvailability, txtDistance;
        Button btnDirection, btnCall;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtName);
            txtAvailability = itemView.findViewById(R.id.txtAvailability);
            txtDistance = itemView.findViewById(R.id.txtDistance);
            btnDirection = itemView.findViewById(R.id.btnDirection);
            btnCall = itemView.findViewById(R.id.btnCall);
        }
    }
}
