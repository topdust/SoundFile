package com.prplx.soundfile;

import java.io.File;
import java.util.ArrayList;


//class for storing content of chosen dir

public class DirContent
{
    private ArrayList<File> dir_content;


    public DirContent(File dir)
    {
        dir_content = SortContent(dir);
    }



    public ArrayList<File> GetContent()
    {
        ArrayList<File> list_to_ret = new ArrayList<File>(this.dir_content);

        return list_to_ret;
    }


    public void SetContent(File dir)
    {
        if(dir_content != null) dir_content.clear();

        dir_content = SortContent(dir);
    }



    public String GetName(int index)
    {
        return dir_content.get(index).getName();
    }


    //sort method: directories precede files
    private ArrayList<File> SortContent(File dir)
    {
        ArrayList<File> subdir_list = new ArrayList<File>();
        ArrayList<File> files_list = new ArrayList<File>();

        if(dir.listFiles() != null)
        {
            for (File file : dir.listFiles())
            {
                if (file.isDirectory())
                {
                    subdir_list.add(file);
                }
                else
                {
                    files_list.add(file);
                }
            }

            //directories are first
            subdir_list.addAll(files_list);
        }

        return subdir_list;
    }

}
