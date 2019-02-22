package com.tencent.liteav.video.play;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.tencent.liteav.basic.log.TXCLog;
import com.tencent.liteav.video.play.bean.TCPlayImageSpriteInfo;
import com.tencent.liteav.video.play.bean.TCPlayInfoStream;
import com.tencent.liteav.video.play.bean.TCVideoConfig;
import com.tencent.liteav.video.play.controller.TCVodControllerBase;
import com.tencent.liteav.video.play.controller.TCVodControllerFloat;
import com.tencent.liteav.video.play.controller.TCVodControllerLarge;
import com.tencent.liteav.video.play.controller.TCVodControllerSmall;
import com.tencent.liteav.video.play.net.LogReport;
import com.tencent.liteav.video.play.net.SuperVodInfoLoader;
import com.tencent.liteav.video.play.net.TCHttpURLClient;
import com.tencent.liteav.video.play.utils.NetWatcher;
import com.tencent.liteav.video.play.utils.PlayInfoResponseParser;
import com.tencent.liteav.video.play.utils.SuperPlayerUtil;
import com.tencent.liteav.video.play.view.TCDanmuView;
import com.tencent.liteav.video.play.view.TCVideoQulity;
import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.ITXVodPlayListener;
import com.tencent.rtmp.TXBitrateItem;
import com.tencent.rtmp.TXLivePlayConfig;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.TXVodPlayConfig;
import com.tencent.rtmp.TXVodPlayer;
import com.tencent.rtmp.ui.TXCloudVideoView;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

/**
 * Created by liyuejiao on 2018/7/3.
 */

public class SuperPlayerView extends RelativeLayout implements ITXVodPlayListener, ITXLivePlayListener {
    private static final String TAG = "SuperVodPlayerView";
    private Context mContext;
    private int mPlayMode = 1;
    private boolean mLockScreen = false;
    private ViewGroup mRootView;
    private TXCloudVideoView mTXCloudVideoView;
    private TCVodControllerLarge mVodControllerLarge;
    private TCVodControllerSmall mVodControllerSmall;
    private TCVodControllerFloat mVodControllerFloat;
    private TCDanmuView mDanmuView;
    private ViewGroup.LayoutParams mLayoutParamWindowMode;
    private LayoutParams mLayoutParamFullScreenMode;
    private android.widget.RelativeLayout.LayoutParams mVodControllerSmallParams;
    private android.widget.RelativeLayout.LayoutParams mVodControllerLargeParams;
    private TXVodPlayer mVodPlayer;
    private TXVodPlayConfig mVodPlayConfig;
    private TXLivePlayer mLivePlayer;
    private TXLivePlayConfig mLivePlayConfig;
    private int mPlayType;
    private int mCurrentPlayState = 1;
    private boolean mDefaultSet;
    private TCVideoConfig mTXVideoConfig;
    private SuperPlayerModel mCurrentSuperPlayerModel;
    private long mReportLiveStartTime = -1L;
    private long mReportVodStartTime = -1L;
    private int mCurrentPlayType;
    private NetWatcher mWatcher;
    private boolean mIsMultiBitrateStream;
    private boolean mIsPlayWithFileid;
    private WindowManager mWindowManager;
    private android.view.WindowManager.LayoutParams mWindowParams;
    private boolean mChangeHWAcceleration;
    private int mSeekPos;
    private SuperPlayerView.PlayerViewCallback mPlayerViewCallback;
    private SuperPlayerView.HideViews mHiderViewCallback;
    private TCVodControllerBase.VodController mVodController = new TCVodControllerBase.VodController() {
        @SuppressLint("WrongConstant")
        public void onRequestPlayMode(int requestPlayMode) {
            if (SuperPlayerView.this.mPlayMode != requestPlayMode) {
                if (!SuperPlayerView.this.mLockScreen) {
                    if (requestPlayMode == 2) {
                        SuperPlayerView.this.fullScreen(true);
                    } else {
                        SuperPlayerView.this.fullScreen(false);
                    }

                    SuperPlayerView.this.mVodControllerFloat.hide();
                    SuperPlayerView.this.mVodControllerSmall.hide();
                    SuperPlayerView.this.mVodControllerLarge.hide();
                    if (requestPlayMode == 2) {
                        TXCLog.i("SuperVodPlayerView", "requestPlayMode FullScreen");
                        if (SuperPlayerView.this.mLayoutParamFullScreenMode == null) {
                            return;
                        }

                        SuperPlayerView.this.removeView(SuperPlayerView.this.mVodControllerSmall);
                        SuperPlayerView.this.addView(SuperPlayerView.this.mVodControllerLarge, SuperPlayerView.this.mVodControllerLargeParams);
                        SuperPlayerView.this.setLayoutParams(SuperPlayerView.this.mLayoutParamFullScreenMode);
                        SuperPlayerView.this.rotateScreenOrientation(1);
                        if (SuperPlayerView.this.mPlayerViewCallback != null) {
                            SuperPlayerView.this.mPlayerViewCallback.hideViews();
                        }
                    } else if (requestPlayMode == 1) {
                        TXCLog.i("SuperVodPlayerView", "requestPlayMode Window");
                        if (SuperPlayerView.this.mPlayMode == 3) {
                            try {
                                Intent intent = new Intent();
                                intent.setAction("action.float.click");
                                SuperPlayerView.this.mContext.startActivity(intent);
                                this.pause();
                                if (SuperPlayerView.this.mLayoutParamWindowMode == null) {
                                    return;
                                }

                                SuperPlayerView.this.mWindowManager.removeView(SuperPlayerView.this.mVodControllerFloat);
                                if (SuperPlayerView.this.mCurrentPlayType == 1) {
                                    SuperPlayerView.this.mVodPlayer.setPlayerView(SuperPlayerView.this.mTXCloudVideoView);
                                } else {
                                    SuperPlayerView.this.mLivePlayer.setPlayerView(SuperPlayerView.this.mTXCloudVideoView);
                                }

                                this.resume();
                            } catch (Exception var5) {
                                var5.printStackTrace();
                            }
                        } else if (SuperPlayerView.this.mPlayMode == 2) {
                            if (SuperPlayerView.this.mLayoutParamWindowMode == null) {
                                return;
                            }

                            SuperPlayerView.this.removeView(SuperPlayerView.this.mVodControllerLarge);
                            SuperPlayerView.this.addView(SuperPlayerView.this.mVodControllerSmall, SuperPlayerView.this.mVodControllerSmallParams);
                            SuperPlayerView.this.setLayoutParams(SuperPlayerView.this.mLayoutParamWindowMode);
                            SuperPlayerView.this.rotateScreenOrientation(2);
                            if (SuperPlayerView.this.mPlayerViewCallback != null) {
                                SuperPlayerView.this.mPlayerViewCallback.showViews();
                            }
                        }
                    } else if (requestPlayMode == 3) {
                        TXCLog.i("SuperVodPlayerView", "requestPlayMode Float :" + Build.MANUFACTURER);
                        SuperPlayerGlobalConfig prefs = SuperPlayerGlobalConfig.getInstance();
                        if (!prefs.enableFloatWindow) {
                            return;
                        }

                        if (Build.VERSION.SDK_INT >= 23) {
                            if (!Settings.canDrawOverlays(SuperPlayerView.this.mContext)) {
                                Intent intentx = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION");
                                intentx.setData(Uri.parse("package:" + SuperPlayerView.this.mContext.getPackageName()));
                                SuperPlayerView.this.mContext.startActivity(intentx);
                                return;
                            }
                        } else if (!SuperPlayerView.this.checkOp(SuperPlayerView.this.mContext, 24)) {
                            Toast.makeText(SuperPlayerView.this.mContext, "进入设置页面失败,请手动开启悬浮窗权限", 0).show();
                            return;
                        }

                        SuperPlayerView.this.mWindowManager = (WindowManager) SuperPlayerView.this.mContext.getApplicationContext().getSystemService("window");
                        SuperPlayerView.this.mWindowParams = new android.view.WindowManager.LayoutParams();
                        SuperPlayerView.this.mWindowParams.type = 2003;
                        SuperPlayerView.this.mWindowParams.flags = 40;
                        SuperPlayerView.this.mWindowParams.format = -3;
                        SuperPlayerView.this.mWindowParams.gravity = 51;
                        SuperPlayerGlobalConfig.TXRect rect = prefs.floatViewRect;
                        SuperPlayerView.this.mWindowParams.x = rect.x;
                        SuperPlayerView.this.mWindowParams.y = rect.y;
                        SuperPlayerView.this.mWindowParams.width = rect.width;
                        SuperPlayerView.this.mWindowParams.height = rect.height;
                        SuperPlayerView.this.mWindowManager.addView(SuperPlayerView.this.mVodControllerFloat, SuperPlayerView.this.mWindowParams);
                        TXCloudVideoView videoView = SuperPlayerView.this.mVodControllerFloat.getFloatVideoView();
                        if (videoView != null) {
                            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                                SuperPlayerView.this.mVodPlayer.setPlayerView(videoView);
                            } else {
                                SuperPlayerView.this.mLivePlayer.setPlayerView(videoView);
                            }
                        }

                        LogReport.getInstance().uploadLogs("floatmode", 0L, 0);
                    }

                    SuperPlayerView.this.mPlayMode = requestPlayMode;
                }
            }
        }

