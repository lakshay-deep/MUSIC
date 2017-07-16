package com.music.player.muzik.fragment;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.cleveroad.audiovisualization.AudioVisualization;
import com.cleveroad.audiovisualization.DbmHandler;
import com.cleveroad.play_widget.PlayLayout;
import com.cleveroad.play_widget.VisualizerShadowChanger;
import com.music.player.muzik.R;
import com.music.player.muzik.model.Song;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;

/**
 * Created by Ashu on 3/25/2017.
 */

public class SongPlayerFragment extends MusicFragment implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, PlayLayout.OnButtonsClickListener, PlayLayout.OnProgressChangedListener {

    @BindView(R.id.play_layout)
    PlayLayout mPlayLayout;
    @BindView(R.id.visualizer_view)
    AudioVisualization mAudioVisualization;
    @BindView(R.id.tv_current_duration)
    TextView mTvCurrentDuration;

    private VisualizerShadowChanger mShadowChanger;
    private MediaPlayer mediaPlayer;
    private Timer timer;
    private boolean preparing;
    private int playingIndex = -1;
    private boolean paused;
    private boolean shuffle = false;
    private boolean repeat = false;
    private static ArrayList<Song> mSongList;

    private static final String EXTRA_PLAYING_INDEX = "playing_index";

