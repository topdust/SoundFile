package com.prplx.soundfile;

import java.util.Comparator;


public class Duration_Comparator implements Comparator<AudioFile>
{
    @Override
    public int compare(AudioFile o1, AudioFile o2)
    {
        return o1.duration_ms - o2.duration_ms;
    }
}
