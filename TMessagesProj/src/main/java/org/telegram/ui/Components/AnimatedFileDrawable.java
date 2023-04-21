/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimatedFileDrawableStream;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.utils.BitmapsCache;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AnimatedFileDrawable extends BitmapDrawable implements Animatable, BitmapsCache.Cacheable {

    public boolean skipFrameUpdate;
    public long currentTime;

    private static native long createDecoder(String src, int[] params, int account, long streamFileSize, Object readCallback, boolean preview);

    private static native void destroyDecoder(long ptr);

    private static native void stopDecoder(long ptr);

    private static native int getVideoFrame(long ptr, Bitmap bitmap, int[] params, int stride, boolean preview, float startTimeSeconds, float endTimeSeconds);

    private static native void seekToMs(long ptr, long ms, boolean precise);

    private static native int getFrameAtTime(long ptr, long ms, Bitmap bitmap, int[] data, int stride);

    private static native void prepareToSeek(long ptr);

    private static native void getVideoInfo(int sdkVersion, String src, int[] params);

    public final static int PARAM_NUM_SUPPORTED_VIDEO_CODEC = 0;
    public final static int PARAM_NUM_WIDTH = 1;
    public final static int PARAM_NUM_HEIGHT = 2;
    public final static int PARAM_NUM_BITRATE = 3;
    public final static int PARAM_NUM_DURATION = 4;
    public final static int PARAM_NUM_AUDIO_FRAME_SIZE = 5;
    public final static int PARAM_NUM_VIDEO_FRAME_SIZE = 6;
    public final static int PARAM_NUM_FRAMERATE = 7;
    public final static int PARAM_NUM_ROTATION = 8;
    public final static int PARAM_NUM_SUPPORTED_AUDIO_CODEC = 9;
    public final static int PARAM_NUM_HAS_AUDIO = 10;
    public final static int PARAM_NUM_COUNT = 11;

    private long lastFrameTime;
    private int lastTimeStamp;
    private int invalidateAfter = 50;
    private final int[] metaData = new int[5];
    private Runnable loadFrameTask;
    private Bitmap renderingBitmap;
    private int renderingBitmapTime;
    private Bitmap nextRenderingBitmap;
    private int nextRenderingBitmapTime;
    private Bitmap backgroundBitmap;
    private int backgroundBitmapTime;
    private boolean destroyWhenDone;
    private boolean decoderCreated;
    private boolean decodeSingleFrame;
    private boolean singleFrameDecoded;
    private boolean forceDecodeAfterNextFrame;
    private File path;
    private long streamFileSize;
    private int currentAccount;
    private boolean recycleWithSecond;
    private volatile long pendingSeekTo = -1;
    private volatile long pendingSeekToUI = -1;
    private boolean pendingRemoveLoading;
    private int pendingRemoveLoadingFramesReset;
    private boolean isRestarted;
    private final Object sync = new Object();

    private boolean invalidateParentViewWithSecond;
    public boolean ignoreNoParent;

    private long lastFrameDecodeTime;

    private RectF actualDrawRect = new RectF();

    private BitmapShader renderingShader;
    private BitmapShader nextRenderingShader;
    private BitmapShader backgroundShader;

    private BitmapShader renderingShaderBackgroundDraw;

    private int[] roundRadius = new int[4];
    private int[] roundRadiusBackup;
    private Matrix shaderMatrix = new Matrix();
    private Path roundPath = new Path();
    private static float[] radii = new float[8];

    private Matrix shaderMatrixBackgroundDraw;

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private boolean applyTransformation;
    private final RectF dstRect = new RectF();
    private volatile boolean isRunning;
    private volatile boolean isRecycled;
    public volatile long nativePtr;
    private DispatchQueue decodeQueue;
    private float startTime;
    private float endTime;
    private int renderingHeight;
    private int renderingWidth;
    private boolean precache;
    private float scaleFactor = 1f;
    public boolean isWebmSticker;
    private final TLRPC.Document document;
    private RectF dstRectBackground;
    private Paint backgroundPaint;

    private View parentView;
    private ArrayList<View> secondParentViews = new ArrayList<>();

    private ArrayList<ImageReceiver> parents = new ArrayList<>();

    private AnimatedFileDrawableStream stream;

    private boolean useSharedQueue;
    private boolean invalidatePath = true;
    private boolean invalidateTaskIsRunning;
    private boolean limitFps;

    public int repeatCount;
    BitmapsCache bitmapsCache;
    BitmapsCache.Metadata cacheMetadata;

    private static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(8, new ThreadPoolExecutor.DiscardPolicy());

    private Runnable uiRunnableNoFrame = new Runnable() {
        @Override
        public void run() {
            chekDestroyDecoder();
            loadFrameTask = null;
            scheduleNextGetFrame();
            invalidateInternal();
        }
    };

    boolean generatingCache;
    Runnable cacheGenRunnable;
    private Runnable uiRunnableGenerateCache = new Runnable() {
        @Override
        public void run() {
            if (!isRecycled && !destroyWhenDone && !generatingCache) {
                startTime = System.currentTimeMillis();
                if (RLottieDrawable.lottieCacheGenerateQueue == null) {
                    RLottieDrawable.createCacheGenQueue();
                }
                generatingCache = true;
                loadFrameTask = null;
                RLottieDrawable.lottieCacheGenerateQueue.postRunnable(cacheGenRunnable = () -> {
                    bitmapsCache.createCache();
                    AndroidUtilities.runOnUIThread(() -> {
                        generatingCache = false;
                        scheduleNextGetFrame();
                    });
                });
            }
        }
    };

    private void chekDestroyDecoder() {
        if (loadFrameRunnable == null && destroyWhenDone && nativePtr != 0 && !generatingCache) {
            destroyDecoder(nativePtr);
            nativePtr = 0;
        }
        if (!canLoadFrames()) {
            if (renderingBitmap != null) {
                renderingBitmap.recycle();
                renderingBitmap = null;
            }
            if (backgroundBitmap != null) {
                backgroundBitmap.recycle();
                backgroundBitmap = null;
            }
            if (decodeQueue != null) {
                decodeQueue.recycle();
                decodeQueue = null;
            }
            invalidateInternal();
        }
    }

    private void invalidateInternal() {
        for (int i = 0; i < parents.size(); i++) {
            parents.get(i).invalidate();
        }
    }

    private Runnable uiRunnable = new Runnable() {
        @Override
        public void run() {
            chekDestroyDecoder();
            if (stream != null && pendingRemoveLoading) {
                FileLoader.getInstance(currentAccount).removeLoadingVideo(stream.getDocument(), false, false);
            }
            if (pendingRemoveLoadingFramesReset <= 0) {
                pendingRemoveLoading = true;
            } else {
                pendingRemoveLoadingFramesReset--;
            }
            if (!forceDecodeAfterNextFrame) {
                singleFrameDecoded = true;
            } else {
                forceDecodeAfterNextFrame = false;
            }
            loadFrameTask = null;
            nextRenderingBitmap = backgroundBitmap;
            nextRenderingBitmapTime = backgroundBitmapTime;
            nextRenderingShader = backgroundShader;
            if (isRestarted) {
                isRestarted = false;
                repeatCount++;
                checkRepeat();
            }

            if (metaData[3] < lastTimeStamp) {
                lastTimeStamp = startTime > 0 ? (int) (startTime * 1000) : 0;
            }
            if (metaData[3] - lastTimeStamp != 0) {
                invalidateAfter = metaData[3] - lastTimeStamp;
                if (limitFps && invalidateAfter < 32) {
                    invalidateAfter = 32;
                }
            }
            if (pendingSeekToUI >= 0 && pendingSeekTo == -1) {
                pendingSeekToUI = -1;
                invalidateAfter = 0;
            }
            lastTimeStamp = metaData[3];
            if (!secondParentViews.isEmpty()) {
                for (int a = 0, N = secondParentViews.size(); a < N; a++) {
                    secondParentViews.get(a).invalidate();
                }
            }
            invalidateInternal();
            scheduleNextGetFrame();
        }
    };

    public void checkRepeat() {
        if (ignoreNoParent) {
            start();
            return;
        }
        int count = 0;
        for (int j = 0; j < parents.size(); j++) {
            ImageReceiver parent = parents.get(j);
            if (!parent.isAttachedToWindow()) {
                parents.remove(j);
                j--;
            }
            if (parent.animatedFileDrawableRepeatMaxCount > 0 && repeatCount >= parent.animatedFileDrawableRepeatMaxCount) {
                count++;
            }
        }
        if (parents.size() == count) {
            stop();
        } else {
            start();
        }
    }

    private Runnable loadFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecycled) {
                if (!decoderCreated && nativePtr == 0) {
                    nativePtr = createDecoder(path.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, false);
                    if (nativePtr != 0 && (metaData[0] > 3840 || metaData[1] > 3840)) {
                        destroyDecoder(nativePtr);
                        nativePtr = 0;
                    }
                    updateScaleFactor();
                    decoderCreated = true;
                }
                try {
                    if (bitmapsCache != null) {
                        if (backgroundBitmap == null) {
                            backgroundBitmap = Bitmap.createBitmap(renderingWidth, renderingHeight, Bitmap.Config.ARGB_8888);
                        }
                        if (cacheMetadata == null) {
                            cacheMetadata = new BitmapsCache.Metadata();
                        }
                        lastFrameDecodeTime = System.currentTimeMillis();
                        int lastFrame = cacheMetadata.frame;
                        int result = bitmapsCache.getFrame(backgroundBitmap, cacheMetadata);
                        if (result != -1 && cacheMetadata.frame < lastFrame) {
                            isRestarted = true;
                        }
                        metaData[3] = backgroundBitmapTime = cacheMetadata.frame * Math.max(16, metaData[4] / Math.max(1, bitmapsCache.getFrameCount()));

                        if (bitmapsCache.needGenCache()) {
                            AndroidUtilities.runOnUIThread(uiRunnableGenerateCache);
                        }
                        if (result == -1) {
                            AndroidUtilities.runOnUIThread(uiRunnableNoFrame);
                        } else {
                            AndroidUtilities.runOnUIThread(uiRunnable);
                        }
                        return;
                    }

                    if (nativePtr != 0 || metaData[0] == 0 || metaData[1] == 0) {
                        if (backgroundBitmap == null && metaData[0] > 0 && metaData[1] > 0) {
                            try {
                                backgroundBitmap = Bitmap.createBitmap((int) (metaData[0] * scaleFactor), (int) (metaData[1] * scaleFactor), Bitmap.Config.ARGB_8888);
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                            if (backgroundShader == null && backgroundBitmap != null && hasRoundRadius()) {
                                backgroundShader = new BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                            }
                        }
                        boolean seekWas = false;
                        if (pendingSeekTo >= 0) {
                            metaData[3] = (int) pendingSeekTo;
                            long seekTo = pendingSeekTo;
                            synchronized (sync) {
                                pendingSeekTo = -1;
                            }
                            seekWas = true;
                            if (stream != null) {
                                stream.reset();
                            }
                            seekToMs(nativePtr, seekTo, true);
                        }
                        if (backgroundBitmap != null) {
                            lastFrameDecodeTime = System.currentTimeMillis();

                            if (getVideoFrame(nativePtr, backgroundBitmap, metaData, backgroundBitmap.getRowBytes(), false, startTime, endTime) == 0) {
                                AndroidUtilities.runOnUIThread(uiRunnableNoFrame);
                                return;
                            }
                            if (metaData[3] < lastTimeStamp) {
                                isRestarted = true;
                            }
                            if (seekWas) {
                                lastTimeStamp = metaData[3];
                            }

                            backgroundBitmapTime = metaData[3];
                        }
                    } else {
                        AndroidUtilities.runOnUIThread(uiRunnableNoFrame);
                        return;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            AndroidUtilities.runOnUIThread(uiRunnable);
        }
    };

    private void updateScaleFactor() {
        if (!isWebmSticker && renderingHeight > 0 && renderingWidth > 0 && metaData[0] > 0 && metaData[1] > 0) {
            scaleFactor = Math.max(renderingWidth / (float) metaData[0], renderingHeight / (float) metaData[1]);
            if (scaleFactor <= 0 || scaleFactor > 0.7) {
                scaleFactor = 1;
            }
        } else {
            scaleFactor = 1f;
        }
    }

    private final Runnable mStartTask = () -> {
        if (!secondParentViews.isEmpty()) {
            for (int a = 0, N = secondParentViews.size(); a < N; a++) {
                secondParentViews.get(a).invalidate();
            }
        }
        if ((secondParentViews.isEmpty() || invalidateParentViewWithSecond) && parentView != null) {
            parentView.invalidate();
        }
    };

    public AnimatedFileDrawable(File file, boolean createDecoder, long streamSize, TLRPC.Document document, ImageLocation location, Object parentObject, long seekTo, int account, boolean preview, BitmapsCache.CacheOptions cacheOptions) {
        this(file, createDecoder, streamSize, document, location, parentObject, seekTo, account, preview, 0, 0, cacheOptions);
    }

    public AnimatedFileDrawable(File file, boolean createDecoder, long streamSize, TLRPC.Document document, ImageLocation location, Object parentObject, long seekTo, int account, boolean preview, int w, int h, BitmapsCache.CacheOptions cacheOptions) {
        path = file;
        streamFileSize = streamSize;
        currentAccount = account;
        renderingHeight = h;
        renderingWidth = w;
        this.precache = cacheOptions != null && renderingWidth > 0 && renderingHeight > 0;
        this.document = document;
        getPaint().setFlags(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (streamSize != 0 && (document != null || location != null)) {
            stream = new AnimatedFileDrawableStream(document, location, parentObject, account, preview);
        }
        if (createDecoder && !this.precache) {
            nativePtr = createDecoder(file.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, preview);
            if (nativePtr != 0 && (metaData[0] > 3840 || metaData[1] > 3840)) {
                destroyDecoder(nativePtr);
                nativePtr = 0;
            }
            updateScaleFactor();
            decoderCreated = true;
        }
        if (this.precache) {
            nativePtr = createDecoder(file.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, preview);
            if (nativePtr != 0 && (metaData[0] > 3840 || metaData[1] > 3840)) {
                destroyDecoder(nativePtr);
                nativePtr = 0;
            } else {
                bitmapsCache = new BitmapsCache(file, this, cacheOptions, renderingWidth, renderingHeight, !limitFps);
            }
        }
        if (seekTo != 0) {
            seekTo(seekTo, false);
        }
    }

    public void setIsWebmSticker(boolean b) {
        isWebmSticker = b;
        if (isWebmSticker) {
            useSharedQueue = true;
        }
    }

    public Bitmap getFrameAtTime(long ms) {
        return getFrameAtTime(ms, false);
    }

    public Bitmap getFrameAtTime(long ms, boolean precise) {
        if (!decoderCreated || nativePtr == 0) {
            return null;
        }
        if (stream != null) {
            stream.cancel(false);
            stream.reset();
        }
        if (!precise) {
            seekToMs(nativePtr, ms, precise);
        }
        if (backgroundBitmap == null) {
            backgroundBitmap = Bitmap.createBitmap((int) (metaData[0] * scaleFactor), (int) (metaData[1] * scaleFactor), Bitmap.Config.ARGB_8888);
        }
        int result;
        if (precise) {
            result = getFrameAtTime(nativePtr, ms, backgroundBitmap, metaData, backgroundBitmap.getRowBytes());
        } else {
            result = getVideoFrame(nativePtr, backgroundBitmap, metaData, backgroundBitmap.getRowBytes(), true, 0, 0);
        }
        return result != 0 ? backgroundBitmap : null;
    }

    public void setParentView(View view) {
        if (parentView != null) {
            return;
        }
        parentView = view;
    }

    public void addParent(ImageReceiver imageReceiver) {
        if (imageReceiver != null && !parents.contains(imageReceiver)) {
            parents.add(imageReceiver);
            if (isRunning) {
                scheduleNextGetFrame();
            }
        }
        checkCacheCancel();
    }

    public void removeParent(ImageReceiver imageReceiver) {
        parents.remove(imageReceiver);
        if (parents.size() == 0) {
            repeatCount = 0;
        }
        checkCacheCancel();
    }

    private Runnable cancelCache;
    public void checkCacheCancel() {
        if (bitmapsCache == null) {
            return;
        }
        boolean mustCancel = parents.isEmpty();
        if (mustCancel && cancelCache == null) {
            AndroidUtilities.runOnUIThread(cancelCache = () -> {
                if (bitmapsCache != null) {
                    bitmapsCache.cancelCreate();
                }
            }, 600);
        } else if (!mustCancel && cancelCache != null) {
            AndroidUtilities.cancelRunOnUIThread(cancelCache);
            cancelCache = null;
        }
    }

    public void setInvalidateParentViewWithSecond(boolean value) {
        invalidateParentViewWithSecond = value;
    }

    public void addSecondParentView(View view) {
        if (view == null || secondParentViews.contains(view)) {
            return;
        }
        secondParentViews.add(view);
    }

    public void removeSecondParentView(View view) {
        secondParentViews.remove(view);
        if (secondParentViews.isEmpty()) {
            if (recycleWithSecond) {
                recycle();
            } else {
                if (roundRadiusBackup != null) {
                    setRoundRadius(roundRadiusBackup);
                }
            }
        }
    }

    public void setAllowDecodeSingleFrame(boolean value) {
        decodeSingleFrame = value;
        if (decodeSingleFrame) {
            scheduleNextGetFrame();
        }
    }

    public void seekTo(long ms, boolean removeLoading) {
        seekTo(ms, removeLoading, false);
    }

    public void seekTo(long ms, boolean removeLoading, boolean force) {
        synchronized (sync) {
            pendingSeekTo = ms;
            pendingSeekToUI = ms;
            if (nativePtr != 0) {
                prepareToSeek(nativePtr);
            }
            if (decoderCreated && stream != null) {
                stream.cancel(removeLoading);
                pendingRemoveLoading = removeLoading;
                pendingRemoveLoadingFramesReset = pendingRemoveLoading ? 0 : 10;
            }
            if (force && decodeSingleFrame) {
                singleFrameDecoded = false;
                if (loadFrameTask == null) {
                    scheduleNextGetFrame();
                } else {
                    forceDecodeAfterNextFrame = true;
                }
            }
        }
    }

    public void recycle() {
        if (!secondParentViews.isEmpty()) {
            recycleWithSecond = true;
            return;
        }
        isRunning = false;
        isRecycled = true;
        if (cacheGenRunnable != null) {
            RLottieDrawable.lottieCacheGenerateQueue.cancelRunnable(cacheGenRunnable);
        }
        if (loadFrameTask == null) {
            if (nativePtr != 0) {
                destroyDecoder(nativePtr);
                nativePtr = 0;
            }

            ArrayList<Bitmap> bitmapToRecycle = new ArrayList<>();
            bitmapToRecycle.add(renderingBitmap);
            bitmapToRecycle.add(nextRenderingBitmap);

            if (renderingBitmap != null) {
                renderingBitmap = null;
            }
            if (nextRenderingBitmap != null) {
                nextRenderingBitmap = null;
            }
            if (decodeQueue != null) {
                decodeQueue.recycle();
                decodeQueue = null;
            }
            getPaint().setShader(null);
            AndroidUtilities.recycleBitmaps(bitmapToRecycle);
        } else {
            destroyWhenDone = true;
        }
        if (stream != null) {
            stream.cancel(true);
        }
        invalidateInternal();
    }

    public void resetStream(boolean stop) {
        if (stream != null) {
            stream.cancel(true);
        }
        if (nativePtr != 0) {
            if (stop) {
                stopDecoder(nativePtr);
            } else {
                prepareToSeek(nativePtr);
            }
        }
    }

    public void setUseSharedQueue(boolean value) {
        if (isWebmSticker) {
            return;
        }
        useSharedQueue = value;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void start() {
        if (isRunning || parents.size() == 0 && !ignoreNoParent) {
            return;
        }
        isRunning = true;
        scheduleNextGetFrame();
        AndroidUtilities.runOnUIThread(mStartTask);
    }

    public float getCurrentProgress() {
        if (metaData[4] == 0) {
            return 0;
        }
        if (pendingSeekToUI >= 0) {
            return pendingSeekToUI / (float) metaData[4];
        }
        return metaData[3] / (float) metaData[4];
    }

    public int getCurrentProgressMs() {
        if (pendingSeekToUI >= 0) {
            return (int) pendingSeekToUI;
        }
        return nextRenderingBitmapTime != 0 ? nextRenderingBitmapTime : renderingBitmapTime;
    }

    public int getDurationMs() {
        return metaData[4];
    }

    private void scheduleNextGetFrame() {
        if (loadFrameTask != null || !canLoadFrames() || destroyWhenDone || !isRunning && (!decodeSingleFrame || decodeSingleFrame && singleFrameDecoded) || parents.size() == 0 && !ignoreNoParent || generatingCache) {
            return;
        }
        long ms = 0;
        if (lastFrameDecodeTime != 0) {
            ms = Math.min(invalidateAfter, Math.max(0, invalidateAfter - (System.currentTimeMillis() - lastFrameDecodeTime)));
        }
        if (useSharedQueue) {
            executor.schedule(loadFrameTask = loadFrameRunnable, ms, TimeUnit.MILLISECONDS);
        } else {
            if (decodeQueue == null) {
                decodeQueue = new DispatchQueue("decodeQueue" + this);
            }
            decodeQueue.postRunnable(loadFrameTask = loadFrameRunnable, ms);
        }
    }

    public boolean isLoadingStream() {
        return stream != null && stream.isWaitingForLoad();
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getIntrinsicHeight() {
        int height = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[0] : metaData[1]) : 0;
        if (height == 0) {
            return AndroidUtilities.dp(100);
        } else {
            height *= scaleFactor;
        }
        return height;
    }

    @Override
    public int getIntrinsicWidth() {
        int width = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[1] : metaData[0]) : 0;
        if (width == 0) {
            return AndroidUtilities.dp(100);
        } else {
            width *= scaleFactor;
        }
        return width;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        applyTransformation = true;
    }

    @Override
    public void draw(Canvas canvas) {
        drawInternal(canvas, false, System.currentTimeMillis());
    }

    public void drawInBackground(Canvas canvas, float x, float y, float w, float h, int alpha, ColorFilter colorFilter) {
        if (dstRectBackground == null) {
            dstRectBackground = new RectF();
            backgroundPaint = new Paint();
            backgroundPaint.setFilterBitmap(true);
        }
        backgroundPaint.setAlpha(alpha);
        backgroundPaint.setColorFilter(colorFilter);
        dstRectBackground.set(x, y, x + w, y + h);
        drawInternal(canvas, true, 0);
    }

    public void drawInternal(Canvas canvas, boolean drawInBackground, long currentTime) {
        if (!canLoadFrames() || destroyWhenDone) {
            return;
        }

        if (currentTime == 0) {
            currentTime = System.currentTimeMillis();
        }

        RectF rect = drawInBackground ? dstRectBackground : dstRect;
        Paint paint = drawInBackground ? backgroundPaint : getPaint();

        if (!drawInBackground) {
            updateCurrentFrame(currentTime, false);
        }

        if (renderingBitmap != null) {
            float scaleX = this.scaleX;
            float scaleY = this.scaleY;
            if (drawInBackground) {
                int bitmapW = renderingBitmap.getWidth();
                int bitmapH = renderingBitmap.getHeight();
                if (metaData[2] == 90 || metaData[2] == 270) {
                    int temp = bitmapW;
                    bitmapW = bitmapH;
                    bitmapH = temp;
                }
                scaleX = rect.width() / bitmapW;
                scaleY = rect.height() / bitmapH;
            } else if (applyTransformation) {
                int bitmapW = renderingBitmap.getWidth();
                int bitmapH = renderingBitmap.getHeight();
                if (metaData[2] == 90 || metaData[2] == 270) {
                    int temp = bitmapW;
                    bitmapW = bitmapH;
                    bitmapH = temp;
                }
                rect.set(getBounds());
                this.scaleX = scaleX = rect.width() / bitmapW;
                this.scaleY = scaleY = rect.height() / bitmapH;
                applyTransformation = false;
            }
            if (hasRoundRadius()) {
                if (renderingShader == null) {
                    renderingShader = new BitmapShader(renderingBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
                paint.setShader(renderingShader);
                shaderMatrix.reset();
                shaderMatrix.setTranslate(rect.left, rect.top);
                if (metaData[2] == 90) {
                    shaderMatrix.preRotate(90);
                    shaderMatrix.preTranslate(0, -rect.width());
                } else if (metaData[2] == 180) {
                    shaderMatrix.preRotate(180);
                    shaderMatrix.preTranslate(-rect.width(), -rect.height());
                } else if (metaData[2] == 270) {
                    shaderMatrix.preRotate(270);
                    shaderMatrix.preTranslate(-rect.height(), 0);
                }
                shaderMatrix.preScale(scaleX, scaleY);

                renderingShader.setLocalMatrix(shaderMatrix);
                if (invalidatePath) {
                    invalidatePath = false;
                    for (int a = 0; a < roundRadius.length; a++) {
                        radii[a * 2] = roundRadius[a];
                        radii[a * 2 + 1] = roundRadius[a];
                    }
                    roundPath.reset();
                    roundPath.addRoundRect(drawInBackground ? rect : actualDrawRect, radii, Path.Direction.CW);
                    roundPath.close();
                }
                canvas.drawPath(roundPath, paint);
            } else {
                canvas.translate(rect.left, rect.top);
                if (metaData[2] == 90) {
                    canvas.rotate(90);
                    canvas.translate(0, -rect.width());
                } else if (metaData[2] == 180) {
                    canvas.rotate(180);
                    canvas.translate(-rect.width(), -rect.height());
                } else if (metaData[2] == 270) {
                    canvas.rotate(270);
                    canvas.translate(-rect.height(), 0);
                }
                canvas.scale(scaleX, scaleY);
                canvas.drawBitmap(renderingBitmap, 0, 0, paint);
            }
        }
    }

    public long getLastFrameTimestamp() {
        return lastTimeStamp;
    }

    @Override
    public int getMinimumHeight() {
        int height = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[0] : metaData[1]) : 0;
        if (height == 0) {
            return AndroidUtilities.dp(100);
        }
        return height;
    }

    @Override
    public int getMinimumWidth() {
        int width = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[1] : metaData[0]) : 0;
        if (width == 0) {
            return AndroidUtilities.dp(100);
        }
        return width;
    }

    public Bitmap getRenderingBitmap() {
        return renderingBitmap;
    }

    public Bitmap getNextRenderingBitmap() {
        return nextRenderingBitmap;
    }

    public Bitmap getBackgroundBitmap() {
        return backgroundBitmap;
    }

    public Bitmap getAnimatedBitmap() {
        if (renderingBitmap != null) {
            return renderingBitmap;
        } else if (nextRenderingBitmap != null) {
            return nextRenderingBitmap;
        }
        return null;
    }

    public void setActualDrawRect(float x, float y, float width, float height) {
        float bottom = y + height;
        float right = x + width;
        if (actualDrawRect.left != x || actualDrawRect.top != y || actualDrawRect.right != right || actualDrawRect.bottom != bottom) {
            actualDrawRect.set(x, y, right, bottom);
            invalidatePath = true;
        }
    }

    public void setRoundRadius(int[] value) {
        if (!secondParentViews.isEmpty()) {
            if (roundRadiusBackup == null) {
                roundRadiusBackup = new int[4];
            }
            System.arraycopy(roundRadius, 0, roundRadiusBackup, 0, roundRadiusBackup.length);
        }
        for (int i = 0; i < 4; i++) {
            if (!invalidatePath && value[i] != roundRadius[i]) {
                invalidatePath = true;
            }
            roundRadius[i] = value[i];
        }
    }

    private boolean hasRoundRadius() {
        for (int a = 0; a < roundRadius.length; a++) {
            if (roundRadius[a] != 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBitmap() {
        return canLoadFrames() && (renderingBitmap != null || nextRenderingBitmap != null);
    }

    public int getOrientation() {
        return metaData[2];
    }

    public AnimatedFileDrawable makeCopy() {
        AnimatedFileDrawable drawable;
        if (stream != null) {
            drawable = new AnimatedFileDrawable(path, false, streamFileSize, stream.getDocument(), stream.getLocation(), stream.getParentObject(), pendingSeekToUI, currentAccount, stream != null && stream.isPreview(), null);
        } else {
            drawable = new AnimatedFileDrawable(path, false, streamFileSize, document, null, null, pendingSeekToUI, currentAccount, stream != null && stream.isPreview(), null);
        }
        drawable.metaData[0] = metaData[0];
        drawable.metaData[1] = metaData[1];
        return drawable;
    }

    public static void getVideoInfo(String src, int[] params) {
        getVideoInfo(Build.VERSION.SDK_INT, src, params);
    }

    public void setStartEndTime(long startTime, long endTime) {
        this.startTime = startTime / 1000f;
        this.endTime = endTime / 1000f;
        if (getCurrentProgressMs() < startTime) {
            seekTo(startTime, true);
        }
    }

    public long getStartTime() {
        return (long) (startTime * 1000);
    }

    public boolean isRecycled() {
        return isRecycled;
    }

    public Bitmap getNextFrame() {
        if (nativePtr == 0) {
            return backgroundBitmap;
        }
        if (backgroundBitmap == null) {
            backgroundBitmap = Bitmap.createBitmap((int) (metaData[0] * scaleFactor), (int) (metaData[1] * scaleFactor), Bitmap.Config.ARGB_8888);
        }
        getVideoFrame(nativePtr, backgroundBitmap, metaData, backgroundBitmap.getRowBytes(), false, startTime, endTime);
        return backgroundBitmap;
    }

    public void setLimitFps(boolean limitFps) {
        this.limitFps = limitFps;
    }

    public ArrayList<ImageReceiver> getParents() {
        return parents;
    }

    public File getFilePath() {
        return path;
    }

    long cacheGenerateTimestamp;
    Bitmap generatingCacheBitmap;
    long cacheGenerateNativePtr;
    int tryCount;
    int lastMetadata;

    @Override
    public void prepareForGenerateCache() {
        cacheGenerateNativePtr = createDecoder(path.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, false);
    }

    @Override
    public void releaseForGenerateCache() {
        if (cacheGenerateNativePtr != 0) {
            destroyDecoder(cacheGenerateNativePtr);
        }
    }

    @Override
    public int getNextFrame(Bitmap bitmap) {
        if (cacheGenerateNativePtr == 0) {
            return -1;
        }
        Canvas canvas = new Canvas(bitmap);
        if (generatingCacheBitmap == null) {
            generatingCacheBitmap = Bitmap.createBitmap(metaData[0], metaData[1], Bitmap.Config.ARGB_8888);
        }
        getVideoFrame(cacheGenerateNativePtr, generatingCacheBitmap, metaData, generatingCacheBitmap.getRowBytes(), false, startTime, endTime);
        if (cacheGenerateTimestamp != 0 && (metaData[3] == 0 || cacheGenerateTimestamp > metaData[3])) {
            return 0;
        }
        if (lastMetadata == metaData[3]) {
            tryCount++;
            if (tryCount > 5) {
                return 0;
            }
        }
        lastMetadata = metaData[3];
        bitmap.eraseColor(Color.TRANSPARENT);
        canvas.save();
        float s = (float) renderingWidth / generatingCacheBitmap.getWidth();
        canvas.scale(s, s);
        canvas.drawBitmap(generatingCacheBitmap, 0, 0, null);
        canvas.restore();
        cacheGenerateTimestamp = metaData[3];
        return 1;
    }

    @Override
    public Bitmap getFirstFrame(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        if (generatingCacheBitmap == null) {
            generatingCacheBitmap = Bitmap.createBitmap(metaData[0], metaData[1], Bitmap.Config.ARGB_8888);
        }

        long nativePtr = createDecoder(path.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, false);
        if (nativePtr == 0) {
            return bitmap;
        }
        getVideoFrame(nativePtr, generatingCacheBitmap, metaData, generatingCacheBitmap.getRowBytes(), false, startTime, endTime);
        destroyDecoder(nativePtr);
        bitmap.eraseColor(Color.TRANSPARENT);
        canvas.save();
        float s = (float) renderingWidth / generatingCacheBitmap.getWidth();
        canvas.scale(s, s);
        canvas.drawBitmap(generatingCacheBitmap, 0, 0, null);
        canvas.restore();

        return bitmap;
    }

    public boolean canLoadFrames() {
        if (precache) {
            return bitmapsCache != null;
        } else {
            return nativePtr != 0 || !decoderCreated;
        }
    }

    public void checkCacheExist() {
        if (precache && bitmapsCache != null) {
            bitmapsCache.cacheExist();
        }
    }

    public void updateCurrentFrame(long now, boolean b) {
        if (isRunning) {
            if (renderingBitmap == null && nextRenderingBitmap == null) {
                scheduleNextGetFrame();
            } else if (nextRenderingBitmap != null && (renderingBitmap == null || (Math.abs(now - lastFrameTime) >= invalidateAfter && !skipFrameUpdate))) {
                if (precache) {
                    backgroundBitmap = renderingBitmap;
                }
                renderingBitmap = nextRenderingBitmap;
                renderingBitmapTime = nextRenderingBitmapTime;
                renderingShader = nextRenderingShader;
                nextRenderingBitmap = null;
                nextRenderingBitmapTime = 0;
                nextRenderingShader = null;
                lastFrameTime = now;
            }
        } else if (!isRunning && decodeSingleFrame && Math.abs(now - lastFrameTime) >= invalidateAfter && nextRenderingBitmap != null) {
            if (precache) {
                backgroundBitmap = renderingBitmap;
            }
            renderingBitmap = nextRenderingBitmap;
            renderingBitmapTime = nextRenderingBitmapTime;
            renderingShader = nextRenderingShader;
            nextRenderingBitmap = null;
            nextRenderingBitmapTime = 0;
            nextRenderingShader = null;
            lastFrameTime = now;
        }
    }
}