    private static final long UPDATE_INTERVAL = 20;
    private static final int MY_PERMISSIONS_REQUEST_READ_AUDIO = 11;

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_song_player;
    }

    public static SongPlayerFragment newInstance(ArrayList<Song> list, int playingindex) {
        mSongList = list;
        SongPlayerFragment fragment = new SongPlayerFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(EXTRA_PLAYING_INDEX, playingindex);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
            playingIndex = getArguments().getInt(EXTRA_PLAYING_INDEX, 0);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupPlayerUI();

        mPlayLayout.setOnButtonsClickListener(this);
        mPlayLayout.setOnProgressChangedListener(this);

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        mPlayLayout.fastOpen();
        mAudioVisualization.linkTo(DbmHandler.Factory.newVisualizerHandler(getContext(), 0));
        startCurrentTrack();
    }

    public void setupPlayerUI() {
        mPlayLayout.setBigDiffuserColorResource(R.color.transparent);
        mPlayLayout.setMediumDiffuserColorResource(R.color.transparent);
        mPlayLayout.setPlayButtonBackgroundTintList(ColorStateList.valueOf(getContext().getResources().getColor(R.color.middle_wave)));
        mPlayLayout.setProgressLineColorResource(R.color.second_bottom_wave);
        mPlayLayout.setProgressBallColorResource(R.color.bottom_wave);
    }

    private void checkVisualiserPermissions() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            startVisualiser();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)
                    || ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
                AlertDialog.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            requestPermissions();
                        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                            permissionsNotGranted();
                        }
                    }
                };
                new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.title_permissions))
                        .setMessage(Html.fromHtml(getString(R.string.message_permissions)))
                        .setPositiveButton(getString(R.string.btn_next), onClickListener)
                        .setNegativeButton(getString(R.string.btn_cancel), onClickListener)
                        .show();
            } else {
                requestPermissions();
            }
        }
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                getActivity(),
                new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS},
                MY_PERMISSIONS_REQUEST_READ_AUDIO
        );
    }

    private void permissionsNotGranted() {

    }

    private void startVisualiser() {
        if (mShadowChanger == null) {
            mShadowChanger = VisualizerShadowChanger.newInstance(mediaPlayer.getAudioSessionId());
            mShadowChanger.setEnabledVisualization(true);
            mPlayLayout.setShadowProvider(mShadowChanger);
            Log.i("startVisualiser", "startVisualiser " + mediaPlayer.getAudioSessionId());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_AUDIO) {
            boolean bothGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i]) || Manifest.permission.MODIFY_AUDIO_SETTINGS.equals(permissions[i])) {
                    bothGranted &= grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }
            if (bothGranted) {
                startVisualiser();
            } else {
                permissionsNotGranted();
            }
        }
    }

    @Override
    public void onResume() {
        mAudioVisualization.onResume();
        if (mShadowChanger != null) {
            mShadowChanger.setEnabledVisualization(true);
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        mAudioVisualization.onPause();
        if (mShadowChanger != null) {
            mShadowChanger.setEnabledVisualization(false);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mAudioVisualization.release();
        if (mShadowChanger != null) {
            mShadowChanger.release();
        }
        stopTrackingPosition();
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.reset();
        mediaPlayer.release();
        mediaPlayer = null;
        super.onDestroy();
    }

    //MEDIA PLAYER LISTENERS

    @Override
    public void onPrepared(MediaPlayer mp) {
        preparing = false;
        mediaPlayer.start();
        stopTrackingPosition();
        startTrackingPosition();
        checkVisualiserPermissions();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (playingIndex == -1) {
//            audioWidget.controller().stop();
            if (mPlayLayout != null) {
                mPlayLayout.startDismissAnimation();
            }
            return;
        }
        if (!repeat)
            playingIndex++;
        if (playingIndex >= mSongList.size()) {
            playingIndex = 0;
            if (mSongList.size() == 0) {
//                audioWidget.controller().stop();
                return;
            }
        }
        startCurrentTrack();
    }

    private void startTrackingPosition() {
        timer = new Timer("Player Timer");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                MediaPlayer tempMediaPlayer = mediaPlayer;
                if (tempMediaPlayer != null && tempMediaPlayer.isPlaying()) {
                    mPlayLayout.setPostProgress((float) tempMediaPlayer.getCurrentPosition() / tempMediaPlayer.getDuration());
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mediaPlayer != null && mTvCurrentDuration != null)
                                mTvCurrentDuration.setText(String.valueOf(convertDuration(mediaPlayer.getCurrentPosition())));
                        }
                    });
                }

            }
        }, UPDATE_INTERVAL, UPDATE_INTERVAL);

    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        preparing = true;
        return false;
    }

    private void stopTrackingPosition() {
        if (timer == null)
            return;
        timer.cancel();
        timer.purge();
        timer = null;
    }

    private void startCurrentTrack() {
        setImageForItem();
        if (mediaPlayer.isPlaying() || paused) {
            mediaPlayer.stop();
            paused = false;
        }
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(getContext(), mSongList.get(playingIndex).getFileUri());
            mediaPlayer.prepareAsync();
            preparing = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setImageForItem() {
        if (playingIndex >= 0) {
            Picasso.with(getContext())
                    .load(mSongList.get(playingIndex).getAlbumArtUri())
                    .error(R.drawable.demo_music_logo)
                    .into(target);
        }
    }

    Target target = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            mPlayLayout.setImageBitmap(bitmap);
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {

        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {

        }
    };

    //CONVERTING MILLI SECONDS TO MM:SS

    private String convertDuration(long durationInMs) {
        long durationInSeconds = durationInMs / 1000;
        long seconds = durationInSeconds % 60;
        long minutes = (durationInSeconds % 3600) / 60;
        long hours = durationInSeconds / 3600;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    // PLAY BUTTON LISTENERS

    @Override
    public void onPlayButtonClicked() {
        playButtonClicked();
    }

    @Override
    public void onSkipPreviousClicked() {
        onPreviousClicked();
        if (!mPlayLayout.isOpen()) {
            mPlayLayout.startRevealAnimation();
        }
    }

    @Override
    public void onSkipNextClicked() {
        onNextClicked();
        if (!mPlayLayout.isOpen()) {
            mPlayLayout.startRevealAnimation();
        }
    }

    @Override
    public void onShuffleClicked() {
        if (shuffle) {
            shuffle = false;
            Toast.makeText(getContext(), getString(R.string.shuffle_off), Toast.LENGTH_SHORT).show();
        } else {
            shuffle = true;
            Toast.makeText(getContext(), getString(R.string.shuffle_on), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRepeatClicked() {
        if (repeat) {
            repeat = false;
            Toast.makeText(getContext(), getString(R.string.repeat_off), Toast.LENGTH_SHORT).show();
        } else {
            repeat = true;
            Toast.makeText(getContext(), getString(R.string.repeat_on), Toast.LENGTH_SHORT).show();
        }
    }

    private void playButtonClicked() {
        if (mPlayLayout == null) {
            return;
        }
        if (mPlayLayout.isOpen()) {
            mediaPlayer.pause();
            mPlayLayout.startDismissAnimation();
        } else {
            mediaPlayer.start();
            mPlayLayout.startRevealAnimation();
        }
    }


    public void onNextClicked() {
        if (mSongList.size() == 0) {
            return;
        } else
            checkforShuffle(1); //BUTTON STATE NEXT
        if (playingIndex >= mSongList.size()) {
            playingIndex = 0;
        }
        startCurrentTrack();
    }

    public void checkforShuffle(int button_state) {
        if (shuffle) {
            playingIndex = new Random().nextInt(mSongList.size() - 1) + 1;
        } else {
            if (button_state == 1)
                playingIndex++;
            else if (button_state == 0)
                playingIndex--;
        }
    }

    public void onPreviousClicked() {
        if (mSongList.size() == 0) {
            return;
        } else
            checkforShuffle(0); //BUTTON STATE PREVIOUS
        if (playingIndex < 0) {
            playingIndex = mSongList.size() - 1;
        }
        startCurrentTrack();
    }

    //PLAY LAYOUT PROGRESS LISTENERS

    @Override
    public void onPreSetProgress() {
        stopTrackingPosition();
    }

    @Override
    public void onProgressChanged(float progress) {
        Log.i("onProgressChanged", "Progress = " + progress);
        mediaPlayer.seekTo((int) (mediaPlayer.getDuration() * progress));
        startTrackingPosition();
    }
}
