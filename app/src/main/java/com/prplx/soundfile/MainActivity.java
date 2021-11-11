package com.prplx.soundfile;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.collection.LruCache;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

public class MainActivity extends AppCompatActivity
{
    public static String PACKAGE_NAME;

    private final String READ_PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE;
    private final int PERMISSION_REQUEST_CODE = 672;

    private final int NOTIFICATION_ID = 1;
    private final String MAIN_CHANNEL_ID = "1";

    private float BTN_PRESS_SCALE = Animated_ImageButton.BTN_PRESS_SCALE;
    private float BTN_RELEASE_SCALE = Animated_ImageButton.BTN_RELEASE_SCALE;

    public static ArrayList<AudioFile> list_AudioFile;

    private static LruCache<Long, Bitmap> album_images_cache;

    private static ExecutorService application_additional_threads;

    public static PlayerService playerService = null;
    boolean is_service_bounded = false;

    public int audio_player_volume = 100;

    //if creating player activity for the first time this var is true
    private boolean init_player = true;

    private Animated_ImageButton btn_player;
    private Animated_ImageButton btn_playlists;
    private Animated_ImageButton btn_files;

    private TextView tv_app_version;

    private MainActivity this_activity = this;

    private ServiceConnection service_connection;


    static
    {
        Init_AlbumArt_Cache();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        //in this app user by volume buttons changes only media volume
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        //create notification channel for notify user that he needs to grant access to files
        this.CreateMainNotificationChannel();

        Init_Attrs();

        Init_Views();

        if(is_service_bounded == false)
        {
            //check permission for reading user's data
            if(ContextCompat.checkSelfPermission(this, READ_PERMISSION_STRING) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this, new String[]{READ_PERMISSION_STRING}, PERMISSION_REQUEST_CODE);
            }
            else
            {
                GetAudioFiles();

                Intent service_intent = new Intent(this, PlayerService.class);
                bindService(service_intent, this.service_connection, BIND_AUTO_CREATE);
            }
        }

