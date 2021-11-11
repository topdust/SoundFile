package com.prplx.soundfile;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;


public class Playlist_Fragment extends Fragment implements Comparator<AudioFile>
{
    Duration_Comparator duration_comparator = new Duration_Comparator(); //compares audio files by duration

    ArrayList<AudioFile> initial_fileList = MainActivity.list_AudioFile; //initial version of audio filelist
    ArrayList<AudioFile> current_fileList; //current filelist for operations in this fragment

    RecyclerView recyclerView_fileList = null;
    private EditText ET_search = null;
    private ImageButton IB_sort = null;
    private TextView TV_list_size = null;

    private final String SIS_AUDIO_LIST_SCROLL_POS = "SIS_AUDIO_LIST_SCROLL_POS";

    private final boolean duration_compared = false;

    private Boolean is_search_by_artist = true;
    private Boolean is_search_by_title = true;
    private Boolean is_search_by_album = true;
    private Boolean is_search_by_all = true;

    public static final String SEARCH_BY_ARTIST = "by_artist";
    public static final String SEARCH_BY_TITLE = "by_title";
    public static final String SEARCH_BY_ALBUM = "by_album";
    public static final String SEARCH_BY_ALL = "by_all";
    public static final String SEARCH_BY_NONE = "by_none";

    //save this in MainActivity if fragment will be paused
    private int first_visible_view_pos = -1;

    ActionMode actionMode = null;

    private final ActionMode.Callback action_mode_callback = new ActionMode.Callback()
    {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu)
        {
            actionMode.getMenuInflater().inflate(R.menu.menu_popup_audiolist_search_options, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem)
        {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode)
        {
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        MainActivity main_activity_obj = (MainActivity)getActivity();
        //this.first_visible_view_pos = main_activity_obj.audio_playlist_first_visible_view_position;
    }



    @Override
    public void onPause()
    {
        super.onPause();

        MainActivity main_activity_obj = (MainActivity)getActivity();
        //main_activity_obj.audio_playlist_first_visible_view_position = first_visible_view_pos;
    }


    @Override
    public void onResume()
    {
        super.onResume();

        //if user has searched something before activity pause - return search results
        if(ET_search.getText().toString().isEmpty() == false)
        {
            ET_search.setText(ET_search.getText());
            ET_search.setSelection(ET_search.getText().length());
        }

        //reload list
        this.UpdateRecycleView(current_fileList);

        MainActivity activity = (MainActivity)getActivity();
        //this.first_visible_view_pos = activity.audio_playlist_first_visible_view_position;
        //scroll to prev position
        recyclerView_fileList.scrollToPosition(first_visible_view_pos);
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate and get layout for this fragment
        View root_view = inflater.inflate(R.layout.fragment_playlist, container, false);

        this.InitViews(root_view);

        //search by all criterias by default
        this.set_Search_By(SEARCH_BY_ALL);

        //make temporary list from main list for operating in this fragment
        current_fileList = initial_fileList;

        this.UpdateRecycleView(current_fileList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView_fileList.setLayoutManager(layoutManager);

        return root_view;
    }


    //updates RecycleView of this activity corresponding to the state of the current_fileList parameter
    private void UpdateRecycleView(ArrayList<AudioFile> fileList)
    {
        int size = fileList.size();
        Update_TV_track_size(size);

        long[] ar_id = new long[size];
        String[] ar_abs_path = new String[size];
        String[] ar_artist = new String[size];
        String[] ar_title = new String[size];
        String[] ar_album = new String[size];
        String[] ar_duration = new String[size];


        for(int i = 0; i < size; i++)
        {
            ar_id[i] = fileList.get(i).id;
            ar_abs_path[i] = fileList.get(i).absolute_path;
            ar_artist[i] = fileList.get(i).artist;
            ar_title[i] = fileList.get(i).title;
            ar_album[i] = fileList.get(i).album;
            ar_duration[i] = PlayerActivity.msToMinSec(fileList.get(i).duration_ms);
        }

        Audiofile_CardView_Adapter adapter = new Audiofile_CardView_Adapter(fileList, recyclerView_fileList, MainActivity.playerService);

        //listen for changes in the adapter and update list_size if track has been removed or added
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver()
        {
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount)
            {
                super.onItemRangeChanged(positionStart, itemCount);
                Update_TV_track_size(fileList.size());
            }
        });
        recyclerView_fileList.setAdapter(adapter);
    }



