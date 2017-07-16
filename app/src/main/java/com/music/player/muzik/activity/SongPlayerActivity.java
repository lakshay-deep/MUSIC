package com.music.player.muzik.activity;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import com.music.player.muzik.R;
import com.music.player.muzik.fragment.SongPlayerFragment;
import com.music.player.muzik.model.Song;

import java.util.ArrayList;

/**
 * Created by Ashu on 3/25/2017.
 */

public class SongPlayerActivity extends SingleFragmentActivity {

    private static ArrayList<Song> mSongsList;
    private static String EXTRA_PLAYING_INDEX = "playing_index";

    @Override
    protected Fragment createFragment() {
        return SongPlayerFragment.newInstance(mSongsList, getIntent().getIntExtra(EXTRA_PLAYING_INDEX, 0));
    }

    @Override
    protected String setActionBarTitle() {
        return "Player";
    }

    @Override
    protected boolean showActionBar() {
        return false;
    }

    @Override
    protected int setActionBarColor() {
        return R.color.transparent;
    }

    public static Intent newIntent(Context context, ArrayList<Song> list, int playingindex) {
        mSongsList = list;
        Intent intent = new Intent(context, SongPlayerActivity.class);
        intent.putExtra(EXTRA_PLAYING_INDEX, playingindex);
        return intent;
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.nothing, R.anim.slide_out_up);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                this.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
