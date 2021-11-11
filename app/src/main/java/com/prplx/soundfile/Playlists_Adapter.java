package com.prplx.soundfile;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class Playlists_Adapter extends RecyclerView.Adapter<Playlists_Adapter.ViewHolder>
{
    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private final CardView cardView;
        public TextView TV_name;


        public ViewHolder(CardView arg_cardView)
        {
            super(arg_cardView);

            this.cardView = arg_cardView;

            this.TV_name = (TextView)cardView.findViewById(R.id.cardView_playlist_name);
        }
    }


    private List<String> list_playlists_names;

    public Playlists_Adapter(@NonNull Context context, List<String> arg_list_of_playlists_names)
    {
        this.list_playlists_names = arg_list_of_playlists_names;
    }


    @NonNull
    @Override
    public Playlists_Adapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        CardView cardView = (CardView) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.cardview_playlist, parent, false);

        return new Playlists_Adapter.ViewHolder(cardView);
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position)
    {
        CardView cardView = viewHolder.cardView;

        String name = this.list_playlists_names.get(position);
        viewHolder.TV_name.setText(name);
    }


    @Override
    public int getItemCount()
    {
        return this.list_playlists_names.size();
    }


    public void ChangeDataset(List<String> arg_name_list)
    {
        this.list_playlists_names = arg_name_list;
        for (String name : list_playlists_names)
        {
            Log.v("my_debug", name);
        }

    }
}