    private void InitViews(View root_view)
    {
        root_view.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                root_view.requestFocus();
            }
        });

        recyclerView_fileList = (RecyclerView)root_view.findViewById(R.id.fileList_XML);
        recyclerView_fileList.addOnScrollListener(new RecyclerView.OnScrollListener()
        {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState)
            {
                super.onScrollStateChanged(recyclerView, newState);

                if(newState == RecyclerView.SCROLL_STATE_IDLE)
                {
                    LinearLayoutManager lm = (LinearLayoutManager)recyclerView_fileList.getLayoutManager();
                    first_visible_view_pos = lm.findFirstCompletelyVisibleItemPosition();
                }
            }
        });

        //first view pos initialises from intent in onCreateView or it is first position of a view
        recyclerView_fileList.scrollToPosition(this.first_visible_view_pos);


        ET_search = (EditText)root_view.findViewById(R.id.filelist_ET_search_XML);
        ET_search.setOnFocusChangeListener(new View.OnFocusChangeListener()
        {
            @Override
            public void onFocusChange(View v, boolean hasFocus)
            {
                if(hasFocus == false)
                {
                    InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        });
        ET_search.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                String inputed_string = s.toString().toLowerCase(Locale.getDefault());
                String[] search_words = inputed_string.split(" ");
                ArrayList<AudioFile> matched_audio_list = new ArrayList<AudioFile>();

                for(int i = 0; i < initial_fileList.size(); i++)
                {
                    AudioFile audio_list_item = initial_fileList.get(i);

                    //skip iteration if item already in the match list
                    if(matched_audio_list.contains(audio_list_item))
                    {
                        continue;
                    }

                    int matched_words = 0;

                    for(String word: search_words)
                    {
                        if(is_search_by_artist)
                        {
                            if(audio_list_item.artist.toLowerCase(Locale.getDefault()).contains(word))
                            {
                                matched_words++;
                            }
                        }

                        if(is_search_by_title)
                        {
                            if(audio_list_item.title.toLowerCase(Locale.getDefault()).contains(word))
                            {
                                matched_words++;
                            }
                        }

                        if(is_search_by_album)
                        {
                            if(audio_list_item.album.toLowerCase(Locale.getDefault()).contains(word))
                            {
                                matched_words++;
                            }
                        }
                    }

                    if(matched_words >= search_words.length)
                    {
                        matched_audio_list.add(audio_list_item);
                    }
                }


                current_fileList = matched_audio_list;

                UpdateRecycleView(current_fileList);
            }


            @Override
            public void afterTextChanged(Editable s)
            {

            }
        });


        IB_sort = (ImageButton)root_view.findViewById(R.id.fileList_btn_sort_XML);
        IB_sort.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                PopupMenu popup_menu = new PopupMenu(getContext(), view);
                MenuInflater inflater = popup_menu.getMenuInflater();
                inflater.inflate(R.menu.menu_popup_audiolist_search_options, popup_menu.getMenu());


                popup_menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
                {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem)
                    {
                        switch (menuItem.getItemId())
                        {
                            case R.id.audiolist_search_option_by_all:
                            {
                                //if already checked - remove check from all options
                                if(menuItem.isChecked())
                                {
                                    set_Search_By(SEARCH_BY_NONE);
                                    for(int i = 0; i < popup_menu.getMenu().size(); i++)
                                    {
                                        popup_menu.getMenu().getItem(i).setChecked(false);
                                    }
                                }
                                else // else set check and update search condition
                                {
                                    set_Search_By(SEARCH_BY_ALL);
                                    for(int i = 0; i < popup_menu.getMenu().size(); i++)
                                    {
                                        popup_menu.getMenu().getItem(i).setChecked(true);
                                    }
                                }
                                break;
                            }

                            case R.id.audiolist_search_option_by_artist:
                            {
                                if(menuItem.isChecked())
                                {
                                    is_search_by_artist = false;
                                    menuItem.setChecked(false);
                                }
                                else
                                {
                                    menuItem.setChecked(true);
                                    set_Search_By(SEARCH_BY_ARTIST);
                                }
                                break;
                            }

                            case R.id.audiolist_search_option_by_title:
                            {
                                if(menuItem.isChecked())
                                {
                                    is_search_by_title = false;
                                    menuItem.setChecked(false);
                                }
                                else
                                {
                                    menuItem.setChecked(true);
                                    set_Search_By(SEARCH_BY_TITLE);
                                }
                                break;
                            }

                            case R.id.audiolist_search_option_by_album:
                            {
                                if(menuItem.isChecked())
                                {
                                    is_search_by_album = false;
                                    menuItem.setChecked(false);
                                }
                                else
                                {
                                    menuItem.setChecked(true);
                                    set_Search_By(SEARCH_BY_ALBUM);
                                }
                                break;
                            }

                            default:
                                return false;
                        }

                        //check if need to remove "by_all" option after user's action
                        //"by all" has 0 index in menu
                        for(int i = 1; i < popup_menu.getMenu().size(); i++)
                        {
                            if(popup_menu.getMenu().getItem(i).isChecked() == false)
                            {
                                popup_menu.getMenu().getItem(0).setChecked(false);
                            }
                        }

                        //reload search list with new items
                        ET_search.setText(ET_search.getText());
                        //move cursor to the end of the search phrase
                        ET_search.setSelection(ET_search.getText().length());

                        return true;
                    }
                });

                Menu menu = popup_menu.getMenu();
                //check items which is picked by user
                for(int i = 0; i < menu.size(); i++)
                {
                    MenuItem item = menu.getItem(i);

                    if(is_search_by_artist)
                    {
                        if(item.getTitle().equals(getString(R.string.audiolist_search_menu_by_artist)))
                        {
                            if(item.isChecked() == false)
                            {
                                item.setChecked(true);
                                continue;
                            }
                        }
                    }


                    if(is_search_by_title)
                    {
                        if(item.getTitle().equals(getString(R.string.audiolist_search_menu_by_title)))
                        {
                            if(item.isChecked() == false)
                            {
                                item.setChecked(true);
                                continue;
                            }
                        }
                    }


                    if(is_search_by_album)
                    {
                        if(item.getTitle().equals(getString(R.string.audiolist_search_menu_by_album)))
                        {
                            if(item.isChecked() == false)
                            {
                                item.setChecked(true);
                                continue;
                            }
                        }
                    }
                }

                if(is_search_by_all)
                {
                    int checked_amo = 0;

                    //remove "by all" checked if not all options are checked
                    //"by all" is the first option in the menu
                    for(int indx = 1; indx < menu.size(); indx++)
                    {
                        if(menu.getItem(indx).isChecked())
                        {
                            checked_amo++;
                        }
                    }

                    //if not all options are checked (except "by all" option)
                    //remove checked from "by all"
                    menu.getItem(0).setChecked(checked_amo == menu.size() - 1);
                }

                popup_menu.show();
            }
        });


        TV_list_size = (TextView)root_view.findViewById(R.id.Playlist_TV_tracklist_size);
    }




    private void set_Search_By(String search_condition)
    {
        search_condition = search_condition.toLowerCase();


        if(search_condition.equals(SEARCH_BY_ARTIST))
        {
            this.is_search_by_artist = true;
        }

        if(search_condition.equals(SEARCH_BY_TITLE))
        {
            this.is_search_by_title = true;
        }

        if(search_condition.equals(SEARCH_BY_ALBUM))
        {
            this.is_search_by_album = true;
        }

        if(search_condition.equals(SEARCH_BY_ALL))
        {
            this.is_search_by_all = true;
            this.is_search_by_artist = true;
            this.is_search_by_title = true;
            this.is_search_by_album = true;
        }

        if(search_condition.equals(SEARCH_BY_NONE))
        {
            this.is_search_by_all = false;
            this.is_search_by_artist = false;
            this.is_search_by_title = false;
            this.is_search_by_album = false;
        }
    }


    private void Update_TV_track_size(int size)
    {
        String str_size = Integer.toString(size);

        if(str_size.charAt(str_size.length()-1) == '1')
        {
            str_size = str_size.concat(" track");
        }
        else
        {
            str_size = str_size.concat(" tracks");
        }

        String text = getResources().getString(R.string.playlist_size);
        TV_list_size.setText(String.format("%s %s", text, str_size));
    }

    @Override
    public int compare(AudioFile o1, AudioFile o2)
    {
        return  o1.duration_ms - o2.duration_ms;
    }
}