        public void onBackPress(int playMode) {
            /*if (playMode == 2) {
                this.onRequestPlayMode(1);
            } else if (playMode == 1) {
                if (SuperPlayerView.this.mPlayerViewCallback != null) {
                    SuperPlayerView.this.mPlayerViewCallback.onQuit(1);
                }

                if (SuperPlayerView.this.mCurrentPlayState == 1) {
                    this.onRequestPlayMode(3);
                }
            } else if (playMode == 3) {
                SuperPlayerView.this.mWindowManager.removeView(SuperPlayerView.this.mVodControllerFloat);
                if (SuperPlayerView.this.mPlayerViewCallback != null) {
                    SuperPlayerView.this.mPlayerViewCallback.onQuit(3);
                }
            }*/

            SuperPlayerView.this.mPlayerViewCallback.onQuit(1);

        }

        public void resume() {
            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                if (SuperPlayerView.this.mVodPlayer != null) {
                    SuperPlayerView.this.mVodPlayer.resume();
                }
            } else if (SuperPlayerView.this.mLivePlayer != null) {
                SuperPlayerView.this.mLivePlayer.resume();
            }

            SuperPlayerView.this.mCurrentPlayState = 1;
            SuperPlayerView.this.mVodControllerSmall.updatePlayState(true);
            SuperPlayerView.this.mVodControllerLarge.updatePlayState(true);
            SuperPlayerView.this.mVodControllerLarge.updateReplay(false);
            SuperPlayerView.this.mVodControllerSmall.updateReplay(false);
        }

        public void pause() {
            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                if (SuperPlayerView.this.mVodPlayer != null) {
                    SuperPlayerView.this.mVodPlayer.pause();
                }
            } else {
                if (SuperPlayerView.this.mLivePlayer != null) {
                    SuperPlayerView.this.mLivePlayer.pause();
                }

                if (SuperPlayerView.this.mWatcher != null) {
                    SuperPlayerView.this.mWatcher.stop();
                }
            }

            SuperPlayerView.this.mCurrentPlayState = 2;
            TXCLog.e("lyj", "pause mCurrentPlayState:" + SuperPlayerView.this.mCurrentPlayState);
            SuperPlayerView.this.mVodControllerSmall.updatePlayState(false);
            SuperPlayerView.this.mVodControllerLarge.updatePlayState(false);
        }

        public float getDuration() {
            return SuperPlayerView.this.mVodPlayer.getDuration();
        }

        public float getCurrentPlaybackTime() {
            return SuperPlayerView.this.mVodPlayer.getCurrentPlaybackTime();
        }

        public void seekTo(int position) {
            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                if (SuperPlayerView.this.mVodPlayer != null) {
                    SuperPlayerView.this.mVodPlayer.seek(position);
                }
            } else {
                SuperPlayerView.this.mCurrentPlayType = 3;
                SuperPlayerView.this.mVodControllerSmall.updatePlayType(3);
                SuperPlayerView.this.mVodControllerLarge.updatePlayType(3);
                LogReport.getInstance().uploadLogs("timeshift", 0L, 0);
                if (SuperPlayerView.this.mLivePlayer != null) {
                    SuperPlayerView.this.mLivePlayer.seek(position);
                }

                if (SuperPlayerView.this.mWatcher != null) {
                    SuperPlayerView.this.mWatcher.stop();
                }
            }

        }

        public boolean isPlaying() {
            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                return SuperPlayerView.this.mVodPlayer.isPlaying();
            } else {
                return SuperPlayerView.this.mCurrentPlayState == 1;
            }
        }

        public void onDanmuku(boolean on) {
            if (SuperPlayerView.this.mDanmuView != null) {
                SuperPlayerView.this.mDanmuView.toggle(on);
            }

        }

        public void onSnapshot() {
            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                if (SuperPlayerView.this.mVodPlayer != null) {
                    SuperPlayerView.this.mVodPlayer.snapshot(new TXLivePlayer.ITXSnapshotListener() {
                        public void onSnapshot(Bitmap bmp) {
                            SuperPlayerView.this.showSnapshotWindow(bmp);
                        }
                    });
                }
            } else if (SuperPlayerView.this.mLivePlayer != null) {
                SuperPlayerView.this.mLivePlayer.snapshot(new TXLivePlayer.ITXSnapshotListener() {
                    public void onSnapshot(Bitmap bmp) {
                        SuperPlayerView.this.showSnapshotWindow(bmp);
                    }
                });
            }

        }

        public void onQualitySelect(TCVideoQulity quality) {
            SuperPlayerView.this.mVodControllerLarge.updateVideoQulity(quality);
            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                if (SuperPlayerView.this.mVodPlayer != null) {
                    if (quality.index == -1) {
                        float currentTime = SuperPlayerView.this.mVodPlayer.getCurrentPlaybackTime();
                        SuperPlayerView.this.mVodPlayer.stopPlay(true);
                        TXCLog.i("SuperVodPlayerView", "onQualitySelect quality.url:" + quality.url);
                        SuperPlayerView.this.mVodPlayer.setStartTime(currentTime);
                        SuperPlayerView.this.mVodPlayer.startPlay(quality.url);
                    } else {
                        TXCLog.i("SuperVodPlayerView", "setBitrateIndex quality.index:" + quality.index);
                        SuperPlayerView.this.mVodPlayer.setBitrateIndex(quality.index);
                    }
                }
            } else if (SuperPlayerView.this.mLivePlayer != null && !TextUtils.isEmpty(quality.url)) {
                int result = SuperPlayerView.this.mLivePlayer.switchStream(quality.url);
                if (result < 0) {
                    Toast.makeText(SuperPlayerView.this.getContext(), "切换" + quality.title + "清晰度失败，请稍候重试", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SuperPlayerView.this.getContext(), "正在切换到" + quality.title + "...", Toast.LENGTH_SHORT).show();
                }
            }

            LogReport.getInstance().uploadLogs("change_resolution", 0L, 0);
        }

        public void onSpeedChange(float speedLevel) {
            if (SuperPlayerView.this.mVodPlayer != null) {
                SuperPlayerView.this.mVodPlayer.setRate(speedLevel);
            }

            LogReport.getInstance().uploadLogs("change_speed", 0L, 0);
        }

        public void onMirrorChange(boolean isMirror) {
            if (SuperPlayerView.this.mVodPlayer != null) {
                SuperPlayerView.this.mVodPlayer.setMirror(isMirror);
            }

            if (isMirror) {
                LogReport.getInstance().uploadLogs("mirror", 0L, 0);
            }

        }

        public void onHWAcceleration(boolean isAccelerate) {
            if (SuperPlayerView.this.mCurrentPlayType == 1) {
                SuperPlayerView.this.mChangeHWAcceleration = true;
                if (SuperPlayerView.this.mVodPlayer != null) {
                    SuperPlayerView.this.mVodPlayer.enableHardwareDecode(isAccelerate);
                    SuperPlayerView.this.mSeekPos = (int) SuperPlayerView.this.mVodPlayer.getCurrentPlaybackTime();
                    TXCLog.i("SuperVodPlayerView", "save pos:" + SuperPlayerView.this.mSeekPos);
                    SuperPlayerView.this.stopPlay();
                    SuperPlayerView.this.playWithURL(SuperPlayerView.this.mCurrentSuperPlayerModel);
                }
            } else if (SuperPlayerView.this.mLivePlayer != null) {
                SuperPlayerView.this.mLivePlayer.enableHardwareDecode(isAccelerate);
                SuperPlayerView.this.stopPlay();
                SuperPlayerView.this.playWithMode(SuperPlayerView.this.mCurrentSuperPlayerModel);
            }

            if (isAccelerate) {
                LogReport.getInstance().uploadLogs("hw_decode", 0L, 0);
            } else {
                LogReport.getInstance().uploadLogs("soft_decode", 0L, 0);
            }

        }

        public void onFloatUpdate(int x, int y) {
            SuperPlayerView.this.mWindowParams.x = x;
            SuperPlayerView.this.mWindowParams.y = y;
            SuperPlayerView.this.mWindowManager.updateViewLayout(SuperPlayerView.this.mVodControllerFloat, SuperPlayerView.this.mWindowParams);
        }

        public void onReplay() {
            if (!TextUtils.isEmpty(SuperPlayerView.this.mCurrentSuperPlayerModel.videoURL)) {
                SuperPlayerView.this.playWithMode(SuperPlayerView.this.mCurrentSuperPlayerModel);
            }

            if (SuperPlayerView.this.mVodControllerLarge != null) {
                SuperPlayerView.this.mVodControllerLarge.updateReplay(false);
            }

            if (SuperPlayerView.this.mVodControllerSmall != null) {
                SuperPlayerView.this.mVodControllerSmall.updateReplay(false);
            }

        }

        public void resumeLive() {
            if (SuperPlayerView.this.mLivePlayer != null) {
                SuperPlayerView.this.mLivePlayer.resumeLive();
            }

            SuperPlayerView.this.mVodControllerSmall.updatePlayType(2);
            SuperPlayerView.this.mVodControllerLarge.updatePlayType(2);
        }

    };
    private final int OP_SYSTEM_ALERT_WINDOW = 24;

    public SuperPlayerView(Context context) {
        super(context);
        this.initView(context);
    }

    public SuperPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.initView(context);
    }

    public SuperPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.initView(context);
    }

    private void initView(Context context) {
        this.mContext = context;
        this.mRootView = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.super_vod_player_view, (ViewGroup)null);
        this.mTXCloudVideoView = (TXCloudVideoView)this.mRootView.findViewById(R.id.cloud_video_view);
        this.mVodControllerLarge = (TCVodControllerLarge)this.mRootView.findViewById(R.id.controller_large);
        this.mVodControllerSmall = (TCVodControllerSmall)this.mRootView.findViewById(R.id.controller_small);
        this.mVodControllerFloat = (TCVodControllerFloat)this.mRootView.findViewById(R.id.controller_float);
        this.mDanmuView = (TCDanmuView)this.mRootView.findViewById(R.id.danmaku_view);
        this.mVodControllerSmallParams = new android.widget.RelativeLayout.LayoutParams(-1, -1);
        this.mVodControllerLargeParams = new android.widget.RelativeLayout.LayoutParams(-1, -1);
        this.mVodControllerLarge.setVodController(this.mVodController);
        this.mVodControllerSmall.setVodController(this.mVodController);
        this.mVodControllerFloat.setVodController(this.mVodController);
        this.removeAllViews();
        this.mRootView.removeView(this.mDanmuView);
        this.mRootView.removeView(this.mTXCloudVideoView);
        this.mRootView.removeView(this.mVodControllerSmall);
        this.mRootView.removeView(this.mVodControllerLarge);
        this.mRootView.removeView(this.mVodControllerFloat);
        this.addView(this.mTXCloudVideoView);
        if (this.mPlayMode == 2) {
            this.addView(this.mVodControllerLarge);
            this.mVodControllerLarge.hide();
        } else if (this.mPlayMode == 1) {
            this.addView(this.mVodControllerSmall);
            this.mVodControllerSmall.hide();
        }

        this.addView(this.mDanmuView);
        this.post(new Runnable() {
            public void run() {
                if (SuperPlayerView.this.mPlayMode == 1) {
                    SuperPlayerView.this.mLayoutParamWindowMode = SuperPlayerView.this.getLayoutParams();
                }

                try {
                    Class parentLayoutParamClazz = SuperPlayerView.this.getLayoutParams().getClass();
                    Constructor constructor = parentLayoutParamClazz.getDeclaredConstructor(Integer.TYPE, Integer.TYPE);
                    SuperPlayerView.this.mLayoutParamFullScreenMode = (LayoutParams)constructor.newInstance(-1, -1);
                } catch (Exception var3) {
                    var3.printStackTrace();
                }

            }
        });
        LogReport.getInstance().setAppName(this.getApplicationName());
        LogReport.getInstance().setPackageName(this.getPackagename());
    }

    private String getApplicationName() {
        Context context = this.mContext.getApplicationContext();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    private String getPackagename() {
        String packagename = "";
        if (this.mContext != null) {
            try {
                PackageInfo info = this.mContext.getPackageManager().getPackageInfo(this.mContext.getPackageName(), 0);
                packagename = info.packageName;
            } catch (Exception var4) {
                var4.printStackTrace();
            }
        }

        return packagename;
    }

    private void initVodPlayer(Context context) {
        if (this.mVodPlayer == null) {
            this.mVodPlayer = new TXVodPlayer(context);
            SuperPlayerGlobalConfig config = SuperPlayerGlobalConfig.getInstance();
            this.mVodPlayConfig = new TXVodPlayConfig();
            this.mVodPlayConfig.setCacheFolderPath(Environment.getExternalStorageDirectory().getPath() + "/txcache");
            this.mVodPlayConfig.setMaxCacheItems(config.maxCacheItem);
            this.mVodPlayer.setConfig(this.mVodPlayConfig);
            this.mVodPlayer.setRenderMode(config.renderMode);
            this.mVodPlayer.setVodListener(this);
            this.mVodPlayer.enableHardwareDecode(config.enableHWAcceleration);
        }
    }

    private void initLivePlayer(Context context) {
        if (this.mLivePlayer == null) {
            this.mLivePlayer = new TXLivePlayer(context);
            SuperPlayerGlobalConfig config = SuperPlayerGlobalConfig.getInstance();
            this.mLivePlayConfig = new TXLivePlayConfig();
            this.mLivePlayer.setConfig(this.mLivePlayConfig);
            this.mLivePlayer.setRenderMode(config.renderMode);
            this.mLivePlayer.setRenderRotation(0);
            this.mLivePlayer.setPlayListener(this);
            this.mLivePlayer.enableHardwareDecode(config.enableHWAcceleration);
        }
    }

    public void playWithMode(SuperPlayerModel superPlayerModel) {
        this.initLivePlayer(this.getContext());
        this.initVodPlayer(this.getContext());
        this.stopPlay();
        boolean isLivePlay = this.isLivePlay(superPlayerModel);
        TXCLog.i("SuperVodPlayerView", "playWithMode isLivePlay:" + isLivePlay);
        if (isLivePlay) {
            this.mReportLiveStartTime = System.currentTimeMillis();
            this.mLivePlayer.setPlayerView(this.mTXCloudVideoView);
            ArrayList videoQulities;
            if (this.mPlayType == 1) {
                this.playTimeShiftLiveURL(superPlayerModel);
                if (superPlayerModel.multiVideoURLs != null && !superPlayerModel.multiVideoURLs.isEmpty()) {
                    this.playMultiStreamLiveURL(superPlayerModel);
                } else {
                    videoQulities = new ArrayList();
                    TCVideoQulity quality = new TCVideoQulity();
                    quality.index = 2;
                    quality.name = "FHD";
                    quality.title = "超清";
                    quality.url = superPlayerModel.videoURL;
                    videoQulities.add(quality);
                    this.mVodControllerLarge.setVideoQualityList(videoQulities);
                    this.mVodControllerLarge.updateVideoQulity(quality);
                }
            } else {
                videoQulities = new ArrayList();
                this.mVodControllerLarge.setVideoQualityList(videoQulities);
                this.playNormalLiveURL(superPlayerModel);
            }
        } else {
            this.mReportVodStartTime = System.currentTimeMillis();
            this.mVodPlayer.setPlayerView(this.mTXCloudVideoView);
            if (!TextUtils.isEmpty(superPlayerModel.videoURL)) {
                this.playWithURL(superPlayerModel);
            } else {
                this.playWithFileId(superPlayerModel);
            }
        }

        this.mCurrentPlayType = isLivePlay ? 2 : 1;
        this.mVodControllerSmall.updatePlayType(isLivePlay ? 2 : 1);
        this.mVodControllerLarge.updatePlayType(isLivePlay ? 2 : 1);
        this.mVodControllerSmall.updateTitle(superPlayerModel.title);
        this.mVodControllerLarge.updateTitle(superPlayerModel.title);
        this.mVodControllerSmall.updateVideoProgress(0L, 0L);
        this.mVodControllerLarge.updateVideoProgress(0L, 0L);
        TCPlayImageSpriteInfo info = superPlayerModel.imageInfo;
        this.mVodControllerLarge.updateVttAndImages(info);
        this.mVodControllerLarge.updateKeyFrameDescInfos(superPlayerModel.keyFrameDescInfos);

        if(mHiderViewCallback!=null)
        {
            mHiderViewCallback.hideTitle();
            mHiderViewCallback.hideTitleBackgrdView();
            mHiderViewCallback.hideFullScreenView();
        }
    }

    private void playLiveURL(String videoURL) {
        if (this.mLivePlayer != null) {
            this.mLivePlayer.setPlayListener(this);
            int result = this.mLivePlayer.startPlay(videoURL, this.mPlayType);
            if (result != 0) {
                TXCLog.e("SuperVodPlayerView", "playLiveURL videoURL:" + videoURL + ",result:" + result);
            } else {
                this.mCurrentPlayState = 1;
                TXCLog.e("SuperVodPlayerView", "playLiveURL mCurrentPlayState:" + this.mCurrentPlayState);
            }
        }

    }

    private boolean isLivePlay(SuperPlayerModel superPlayerModel) {
        String videoURL = superPlayerModel.videoURL;
        if (TextUtils.isEmpty(superPlayerModel.videoURL)) {
            return false;
        } else if (videoURL.startsWith("rtmp://")) {
            this.mPlayType = 0;
            return true;
        } else if ((videoURL.startsWith("http://") || videoURL.startsWith("https://")) && videoURL.contains(".flv")) {
            this.mPlayType = 1;
            return true;
        } else {
            return false;
        }
    }

    private void playWithURL(SuperPlayerModel superPlayerModel) {
        this.mCurrentSuperPlayerModel = superPlayerModel;
        TXCLog.i("SuperVodPlayerView", "playWithURL videoURL:" + superPlayerModel.videoURL);
        String videoURL = this.parseVodURL(superPlayerModel);
        if (videoURL.endsWith(".m3u8")) {
            this.mIsMultiBitrateStream = true;
        }

        if (this.mVodPlayer != null) {
            this.mDefaultSet = false;
            this.mVodPlayer.setAutoPlay(true);
            this.mVodPlayer.setVodListener(this);
            int ret = this.mVodPlayer.startPlay(videoURL);
            if (ret == 0) {
                this.mCurrentPlayState = 1;
                TXCLog.e("SuperVodPlayerView", "playWithURL mCurrentPlayState:" + this.mCurrentPlayState);
            }
        }

        this.mIsPlayWithFileid = false;
    }

    private String parseVodURL(SuperPlayerModel superPlayerModel) {
        return superPlayerModel.videoURL;
    }

    private void playTimeShiftLiveURL(SuperPlayerModel superPlayerModel) {
        this.mCurrentSuperPlayerModel = superPlayerModel;
        String liveURL = superPlayerModel.videoURL;
        String bizid = liveURL.substring(liveURL.indexOf("//") + 2, liveURL.indexOf("."));
        String domian = SuperPlayerGlobalConfig.getInstance().playShiftDomain;
        String streamid = liveURL.substring(liveURL.lastIndexOf("/") + 1, liveURL.lastIndexOf("."));
        int appid = superPlayerModel.appid;
        TXCLog.i("SuperVodPlayerView", "bizid:" + bizid + ",streamid:" + streamid + ",appid:" + appid);
        this.mTXVideoConfig = new TCVideoConfig();
        this.mTXVideoConfig.isLive = true;
        this.mTXVideoConfig.appid = appid;
        this.mTXVideoConfig.streamid = streamid;
        this.mTXVideoConfig.bizid = bizid;
        this.mTXVideoConfig.videoURL = liveURL;
        this.mTXVideoConfig.isNormalLive = false;
        this.playLiveURL(liveURL);

        try {
            int bizidNum = Integer.valueOf(bizid);
            this.mLivePlayer.prepareLiveSeek(domian, bizidNum);
        } catch (NumberFormatException var8) {
            var8.printStackTrace();
            Log.e("SuperVodPlayerView", "playTimeShiftLiveURL: bizidNum 错误 = %s " + this.mTXVideoConfig.bizid);
        }

    }

    private void playNormalLiveURL(SuperPlayerModel superPlayerModel) {
        this.mCurrentSuperPlayerModel = superPlayerModel;
        TXCLog.i("SuperVodPlayerView", "playNormalLiveURL videoURL:" + superPlayerModel.videoURL);
        this.mTXVideoConfig = new TCVideoConfig();
        this.mTXVideoConfig.isLive = true;
        this.mTXVideoConfig.videoURL = superPlayerModel.videoURL;
        this.mTXVideoConfig.isNormalLive = true;
        this.playLiveURL(this.mTXVideoConfig.videoURL);
    }

    private void playMultiStreamLiveURL(SuperPlayerModel superPlayerModel) {
        this.mLivePlayConfig.setAutoAdjustCacheTime(false);
        this.mLivePlayConfig.setMaxAutoAdjustCacheTime(5.0F);
        this.mLivePlayConfig.setMinAutoAdjustCacheTime(5.0F);
        this.mLivePlayer.setConfig(this.mLivePlayConfig);
        ArrayList<TCVideoQulity> videoQulities = new ArrayList();
        TCVideoQulity quality = new TCVideoQulity();
        if (!TextUtils.isEmpty(superPlayerModel.videoURL) && superPlayerModel.videoURL.contains("5815.liveplay.myqcloud.com")) {
            quality.index = 0;
            quality.name = "SD";
            quality.title = "标清";
            quality.url = superPlayerModel.videoURL.replace(".flv", "_550.flv");
            videoQulities.add(quality);
            quality = new TCVideoQulity();
            quality.index = 1;
            quality.name = "HD";
            quality.title = "高清";
            quality.url = superPlayerModel.videoURL.replace(".flv", "_900.flv");
            videoQulities.add(quality);
        }

        quality = new TCVideoQulity();
        quality.index = 2;
        quality.name = "FHD";
        quality.title = "超清";
        quality.url = superPlayerModel.videoURL;
        videoQulities.add(quality);
        this.mVodControllerLarge.setVideoQualityList(videoQulities);
        this.mVodControllerLarge.updateVideoQulity(quality);
        if (this.mWatcher == null) {
            this.mWatcher = new NetWatcher(this.mContext);
        }

        this.mWatcher.start(superPlayerModel.videoURL, this.mLivePlayer);
    }

    private void playWithFileId(SuperPlayerModel superPlayerModel) {
        SuperVodInfoLoader loader = new SuperVodInfoLoader();
        loader.setOnVodInfoLoadListener(new SuperVodInfoLoader.OnVodInfoLoadListener() {
            public void onSuccess(PlayInfoResponseParser response) {
                SuperPlayerModel playerModel = new SuperPlayerModel();
                TCPlayInfoStream masterPlayList = response.getMasterPlayList();
                playerModel.imageInfo = response.getImageSpriteInfo();
                playerModel.keyFrameDescInfos = response.getKeyFrameDescInfos();
                if (masterPlayList != null) {
                    String videoURLx = masterPlayList.getUrl();
                    playerModel.videoURL = videoURLx;
                    SuperPlayerView.this.playWithURL(playerModel);
                    SuperPlayerView.this.mIsMultiBitrateStream = true;
                    SuperPlayerView.this.mIsPlayWithFileid = true;
                } else {
                    LinkedHashMap<String, TCPlayInfoStream> transcodeList = response.getTranscodePlayList();
                    String defaultClassificationx;
                    TCVideoQulity defaultVideoQulity;
                    ArrayList videoQulities;
                    if (transcodeList != null && transcodeList.size() != 0) {
                        String defaultClassification = response.getDefaultVideoClassification();
                        TCPlayInfoStream stream = (TCPlayInfoStream)transcodeList.get(defaultClassification);
                        defaultClassificationx = stream.getUrl();
                        playerModel.videoURL = defaultClassificationx;
                        SuperPlayerView.this.playWithURL(playerModel);
                        defaultVideoQulity = SuperPlayerUtil.convertToVideoQuality(stream);
                        SuperPlayerView.this.mVodControllerLarge.updateVideoQulity(defaultVideoQulity);
                        videoQulities = SuperPlayerUtil.convertToVideoQualityList(transcodeList);
                        SuperPlayerView.this.mVodControllerLarge.setVideoQualityList(videoQulities);
                        SuperPlayerView.this.mIsMultiBitrateStream = false;
                        SuperPlayerView.this.mIsPlayWithFileid = true;
                    } else {
                        TCPlayInfoStream sourceStream = response.getSource();
                        if (sourceStream != null) {
                            String videoURL = sourceStream.getUrl();
                            playerModel.videoURL = videoURL;
                            SuperPlayerView.this.playWithURL(playerModel);
                            defaultClassificationx = response.getDefaultVideoClassification();
                            if (defaultClassificationx != null) {
                                defaultVideoQulity = SuperPlayerUtil.convertToVideoQuality(sourceStream, defaultClassificationx);
                                SuperPlayerView.this.mVodControllerLarge.updateVideoQulity(defaultVideoQulity);
                                videoQulities = new ArrayList();
                                videoQulities.add(defaultVideoQulity);
                                SuperPlayerView.this.mVodControllerLarge.setVideoQualityList(videoQulities);
                                SuperPlayerView.this.mIsMultiBitrateStream = false;
                            }
                        }

                    }
                }
            }

            public void onFail(int errCode) {
            }
        });
        loader.getVodByFileId(superPlayerModel);
    }

    public void onResume() {
        if (this.mDanmuView != null && this.mDanmuView.isPrepared() && this.mDanmuView.isPaused()) {
            this.mDanmuView.resume();
        }

        this.resume();
    }

    private void resume() {
        if (this.mCurrentPlayType == 1 && this.mVodPlayer != null) {
            this.mVodPlayer.resume();
        }

    }

    public void onPause() {
        if (this.mDanmuView != null && this.mDanmuView.isPrepared()) {
            this.mDanmuView.pause();
        }

        this.pause();
    }

    private void pause() {
        if (this.mCurrentPlayType == 1 && this.mVodPlayer != null) {
            this.mVodPlayer.pause();
        }

    }

    public void resetPlayer() {
        if (this.mDanmuView != null) {
            this.mDanmuView.release();
            this.mDanmuView = null;
        }

        this.stopPlay();
    }

    private void stopPlay() {
        if (this.mVodPlayer != null) {
            this.mVodPlayer.setVodListener((ITXVodPlayListener)null);
            this.mVodPlayer.stopPlay(false);
        }

        if (this.mLivePlayer != null) {
            this.mLivePlayer.setPlayListener((ITXLivePlayListener)null);
            this.mLivePlayer.stopPlay(false);
            this.mTXCloudVideoView.removeVideoView();
        }

        if (this.mWatcher != null) {
            this.mWatcher.stop();
        }

        this.mCurrentPlayState = 2;
        TXCLog.e("SuperVodPlayerView", "stopPlay mCurrentPlayState:" + this.mCurrentPlayState);
        this.reportPlayTime();
    }

    private void reportPlayTime() {
        long reportEndTime;
        long diff;
        if (this.mReportLiveStartTime != -1L) {
            reportEndTime = System.currentTimeMillis();
            diff = (reportEndTime - this.mReportLiveStartTime) / 1000L;
            LogReport.getInstance().uploadLogs("superlive", diff, 0);
            this.mReportLiveStartTime = -1L;
        }

        if (this.mReportVodStartTime != -1L) {
            reportEndTime = System.currentTimeMillis();
            diff = (reportEndTime - this.mReportVodStartTime) / 1000L;
            LogReport.getInstance().uploadLogs("supervod", diff, this.mIsPlayWithFileid ? 1 : 0);
            this.mReportVodStartTime = -1L;
        }

    }

    public void setPlayerViewCallback(SuperPlayerView.PlayerViewCallback callback) {
        this.mPlayerViewCallback = callback;
    }

    public void setmHiderViewCallback(SuperPlayerView.HideViews callback)
    {
        this.mHiderViewCallback = callback;
    }

    public void fullScreen(boolean isFull) {
        if (this.getContext() instanceof Activity) {
            Activity activity = (Activity)this.getContext();
            View decorView;
            if (isFull) {
                decorView = activity.getWindow().getDecorView();
                if (decorView == null) {
                    return;
                }

                if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
                    decorView.setSystemUiVisibility(8);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    int uiOptions = 4102;
                    decorView.setSystemUiVisibility(uiOptions);
                }
            } else {
                decorView = activity.getWindow().getDecorView();
                if (decorView == null) {
                    return;
                }

                if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
                    decorView.setSystemUiVisibility(0);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    decorView.setSystemUiVisibility(0);
                }
            }
        }

    }

    private void showSnapshotWindow(final Bitmap bmp) {
        final PopupWindow popupWindow = new PopupWindow(this.mContext);
        popupWindow.setWidth(-2);
        popupWindow.setHeight(-2);
        View view = LayoutInflater.from(this.mContext).inflate(R.layout.layout_new_vod_snap, (ViewGroup)null);
        ImageView imageView = (ImageView)view.findViewById(R.id.iv_snap);
        imageView.setImageBitmap(bmp);
        popupWindow.setContentView(view);
        popupWindow.setOutsideTouchable(true);
        popupWindow.showAtLocation(this.mRootView, 48, 1800, 300);
        AsyncTask.execute(new Runnable() {
            public void run() {
                SuperPlayerView.this.save2MediaStore(bmp);
            }
        });
        this.postDelayed(new Runnable() {
            public void run() {
                popupWindow.dismiss();
            }
        }, 3000L);
    }

    private void save2MediaStore(Bitmap image) {
        try {
            File appDir = new File(Environment.getExternalStorageDirectory(), "superplayer");
            if (!appDir.exists()) {
                appDir.mkdir();
            }

            long dateSeconds = System.currentTimeMillis() / 1000L;
            String fileName = System.currentTimeMillis() + ".jpg";
            File file = new File(appDir, fileName);
            String filePath = file.getAbsolutePath();
            ContentValues values = new ContentValues();
            ContentResolver resolver = this.mContext.getContentResolver();
            values.put("_data", filePath);
            values.put("title", fileName);
            values.put("_display_name", fileName);
            values.put("date_added", dateSeconds);
            values.put("date_modified", dateSeconds);
            values.put("mime_type", "image/png");
            values.put("width", image.getWidth());
            values.put("height", image.getHeight());
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            OutputStream out = resolver.openOutputStream(uri);
            image.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
            values.clear();
            values.put("_size", (new File(filePath)).length());
            resolver.update(uri, values, (String)null, (String[])null);
        } catch (Exception var12) {
            ;
        }

    }

    private void rotateScreenOrientation(int orientation) {
        switch(orientation) {
            case 1:
                ((Activity)this.mContext).setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case 2:
                ((Activity)this.mContext).setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
        }

    }

    public void onPlayEvent(TXVodPlayer player, int event, Bundle param) {
        if (event != 2005) {
            String playEventLog = "TXVodPlayer onPlayEvent event: " + event + ", " + param.getString("EVT_MSG");
            TXCLog.d("SuperVodPlayerView", playEventLog);
        }

        if (event == 2013) {
            this.mVodControllerSmall.updateLiveLoadingState(false);
            this.mVodControllerLarge.updateLiveLoadingState(false);
            this.mVodControllerSmall.updatePlayState(true);
            this.mVodControllerLarge.updatePlayState(true);
            this.mVodControllerSmall.updateReplay(false);
            this.mVodControllerLarge.updateReplay(false);
            if (this.mIsMultiBitrateStream) {
                ArrayList<TXBitrateItem> bitrateItems = this.mVodPlayer.getSupportedBitrates();
                if (bitrateItems == null || bitrateItems.size() == 0) {
                    return;
                }

                Collections.sort(bitrateItems);
                ArrayList<TCVideoQulity> videoQulities = new ArrayList();
                int size = bitrateItems.size();

                TXBitrateItem bitrateItem;
                TCVideoQulity defaultVideoQuality;
                for(int i = 0; i < size; ++i) {
                    bitrateItem = (TXBitrateItem)bitrateItems.get(i);
                    defaultVideoQuality = SuperPlayerUtil.convertToVideoQuality(bitrateItem, i);
                    videoQulities.add(defaultVideoQuality);
                }

                if (!this.mDefaultSet) {
                    TXBitrateItem defaultitem = (TXBitrateItem)bitrateItems.get(bitrateItems.size() - 1);
                    this.mVodPlayer.setBitrateIndex(defaultitem.index);
                    bitrateItem = (TXBitrateItem)bitrateItems.get(bitrateItems.size() - 1);
                    defaultVideoQuality = SuperPlayerUtil.convertToVideoQuality(bitrateItem, bitrateItems.size() - 1);
                    this.mVodControllerLarge.updateVideoQulity(defaultVideoQuality);
                    this.mDefaultSet = true;
                }

                this.mVodControllerLarge.setVideoQualityList(videoQulities);
            }
        } else if (event == 2003) {
            if (this.mChangeHWAcceleration) {
                TXCLog.i("SuperVodPlayerView", "seek pos:" + this.mSeekPos);
                this.mVodController.seekTo(this.mSeekPos);
                this.mChangeHWAcceleration = false;
            }
        } else if (event == 2006) {
            this.mCurrentPlayState = 2;
            this.mVodControllerSmall.updatePlayState(false);
            this.mVodControllerLarge.updatePlayState(false);
            this.mVodControllerSmall.updateReplay(true);
            this.mVodControllerLarge.updateReplay(true);
        } else if (event == 2005) {
            int progress = param.getInt("EVT_PLAY_PROGRESS_MS");
            int duration = param.getInt("EVT_PLAY_DURATION_MS");
            this.mVodControllerSmall.updateVideoProgress((long)(progress / 1000), (long)(duration / 1000));
            this.mVodControllerLarge.updateVideoProgress((long)(progress / 1000), (long)(duration / 1000));
        }

        if (event < 0) {
            this.mVodPlayer.stopPlay(true);
            this.mVodControllerSmall.updatePlayState(false);
            this.mVodControllerLarge.updatePlayState(false);
            Toast.makeText(this.mContext, param.getString("EVT_MSG"), Toast.LENGTH_SHORT).show();
        }

    }

    public void onNetStatus(TXVodPlayer player, Bundle status) {
    }

    public void onPlayEvent(int event, Bundle param) {
        if (event != 2005) {
            String playEventLog = "TXLivePlayer onPlayEvent event: " + event + ", " + param.getString("EVT_MSG");
            TXCLog.d("SuperVodPlayerView", playEventLog);
        }

        if (event == 2013) {
            this.mVodControllerSmall.updateLiveLoadingState(false);
            this.mVodControllerLarge.updateLiveLoadingState(false);
            this.mVodControllerSmall.updatePlayState(true);
            this.mVodControllerLarge.updatePlayState(true);
            this.mVodControllerSmall.updateReplay(false);
            this.mVodControllerLarge.updateReplay(false);
        } else if (event == 2004) {
            this.mVodControllerSmall.updateLiveLoadingState(false);
            this.mVodControllerLarge.updateLiveLoadingState(false);
            this.mVodControllerSmall.updatePlayState(true);
            this.mVodControllerLarge.updatePlayState(true);
            this.mVodControllerSmall.updateReplay(false);
            this.mVodControllerLarge.updateReplay(false);
            if (this.mWatcher != null) {
                this.mWatcher.exitLoading();
            }
        } else if (event != -2301 && event != 2006) {
            if (event == 2007) {
                this.mVodControllerSmall.updateLiveLoadingState(true);
                this.mVodControllerLarge.updateLiveLoadingState(true);
                if (this.mWatcher != null) {
                    this.mWatcher.enterLoading();
                }
            } else if (event != 2003 && event != 2009) {
                if (event == 2011) {
                    return;
                }

                if (event == 2015) {
                    Toast.makeText(this.mContext, "清晰度切换成功", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (event == -2307) {
                    Toast.makeText(this.mContext, "清晰度切换失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (event == 2005) {
                    int progress = param.getInt("EVT_PLAY_PROGRESS_MS");
                    int duration = param.getInt("EVT_PLAY_DURATION_MS");
                    this.mVodControllerSmall.updateVideoProgress((long)(progress / 1000), (long)(duration / 1000));
                    this.mVodControllerLarge.updateVideoProgress((long)(progress / 1000), (long)(duration / 1000));
                }
            }
        } else if (this.mCurrentPlayType == 3) {
            this.mVodController.resumeLive();
            Toast.makeText(this.mContext, "时移失败,返回直播", Toast.LENGTH_SHORT).show();
            this.mVodControllerSmall.updateReplay(false);
            this.mVodControllerLarge.updateReplay(false);
            this.mVodControllerSmall.updateLiveLoadingState(false);
            this.mVodControllerLarge.updateLiveLoadingState(false);
        } else {
            this.stopPlay();
            this.mVodControllerSmall.updatePlayState(false);
            this.mVodControllerLarge.updatePlayState(false);
            this.mVodControllerSmall.updateReplay(true);
            this.mVodControllerLarge.updateReplay(true);
            if (event == -2301) {
                Toast.makeText(this.mContext, "网络不给力,点击重试", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this.mContext, param.getString("EVT_MSG"), Toast.LENGTH_SHORT).show();
            }
        }

    }

    public void onNetStatus(Bundle status) {
    }

    public void requestPlayMode(int playMode) {
        if (playMode == 1) {
            if (this.mVodController != null) {
                this.mVodController.onRequestPlayMode(1);
            }
        } else if (playMode == 3) {
            if (this.mPlayerViewCallback != null) {
                this.mPlayerViewCallback.onQuit(1);
            }

            if (this.mVodController != null) {
                this.mVodController.onRequestPlayMode(3);
            }
        }

    }

    private boolean checkOp(Context context, int op) {
        if (Build.VERSION.SDK_INT >= 19) {
            @SuppressLint("WrongConstant") AppOpsManager manager = (AppOpsManager)context.getSystemService("appops");

            try {
                Method method = AppOpsManager.class.getDeclaredMethod("checkOp", Integer.TYPE, Integer.TYPE, String.class);
                return 0 == (Integer)method.invoke(manager, op, Binder.getCallingUid(), context.getPackageName());
            } catch (Exception var5) {
                Log.e("SuperVodPlayerView", Log.getStackTraceString(var5));
            }
        }

        return true;
    }

    public int getPlayMode() {
        return this.mPlayMode;
    }

    public int getPlayState() {
        return this.mCurrentPlayState;
    }

    public void release() {
        if (this.mVodControllerSmall != null) {
            this.mVodControllerSmall.release();
        }

        if (this.mVodControllerLarge != null) {
            this.mVodControllerLarge.release();
        }

        if (this.mVodControllerFloat != null) {
            this.mVodControllerFloat.release();
        }

        TCHttpURLClient.getInstance().release();
    }

    public interface PlayerViewCallback {
        void hideViews();

        void showViews();

        void onQuit(int var1);
    }

    public interface HideViews{
        void hideTitle();
        void hideTitleBackgrdView();
        void hideFullScreenView();
    }

    public void setTitleVisable(int v) {
        mVodControllerSmall.setTitleVisable(v);
    }
    public void setTitleBackgdVisable(int v){
        mVodControllerSmall.setTitleBackgrdVisable(v);
    }
    public void setFullScreenVisable(int v){
        mVodControllerSmall.setFullScreenVisable(v);
    }
}
