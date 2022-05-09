package com.prplx.soundfile;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


public class PlayerService extends IntentService
{
    public class LocalBinder extends Binder
    {
        PlayerService getService()
        {
            // Return this instance of PlayerService so clients can call public methods
            return PlayerService.this;
        }
    }

    public final static String ACTION_MEDIAPLAYER_STATE_CHANGED = "ACTION_MEDIAPLAYER_STATE_CHANGED";

    private static final String PLAYER_SP_FILE_NAME = "SP_player";
    private static final String PLAYER_SP_VOLUME = "volume";
    private static final String PLAYER_SP_TRACK_ID = "id";
    private static final String PLAYER_SP_TRACK_POSITION = "position";
    private static final String PLAYER_SP_IS_SHUFFLED = "is_shuffled";
    private static final String PLAYER_SP_IS_LOOPING = "is_looping";

    private final String CHANNEL_ID = "273";

    public final int FOREGROUND_NOTIFICATION_ID = 1;

    public static final String ACTION_PLAY = "action_play";
    public static final String ACTION_PAUSE = "action_pause";
    public static final String ACTION_PLAY_PAUSE = "action_play_pause";
    public static final String ACTION_SKIP_TO_NEXT = "action_next";
    public static final String ACTION_SKIP_TO_PREVIOUS = "action_previous";

    ArrayList<AudioFile> list_AudioFile = MainActivity.list_AudioFile;
    public int current_index_in_list = 0;
    public boolean resume_play = false;
    public boolean is_looping = false;
    public boolean is_shuffled = false;

    public long current_track_id = -1;

    //storage for indices when user shuffles playlist
    int[] shuffled_indicies = null;
    int shuffled_indx = 0;

    private final String SERVICE_CHANNEL_ID = "2";

    public MediaPlayer mediaPlayer;

    private MediaSessionCompat mediaSession;
    private MediaSessionCompat.Callback mediaSessionCallback;
    private PlaybackStateCompat.Builder mediaSession_state_builder;
    private MediaMetadataCompat.Builder mediaSession_metadata_builder;

    NotificationCompat.Builder player_notification_builder;

    public String current_pathToFile = "";

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    AudioManager audioManager;

    private AudioAttributes playerService_playbackAttributes;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audio_focus_gain_request;

    //what to do when unplugging headphones when music is being played
    public BroadcastReceiver becomingNoisyReceiver;
    private boolean is_becomingNoisyReceiver_registered;


    //for handling incoming phone calls
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    //constructor
    public PlayerService()
    {
        super("Audio player service");
    }


    private ExecutorService additional_threads;
    private Future additional_threads_future;
    private Bitmap default_large_icon = null;
    private Bitmap current_large_icon = null;

    private long prev_notification_update_time_ms = -1;
    private final long NOTIFICATION_UPDATE_MIN_TIME_MS = 200;

    private float cur_volume_ = 0.75f;

    @Override
    public void onCreate()
    {
        super.onCreate();

        // initialise PlayerService object attributes
        Init_Attrs();

        //create channel for notification
        this.CreateServiceNotificationChannel();
        this.player_notification_builder = Create_Player_Notification_Builder(mediaPlayer.isPlaying());
        Notification notification = Create_Player_Notification(this.player_notification_builder);

        //start service in foreground
        startForeground(this.FOREGROUND_NOTIFICATION_ID, notification);
        this.prev_notification_update_time_ms = Instant.now().toEpochMilli();
    }



    @Override
    public void onStart(@Nullable Intent intent, int startId)
    {
        super.onStart(intent, startId);

        switch (intent.getAction())
        {
            case ACTION_PLAY_PAUSE:
            {
                boolean is_playing = mediaPlayer.isPlaying();
                if(is_playing)
                {
                    this.Pause();
                }
                else
                {
                    this.Play();
                }
                break;
            }

            case ACTION_SKIP_TO_NEXT:
            {
                this.Skip_To_Next();
                break;
            }

            case ACTION_SKIP_TO_PREVIOUS:
            {
                this.Skip_to_Previous();
                break;
            }

            default:
            {
                this.Pause();
            }
        }

        Intent broadcast_intent = new Intent(PlayerService.ACTION_MEDIAPLAYER_STATE_CHANGED);
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(broadcast_intent);

        MediaButtonReceiver.handleIntent(mediaSession, intent);
    }


