package com.music.player.muzik.service;

/**
 * Created by Ashu on 3/26/2017.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;;
import com.cleveroad.audiowidget.AudioWidget;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.music.player.muzik.R;
import com.music.player.muzik.activity.SongPlayerActivity;
import com.music.player.muzik.model.Song;
import com.music.player.muzik.utils.UriDeserializer;
import com.music.player.muzik.utils.UriSerializer;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jp.wasabeef.picasso.transformations.CropCircleTransformation;

public class MusicService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioWidget.OnControlsClickListener, AudioWidget.OnWidgetStateChangedListener {

    private static final String TAG = "MusicService";
    private static final String ACTION_SET_TRACKS = "ACTION_SET_TRACKS";
    private static final String ACTION_PLAY_TRACKS = "ACTION_PLAY_TRACKS";
    private static final String ACTION_CHANGE_STATE = "ACTION_CHANGE_STATE";
    private static final String EXTRA_SELECT_TRACK = "EXTRA_SELECT_TRACK";
    private static final String EXTRA_CHANGE_STATE = "EXTRA_CHANGE_STATE";
    private static final long UPDATE_INTERVAL = 1000;
    private static final String KEY_POSITION_X = "position_x";
    private static final String KEY_POSITION_Y = "position_y";

    private static Song[] tracks;
    private final List<Song> items = new ArrayList<>();
    private AudioWidget audioWidget;
    private MediaPlayer mediaPlayer;
    private boolean preparing;
    private int playingIndex = -1;
    private boolean paused;
    private Timer timer;
    private CropCircleTransformation cropCircleTransformation;
    private SharedPreferences preferences;


    public static void setTracks(@NonNull Context context, @NonNull Song[] tracks) {
        Intent intent = new Intent(ACTION_SET_TRACKS, null, context, MusicService.class);
        MusicService.tracks = tracks;
        context.startService(intent);
    }

    public static void playTrack(@NonNull Context context, @NonNull Song item) {
        Intent intent = new Intent(ACTION_PLAY_TRACKS, null, context, MusicService.class);
//        intent.putExtra(EXTRA_SELECT_TRACK, item);
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new UriSerializer())
                .create();
        intent.putExtra(EXTRA_SELECT_TRACK, gson.toJson(item));
        context.startService(intent);
    }

    public static void setState(@NonNull Context context, boolean isShowing) {
        Intent intent = new Intent(ACTION_CHANGE_STATE, null, context, MusicService.class);
        intent.putExtra(EXTRA_CHANGE_STATE, isShowing);
        context.startService(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        audioWidget = new AudioWidget.Builder(this).build();
        audioWidget.controller().onControlsClickListener(this);
        audioWidget.controller().onWidgetStateChangedListener(this);
        cropCircleTransformation = new CropCircleTransformation();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_SET_TRACKS: {
                    updateTracks();
                    break;
                }
                case ACTION_PLAY_TRACKS: {
                    selectNewTrack(intent);
                    break;
                }
                case ACTION_CHANGE_STATE: {
                    if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))) {
                        boolean show = intent.getBooleanExtra(EXTRA_CHANGE_STATE, false);
                        if (show) {
                            audioWidget.show(preferences.getInt(KEY_POSITION_X, 100), preferences.getInt(KEY_POSITION_Y, 100));
                        } else {
                            audioWidget.hide();
                        }
                    } else {
                        Log.w(TAG, "Can't change audio widget state! Device does not have drawOverlays permissions!");
                    }
                    break;
                }
            }
        }
        return START_STICKY;
    }

    private void selectNewTrack(Intent intent) {
        if (preparing) {
            return;
        }
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new UriDeserializer())
                .create();
        Song item = gson.fromJson(intent.getStringExtra(EXTRA_SELECT_TRACK), Song.class);
        if (item == null && playingIndex == -1 || playingIndex != -1 && items.get(playingIndex).equals(item)) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                audioWidget.controller().pause();
            } else {
                mediaPlayer.start();
                audioWidget.controller().start();
            }
            return;
        }
        playingIndex = items.indexOf(item);
        startCurrentTrack();
    }

    private void startCurrentTrack() {
        if (mediaPlayer.isPlaying() || paused) {
            mediaPlayer.stop();
            paused = false;
        }
        mediaPlayer.reset();
        if (playingIndex < 0) {
            return;
        }
        try {
            mediaPlayer.setDataSource(this, items.get(playingIndex).getFileUri());
            mediaPlayer.prepareAsync();
            preparing = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTracks() {
        Song playingItem = null;
        if (playingIndex != -1) {
            playingItem = items.get(playingIndex);
        }
        items.clear();
        if (MusicService.tracks != null) {
            items.addAll(Arrays.asList(MusicService.tracks));
            MusicService.tracks = null;
        }
        if (playingItem == null) {
            playingIndex = -1;
        } else {
            playingIndex = this.items.indexOf(playingItem);
        }
        if (playingIndex == -1 && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
    }

    @Override
    public void onDestroy() {
        audioWidget.controller().onControlsClickListener(null);
        audioWidget.controller().onWidgetStateChangedListener(null);
        audioWidget.hide();
        audioWidget = null;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.reset();
        mediaPlayer.release();
        mediaPlayer = null;
        stopTrackingPosition();
        cropCircleTransformation = null;
        preferences = null;
        super.onDestroy();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        preparing = false;
        mediaPlayer.start();
        if (!audioWidget.isShown()) {
            audioWidget.show(preferences.getInt(KEY_POSITION_X, 100), preferences.getInt(KEY_POSITION_Y, 100));
        }
        audioWidget.controller().start();
        audioWidget.controller().position(0);
        audioWidget.controller().duration(mediaPlayer.getDuration());
        stopTrackingPosition();
        startTrackingPosition();
        int size = getResources().getDimensionPixelSize(R.dimen.cover_size);
        Picasso.with(this)
                .load(items.get(playingIndex).getAlbumArtUri())
                .transform(cropCircleTransformation)
                .into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        if (audioWidget != null) {
                            audioWidget.controller().albumCoverBitmap(bitmap);
                        }
                    }

                    @Override
                    public void onBitmapFailed(Drawable errorDrawable) {
                        if (audioWidget != null) {
                            audioWidget.controller().albumCover(null);
                        }
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {

                    }
                });
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (playingIndex == -1) {
            audioWidget.controller().stop();
            return;
        }
        playingIndex++;
        if (playingIndex >= items.size()) {
            playingIndex = 0;
            if (items.size() == 0) {
                audioWidget.controller().stop();
                return;
            }
        }
        startCurrentTrack();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        preparing = true;
        return false;
    }

    @Override
    public boolean onPlaylistClicked() {
        Intent intent = new Intent(this, SongPlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return false;
    }

    @Override
    public void onPlaylistLongClicked() {
        Log.d(TAG, "playlist long clicked");
    }

    @Override
    public void onPreviousClicked() {
        if (items.size() == 0)
            return;
        playingIndex--;
        if (playingIndex < 0) {
            playingIndex = items.size() - 1;
        }
        startCurrentTrack();
    }

    @Override
    public void onPreviousLongClicked() {
        Log.d(TAG, "previous long clicked");
    }

    @Override
    public boolean onPlayPauseClicked() {
        if (playingIndex == -1) {
            Toast.makeText(this, R.string.song_not_selected, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (mediaPlayer.isPlaying()) {
            stopTrackingPosition();
            mediaPlayer.pause();
            audioWidget.controller().start();
            paused = true;
        } else {
            startTrackingPosition();
            audioWidget.controller().pause();
            mediaPlayer.start();
            paused = false;
        }
        return false;
    }

    @Override
    public void onPlayPauseLongClicked() {
        Log.d(TAG, "play/pause long clicked");
    }

    @Override
    public void onNextClicked() {
        if (items.size() == 0)
            return;
        playingIndex++;
        if (playingIndex >= items.size()) {
            playingIndex = 0;
        }
        startCurrentTrack();
    }

    @Override
    public void onNextLongClicked() {
        Log.d(TAG, "next long clicked");
    }

    @Override
    public void onAlbumClicked() {
        Log.d(TAG, "album clicked");
    }

    @Override
    public void onAlbumLongClicked() {
        Log.d(TAG, "album long clicked");
    }

    private void startTrackingPosition() {
        timer = new Timer(TAG);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                AudioWidget widget = audioWidget;
                MediaPlayer player = mediaPlayer;
                if (widget != null) {
                    widget.controller().position(player.getCurrentPosition());
                }
            }
        }, UPDATE_INTERVAL, UPDATE_INTERVAL);
    }

    private void stopTrackingPosition() {
        if (timer == null)
            return;
        timer.cancel();
        timer.purge();
        timer = null;
    }

    @Override
    public void onWidgetStateChanged(@NonNull AudioWidget.State state) {

    }

    @Override
    public void onWidgetPositionChanged(int cx, int cy) {
        preferences.edit()
                .putInt(KEY_POSITION_X, cx)
                .putInt(KEY_POSITION_Y, cy)
                .apply();
    }
}
