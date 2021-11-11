package com.prplx.soundfile;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.zip.Inflater;

public class NT_Toast
{
    Toast toast;
    Activity activity;

    public NT_Toast(Activity arg_activity)
    {
        activity = arg_activity;
    }

    public void show(int gravity, String arg_text, int duration)
    {
        if(this.toast != null)
        {
            toast.cancel();
            toast = null;
        }

        this.toast = new Toast(activity);
        toast.setDuration(duration);
        toast.setGravity(gravity, 10,10);

        View toast_nt_layout = activity.getLayoutInflater().inflate(R.layout.toast_nt, activity.findViewById(R.id.toast_nt_root));
        TextView textView = toast_nt_layout.findViewById(R.id.toast_nt_text);
        textView.setText(arg_text);
        toast.setView(toast_nt_layout);

        toast.show();
    }
}