    @Override
    public void onDestroy()
    {
        super.onDestroy();

        audioManager.abandonAudioFocusRequest(PlayerService.this.audio_focus_gain_request);

        SP_Save_TrackID();
        SP_Save_TrackPosition();
        SP_Save_IsShuffled();
        SP_Save_IsLooping();

        if(this.mediaPlayer != null)
        {
            this.mediaPlayer.stop();
            this.mediaPlayer.release();
            this.mediaPlayer = null;
        }

        this.mediaSession.setActive(false);
        this.mediaSession.release();

        if (phoneStateListener != null)
        {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        unregister_BecomingNoisyReceiver();
    }


    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId)
    {
        return super.onStartCommand(intent, flags, startId);
    }


    private void register_BecomingNoisyReceiver()
    {
        if(this.is_becomingNoisyReceiver_registered == false)
        {
            IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            registerReceiver(becomingNoisyReceiver, intentFilter);

            this.is_becomingNoisyReceiver_registered = true;
        }
    }

    private void unregister_BecomingNoisyReceiver()
    {
        if(this.is_becomingNoisyReceiver_registered)
        {
            unregisterReceiver(becomingNoisyReceiver);
            this.is_becomingNoisyReceiver_registered = false;
        }
    }


    public void Play_Audio_By_ID(long arg_id)
    {
        boolean is_present = false;

        //get index of an audiofile in current list
        for(int i = 0; i < list_AudioFile.size(); i++)
        {
            if(list_AudioFile.get(i).id == arg_id)
            {
                is_present = true;
                this.current_index_in_list = i;
                break;
            }
        }

        if(is_present)
        {
            this.resume_play = true;
            this.SetAudio(current_index_in_list);
        }
        else
        {
            throw new ArrayIndexOutOfBoundsException();
        }
    }


    public void Play_Audio_By_Path(String path)
    {
        boolean is_present = false;

        //get index of an audiofile in current list
        for(int i = 0; i < list_AudioFile.size(); i++)
        {
            if(list_AudioFile.get(i).absolute_path.equals(path))
            {
                is_present = true;
                this.current_index_in_list = i;
                break;
            }
        }

        if(is_present)
        {
            this.SetAudio(this.current_index_in_list);
        }
        else
        {
            throw new InvalidPathException(path, "Can't find track with this path");
        }
    }


    public void Play_N_Pause()
    {
        this.current_track_id = this.list_AudioFile.get(this.current_index_in_list).id;

        if(this.mediaPlayer.isPlaying())
        {
            this.Pause();
        }
        else
        {
            this.Play();
        }
    }


