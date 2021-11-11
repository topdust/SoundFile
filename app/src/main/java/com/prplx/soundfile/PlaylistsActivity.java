package com.prplx.soundfile;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaylistsActivity extends AppCompatActivity
{
    ConstraintLayout root_view;
    RecyclerView recyclerView_playlists;
    Playlists_Adapter playlists_adapter;

    public static final String PLAYLISTS_DIR_NAME = "playlists";
    private Path path_to_playlists_dir;
    private File playlists_dir;

    private String user_playlist_name = "";

    NT_Toast nt_toast;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.playlists_toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setTitle("Playlists");
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);

        this.root_view = findViewById(R.id.a_playlists_root_view);
        InitClassAttr();
        InitViews(this.root_view);

        LinearLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerView_playlists.setLayoutManager(layoutManager);

        List<String> list_of_playlists = Get_Playlists_Names();

        for (String name : list_of_playlists)
        {
            Log.v("my_debug", name);
        }

        this.playlists_adapter = new Playlists_Adapter(root_view.getContext(), list_of_playlists);
        recyclerView_playlists.setAdapter(playlists_adapter);
    }

    private void InitClassAttr()
    {
        this.path_to_playlists_dir = Paths.get( getFilesDir().toString(), PlaylistsActivity.PLAYLISTS_DIR_NAME);
        this.playlists_dir = new File(this.path_to_playlists_dir.toString());
        if(this.playlists_dir.exists() == false)
        {
            this.playlists_dir.mkdir();
        }

        this.nt_toast = new NT_Toast(this);
    }

    private void InitViews(View root_view)
    {
        this.recyclerView_playlists = findViewById(R.id.playlist_RV_XML);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.playlists_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }


    public void Create_Playlist(String arg_name)
    {
        this.user_playlist_name = arg_name;

        if(this.playlists_dir.exists() == false)
        {
            this.playlists_dir.mkdir();
        }

        Path path_to_playlist_file = Paths.get(this.path_to_playlists_dir.toString(), arg_name);

        File playlist_file = new File(path_to_playlist_file.toString());

        if(playlist_file.exists())
        {

        }
        else
        {
            try
            {
                FileWriter fw = new FileWriter(playlist_file);
                fw.write(1);
            }
            catch (FileNotFoundException ex)
            {
                Log.v("my_debug", ex.toString());
            }
            catch (IOException ex)
            {
                Log.v("my_debug", ex.toString());
            }

            Log.v("my_debug", "File " + playlist_file.getPath() + " has been created");

            String toast_txt = "Playlist '" + arg_name + "' has been created.";
            nt_toast.show(Gravity.TOP | Gravity.CENTER_HORIZONTAL, toast_txt, Toast.LENGTH_LONG);

            List<String> name_list = Get_Playlists_Names();
            playlists_adapter.ChangeDataset(name_list);
            playlists_adapter.notifyDataSetChanged();
        }
    }


    private List<String> Get_Playlists_Names()
    {
        if(playlists_dir != null)
        {
            return Arrays.asList(this.playlists_dir.list());
        }
        else
        {
            return new ArrayList<>();
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.playlists_action_add_playlists:
                CreatePlaylist_DialogFragment dialogFragment = new CreatePlaylist_DialogFragment();
                dialogFragment.show(getSupportFragmentManager(), null);
                return true;


            default:
                return super.onOptionsItemSelected(item);

        }
    }
}