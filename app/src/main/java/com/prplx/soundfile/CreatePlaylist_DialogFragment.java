package com.prplx.soundfile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Pattern;


public class CreatePlaylist_DialogFragment extends DialogFragment
{
    private final float BUTTON_PRESS_SCALE = 0.9f;
    private final float BUTTON_RELEASE_SCALE = 1.0f;

    private LayoutInflater layoutInflater;

    private View dialog_view;
    private EditText playlist_name_inp;
    private Button cancel_btn;
    private Button create_btn;
    private ImageButton clear_inp_btn;

    NT_Toast nt_toast;

    String regex = "^[^\\./\"\'\\\\]+$";
    Pattern pattern = Pattern.compile(regex);


    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        Init_Class_Attr();

        //initialise dialog view and all it's views
        Init_dialog_view();

        builder.setView(dialog_view);

        return builder.create();
    }


    private void Init_Class_Attr()
    {
        this.layoutInflater = getActivity().getLayoutInflater();
        this.nt_toast = new NT_Toast(getActivity());
    }


    private void Init_dialog_view()
    {
        this.dialog_view = layoutInflater.inflate(R.layout.create_playlist, null);

        this.playlist_name_inp = dialog_view.findViewById(R.id.create_playlist_inp_playlist_name_ET);
        playlist_name_inp.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                if(s.toString().equals(""))
                {
                    clear_inp_btn.setImageResource(android.R.color.transparent);
                }
                else
                {
                    clear_inp_btn.setImageResource(R.drawable.ic_cancel);
                }
            }

            @Override
            public void afterTextChanged(Editable s)
            {

            }
        });

        this.cancel_btn = dialog_view.findViewById(R.id.create_playlist_cancel_BTN);
        cancel_btn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                dismiss();
            }
        });


        cancel_btn.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent)
            {
                switch (motionEvent.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                    {
                        view.setScaleX(BUTTON_PRESS_SCALE);
                        view.setScaleY(BUTTON_PRESS_SCALE);
                        break;
                    }

                    case MotionEvent.ACTION_UP:
                    {
                        view.setScaleX(BUTTON_RELEASE_SCALE);
                        view.setScaleY(BUTTON_RELEASE_SCALE);
                        view.performClick();
                        break;
                    }
                }

                return true;
            }
        });


        this.create_btn = dialog_view.findViewById(R.id.create_playlist_create_BTN);
        create_btn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                String playlist_name = playlist_name_inp.getText().toString().trim();

                if(playlist_name.isEmpty() || (pattern.matcher(playlist_name).matches() == false))
                {
                    return;
                }
                else
                {
                    Path path_to_playlists_file = Paths.get(v.getContext().getFilesDir().toString(),
                            PlaylistsActivity.PLAYLISTS_DIR_NAME, playlist_name);
                    File playlist_file = new File(path_to_playlists_file.toString());

                    if(playlist_file.exists())
                    {
                        String toast_txt = "Playlist '" + playlist_name + "' already exists";

                        nt_toast.show(Gravity.TOP | Gravity.CENTER_HORIZONTAL, toast_txt, Toast.LENGTH_LONG);

                        return;
                    }
                    else
                    {
                        PlaylistsActivity playlistsActivity = (PlaylistsActivity)getActivity();
                        playlistsActivity.Create_Playlist(playlist_name);
                        dismiss();
                    }
                }
            }
        });

        create_btn.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent)
            {
                switch (motionEvent.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                    {
                        view.setScaleX(BUTTON_PRESS_SCALE);
                        view.setScaleY(BUTTON_PRESS_SCALE);
                        break;
                    }

                    case MotionEvent.ACTION_UP:
                    {
                        view.setScaleX(BUTTON_RELEASE_SCALE);
                        view.setScaleY(BUTTON_RELEASE_SCALE);
                        view.performClick();
                        break;
                    }
                }

                return true;
            }
        });

        this.clear_inp_btn = dialog_view.findViewById(R.id.create_playlist_clr_inp);
        this.clear_inp_btn.setImageResource(android.R.color.transparent); //hide clear button
        clear_inp_btn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(playlist_name_inp != null)
                {
                    if(playlist_name_inp.getText().toString().equals("") == false)
                    {
                        playlist_name_inp.setText("");
                    }
                    else
                    {
                        return;
                    }
                }
            }
        });
    }
}
