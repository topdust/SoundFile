package com.prplx.soundfile;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


class PlayerSearchAdapter extends ArrayAdapter<AudioFile>
{
    List<AudioFile> publishResults_list;
    List<AudioFile> init_list;

    public boolean by_artist_search = true;
    public boolean by_title_search = true;
    public boolean by_album_search = true;

    private final PlayerService playerService;
    private final PlayerActivity playerActivity;

    private ExecutorService app_Additional_Threads;
    private ArrayList<Future> threads_future_list; // list to save all working threads


    public PlayerSearchAdapter(Context context, List<AudioFile> init_list, PlayerService arg_playerService)
    {
        super(context, R.layout.cardview_player_search, init_list);
        this.playerActivity = (PlayerActivity)context;
        this.init_list = init_list;
        this.publishResults_list = new ArrayList<AudioFile>();
        this.playerService = arg_playerService;
        this.app_Additional_Threads = MainActivity.get_App_Additional_Threads();
        this.threads_future_list = new ArrayList<Future>();
    }

    @NonNull
    @Override
    public String toString()
    {
        return super.toString();
    }


    /**
     * Initialises view for AutoCompleteTextView dropdown list
     * @param parent - see Adapter.java @param parent
     * @return initialised view
     */
    private View init_View(int position, ViewGroup parent)
    {
        View view = LayoutInflater.from(playerActivity)
                .inflate(R.layout.cardview_player_search, parent, false);

        ImageView imageView_AlbumArt = view.findViewById(R.id.player_cardView_image);
        //set default album image
        imageView_AlbumArt.setImageResource(R.drawable.ic_app_launcher_foreground);

        view.setOnTouchListener(new View.OnTouchListener()
        {
            private final float PRESS_SCALE = 0.95f;
            private final float RELEASE_SCALE = 1.0f;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent)
            {
                switch (motionEvent.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                    {
                        view.setScaleX(PRESS_SCALE);
                        view.setScaleY(PRESS_SCALE);
                        break;
                    }

                    case MotionEvent.ACTION_CANCEL:
                    {
                        view.setScaleX(RELEASE_SCALE);
                        view.setScaleY(RELEASE_SCALE);
                        break;
                    }

                    case MotionEvent.ACTION_UP:
                    {
                        view.setScaleX(RELEASE_SCALE);
                        view.setScaleY(RELEASE_SCALE);

                        view.performClick();
                        break;
                    }

                    case MotionEvent.ACTION_MOVE:
                    {

                        break;
                    }
                }

                return true;
            }
        });

        return view;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        if (convertView == null)
        {
            convertView = init_View(position, parent);
        }

        AudioFile item = publishResults_list.get(position);

        //asynchronously load MediaStore album image
        View finalConvertView = convertView;
        Future thread_future = app_Additional_Threads.submit(() ->
        {
            Bitmap album_art_bitmap = MainActivity.getAlbumArt(playerActivity, item.album_id);

            final ImageView imageView_AlbumArt = finalConvertView.findViewById(R.id.player_cardView_image);

            if(imageView_AlbumArt != null)
            {
                if(album_art_bitmap != null)
                {
                    Drawable album_art_drawable = new BitmapDrawable(playerActivity.getResources(), album_art_bitmap);
                    playerActivity.runOnUiThread(() ->
                    {
                        imageView_AlbumArt.setImageDrawable(album_art_drawable);
                    });
                }
                else
                {
                    playerActivity.runOnUiThread(() ->
                    {
                        imageView_AlbumArt.setImageResource(R.drawable.ic_app_launcher_foreground);
                    });
                }
            }
        });
        this.threads_future_list.add(thread_future);

        //change background if it's current track item
        if(item.id == playerService.getCurrentTrackID())
        {
            convertView.findViewById(R.id.player_cardView_root)
                    .setBackgroundColor(convertView.getContext().getColor(R.color.player_search_item_back_color_active));
        }
        else
        {
            convertView.findViewById(R.id.player_cardView_root)
                    .setBackgroundColor(convertView.getContext().getColor(R.color.MyAppTheme_colorPrimaryDark75transparent));
        }

        ((TextView) convertView.findViewById(R.id.player_cardView_artist))
                .setText(item.artist);
        ((TextView) convertView.findViewById(R.id.player_cardView_title))
                .setText(item.title);
        ((TextView) convertView.findViewById(R.id.player_cardView_duration))
                .setText(item.duration_in_Min_Sec);


