package com.prplx.soundfile;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class Edit_audiofile extends AppCompatActivity
{
    private final String WRITE_PERMISSION_STRING = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private final int PERMISSION_REQUEST_CODE = 673;

    private final int NOTIFICATION_ID = 3;
    private final String EDIT_CHANNEL_ID = "3";

    //if intent comes from popup menu therefore this activity can't pass result to it
    public static String FROM_MENU_EXTRA = "from_menu_extra";
    public static String FROM_PLAYER_EXTRA = "FROM_PLAYER_EXTRA";

    private boolean is_from_player = false;

    public static String ID_EXTRA = "id_extra";
    public static String URI_EXTRA = "uri_extra";
    public static String ARTIST_EXTRA = "artist_extra";
    public static String TITLE_EXTRA = "title_extra";
    public static String ALBUM_EXTRA = "album_extra";
    public static String INDEX_EXTRA = "index_extra";

    private EditText title_field = null;
    private EditText artist_field = null;
    private EditText album_field = null;

    private String intent_abs_path = null;
    private long intent_id = -1;
    private String intent_artist = null;
    private String intent_title = null;
    private String intent_album = null;
    private int intent_list_index = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_audiofile);

        init_Views();

        Intent intent = getIntent();

        if(intent != null)
        {
            this.intent_abs_path = intent.getStringExtra(URI_EXTRA);
            this.intent_id = intent.getLongExtra(ID_EXTRA, -1);
            this.intent_artist = intent.getStringExtra(ARTIST_EXTRA);
            this.intent_title = intent.getStringExtra(TITLE_EXTRA);
            this.intent_album = intent.getStringExtra(ALBUM_EXTRA);
            this.intent_list_index = intent.getIntExtra(INDEX_EXTRA, -1);
            this.is_from_player = intent.getBooleanExtra(FROM_PLAYER_EXTRA, false);


            this.artist_field.setText(intent_artist);
            this.title_field.setText(intent_title);
            this.album_field.setText(intent_album);
        }

        //create channel for action below
        CreateEditNotificationChannel();
        //check permission for writing user's data
        if(ContextCompat.checkSelfPermission(this, WRITE_PERMISSION_STRING) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[]{WRITE_PERMISSION_STRING}, PERMISSION_REQUEST_CODE);
        }
    }


    private void init_Views()
    {
        this.title_field = findViewById(R.id.edit_audio_title_field);
        this.artist_field = findViewById(R.id.edit_audio_artist_field);
        this.album_field = findViewById(R.id.edit_audio_album_field);
    }


    public void btn_edit_audio_save_onClick(View view)
    {
        ContentValues contentValues = new ContentValues();

        try
        {
            Uri audioCollection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            {
                audioCollection = MediaStore.Audio.Media
                        .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
            else
            {
                audioCollection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }

            //get new values
            String new_audiofile_artist = this.artist_field.getText().toString();
            String new_audiofile_title = this.title_field.getText().toString();
            String new_audiofile_album = this.album_field.getText().toString();

            contentValues.put(MediaStore.Audio.Media.ARTIST, new_audiofile_artist);
            contentValues.put(MediaStore.Audio.Media.TITLE, new_audiofile_title);
            contentValues.put(MediaStore.Audio.Media.ALBUM, new_audiofile_album);

            //update file with new values
            int udpated_rows = getContentResolver().update(audioCollection, contentValues,
                    MediaStore.Audio.Media._ID + "=" + this.intent_id, null);

            if(udpated_rows > 0)
            {
                //intent to pass back to the PlayerActivity
                //give PlayerActivity new values to update file in the Player's list and in the global list
                Intent intent = new Intent();
                intent.putExtra(ID_EXTRA, this.intent_id);
                intent.putExtra(ARTIST_EXTRA, new_audiofile_artist);
                intent.putExtra(TITLE_EXTRA, new_audiofile_title);
                intent.putExtra(ALBUM_EXTRA, new_audiofile_album);
                intent.putExtra(INDEX_EXTRA, this.intent_list_index);

                setResult(RESULT_OK, intent);
                Toast.makeText(this, R.string.edit_audiofile_saved_text, Toast.LENGTH_SHORT).show();
            }
            else
            {
                setResult(RESULT_CANCELED);
                throw new Exception();
            }

            //update global list
            //search all list to find the file to update by it's ID
            for(int i = 0; i < MainActivity.list_AudioFile.size(); i++)
            {
                if(MainActivity.list_AudioFile.get(i).id == intent_id)
                {
                    AudioFile item = MainActivity.list_AudioFile.get(i);
                    item.artist = new_audiofile_artist;
                    item.title = new_audiofile_title;
                    item.album = new_audiofile_album;
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            Toast.makeText(this, R.string.edit_audiofile_not_saved_text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed()
    {
        finish();
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
                    return;
                }
                else
                {
                    Intent intent = new Intent(this, Edit_audiofile.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                    NotificationCompat.Builder notification_Builder = new NotificationCompat.Builder(this, this.EDIT_CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_app_launcher)
                            .setContentTitle("SoundFile")
                            .setContentText("Writing permission is required for updating file info")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setVibrate(new long[]{1000, 1000})
                            .setAutoCancel(true);

                    notification_Builder.setContentIntent(pendingIntent);

                    NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                    //send notification to user
                    notificationManager.notify(NOTIFICATION_ID, notification_Builder.build());

                    //close app
                    this.finishAndRemoveTask();
                }
            }
        }
    }

    private void CreateEditNotificationChannel()
    {
        CharSequence name = "SoundFile edit notification channel";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationChannel channel = new NotificationChannel(this.EDIT_CHANNEL_ID, name, importance);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}