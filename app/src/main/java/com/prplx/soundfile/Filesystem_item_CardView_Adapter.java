package com.prplx.soundfile;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;


public class Filesystem_item_CardView_Adapter extends RecyclerView.Adapter<Filesystem_item_CardView_Adapter.ViewHolder>
{
    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private final CardView cardView;


        public ViewHolder(CardView arg_cardView)
        {
            super(arg_cardView);

            this.cardView = arg_cardView;
        }
    }



    public static class Builder
    {
        public String[] ar_path;
        public String[] ar_name;
        public int[] ar_type;


        public Builder(DirContent dirContent)
        {
            //list of all files in current dir
            ArrayList<File> dirContent_fileList = new ArrayList<File>(dirContent.GetContent());
            int size = dirContent_fileList.size();

            this.ar_path = new String[size];
            this.ar_name = new String[size];
            this.ar_type = new int[size];

            for(int i = 0; i < size; i++)
            {
                this.ar_path[i] = dirContent_fileList.get(i).getAbsolutePath();
                this.ar_name[i] = dirContent_fileList.get(i).getName();

                if(dirContent_fileList.get(i).isDirectory())
                {
                    this.ar_type[i] = Files_Activity.FILE_TYPES.TYPE_DIRECTORY.ordinal();
                }
                else
                {
                    this.ar_type[i] = Files_Activity.FILE_TYPES.TYPE_FILE.ordinal();
                }
            }
        }


        public Filesystem_item_CardView_Adapter build()
        {
            return new Filesystem_item_CardView_Adapter(this);
        }
    }


    private final String[] ar_path;
    private final String[] ar_name;
    private final int[] ar_type;


    //constructor of CardView Adapter
    public Filesystem_item_CardView_Adapter(@NonNull Filesystem_item_CardView_Adapter.Builder builder)
    {
        this.ar_path = builder.ar_path;
        this.ar_name = builder.ar_name;
        this.ar_type = builder.ar_type;
    }


    @Override
    public Filesystem_item_CardView_Adapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        CardView cardView = (CardView) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.filesystem_item_cardview, parent, false);

        return new Filesystem_item_CardView_Adapter.ViewHolder(cardView);
    }


    @Override
    public void onBindViewHolder(Filesystem_item_CardView_Adapter.ViewHolder viewHolder, int position)
    {
        CardView cardView = viewHolder.cardView;

        TextView TV_filename = (TextView)cardView.findViewById(R.id.filesystem_item_cardView_filename);
        TV_filename.setText(this.ar_name[position]);

        ImageView IV_filetype = (ImageView)cardView.findViewById(R.id.filesystem_item_cardView_img);


        ImageButton IB_more = (ImageButton) cardView.findViewById(R.id.filesystem_item_cardView_more);
        IB_more.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
                MenuInflater inflater = popupMenu.getMenuInflater();

                File file = new File(ar_path[position]);
                if(file.isFile())
                {
                    inflater.inflate(R.menu.menu_popup_filesystem_item_file, popupMenu.getMenu());
                }
                else
                {
                    inflater.inflate(R.menu.menu_popup_filesystem_item_file, popupMenu.getMenu());
                }

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
                {
                    @Override
                    public boolean onMenuItemClick(MenuItem item)
                    {
                        boolean deleted = false;

                        switch (item.getItemId())
                        {
                            case R.id.popup_menu_filesystem_item_action_delete:
                            {
                                if(file.isFile())
                                {
                                    try
                                    {
                                        file.delete();
                                        Toast.makeText(v.getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                                    }
                                    catch (SecurityException ex)
                                    {
                                        Toast.makeText(v.getContext(), "Security error: Can't delete file", Toast.LENGTH_LONG).show();
                                    }
                                }
                                else
                                {
                                    try
                                    {
                                        delete_recursively(file);
                                        Toast.makeText(v.getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                                    }
                                    catch (SecurityException ex)
                                    {
                                        Toast.makeText(v.getContext(), "Security error: Can't delete directory", Toast.LENGTH_LONG).show();
                                    }
                                }

                                break;
                            }

                            case R.id.popup_menu_filesystem_item_action_rename:
                            {
                                break;
                            }


                            default:
                                return false;
                        }

                        return true;
                    }
                });

                popupMenu.show();
            }
        });


        if(this.ar_type[position] == Files_Activity.FILE_TYPES.TYPE_DIRECTORY.ordinal())
        {
            IV_filetype.setImageResource(R.drawable.ic_search_folder);
        }
        else
        {
            IV_filetype.setImageResource(R.drawable.ic_search_file);
        }


        cardView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                File file = new File(Files_Activity.str_current_dir + "/" + Files_Activity.dirContent.GetName(position));

                if (file != null)
                {
                    if (file.isDirectory() ) //if file is a directory
                    {
                        Files_Activity.str_current_dir = file.getAbsolutePath();
                        Files_Activity.str_parent_dir = file.getParent();

                        try
                        {
                            Files_Activity.dirContent.SetContent(file);

                            //builder of Adapter
                            Filesystem_item_CardView_Adapter.Builder builder = new Filesystem_item_CardView_Adapter.Builder(Files_Activity.dirContent);

                            //make adapter from it's builder
                            Filesystem_item_CardView_Adapter search_CardView_adapter = builder.build();
                            Files_Activity.fileList.setAdapter(search_CardView_adapter);
                        }
                        catch (Exception ex)
                        {
                            Log.d(null, ex.getMessage());
                        }
                    }
                    else //if file is not a directory
                    {
                        //check if file is audio file by it's name
                        if( IsAudioFile( file.getName() ) == true)
                        {
                            Intent intentPlayer = new Intent(view.getContext(), PlayerActivity.class);
                            intentPlayer.putExtra(PlayerActivity.PATH_EXTRA, file.getAbsolutePath());
                            view.getContext().startActivity(intentPlayer);
                        }
                    }
                }
            }
        });
    }



    private boolean delete_recursively(@NonNull File file)
    {
        if(file.canWrite() == false)
        {
            throw new SecurityException();
        }

        if(file.isFile())
        {
            try
            {
                file.delete();
            }
            catch (SecurityException ex)
            {
                throw ex;
            }
        }
        else
        {
            try
            {
                for(File child_file : file.listFiles())
                {
                    delete_recursively(child_file);
                }

                file.delete();
            }
            catch (SecurityException ex)
            {
                throw ex;
            }
        }

        return true;
    }



    private boolean IsAudioFile(final String filename)
    {
        String[] audio_ext = {".mp3", ".ogg", ".wav", ".flac", ".aac"};

        String LC_filename = filename.toLowerCase();

        for(int i = 0; i < audio_ext.length; i++)
        {
            //if filename string contains any string of audio extensions array -
            //it's probably an audio file
            if( LC_filename.contains(audio_ext[i]) == true)
            {
                return true;
            }
        }

        return false;
    }


    @Override
    public int getItemCount()
    {
        return this.ar_path.length;
    }
}
