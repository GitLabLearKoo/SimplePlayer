package com.zt.listvideo.base;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.FrameLayout;

import com.zt.listvideo.R;
import com.zt.listvideo.listener.OnFullScreenChangeListener;
import com.zt.listvideo.listener.StateCallback;
import com.zt.listvideo.util.VideoUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public abstract class BaseVideoView extends FrameLayout implements StateCallback, MediaPlayer.OnVideoSizeChangedListener {

    public static final int LANDSCAPE_FULLSCREEN_MODE = 1;  //横向的全屏模式
    public static final int PORTRAIT_FULLSCREEN_MODE = 2;  //竖向的全屏模式
    public static final int AUTO_FULLSCREEN_MODE = 3;      //根据视频内容宽高比，自动判定全屏模式, 宽>高（横屏全屏), 宽 < 高(竖屏全屏)

    @IntDef({LANDSCAPE_FULLSCREEN_MODE, PORTRAIT_FULLSCREEN_MODE, AUTO_FULLSCREEN_MODE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FullScreeMode {
    }

    protected BasePlayer player;

    private TextureView textureView;

    protected boolean isFullScreen = false;
    protected OnFullScreenChangeListener onFullScreenChangeListener;
    protected int fullScreenMode = LANDSCAPE_FULLSCREEN_MODE;

    //正常状态下控件的宽高
    protected int originWidth;
    protected int originHeight;

    protected ViewParent viewParent;

    protected int mSystemUiVisibility;

    private boolean isShowMobileDataDialog = false;

    public BaseVideoView(@NonNull Context context) {
        this(context, null);
    }

    public BaseVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(getLayoutId(), this);
        player = new BasePlayer(context);
        player.setStateCallback(this);
        player.setOnVideoSizeChangedListener(this);
    }

    public void setVideoPath(String url) {
        player.setVideoPath(url);
    }

    public void startVideo() {
        int currentState = player.getCurrentState();
        if (currentState == BasePlayer.STATE_IDLE || currentState == BasePlayer.STATE_ERROR) {
            prepareToPlay();
        } else if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void prepareToPlay() {
        textureView = new TextureView(getContext());
        player.setTextureView(textureView);


        ViewGroup surfaceContainer = getSurfaceContainer();
        surfaceContainer.removeAllViews();

        LayoutParams layoutParams =
                new LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER);

        surfaceContainer.addView(textureView, layoutParams);
    }

    //region 全屏处理

    public void setFullScreenMode(@FullScreeMode int fullScreenMode) {
        this.fullScreenMode = fullScreenMode;
    }

    //视频全屏策略，竖向全屏，横向全屏，还是根据宽高比来选择
    protected int getFullScreenOrientation() {
        if (fullScreenMode == PORTRAIT_FULLSCREEN_MODE) {
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        if (fullScreenMode == AUTO_FULLSCREEN_MODE) {
            return player.getAspectRation() >= 1 ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    public boolean isFullScreen() {
        return isFullScreen;
    }

    public void setOnFullScreenChangeListener(OnFullScreenChangeListener onFullScreenChangeListener) {
        this.onFullScreenChangeListener = onFullScreenChangeListener;
    }

    protected void startFullScreen() {

        isFullScreen = true;

        Activity activity = VideoUtils.getActivity(getContext());

        mSystemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();

        activity.setRequestedOrientation(getFullScreenOrientation());

        VideoUtils.hideSupportActionBar(activity, true);
        VideoUtils.addFullScreenFlag(activity);
        VideoUtils.hideNavKey(activity);

        changeToFullScreen();

        postRunnableToResizeTexture();

        if (onFullScreenChangeListener != null) {
            onFullScreenChangeListener.onFullScreenChange(true);
        }
    }

    protected void changeToFullScreen() {

        originWidth = getWidth();
        originHeight = getHeight();

        viewParent = getParent();

        ViewGroup vp = getRootViewGroup();

        removePlayerFromParent();

        final LayoutParams lpParent = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        final FrameLayout frameLayout = new FrameLayout(getContext());
        frameLayout.setBackgroundColor(Color.BLACK);

        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frameLayout.addView(this, lp);
        vp.addView(frameLayout, lpParent);
    }

    private ViewGroup getRootViewGroup() {
        Activity activity = (Activity) getContext();
        if (activity != null) {
            return (ViewGroup) activity.findViewById(Window.ID_ANDROID_CONTENT);
        }
        return null;
    }

    private void removePlayerFromParent() {
        ViewParent parent = getParent();
        if (parent != null) {
            ((ViewGroup) parent).removeView(this);
        }
    }

    protected void exitFullscreen() {

        isFullScreen = false;

        Activity activity = VideoUtils.getActivity(getContext());

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        VideoUtils.showSupportActionBar(activity, true);   //根据需要是否显示actionbar和状态栏
        VideoUtils.clearFullScreenFlag(activity);

        activity.getWindow().getDecorView().setSystemUiVisibility(mSystemUiVisibility);

        changeToNormalScreen();

        postRunnableToResizeTexture();

        if (onFullScreenChangeListener != null) {
            onFullScreenChangeListener.onFullScreenChange(false);
        }
    }

    protected void changeToNormalScreen() {
        ViewGroup vp = getRootViewGroup();
        vp.removeView((View) this.getParent());
        removePlayerFromParent();

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(originWidth, originHeight);
        setLayoutParams(layoutParams);

        if (viewParent != null) {
            ((ViewGroup) viewParent).addView(this);
        }
    }


    //endregion

    //region 播放控制

    protected boolean isInPlaybackState() {
        return player != null && player.isInPlaybackState();
    }

    public void start() {
        if (!player.getUrl().startsWith("file") && !VideoUtils.isWifiConnected(getContext()) && !isShowMobileDataDialog) {
            showMobileDataDialog();
            return;
        }
        startVideo();
    }

    public void showMobileDataDialog() {
        if (isShowMobileDataDialog) {
            return;
        }
        isShowMobileDataDialog = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.Theme_AppCompat_Light_Dialog_Alert);
        builder.setMessage(getResources().getString(R.string.mobile_data_tips));
        builder.setPositiveButton(getResources().getString(R.string.contine_playing), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                startVideo();
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.stop_play), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    public void release() {
        if (player != null) {
            player.release();
        }
    }

    public void resetSurface() {
        if (player != null) {
            player.resetSurface();
        }
    }

    protected void replay() {
        release();
        resetSurface();
        start();
    }

    public void destroy() {
        if (player != null) {
            player.destroy();
        }
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
    }
    //endregion

    private void postRunnableToResizeTexture() {
        post(new Runnable() {
            @Override
            public void run() {
                resizeTextureView(player.getVideoWidth(), player.getVideoHeight());
            }
        });
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        resizeTextureView(width, height);
    }

    //根据视频内容重新调整视频渲染区域大小
    private void resizeTextureView(int width, int height) {
        if (width == 0 || height == 0 || textureView == null) {
            return;
        }
        float aspectRation = (float) width / height;

        View surfaceContainer = getSurfaceContainer();
        int parentWidth = surfaceContainer.getWidth();
        int parentHeight = surfaceContainer.getHeight();

        int w, h;

        if (aspectRation >= 1) {
            w = parentWidth;
            h = (int) (w / aspectRation);
        } else {
            h = parentHeight;
            w = (int) (h * aspectRation);
        }

        ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
        layoutParams.width = w;
        layoutParams.height = h;
        textureView.setLayoutParams(layoutParams);
    }

    protected abstract ViewGroup getSurfaceContainer();

    protected abstract int getLayoutId();

    @Override
    public abstract void onStateChange(int state);
}