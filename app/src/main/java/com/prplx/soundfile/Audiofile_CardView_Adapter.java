package com.prplx.soundfile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;


public class Audiofile_CardView_Adapter extends RecyclerView.Adapter<Audiofile_CardView_Adapter.ViewHolder>
{

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private final CardView cardView;
        public TextView TV_artist;
        public TextView TV_title;
        public TextView TV_duration;
        public ImageButton IB_more;
        private final ConstraintLayout constraintLayout;

        public ViewHolder(CardView arg_cardView)
        {
            super(arg_cardView);

            this.cardView = arg_cardView;

            this.TV_artist = (TextView)cardView.findViewById(R.id.audioplaylist_cardView_artist);
            this.TV_title = (TextView)cardView.findViewById(R.id.audioplaylist_cardView_title);
            this.TV_duration = (TextView)cardView.findViewById(R.id.audioplaylist_cardView_duration);
            this.IB_more = (ImageButton)cardView.findViewById(R.id.playlist_cardView_more);
            this.constraintLayout = (ConstraintLayout)cardView.findViewById(R.id.constraint_layout);
        }
    }



    ArrayList<AudioFile> fileList;
    private final RecyclerView recyclerView;
    long[] ar_id;
    String[] ar_abs_path;
    String[] ar_artist;
    String[] ar_title;
    String[] ar_album;
    int[] ar_duration;
    int list_size;
    private final PlayerService playerService;


    public Audiofile_CardView_Adapter(ArrayList<AudioFile> arg_fileList,
                                      RecyclerView arg_recyclerView_fileList, PlayerService arg_playerService)
    {
        this.fileList = arg_fileList;
        this.list_size = fileList.size();
        this.recyclerView = arg_recyclerView_fileList;
        this.playerService = arg_playerService;

        //no need to acquire space for String arrays
        this.ar_id = new long[list_size];
        this.ar_duration = new int[list_size];
        this.ar_abs_path = new String[list_size];
        this.ar_artist = new String[list_size];
        this.ar_title = new String[list_size];
        this.ar_album = new String[list_size];
        this.ar_duration = new int[list_size];

        for(int i = 0; i < list_size; i++)
        {
            this.ar_id[i] = arg_fileList.get(i).id;
            this.ar_abs_path[i] = arg_fileList.get(i).absolute_path;
            this.ar_artist[i] = arg_fileList.get(i).artist;
            this.ar_title[i] = arg_fileList.get(i).title;
            this.ar_album[i] = arg_fileList.get(i).album;
            this.ar_duration[i] = arg_fileList.get(i).duration_ms;
        }
    }



    @Override
    public Audiofile_CardView_Adapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        CardView cardView = (CardView) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.track_cardview, parent, false);

        return new ViewHolder(cardView);
    }


    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position)
    {
        CardView cardView = viewHolder.cardView;

        //on click start Player activity with track of ID <ar_id[position]>
        cardView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(cardView.getContext(), PlayerActivity.class);
                intent.putExtra(PlayerActivity.ID_EXTRA, ar_id[position]);
                cardView.getContext().startActivity(intent);
            }
        });


        if(ar_id[position] == playerService.getCurrentTrackID())
        {
            viewHolder.constraintLayout.setBackgroundColor(ContextCompat.getColor(cardView.getContext(), R.color.playlist_item_back_color_active));
        }
        else
        {
            viewHolder.constraintLayout.setBackgroundColor(ContextCompat.getColor(cardView.getContext(), R.color.playlist_item_back_color));
        }

        viewHolder.TV_artist.setText(this.ar_artist[position]);
        viewHolder.TV_title.setText(this.ar_title[position]);
        viewHolder.TV_duration.setText(PlayerActivity.msToMinSec(this.ar_duration[position]));
        viewHolder.IB_more.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
                MenuInflater inflater = popupMenu.getMenuInflater();
                inflater.inflate(R.menu.menu_popup_more_audio_playlist, popupMenu.getMenu());

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
                {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem)
                    {
                        //get current viewholder position in the recycleview,
                        // not the position when viewholder was binded
                        int selected_item_position = viewHolder.getAdapterPosition();

                        switch (menuItem.getItemId())
                        {
                            case R.id.menu_more_audio_playlist_action_edit:
                            {
                                Intent intent_edit_audio = new Intent(view.getContext(), Edit_audiofile.class);

                                long id = ar_id[selected_item_position];
                                String artist = ar_artist[selected_item_position];
                                String title = ar_title[selected_item_position];
                                String album = ar_album[selected_item_position];
                                int current_index_in_list = selected_item_position;

                                intent_edit_audio.putExtra(Edit_audiofile.ID_EXTRA, id);
                                intent_edit_audio.putExtra(Edit_audiofile.ARTIST_EXTRA, artist);
                                intent_edit_audio.putExtra(Edit_audiofile.TITLE_EXTRA, title);
                                intent_edit_audio.putExtra(Edit_audiofile.ALBUM_EXTRA, album);
                                intent_edit_audio.putExtra(Edit_audiofile.INDEX_EXTRA, current_index_in_list);

                                try
                                {
                                    view.getContext().startActivity(intent_edit_audio);

                                    recyclerView.getAdapter().notifyItemChanged(selected_item_position);
                                }
                                catch (ActivityNotFoundException ex)
                                {
                                    //PrintError(ex);
                                }
                                break;
                            }


                            default:
                                break;
                        }
                        return false;
                    }
                });
                popupMenu.show();
            }
        });
    }

    @Override
    public int getItemCount()
    {
        return this.fileList.size();
    }
}