        Intent intent = getIntent();
        this.onNewIntent(intent);
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if(is_service_bounded)
        {
            unbindService(this.service_connection);
            this.is_service_bounded = false;
        }
    }


    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        if(intent != null)
        {
            String intent_action = intent.getAction();

            if(intent_action != null)
            {
                switch (intent_action)
                {
                    //intent from file explorer to open an audio
                    case Intent.ACTION_VIEW:
                    {
                        String path = getPathFromUri(this, intent.getData());

                        Intent player_intent = new Intent(this, PlayerActivity.class);
                        player_intent.putExtra(PlayerActivity.PATH_EXTRA, path);
                        startActivity(player_intent);

                        break;
                    }
                }
            }
        }
    }


    private void Init_Attrs()
    {
        MainActivity.PACKAGE_NAME = getApplicationContext().getPackageName();

        this.service_connection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service)
            {
                // We've bound to PlayerService, cast the IBinder and get PlayerService instance
                PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
                playerService = binder.getService();
                is_service_bounded = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0)
            {
                is_service_bounded = false;
            }
        };
    }


    private void Init_Views()
    {
        this.tv_app_version = this.findViewById(R.id.main_activity_tv_app_version);
        String tv_app_version_str = getString(R.string.main_activity_tv_app_version);
        tv_app_version_str = tv_app_version_str.concat(" " + BuildConfig.VERSION_NAME);
        tv_app_version.setText(tv_app_version_str);


        this.btn_player = this.findViewById(R.id.main_activity_player_btn);
        btn_player.setOnClickListener(v ->
        {
            Intent intent = new Intent(this_activity, PlayerActivity.class);
            startActivity(intent);
        });


        this.btn_playlists = this.findViewById(R.id.main_activity_playlist_btn);
        btn_playlists.setOnClickListener(v ->
        {
            Intent intent = new Intent(this_activity, PlaylistsActivity.class);
            startActivity(intent);
        });


        this.btn_files = this.findViewById(R.id.main_activity_files_btn);
        btn_files.setOnClickListener(v ->
        {
            Intent intent = new Intent(this_activity, Files_Activity.class);
            startActivity(intent);
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        switch(requestCode)
        {
            case PERMISSION_REQUEST_CODE:
            {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    GetAudioFiles();

                    Intent service_intent = new Intent(this, PlayerService.class);
                    bindService(service_intent, this.service_connection, BIND_AUTO_CREATE);
                }
                else
                {
                    Intent intent = new Intent(this, MainActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                    NotificationCompat.Builder notification_Builder = new NotificationCompat.Builder(this, MAIN_CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_app_launcher)
                            .setContentTitle("SoundFile")
                            .setContentText("Storage permission is required for this app")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setVibrate(new long[]{1000, 1000})
                            .setAutoCancel(true);

                    notification_Builder.setContentIntent(pendingIntent);

                    NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                    //send notification to user
                    notificationManager.notify(NOTIFICATION_ID, notification_Builder.build());

                    //close app
                    this.finish();
                }
            }
        }
    }


    private void CreateMainNotificationChannel()
    {
        CharSequence name = "Main notification channel";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel channel = new NotificationChannel(this.MAIN_CHANNEL_ID, name, importance);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }



    //get Audio files from MediaStore
    private void GetAudioFiles()
    {
        //init list for storing audios
        list_AudioFile = new ArrayList<AudioFile>();

        //which columns is needed in cursor
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID};

        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC, null, null);

        if(cursor == null)
        {
            //query failed
        }
        else if(cursor.moveToFirst() == false)
        {
            String toast_text = "No aduio on the device";

            NT_Toast nt_toast = new NT_Toast(this);
            nt_toast.show(Gravity.CENTER, toast_text, Toast.LENGTH_LONG);
        }
        else
        {
            do
            {
                try
                {
                    list_AudioFile.add( new AudioFile(cursor) );
                }
                catch(Exception ex)
                {
                    Log.d(null, ">>Error: " + ex.getMessage());
                }
            }
            while(cursor.moveToNext());
        }

        cursor.close();

        //sort list by file name
        Collections.sort(MainActivity.list_AudioFile);
    }



    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
    }


    public static String getPathFromUri(@NonNull Context context, @NonNull Uri uri)
    {
        String[] proj = { MediaStore.Audio.Media.DATA };
        Cursor cursor = context.getContentResolver().query(uri, proj, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        cursor.moveToFirst();
        String result = cursor.getString(column_index);
        cursor.close();

        return result;
    }


    public static void hideSoftKeyboard(@NonNull Activity activity)
    {
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);

        if(inputMethodManager.isAcceptingText())
        {
            inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
        }
    }


    private static void Add_AlbumArt_To_Cache(long key_ID, Bitmap bitmap_art)
    {
        if (Get_AlbumArt_From_Cache(key_ID) == null)
        {
            MainActivity.album_images_cache.put(key_ID, bitmap_art);

            String log_msg = String.format(Locale.getDefault(), "Album art cache size: %d kb / %d kb",
                    album_images_cache.size(), album_images_cache.maxSize());
            Log.v("my_verbose", log_msg);
        }
    }


    private static Bitmap Get_AlbumArt_From_Cache(@NonNull long key_ID)
    {
        return MainActivity.album_images_cache.get(key_ID);
    }


    /**
     *
     * @param context
     * @param albumId
     * @return
     */
    static @Nullable Bitmap getAlbumArt(@NonNull Context context, @NonNull long albumId)
    {

        Bitmap album_image = MainActivity.Get_AlbumArt_From_Cache(albumId); //return cached album art if possible
        if( album_image != null)
        {
            Log.v("my_verbose", "Returned cached album art");
            return album_image;
        }

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.AlbumColumns.ALBUM_ART},
                "_ID" + "=?",
                new String[]{String.valueOf(albumId)},
                null))
        {

            if (cursor == null || cursor.moveToFirst() == false)
            {
                return null;
            }

            String artLink = cursor.getString(cursor.getColumnIndex
                    (MediaStore.Audio.AlbumColumns.ALBUM_ART));
            if(artLink == null)
            {
                return null;
            }

            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            Point point = new Point();
            display.getSize(point);
            int display_width = point.x;
            int display_height = point.y;

            //get only dimensions of image without decoding
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(artLink, bmOptions);
            int srcWidth = bmOptions.outWidth;
            int srcHeight = bmOptions.outHeight;
            bmOptions.inJustDecodeBounds = false;

            if(srcHeight == 0 && srcWidth == 0)
            {
                return null;
            }

            if(srcHeight > display_height || srcWidth > display_width)
            {
                bmOptions.inScaled = true;
                bmOptions.inDensity = srcWidth;
                bmOptions.inTargetDensity = display_width;
                album_image = BitmapFactory.decodeFile(artLink, bmOptions);
            }
            else
            {
                album_image = BitmapFactory.decodeFile(artLink);
            }

            if(album_image == null)
            {
                return null;
            }

            Add_AlbumArt_To_Cache(albumId, album_image); //cache image for possible next use

            return album_image;
        }
    }


    private static void Init_AlbumArt_Cache()
    {
        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        Log.v("my_verbose", "Max memory for current app: "
                + String.valueOf(maxMemory) + " Kb");

        // Use 1/6th of the available memory for this memory cache.
        final int cache_sz = maxMemory / 6;

        MainActivity.album_images_cache = new LruCache<Long, Bitmap>(cache_sz)
        {
            protected int sizeOf(Long key, Bitmap bitmap)
            {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
    }


    /**
     * Singelton function
     * @return newFixedThreadPool with amount of threads equal to
     * available processor cores multiplied by 2
     */
    public static ExecutorService get_App_Additional_Threads()
    {
        if(MainActivity.application_additional_threads == null)
        {
            MainActivity.application_additional_threads =
                    Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        }

        return MainActivity.application_additional_threads;
    }
}