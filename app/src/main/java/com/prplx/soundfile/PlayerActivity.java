package com.prplx.soundfile;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class PlayerActivity extends AppCompatActivity
{
    //request code of startActivityForResult() for Edit_audiofile activity
    private static final int Edit_audio_REQUEST_CODE = 1;

    private float BTN_PRESS_SCALE = Animated_ImageButton.BTN_PRESS_SCALE;
    private float BTN_RELEASE_SCALE = Animated_ImageButton.BTN_RELEASE_SCALE;

    public static final String ACTION_FINISH_MAIN_ACTIVITY = "a_f_m_a";

    private boolean is_stopped;
    private boolean is_paused;
    private boolean is_resumed;
    private boolean is_started;

    public static String ID_EXTRA = "id_extra";
    public static String PATH_EXTRA = "path_extra";

    private static final float BUTTON_PRESS_SCALE = 0.9f;
    private static final float BUTTON_RELEASE_SCALE = 1f;

    private final String SIS_index_list = "current_index_in_list";
    private final String SIS_audio_cur_pos = "currentPosition_AudioFile";
    private final String SIS_resume_play = "resume_play";
    private final String SIS_path_to_file = "path_to_file";

    private final String SIS_is_search_by_all = "sis_is_search_by_all";
    private final String SIS_is_search_by_artist = "sis_is_search_by_artist";
    private final String SIS_is_search_by_title = "sis_is_search_by_title";
    private final String SIS_is_search_by_album = "sis_is_search_by_album";

    ArrayList<AudioFile> list_AudioFile = MainActivity.list_AudioFile;
    private int currentPosition_AudioFile = 0;
    private boolean resume_play = false;
    private String path_to_file = "";

    PlayerActivity this_activity = this;

    NT_Toast nt_toast;

    Player_AutoCompleteTextView Search_TV = null; //custom extended class from AutoCompleteTextView

    private PlayerSearchAdapter playerSearchAdapter = null;

    private TextView TV_title = null;
    private TextView TV_artist = null;
    private TextView TV_duration = null;
    private TextView TV_current_position = null;
    private TextView TV_shift_position = null;

    private Animated_ImageButton Btn_next;
    private Animated_ImageButton Btn_prev;
    private Animated_ImageButton Btn_pause_N_play;
    private ImageButton Btn_repeat;
    private ImageButton Btn_shuffle;
    private ImageButton Btn_clr_inp;

    private ImageView IV_album_image;
    private Drawable default_album_art_image;
    private Drawable current_album_art_image;

    private SeekBar SB_duration = null;

    private Boolean is_search_by_artist = true;
    private Boolean is_search_by_title = true;
    private Boolean is_search_by_album = true;
    private Boolean is_search_by_all = true;

    private ExecutorService additional_threads;
    private Future additional_thread_future = null;

    //for updating current position time
    Handler handler_curr_pos = null;

    PlayerService playerService = MainActivity.playerService;
    boolean is_service_bounded = false;

    //for handling incoming phone calls
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    //what to do when unplugging headphones when music is being played
    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            //pause audio
            Btn_pause_N_play.setImageDrawable(
                    ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_player_btn_play));
        }
    };

    //player service will send local broadcatst intents when it's state has been changed (paused, playing, skip to next)
    private final BroadcastReceiver mediaservice_state_changed_Receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if(intent.getAction().equals(PlayerService.ACTION_MEDIAPLAYER_STATE_CHANGED))
            {
                SetPlayerUI(playerService.current_index_in_list);
            }
        }
    };

    LocalBroadcastManager localBroadcastManager;
    AudioManager audioManager;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        Init_Class_Attr();

        if(savedInstanceState != null)
        {
            this.resume_play = savedInstanceState.getBoolean(this.SIS_resume_play, false);
            this.path_to_file = savedInstanceState.getString(this.SIS_path_to_file, "");

            this.is_search_by_all = savedInstanceState.getBoolean(this.SIS_is_search_by_all, true);
            this.is_search_by_artist = savedInstanceState.getBoolean(this.SIS_is_search_by_artist, true);
            this.is_search_by_title = savedInstanceState.getBoolean(this.SIS_is_search_by_title, true);
            this.is_search_by_album = savedInstanceState.getBoolean(this.SIS_is_search_by_album, true);
        }


        Intent intent = getIntent();
        if(intent != null)
        {
            //intent from Playlist activity
            if(intent.hasExtra(PlayerActivity.ID_EXTRA))
            {
                long id_extra = intent.getLongExtra(PlayerActivity.ID_EXTRA, -1);

                //search for file if activity is not restored. If restored - do nothing because file is already playing
                if(savedInstanceState == null)
                {
                    //find index of audio file with retrieved ID
                    boolean is_in_list = false;
                    int index;
                    for (index = 0; index < this.list_AudioFile.size(); index++)
                    {
                        if (list_AudioFile.get(index).id == id_extra)
                        {
                            is_in_list = true;
                            break;
                        }
                    }

                    //if track has been found
                    if(is_in_list)
                    {
                        try
                        {
                            //only play track by path if it's not the same track that were playing
                            if(this.list_AudioFile.get(this.playerService.current_index_in_list).id != id_extra)
                            {
                                //update index of the list
                                this.playerService.current_index_in_list = index;

                                this.playerService.Play_Audio_By_ID(id_extra);
                            }
                        }
                        catch (IllegalArgumentException ex)
                        {
                            PrintError(ex);
                        }

                        //start audio in case of new loaded audiofile
                        this.resume_play = true;
                    }
                    else
                    {
                        throw new IllegalArgumentException();
                    }
                }
            }
            //intent from filesystem activity
            else if(intent.hasExtra(PlayerActivity.PATH_EXTRA))
            {
                String path_extra = intent.getStringExtra(PlayerActivity.PATH_EXTRA);

                //search for file if activity is not restored. If restored - do nothing because file is already playing
                if(savedInstanceState == null)
                {
                    //check if file with this path is already in the list
                    for(int i = 0; i < list_AudioFile.size(); i++)
                    {
                        //if file is already exists in the list
                        if( list_AudioFile.get(i).absolute_path.equals(path_extra))
                        {
                            //position in the list of founded file
                            this.playerService.current_index_in_list = i;
                            break;
                        }
                    }


                    String prev_path_from_player = this.playerService.current_pathToFile;
                    this.playerService.current_pathToFile = list_AudioFile.get(this.playerService.current_index_in_list).absolute_path;

                    try
                    {
                        this.playerService.mediaPlayer.reset();
                        this.playerService.mediaPlayer.setDataSource(this.playerService.current_pathToFile);
                        this.playerService.mediaPlayer.prepare();

                        //if opened same audiofile - continue from current position
                        if(prev_path_from_player.equals(this.playerService.current_pathToFile))
                        {
                            this.playerService.mediaPlayer.seekTo(this.currentPosition_AudioFile);
                        }
                        else //if opened new audiofile - reset position
                        {
                            this.playerService.mediaPlayer.seekTo(0);
                        }
                    }
                    catch (IOException ex)
                    {
                        PrintError(ex);
                    }
                    catch (IllegalArgumentException ex)
                    {
                        PrintError(ex);
                    }
                }

                //start audio in case of new loaded audiofile
                if(savedInstanceState == null)
                {
                    this.resume_play = true;
                }
            }

            // path will be:
            // or from audiolist index
            // or from filesystem fragment path
            // or from standard initialization of this Activity (int 0)
            // or the same if creating player activity not the first time
        }

        //initialising Views of UI
        InitViews();

        //handler for updating current position of audio
        handler_curr_pos = new Handler();
        handler_curr_pos.post(new Runnable()
        {
            private int current_pos = 0;

            @Override
            public void run()
            {
                if(playerService.mediaPlayer.isPlaying())
                {
                    this.current_pos = playerService.mediaPlayer.getCurrentPosition();
                    SB_duration.setProgress(current_pos);
                    TV_current_position.setText(msToMinSec(this.current_pos));
                }

                handler_curr_pos.postDelayed(this, 500);
            }
        });
    }



    @Override
    protected void onStart()
    {
        //in this state activity is visible for user
        super.onStart();

        this.is_stopped = false;
        this.is_started = true;

        //load UI
        SetPlayerUI(playerService.current_index_in_list);
    }


    @Override
    protected void onResume()
    {
        super.onResume();

        is_paused = false;
        is_resumed = true;


        //check if app has been restarted
        //if restarted - UI has been reloaded in the onStart() and no need to reload it
        if(is_started == false) //reload UI if activity is not restated, but resumed
        {
            //reload UI
            SetPlayerUI(playerService.current_index_in_list);
        }

    }


    @Override
    protected void onPause()
    {
        super.onPause();

        //mark that activity is not in active state
        this.is_resumed = false;
        this.is_started = false;

        this.is_paused = true;
    }


    @Override
    protected void onStop()
    {
        super.onStop();

        this.is_stopped = true;
    }


    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
    }


    protected void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putInt(this.SIS_index_list, this.playerService.current_index_in_list);
        savedInstanceState.putInt(this.SIS_audio_cur_pos, this.playerService.mediaPlayer.getCurrentPosition());
        savedInstanceState.putBoolean(this.SIS_resume_play, playerService.mediaPlayer.isPlaying());
        savedInstanceState.putString(this.SIS_path_to_file, this.playerService.current_pathToFile);

        savedInstanceState.putBoolean(this.SIS_is_search_by_all, this.is_search_by_all);
        savedInstanceState.putBoolean(this.SIS_is_search_by_artist, this.is_search_by_artist);
        savedInstanceState.putBoolean(this.SIS_is_search_by_title, this.is_search_by_title);
        savedInstanceState.putBoolean(this.SIS_is_search_by_album, this.is_search_by_album);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        //reload UI if audio file has been edited in Edit_audiofile
        if(requestCode == this.Edit_audio_REQUEST_CODE)
        {
            SetPlayerUI(this.playerService.current_index_in_list);
        }
    }



    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        handler_curr_pos.removeCallbacksAndMessages(null);
        handler_curr_pos = null;

        if (phoneStateListener != null)
        {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        //unregister BroadcastReceivers

        unregisterReceiver(becomingNoisyReceiver);

        localBroadcastManager.unregisterReceiver(mediaservice_state_changed_Receiver);


//        Intent intent_to_main_activity = new Intent(this, MainActivity.class);
//        intent_to_main_activity.setAction(PlayerActivity.ACTION_FINISH_MAIN_ACTIVITY);
//        startActivity(intent_to_main_activity);
    }


    private void Init_Class_Attr()
    {
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        IntentFilter intentFilter = new IntentFilter(PlayerService.ACTION_MEDIAPLAYER_STATE_CHANGED);
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(mediaservice_state_changed_Receiver, intentFilter);

        this.audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        this.nt_toast = new NT_Toast(this);

        this.currentPosition_AudioFile = this.playerService.mediaPlayer.getCurrentPosition();

        this.path_to_file = this.playerService.current_pathToFile;

        //make player play next audio after previous has been finished
        this.playerService.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp)
            {
                playerService.resume_play = true;
                playerService.Skip_To_Next();
                SetPlayerUI(playerService.current_index_in_list);
            }
        });

        this.default_album_art_image = getResources().getDrawable(R.drawable.ic_app_launcher_foreground, null);
        this.current_album_art_image = default_album_art_image;

        this.additional_threads = MainActivity.get_App_Additional_Threads();

    }


    ////////////// BUTTON HANDLERS //////////////

    private void set_View_btn_repeat(Boolean state)
    {
        if(state == true)
        {
            //highlight and shrink button
            Btn_repeat.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_repeat_activ));
            Btn_repeat.setScaleX(0.9f);
            Btn_repeat.setScaleY(0.9f);
        }
        else
        {
            //stop highlighting and expand button
            Btn_repeat.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_repeat));
            Btn_repeat.setScaleX(1);
            Btn_repeat.setScaleY(1);
        }
    }

    ////////////// END OF BUTTON HANDLERS //////////////


    private void set_Search_By(String search_condition)
    {
        search_condition = search_condition.toLowerCase();


        if(search_condition.equals(Playlist_Fragment.SEARCH_BY_ARTIST))
        {
            this.is_search_by_artist = true;
        }

        if(search_condition.equals(Playlist_Fragment.SEARCH_BY_TITLE))
        {
            this.is_search_by_title = true;
        }

        if(search_condition.equals(Playlist_Fragment.SEARCH_BY_ALBUM))
        {
            this.is_search_by_album = true;
        }

        if(search_condition.equals(Playlist_Fragment.SEARCH_BY_ALL))
        {
            this.is_search_by_all = true;
            this.is_search_by_artist = true;
            this.is_search_by_title = true;
            this.is_search_by_album = true;
        }

        if(search_condition.equals(Playlist_Fragment.SEARCH_BY_NONE))
        {
            this.is_search_by_all = false;
            this.is_search_by_artist = false;
            this.is_search_by_title = false;
            this.is_search_by_album = false;
        }
    }


    private int Init_Buttons()
    {
        this.Btn_clr_inp = this.findViewById(R.id.player_clr_inp);
        Btn_clr_inp.setImageResource(android.R.color.transparent); //hide cancel icon
        Btn_clr_inp.setOnClickListener(v ->
        {
            Search_TV.setText("");
        });


        Btn_prev = findViewById(R.id.player_btn_prev);
        Btn_prev.setOnClickListener(v ->
        {
            PlayerActivity activity = (PlayerActivity)v.getContext();
            int index_of_audio = activity.playerService.Skip_to_Previous();

            activity.SetPlayerUI(index_of_audio);
        });


        Btn_pause_N_play = findViewById(R.id.player_btn_pause_n_play);
        Btn_pause_N_play.setOnClickListener(v ->
        {
            PlayerActivity playerActivity = (PlayerActivity)v.getContext();
            playerActivity.playerService.Play_N_Pause();
            playerActivity.SetPlayerUI(playerActivity.playerService.current_index_in_list);
        });



        Btn_next = findViewById(R.id.player_btn_next);
        Btn_next.setOnClickListener(v ->
        {
            PlayerActivity playerActivity = (PlayerActivity)v.getContext();

            int index_of_audio = playerActivity.playerService.Skip_To_Next();
            playerActivity.SetPlayerUI(index_of_audio);
        });



       /* Btn_edit = findViewById(R.id.player_btn_edit);
        Btn_edit.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                PlayerActivity playerActivity = (PlayerActivity)v.getContext();

                Intent intent_edit_audio = new Intent(playerActivity, Edit_audiofile.class);

                String uri = playerActivity.list_AudioFile.get(playerActivity.playerService.current_index_in_list).absolute_path;
                long id = playerActivity.list_AudioFile.get(playerActivity.playerService.current_index_in_list).id;
                String artist = playerActivity.list_AudioFile.get(playerActivity.playerService.current_index_in_list).artist;
                String title = playerActivity.list_AudioFile.get(playerActivity.playerService.current_index_in_list).title;
                String album = playerActivity.list_AudioFile.get(playerActivity.playerService.current_index_in_list).album;
                int current_index_in_list = playerActivity.playerService.current_index_in_list;

                intent_edit_audio.putExtra(Edit_audiofile.URI_EXTRA, uri);
                intent_edit_audio.putExtra(Edit_audiofile.ID_EXTRA, id);
                intent_edit_audio.putExtra(Edit_audiofile.ARTIST_EXTRA, artist);
                intent_edit_audio.putExtra(Edit_audiofile.TITLE_EXTRA, title);
                intent_edit_audio.putExtra(Edit_audiofile.ALBUM_EXTRA, album);
                intent_edit_audio.putExtra(Edit_audiofile.INDEX_EXTRA, current_index_in_list);

                //mark this intent as intent from Player activity
                intent_edit_audio.putExtra(Edit_audiofile.FROM_PLAYER_EXTRA, true);

                try
                {
                    startActivityForResult(intent_edit_audio, PlayerActivity.Edit_audio_REQUEST_CODE);
                }
                catch (ActivityNotFoundException ex)
                {
                    PrintError(ex);
                }
            }
        });*/


        Btn_repeat = findViewById(R.id.player_btn_repeat);
        Btn_repeat.setOnClickListener( v ->
        {
            PlayerActivity playerActivity = (PlayerActivity) v.getContext();

            playerActivity.playerService.Repeat();

            //change repeat button view
            if(playerActivity.playerService.mediaPlayer.isLooping())
            {
                Btn_repeat.setImageDrawable(ContextCompat.getDrawable(playerActivity, R.drawable.ic_player_btn_repeat_activ));

                //get custom toast for this activity
                String message = getString(R.string.player_activity_toast_looping);
                this_activity.nt_toast.show(Gravity.BOTTOM, message, Toast.LENGTH_SHORT);
            }
            else
            {
                Btn_repeat.setImageDrawable(ContextCompat.getDrawable(playerActivity, R.drawable.ic_player_btn_repeat));
            }
        });
        

        Btn_shuffle = findViewById(R.id.player_btn_shuffle);
        Btn_shuffle.setOnClickListener( v ->
        {
            PlayerActivity playerActivity = (PlayerActivity)v.getContext();
            ImageButton this_view = (ImageButton) v;
            if(playerActivity.playerService.is_shuffled)
            {
                playerActivity.playerService.Shuffle(false);
            }
            else
            {
                playerActivity.playerService.Shuffle(true);
            }

            //shuffle button
            if(playerActivity.playerService.is_shuffled)
            {
                this_view.setImageDrawable(ContextCompat.getDrawable(playerActivity, R.drawable.ic_player_btn_shuffle_active));

                //get custom toast for this activity
                String message = getString(R.string.player_activity_toast_shuffled);
                playerActivity.nt_toast.show(Gravity.BOTTOM, message, Toast.LENGTH_SHORT);
            }
            else
            {
                this_view.setImageDrawable(ContextCompat.getDrawable(playerActivity, R.drawable.ic_player_btn_shuffle));
            }
        });


        return 0;
    }


    private void InitViews()
    {
        ConstraintLayout root_elem = findViewById(R.id.audioplayer_rootview_constr_lay);

        Init_Buttons();

        Search_TV = (Player_AutoCompleteTextView)(findViewById(R.id.audioplayer_TB_search));
        this.playerSearchAdapter = new PlayerSearchAdapter(this, this.list_AudioFile, this.playerService);
        Search_TV.setAdapter(playerSearchAdapter);
        Search_TV.setDropDownAnchor(R.id.audioplayer_TB_search);
        Search_TV.setDropDownWidth(Search_TV.getWidth()+Btn_clr_inp.getWidth()); //init with width and keep reinitialising after beforeTextChanged() event

        int color = ContextCompat.getColor(this, R.color.MyAppTheme_colorPrimaryDark50transparent);
        ColorDrawable cd = new ColorDrawable(color);
        Search_TV.setDropDownBackgroundDrawable(cd);

        Search_TV.setOnClickListener(v ->
        {
            Player_AutoCompleteTextView view = (Player_AutoCompleteTextView)v;

            if(view.hasFocus())
            {
                view.clearFocus();
            }
            else
            {
                view.requestFocus();
            }
        });

        Search_TV.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                if(s.toString().isEmpty())
                {
                    Btn_clr_inp.setImageResource(android.R.color.transparent);
                }
                else
                {
                    Btn_clr_inp.setImageResource(R.drawable.ic_cancel);
                }
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                //match dropdown to width of anchored view and right view
                //TODO - not showing dropdownlist if comment this line
                Search_TV.setDropDownWidth(Search_TV.getWidth()+Btn_clr_inp.getWidth());
            }
        });


        Search_TV.setOnFocusChangeListener( (v, hasFocus) ->
        {
            Player_AutoCompleteTextView view = (Player_AutoCompleteTextView) v;
            if(hasFocus)
            {
                view.setCursorVisible(true);

                view.setHintTextColor(getColor(R.color.MyAppTheme_colorAccent));

                if(view.getText().toString().isEmpty() == false)
                {
                    view.showDropDown();
                }
            }
            else
            {
                view.setCursorVisible(false);

                view.setHintTextColor(getColor(R.color.MyAppTheme_colorAccent50transparent));

                if(view.isPopupShowing())
                {
                    view.setIgnoreDismissDropDown(false);
                    view.dismissDropDown();
                }

                //hide softkeyboard
                InputMethodManager inputManager = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(Search_TV.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        });



        TV_artist = findViewById(R.id.Player_TV_artist);

        TV_title = findViewById(R.id.Player_TV_title);
        root_elem.setOnClickListener(v -> TV_title.requestFocus(View.FOCUS_DOWN, null));

        IV_album_image = findViewById(R.id.player_image);
        IV_album_image.setImageDrawable(this.default_album_art_image);

        TV_duration = findViewById(R.id.Player_TV_duration);
        TV_current_position = findViewById(R.id.Player_TV_current_position);
        TV_shift_position = findViewById(R.id.Player_TV_time_shift_pos);


        //hide TV until user wants to shift position by progressbar
        TV_shift_position.setVisibility(View.INVISIBLE);



        /*
          set image of play button to pause image when headset is unplugged or incoming call.
          Handlers may not work if button isn't initialised.
        */
        //init headset unplug handler
        register_BecomingNoisyReceiver();

        //init incoming call handler
        register_callStateListener();



        SB_duration = findViewById(R.id.SB_duration);
        SB_duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            private int progress_ms;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                //refresh display of time shift
                if(fromUser)
                {
                    TV_shift_position.setText(msToMinSec(seekBar.getProgress()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
                //show time shift
                TV_shift_position.setVisibility(View.VISIBLE);
                TV_shift_position.setText(msToMinSec(seekBar.getProgress()));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                //hide time of shift
                TV_shift_position.setVisibility(View.INVISIBLE);

                //get position where user has stopped moving progressbar
                this.progress_ms = seekBar.getProgress();
                TV_current_position.setText(msToMinSec(progress_ms));

                //pass new position to the service
                playerService.on_SB_duration_Changed(this.progress_ms);
            }
        });
    }



    public void SetPlayerUI(int current_index_in_list)
    {
        //cancel of loading previous album art if it's still loading
        if(this.additional_thread_future != null && this.additional_thread_future.isDone() == false)
        {
            this.additional_thread_future.cancel(true);
        }

        AudioFile current_audio = list_AudioFile.get(current_index_in_list);

        TV_artist.setText(current_audio.artist);
        TV_title.setText(current_audio.title);
        TV_title.requestFocus(View.FOCUS_DOWN, null);

        TV_duration.setText(current_audio.duration_in_Min_Sec);
        SB_duration.setMax(current_audio.duration_ms);
        SB_duration.setProgress(this.playerService.mediaPlayer.getCurrentPosition());

        TV_current_position.setText( msToMinSec(this.playerService.mediaPlayer.getCurrentPosition()) );


        //set or hide cancel icon in input bar
        if(Search_TV.getText().toString().isEmpty() == false)
        {
            Btn_clr_inp.setImageResource(R.drawable.ic_cancel);
        }
        else
        {
            Btn_clr_inp.setImageResource(android.R.color.transparent);
        }


        //play n pause button
        if(this.playerService.mediaPlayer.isPlaying())
        {
            Btn_pause_N_play.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_pause));
        }
        else
        {
            Btn_pause_N_play.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_play));
        }


        //shuffle button
        //highlight button
        if(this.playerService.is_shuffled)
        {
            Btn_shuffle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_shuffle_active));
            Btn_shuffle.setScaleX(BTN_PRESS_SCALE);
            Btn_shuffle.setScaleY(BTN_PRESS_SCALE);
        }
        else //stop highlighting
        {
            Btn_shuffle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_shuffle));
            Btn_shuffle.setScaleX(BTN_RELEASE_SCALE);
            Btn_shuffle.setScaleY(BTN_RELEASE_SCALE);
        }


        //repeat button
        //highlight button
        if(this.playerService.is_looping)
        {
            Btn_repeat.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_repeat_activ));
            Btn_repeat.setScaleX(BTN_PRESS_SCALE);
            Btn_repeat.setScaleY(BTN_PRESS_SCALE);
        }
        else //stop highlighting
        {
            Btn_repeat.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_player_btn_repeat));
            Btn_repeat.setScaleX(BTN_RELEASE_SCALE);
            Btn_repeat.setScaleY(BTN_RELEASE_SCALE);
        }


        this.additional_thread_future = this.additional_threads.submit(() ->
        {
            Bitmap album_art = MainActivity.getAlbumArt(PlayerActivity.this,
                                                                current_audio.album_id);

            if(album_art != null)
            {
                PlayerActivity.this.current_album_art_image = new BitmapDrawable(getResources(), album_art);

                PlayerActivity.this.runOnUiThread(() ->
                {
                    PlayerActivity.this.IV_album_image
                            .setImageDrawable(PlayerActivity.this.current_album_art_image);
                });
            }
            else
            {
                PlayerActivity.this.current_album_art_image = PlayerActivity.this.default_album_art_image;

                PlayerActivity.this.runOnUiThread(() ->
                {
                    PlayerActivity.this.IV_album_image
                            .setImageDrawable(PlayerActivity.this.default_album_art_image);
                });
            }
        });
    }



    private void register_BecomingNoisyReceiver()
    {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(this.becomingNoisyReceiver, intentFilter);
    }


    //Handle incoming phone calls
    private void register_callStateListener()
    {
        //init variables

        // Get the telephony manager
        this.telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        //Starting listening for PhoneState changes
        this.phoneStateListener = new PhoneStateListener()
        {
            @Override
            public void onCallStateChanged(int state, String incomingNumber)
            {

                if (playerService.mediaPlayer == null) return;

                //if at least one call exists or the phone is ringing
                //pause the MediaPlayer
                switch(state)
                {
                    //switch button to paused state
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                    {
                        //switch button to pause state
                        Btn_pause_N_play.setImageDrawable(
                                ContextCompat.getDrawable(getBaseContext(), R.drawable.ic_player_btn_play));
                        break;
                    }
                }
            }
        };

        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }



    public static String msToMinSec(int ms)
    {
        return  String.format(Locale.getDefault(), "%d:%02d", ms/1000/60, ms/1000%60);
    }


    void PrintError(Exception ex)
    {
        if(ex.getClass() == IOException.class)
        {

        }
        else if(ex.getClass() == IllegalArgumentException.class)
        {
            ex = (IllegalArgumentException)ex;
        }

        ex.printStackTrace();
        Log.e("\n>> onError: ", "Error type: " + ex.toString() + "\nError message:" + ex.getMessage());
    }
}