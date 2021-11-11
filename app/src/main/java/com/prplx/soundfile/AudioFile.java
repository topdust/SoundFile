package com.prplx.soundfile;

import android.database.Cursor;
import android.provider.MediaStore;

import java.io.File;
import java.util.Locale;



public class AudioFile implements Comparable<AudioFile>
{
    private static int static_id;

    public final long id;
    public String title;
    public String artist;
    public String album;
    public long album_id;
    public String absolute_path;
    public final int duration_ms;
    public final String duration_in_Min_Sec;


    public AudioFile(Cursor cursor)
    {
        this.id = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
        this.title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
        this.artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
        this.album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
        this.album_id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID) );
        this.absolute_path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));

        this.duration_ms = Integer.parseInt(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)));
        this.duration_in_Min_Sec = String.format(Locale.getDefault(), "%d:%02d", this.duration_ms/1000/60, this.duration_ms/1000%60);
    }

    public AudioFile(File file)
    {
        this.id = AudioFile.static_id++;

        this.title = file.getName();
        this.artist = "unknown";
        this.album = "unknown";
        this.absolute_path = file.getAbsolutePath();

        this.duration_ms = 0;
        this.duration_in_Min_Sec = String.format(Locale.getDefault(), "%d:%02d", this.duration_ms/1000/60, this.duration_ms/1000%60);
    }



    @Override
    public int compareTo(AudioFile o)
    {
        return title.compareTo(o.title);
    }
}