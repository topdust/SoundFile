package com.prplx.soundfile;

import android.media.AudioManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;


public class Files_Activity extends AppCompatActivity
{
    public enum FILE_TYPES{TYPE_DIRECTORY, TYPE_FILE}

    public static DirContent dirContent = null;
    public static String str_root_dir = "";
    public static String str_parent_dir = "";
    public static String str_current_dir = "";

    public static RecyclerView fileList = null;

    Filesystem_item_CardView_Adapter search_CardView_adapter = null;


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_files);

        //in this app user by volume buttons changes only media volume
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        Toolbar toolbar = (Toolbar)this.findViewById(R.id.toolbar_search);
        toolbar.inflateMenu(R.menu.menu_usrfilesearch);
        this.setSupportActionBar(toolbar);
        this.getSupportActionBar().setTitle("");

        //init
        fileList = this.findViewById(R.id.search_RecycleView_filelist);

        try
        {
            File init_dir = Environment.getExternalStorageDirectory();

            str_root_dir = init_dir.getAbsolutePath();
            str_current_dir = str_root_dir;
            str_parent_dir = null;

            //store content of init dir
            dirContent = new DirContent(init_dir);

            //builder of Adapter
            Filesystem_item_CardView_Adapter.Builder builder = new Filesystem_item_CardView_Adapter.Builder(dirContent);
            //make adapter from it's builder
            search_CardView_adapter = builder.build();
            fileList.setAdapter(search_CardView_adapter);

            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            fileList.setLayoutManager(layoutManager);
        }
        catch (Exception ex)
        {
            Log.d(null, ">>Error: " + ex.getMessage());
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_usrfilesearch, menu);

        return super.onCreateOptionsMenu(menu);
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            //if button "Back" has been pressed
            case R.id.menu_action_parent_dir:

                if(str_current_dir.equals(str_root_dir) == false)
                {
                    File dir = new File(str_parent_dir); //to parent dir
                    dirContent.SetContent(dir);

                    //make new adapter from new directory
                    Filesystem_item_CardView_Adapter.Builder builder = new Filesystem_item_CardView_Adapter.Builder(dirContent);
                    search_CardView_adapter = builder.build();
                    //set new adapter
                    fileList.swapAdapter(search_CardView_adapter, true);


                    str_current_dir = str_parent_dir; //got content of parent dir so it is current dir now
                    File curr_dir = new File(str_current_dir);
                    str_parent_dir = curr_dir.getParent();//prev dir of current dir
                }
                return true;

            default: return super.onOptionsItemSelected(item);
        }
    }
}