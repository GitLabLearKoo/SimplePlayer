package com.zt.core.demo;


import com.zt.core.base.ITinyVideoView;
import com.zt.core.player.FloatVideoManager;
import com.zt.core.view.FloatVideoView;

/**
 * Created by zhouteng on 2019-09-14
 */
public class FloatVideoActivity extends NormalVideoActivity {

    @Override
    protected void onDestroy() {
        super.onDestroy();
        startFloatVideoView();
    }

    //小窗口视频播放时，不销毁原来的播放器
    @Override
    protected void destroyVideoView() {

    }

    private void startFloatVideoView() {
        FloatVideoView floatVideoView = new FloatVideoView(this);
        ITinyVideoView.VideoLayoutParams videoLayoutParams = new ITinyVideoView.VideoLayoutParams(600, 336);
        videoLayoutParams.x = 20;
        videoLayoutParams.y = 20;
        floatVideoView.setVideoLayoutParams(videoLayoutParams);
        FloatVideoManager.getInstance().startFloatVideo(floatVideoView, videoView.getRenderView(), videoView.getPlayer());
        floatVideoView.start();
        finish();
    }
}