        convertView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                //allow to hide dropdown of search bar
                playerActivity.Search_TV.setIgnoreDismissDropDown(false);
                playerActivity.Search_TV.dismissDropDown();
                playerActivity.Search_TV.clearFocus();
                MainActivity.hideSoftKeyboard(playerActivity);

                //do not replay track if it is playing
                if(item.id == playerService.getCurrentTrackID())
                {
                    return;
                }
                else
                {
                    playerService.Play_Audio_By_ID(item.id);
                    playerActivity.SetPlayerUI(playerService.current_index_in_list);
                    notifyDataSetChanged();
                }
            }
        });


        ((ImageButton) convertView.findViewById(R.id.player_cardView_more)).setOnClickListener(new View.OnClickListener()
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
                        switch (menuItem.getItemId())
                        {
                            //save audiofile attributes and pass them to the edit_audiofile activity
                            case R.id.menu_more_audio_playlist_action_edit:
                            {
                                Intent intent_edit_audio = new Intent(view.getContext(), Edit_audiofile.class);

                                long id = item.id;
                                String artist = item.artist;
                                String title = item.title;
                                String album = item.album;
                                int current_index_in_list = position;

                                intent_edit_audio.putExtra(Edit_audiofile.ID_EXTRA, id);
                                intent_edit_audio.putExtra(Edit_audiofile.ARTIST_EXTRA, artist);
                                intent_edit_audio.putExtra(Edit_audiofile.TITLE_EXTRA, title);
                                intent_edit_audio.putExtra(Edit_audiofile.ALBUM_EXTRA, album);
                                intent_edit_audio.putExtra(Edit_audiofile.INDEX_EXTRA, current_index_in_list);


                                try
                                {
                                    view.getContext().startActivity(intent_edit_audio);
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

                playerActivity.Search_TV.setIgnoreDismissDropDown(true);
                popupMenu.show();
            }
        });


        return convertView;
    }


    @Override
    public int getCount() {
        return this.publishResults_list.size();
    }

    @Override
    public AudioFile getItem(int position)
    {
        return this.publishResults_list.get(position);
    }

    @Override
    public Filter getFilter()
    {
        return nameFilter;
    }

    /**
     * Custom Filter implementation for custom suggestions we provide.
     */
    private Filter nameFilter = new Filter()
    {
        @Override
        public CharSequence convertResultToString(Object resultValue)
        {
            String str = ((AudioFile) resultValue).artist + " - "  +((AudioFile) resultValue).title;
            return str;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint)
        {
            //cancel all working threads which loading images
            for(Future future: PlayerSearchAdapter.this.threads_future_list)
            {
                if(!future.isDone())
                {
                    future.cancel(true);
                }
            }
            PlayerSearchAdapter.this.threads_future_list.clear();

            FilterResults filterResults = new FilterResults();
            String str_constraint = "";

            if(constraint == null || constraint.toString().trim().equals(""))
            {
                filterResults.values = init_list;
                filterResults.count = init_list.size();

                return filterResults;
            }
            else
            {
                str_constraint = constraint.toString().trim().toLowerCase();
            }

            //filter by each word in the search string
            String[] search_words = str_constraint.split(" ");
            List<AudioFile> matched_audiofile_list = new ArrayList<AudioFile>();

            //make list where search will be going
            final ArrayList<AudioFile> search_list = new ArrayList<AudioFile>(init_list);
            for (AudioFile audiofile : search_list)
            {
                //skip iteration if item already in the match list
                if(matched_audiofile_list.contains(audiofile))
                {
                    continue;
                }

                int matched_words = 0;

                for(String word : search_words)
                {
                    if(by_artist_search)
                    {

                        if (audiofile.artist.toLowerCase().contains(word.toLowerCase()))
                        {
                            matched_words++;
                        }

                    }

                    if(by_title_search)
                    {
                        if (audiofile.title.toLowerCase().contains(word.toLowerCase()))
                        {
                            matched_words++;
                        }
                    }

                    if(by_album_search)
                    {
                        if (audiofile.album.toLowerCase().contains(word.toLowerCase()))
                        {
                            matched_words++;
                        }
                    }
                }

                if(matched_words >= search_words.length)
                {
                    matched_audiofile_list.add(audiofile);
                }
            }

            filterResults.values = matched_audiofile_list;
            filterResults.count = matched_audiofile_list.size();

            return filterResults;
        }


        @Override
        protected void publishResults(CharSequence constraint, FilterResults results)
        {
            List<AudioFile> filterList = (ArrayList<AudioFile>) results.values;
            if (results != null && results.count > 0)
            {
                publishResults_list.clear();
                publishResults_list.addAll(filterList);
                notifyDataSetChanged();
            }
        }
    };
}
