package com.prplx.soundfile;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;


//create custom AutoCompleteTextView for custom disabling dismissDropDown()
public class Player_AutoCompleteTextView extends AppCompatAutoCompleteTextView
{
    private boolean ignoreDismissDropDown = false;



    public Player_AutoCompleteTextView(@NonNull Context context)
    {
        super(context);
    }

    public Player_AutoCompleteTextView(@NonNull Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
    }

    public Player_AutoCompleteTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void dismissDropDown()
    {
        if (this.ignoreDismissDropDown == true)
        {
            this.ignoreDismissDropDown = false;
            return;
        }
        else
        {
            super.dismissDropDown();
        }
    }

    public void setIgnoreDismissDropDown(boolean flag)
    {
        this.ignoreDismissDropDown = flag;
    }
}
