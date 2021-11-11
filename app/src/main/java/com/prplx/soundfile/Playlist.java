package com.prplx.soundfile;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.List;

@Entity
public class Playlist
{
    @PrimaryKey
    public long id;
    public String name;
}