    private void Play()
    {
        int res = audioManager.requestAudioFocus(PlayerService.this.audio_focus_gain_request);

        if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        {
            PlaybackStateCompat state =  this.mediaSession_state_builder
                    .setState(PlaybackStateCompat.STATE_PLAYING,
                            (long)this.mediaPlayer.getCurrentPosition(), 1.0f)
                    .build();

            mediaSession.setPlaybackState(state);

            this.register_BecomingNoisyReceiver();

            this.mediaPlayer.start();

            Intent intent = new Intent(PlayerService.ACTION_MEDIAPLAYER_STATE_CHANGED);
            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);

            this.player_notification_builder = Create_Player_Notification_Builder(mediaPlayer.isPlaying());
            PlayerService.this.Update_Notification(Create_Player_Notification(this.player_notification_builder));
        }
    }


    private void Pause()
    {
        this.mediaPlayer.pause();

        PlaybackStateCompat state = PlayerService.this.mediaSession_state_builder
                .setState(PlaybackStateCompat.STATE_PAUSED,
                        (long)this.mediaPlayer.getCurrentPosition(), 0.0f)
                .build();

        mediaSession.setPlaybackState(state);

        this.unregister_BecomingNoisyReceiver();

        Intent intent = new Intent(PlayerService.ACTION_MEDIAPLAYER_STATE_CHANGED);
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);

        this.player_notification_builder = Create_Player_Notification_Builder(mediaPlayer.isPlaying());
        PlayerService.this.Update_Notification(Create_Player_Notification(this.player_notification_builder));
    }


    public int Skip_To_Next()
    {
        if(is_shuffled)
        {
            //check if the index is not out of bounds
            if(shuffled_indx == list_AudioFile.size()-1)
            {
                shuffled_indx = 0;
                current_index_in_list = shuffled_indicies[shuffled_indx];
            }
            else
            {
                ++shuffled_indx;
                current_index_in_list = shuffled_indicies[shuffled_indx];
            }
        }
        else
        {
            //check if index is not out of bounds
            if(current_index_in_list == list_AudioFile.size()-1)
            {
                current_index_in_list = 0;
            }
            else
            {
                current_index_in_list++;
            }
        }

        //set next audio
        this.SetAudio(current_index_in_list);
        return current_index_in_list;
    }


    public int Skip_to_Previous()
    {
        if(is_shuffled)
        {
            //check if the index is not out of bounds
            if(shuffled_indx == 0)
            {
                shuffled_indx = shuffled_indicies.length-1;
                current_index_in_list = shuffled_indicies[shuffled_indx];
            }
            else
            {
                --shuffled_indx;
                current_index_in_list = shuffled_indicies[shuffled_indx];
            }
        }
        else
        {
            if(current_index_in_list == 0)
            {
                current_index_in_list = list_AudioFile.size()-1;
            }
            else
            {
                current_index_in_list--;
            }
        }

        //set previous audio
        this.SetAudio(current_index_in_list);
        return current_index_in_list;
    }


    public void Repeat()
    {
        if(this.mediaPlayer.isLooping())
        {
            this.mediaPlayer.setLooping(false);
            this.is_looping = false;
        }
        else
        {
            this.mediaPlayer.setLooping(true);
            this.is_looping = true;
        }
    }


    public int Shuffle(boolean flag)
    {
        if(flag)
        {
            //create new array of indices
            final int AR_SIZE = MainActivity.list_AudioFile.size();
            this.shuffled_indicies = new int[AR_SIZE];
            this.shuffled_indx = 0;

            for(int i = 0; i < AR_SIZE; i++)
            {
                shuffled_indicies[i] = i;
            }
            shuffleArray(shuffled_indicies);

            this.is_shuffled = true;
        }
        else
        {
            this.is_shuffled = false;
        }

        return 0;
    }

    static void shuffleArray(@NonNull int[] ar)
    {
        Random rnd = new Random();
        rnd.setSeed(System.currentTimeMillis());

        for (int i = ar.length - 1; i > 0; i--)
        {
            int index = rnd.nextInt(i + 1);
            int a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }


    /**
     * Initialise attributes of PlayerService object
     */
    private void Init_Attrs()
    {
        this.audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);


        //init player
        this.mediaPlayer = new MediaPlayer();

        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
        );

        // set max volume of media player
        SetVolume(100);

        //init with saved track ID or -1
        this.current_track_id = SP_Load_TrackID();
        this.is_looping = SP_Load_IsLooping();
        this.mediaPlayer.setLooping(this.is_looping);

        //if list is not empty - initialise media player
        if(this.list_AudioFile.size() > 0)
        {
            //if track ID has been saved - search it in the list
            if(current_track_id != -1)
            {
                for(int i = 0; i < this.list_AudioFile.size(); i++)
                {
                    if(this.list_AudioFile.get(i).id == this.current_track_id)
                    {
                        this.current_pathToFile = this.list_AudioFile.get(i).absolute_path;
                        this.current_index_in_list = i;

                        break;
                    }
                }
            }
            else
            {
                this.current_pathToFile = this.list_AudioFile.get(0).absolute_path;
                this.current_index_in_list = 0;
            }

            //shuffle tracks if before it has been shuffled
            this.is_shuffled = SP_Load_IsShuffled();
            Shuffle(this.is_shuffled);

            try
            {
                this.mediaPlayer.setDataSource(this.current_pathToFile);
                this.mediaPlayer.prepare();
                this.mediaPlayer.seekTo(SP_Load_TrackPosition());
            }
            catch (Exception ex)
            {
                PrintError(ex);
            }
        }


        mediaPlayer.setOnCompletionListener(mp ->
        {
            PlayerService.this.resume_play = true;
            PlayerService.this.Skip_To_Next();
        });


        this.audioFocusChangeListener = focusChange -> {

            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN: {
                    if (this.resume_play == true) {
                        Play();
                    }
                    break;
                }
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                    this.resume_play = mediaPlayer.isPlaying();
                    Pause();
                }
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                }
            }
        };

        this.playerService_playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        this.audio_focus_gain_request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(PlayerService.this.playerService_playbackAttributes)
                .setOnAudioFocusChangeListener(PlayerService.this.audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .build();


        //for headset
        this.becomingNoisyReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                //pause audio
                if(PlayerService.this.mediaPlayer != null)
                {
                    PlayerService.this.Pause();
                }
            }
        };

        this.default_large_icon = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.ic_app_launcher);
        this.current_large_icon = this.default_large_icon;
        this.additional_threads = MainActivity.get_App_Additional_Threads();

        //for incoming calls
        Init_callStateListener();

        Init_MediaSession();

        this.player_notification_builder = Create_Player_Notification_Builder(this.mediaPlayer.isPlaying());
    }



    private NotificationCompat.Builder Create_Player_Notification_Builder(boolean is_playing)
    {
        // when user presses on the notification - opens Player activity
        Intent content_intent = new Intent(this, PlayerActivity.class);
        PendingIntent pending_intent_to_player_activity = PendingIntent.getActivity(this, 567, content_intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notification_builder = new NotificationCompat
                .Builder(this, this.SERVICE_CHANNEL_ID)
                .setContentIntent(pending_intent_to_player_activity)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_app_launcher_foreground)
                .addAction(R.drawable.ic_button_prev, "Skip to previous", Get_Notification_PendingIntent(ACTION_SKIP_TO_PREVIOUS));

        if(is_playing == true)
        {
            notification_builder.addAction(R.drawable.ic_button_pause, "Play and pause", Get_Notification_PendingIntent(ACTION_PLAY_PAUSE));
        }
        else
        {
            notification_builder.addAction(R.drawable.ic_button_play, "Play and pause", Get_Notification_PendingIntent(ACTION_PLAY_PAUSE));
        }

        notification_builder
                .addAction(R.drawable.ic_button_next, "Skip to next", Get_Notification_PendingIntent(ACTION_SKIP_TO_NEXT))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(PlayerService.this.mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        return notification_builder;
    }


    public void on_SB_duration_Changed(int progress_position)
    {
        mediaPlayer.seekTo(progress_position);
    }


    /**
     * Sets DataSource of the mediaplayer to the audio of the PlayerService.list_AudioFile.
     * Sets PlayerService.current_index_in_list
     * Sets PlayerService.current_pathToFile.
     * Sets this.current_track_id
     * @param arg_current_index_in_list Index of the audio file in the PlayerService.list_AudioFile
     */
    private void SetAudio(int arg_current_index_in_list)
    {
        //cancel of loading previous large icon
        if(this.additional_threads_future != null && additional_threads_future.isDone() == false)
        {
            this.additional_threads_future.cancel(true);
        }

        //check for correct index
        if(arg_current_index_in_list < 0 || arg_current_index_in_list > list_AudioFile.size())
        {
            throw new IndexOutOfBoundsException();
        }

        if(mediaPlayer.isPlaying())
        {
            //if setting audio while previous audio is playing - immediately start new audio after it has been set
            this.resume_play = true;

            mediaPlayer.stop();
        }

        this.current_index_in_list = arg_current_index_in_list;

        //update path
        this.current_pathToFile = list_AudioFile.get(arg_current_index_in_list).absolute_path;

        //save current track ID
        this.current_track_id = list_AudioFile.get(arg_current_index_in_list).id;

        try
        {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(this.current_pathToFile);
            mediaPlayer.prepare();
        }
        catch (Exception ex)
        {
            Log.d(null, ex.getMessage());
        }

        AudioFile audio = Get_Audio_By_Index(this.current_index_in_list);

        this.mediaSession_metadata_builder
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, audio.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audio.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, audio.duration_ms)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, this.current_large_icon);

        MediaMetadataCompat mediaMetadata = this.mediaSession_metadata_builder.build();
        this.mediaSession.setMetadata(mediaMetadata);

        this.additional_threads_future = this.additional_threads.submit(() ->
        {
            Bitmap album_art = MainActivity.getAlbumArt(PlayerService.this,  audio.album_id);

            if(album_art != null)
            {
                PlayerService.this.current_large_icon = album_art;
            }
            else
            {
                PlayerService.this.current_large_icon = PlayerService.this.default_large_icon;
            }

            MediaMetadataCompat mediaMetadataCompat = PlayerService.this.mediaSession_metadata_builder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                            PlayerService.this.current_large_icon)
                    .build();
            PlayerService.this.mediaSession.setMetadata(mediaMetadataCompat);

            this.player_notification_builder = Create_Player_Notification_Builder(mediaPlayer.isPlaying());
            PlayerService.this.Update_Notification(Create_Player_Notification(this.player_notification_builder));
        });


        //if repeat button was pressed on previous audio
        if(this.is_looping)
        {
            mediaPlayer.setLooping(true);
        }

        if(resume_play == true)
        {
            this.Play();
            resume_play = false;
        }
    }


    /**
     * Android will drop notifications it they will be refreshing too fast.
     * This function controls update rate of notifications.
     * @param notification that will be updated
     */
    private void Update_Notification(Notification notification)
    {
        try
        {
            //get how many milliseconds passed from previous notification update
            long time_update_delta = Instant.now().toEpochMilli() - this.prev_notification_update_time_ms;

            // if previous update was less then NOTIFICATION_UPDATE_MIN_TIME_MS ago -
            // wait until it's more or equals NOTIFICATION_UPDATE_MIN_TIME_MS
            if(time_update_delta < this.NOTIFICATION_UPDATE_MIN_TIME_MS)
            {
                Thread.sleep(this.NOTIFICATION_UPDATE_MIN_TIME_MS - time_update_delta);
            }

            NotificationManagerCompat.from(PlayerService.this).notify(PlayerService.this.FOREGROUND_NOTIFICATION_ID, notification);
            PlayerService.this.prev_notification_update_time_ms = Instant.now().toEpochMilli();
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }


    /** Saves to Shared Preferences is player repeating track */
    public void SP_Save_IsLooping()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PlayerService.PLAYER_SP_IS_LOOPING, this.is_looping);
        editor.apply();
    }

    /** Returns is player looping track from Shared Preferences */
    public boolean SP_Load_IsLooping()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        return sharedPreferences.getBoolean(PlayerService.PLAYER_SP_IS_LOOPING, false);
    }


    /** Saves to Shared Preferences has user shuffled tracks */
    public void SP_Save_IsShuffled()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PlayerService.PLAYER_SP_IS_SHUFFLED, this.is_shuffled);
        editor.apply();
    }

    /** Returns from Shared Preferences has user shuffled tracks  */
    public boolean SP_Load_IsShuffled()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        return sharedPreferences.getBoolean(PlayerService.PLAYER_SP_IS_SHUFFLED, false);
    }


    /** Saves current track position to Shared Preferences */
    public void SP_Save_TrackPosition()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(PlayerService.PLAYER_SP_TRACK_POSITION, this.mediaPlayer.getCurrentPosition());
        editor.apply();
    }

    /** Returns current track position from Shared Preferences or returns 0*/
    public int SP_Load_TrackPosition()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        return sharedPreferences.getInt(PlayerService.PLAYER_SP_TRACK_POSITION, 0);
    }


    /** Saves current track ID to Shared Preferences */
    public void SP_Save_TrackID()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(PlayerService.PLAYER_SP_TRACK_ID, this.current_track_id);
        editor.apply();
    }

    /** Returns saved track ID from Shared Preferences or returns -1 */
    public long SP_Load_TrackID()
    {
        SharedPreferences sharedPreferences = getSharedPreferences(PlayerService.PLAYER_SP_FILE_NAME, MODE_PRIVATE);
        return sharedPreferences.getLong(PlayerService.PLAYER_SP_TRACK_ID, -1);
    }

    public float GetVolume() {
        return cur_volume_;
    }

    /**
     *
     * Sets volume of media player
     * @param value Media player volume level to be set. Range from 0 to 1.
     * @return previous volume
     */
    public float SetVolume(float value) {
        float prev_volume;

        if (value <= 0) {
            value = 0.0f;
        } else if(value >= 1.0f) {
            value = 1.0f;
        }

        prev_volume = cur_volume_;
        cur_volume_ = value;
        this.mediaPlayer.setVolume(value, value);

        return prev_volume;
    }


    //Handle incoming phone calls
    private void Init_callStateListener()
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
                if (mediaPlayer != null)
                {
                    switch(state)
                    {
                        //if at least one call exists or the phone is ringing
                        //pause the MediaPlayer
                        case TelephonyManager.CALL_STATE_RINGING:
                            if(mediaPlayer.isPlaying())
                            {
                                Pause();
                            }
                            break;

                        case TelephonyManager.CALL_STATE_IDLE:

                            break;
                    }
                }
            }
        };

        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }



    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }


    @Override
    protected void onHandleIntent(@Nullable Intent intent)
    {

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


    public long getCurrentTrackID()
    {
        return this.current_track_id;
    }


    @Override
    public boolean onUnbind(Intent intent)
    {
        if(this.mediaSession != null)
        {
            this.mediaSession.release();
        }

        return super.onUnbind(intent);
    }


    private void CreateServiceNotificationChannel()
    {
        CharSequence name = "Player notification channel";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(this.SERVICE_CHANNEL_ID, name, importance);
        channel.enableVibration(false);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }


    private void Init_MediaSession()
    {
        this.mediaSession = new MediaSessionCompat(this, PlayerService.class.getName());

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        this.mediaSession_state_builder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SEEK_TO);
        this.mediaSession_state_builder.setState(PlaybackStateCompat.STATE_NONE, 0, 0);


        mediaSession.setPlaybackState(mediaSession_state_builder.build());

        this.mediaSession_metadata_builder = new MediaMetadataCompat.Builder();

        this.mediaSessionCallback = new MediaSessionCompat.Callback()
        {
            long key_prev_press_time = -1;


            @Override
            public void onPlay()
            {
                super.onPlay();

                PlayerService.this.Play();
            }


            @Override
            public void onPause()
            {
                super.onPause();

                PlayerService.this.Pause();
            }


            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent)
            {
                KeyEvent keyEvent = (KeyEvent) mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                int keycode = keyEvent.getKeyCode();

                long key_press_time = System.currentTimeMillis();


                if(keyEvent != null)
                {
                    switch (keyEvent.getAction())
                    {
                        case KeyEvent.ACTION_DOWN:
                        {
                            if( (key_press_time - key_prev_press_time) < 500)
                            {
                                this.key_prev_press_time = 0;

                                resume_play = true;
                                PlayerService.this.Skip_To_Next();
                            }
                        }
                    }
                }

                key_prev_press_time = key_press_time;

                return super.onMediaButtonEvent(mediaButtonEvent);
            }
        };

        mediaSession.setCallback(mediaSessionCallback);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        PendingIntent bmr_intent = PendingIntent.
                getBroadcast(this, 0, mediaButtonIntent, 0);
        mediaSession.setMediaButtonReceiver(bmr_intent);

        AudioFile audio = Get_Audio_By_Index(this.current_index_in_list);

        if(audio != null)
        {
            this.mediaSession_metadata_builder
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, audio.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audio.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, audio.duration_ms)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, this.current_large_icon);
        }

        MediaMetadataCompat mediaMetadata = this.mediaSession_metadata_builder.build();
        this.mediaSession.setMetadata(mediaMetadata);

        this.additional_threads_future = this.additional_threads.submit(() ->
        {
            Bitmap album_art = MainActivity.getAlbumArt(PlayerService.this,  audio.album_id);

            if(album_art != null)
            {
                PlayerService.this.current_large_icon = album_art;
            }
            else
            {
                PlayerService.this.current_large_icon = PlayerService.this.default_large_icon;
            }

            MediaMetadataCompat mediaMetadataCompat = PlayerService.this.mediaSession_metadata_builder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                            PlayerService.this.current_large_icon)
                    .build();
            PlayerService.this.mediaSession.setMetadata(mediaMetadataCompat);
            this.player_notification_builder = Create_Player_Notification_Builder(mediaPlayer.isPlaying());
            PlayerService.this.Update_Notification(Create_Player_Notification(this.player_notification_builder));
        });

        mediaSession.setActive(true);
    }


    private PendingIntent Get_Notification_PendingIntent(@NonNull String action)
    {
        PendingIntent pendingIntent;
        Intent intent = new Intent(this, PlayerService.class);
        intent.setClass(this, PlayerService.class);

        switch (action)
        {
            case ACTION_PLAY_PAUSE:
            {
                intent.setAction(ACTION_PLAY_PAUSE);
                break;
            }
            case ACTION_SKIP_TO_PREVIOUS:
            {
                intent.setAction(ACTION_SKIP_TO_PREVIOUS);
                break;
            }
            case ACTION_SKIP_TO_NEXT:
            {
                intent.setAction(ACTION_SKIP_TO_NEXT);
                break;
            }
            default:
            {
                intent.setAction(ACTION_PAUSE);
            }
        }

        pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return pendingIntent;
    }


    /**
     * Returns mediaStyle notification based on this.mediaSession mediaMetadata.
     * Before invoking this function set mediaMetadata of this.mediaSession.
     * @return mediaStyle notification based on current state of this.mediaSession.mediaMetadata
     */
    private Notification Create_Player_Notification(NotificationCompat.Builder notification_builder)
    {
        StringBuilder str_artist = new StringBuilder("None");
        StringBuilder str_title = new StringBuilder("None");
        StringBuilder stringBuilder_buffer = new StringBuilder();
        Bitmap large_icon = null;

        MediaControllerCompat controllerCompat = mediaSession.getController();
        MediaMetadataCompat mediaMetadataCompat = controllerCompat.getMetadata();
        if(mediaMetadataCompat != null)
        {
            stringBuilder_buffer.append(mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            if(stringBuilder_buffer.length() != 0)
            {
                str_artist.delete(0, str_artist.length());
                str_artist.append(stringBuilder_buffer);
                stringBuilder_buffer.delete(0, stringBuilder_buffer.length());
            }

            stringBuilder_buffer.append(mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            if(stringBuilder_buffer.length() != 0)
            {
                str_title.delete(0, str_title.length());
                str_title.append(stringBuilder_buffer);
                stringBuilder_buffer.delete(0, stringBuilder_buffer.length());
            }

            large_icon = mediaMetadataCompat.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            if(large_icon != null)
            {
                large_icon = Bitmap.createScaledBitmap(large_icon, 256, 256, true);
            }
        }

        Notification notification = notification_builder
                .setLargeIcon(large_icon)
                .setContentText(str_artist)
                .setContentTitle(str_title)
                .build();

        return notification;
    }


    AudioFile Get_Audio_By_Index(@NonNull int index)
    {
        return list_AudioFile.get(index);
    }


    AudioFile GetCurrentAudio()
    {
        return list_AudioFile.get(this.current_index_in_list);
    }
}