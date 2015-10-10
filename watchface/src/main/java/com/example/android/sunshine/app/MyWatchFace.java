/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
                    GoogleApiClient.ConnectionCallbacks,
                    GoogleApiClient.OnConnectionFailedListener,
                    DataApi.DataListener,
                    NodeApi.NodeListener{

        protected static final int MSG_UPDATE_TIME = 0;
        protected long UPDATE_RATE_MS;
        protected static final long WEATHER_INFO_TIME_OUT = DateUtils.HOUR_IN_MILLIS * 6;


        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mWeatherPaint;


        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        protected GoogleApiClient mGoogleApiClient;
        protected int mBackgroundColor;
        protected int mBackgroundDefaultColor;
        protected int mRequireInterval;
        protected int mTemperature = 0;
        protected int mTemperaturemin = 0;
        protected int mTemperatureScale;
        protected long mWeatherInfoReceivedTime;
        protected long mWeatherInfoRequiredTime;
        protected String mWeatherCondition;
        Bitmap mBackgroundBitmap;
        Paint mTickAndCirclePaint;
        Paint mTickAndCirclePaint1;


        public Engine() {
            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d("e", "Connected: " + bundle);
            getConfig();

            Wearable.NodeApi.addListener(mGoogleApiClient, this);
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            requireWeatherInfo();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("e", "ConnectionSuspended: " + i);
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (int i = 0; i < dataEvents.getCount(); i++) {
                DataEvent event = dataEvents.get(i);
                DataMap dataMap = DataMap.fromByteArray(event.getDataItem().getData());
                Log.d("e", "onDataChanged: " + dataMap);

                fetchConfig(dataMap);
            }
        }

        @Override
        public void onPeerConnected(Node node) {
            Log.d("e", "PeerConnected: " + node);
            requireWeatherInfo();
        }

        @Override
        public void onPeerDisconnected(Node node) {
            Log.d("e", "PeerDisconnected: " + node);
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d("e", "ConnectionFailed: " + connectionResult);

        }


        Resources mResources;
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;

        int count = 2;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.TOP | Gravity.CENTER)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER )
                    .build());

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mWeatherPaint = new Paint();
            mWeatherPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(resources.getColor(R.color.digital_text));
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, R.color.digital_text);

            mTickAndCirclePaint1 = new Paint();
            mTickAndCirclePaint1.setColor(resources.getColor(R.color.red));
            mTickAndCirclePaint1.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint1.setAntiAlias(true);
            mTickAndCirclePaint1.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint1.setShadowLayer(SHADOW_RADIUS, 0, 0, R.color.red);

            mTime = new Time();

            mWeatherInfoRequiredTime = System.currentTimeMillis() - (DateUtils.SECOND_IN_MILLIS * 58);

            mResources = MyWatchFace.this.getResources();


            mGoogleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mWeatherPaint.setTextSize(resources.getDimension(R.dimen.digital_text_size_round_small));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            requireWeatherInfo();
            invalidate();
        }

        public String nodeId;

        private void retrieveDeviceNode(final String message, final String uri) {
            final GoogleApiClient client = mGoogleApiClient;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    client.blockingConnect(100, TimeUnit.MILLISECONDS);
                    NodeApi.GetConnectedNodesResult result =
                            Wearable.NodeApi.getConnectedNodes(client).await();
                    List<Node> nodes = result.getNodes();
                    if (nodes.size() > 0) {
                        nodeId = nodes.get(0).getId();
                    }
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, uri, message.getBytes())
                            .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                                @Override
                                public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                    Log.d("2,", "SendRequireMessage:" + sendMessageResult.getStatus());
                                }
                            });
                }
            }).start();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                        Wearable.DataApi.removeListener(mGoogleApiClient, this);
                        Wearable.NodeApi.removeListener(mGoogleApiClient, this);
                        mGoogleApiClient.disconnect();
                    }

                }
                mGoogleApiClient.connect();
                requireWeatherInfo();
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            float mCenterX = 320 / 2f;
            float mCenterY = 320 / 2f;


            mTickAndCirclePaint1.setColor(getResources().getColor(R.color.green));
            float tickRot2 = (float) (mTime.hour * Math.PI * 2 / 12);
            float innerTickRadius2 = mCenterX - 20;
            float outerTickRadius2 = mCenterX;
            float innerX2 = (float) Math.sin(tickRot2) * innerTickRadius2;
            float innerY2 = (float) -Math.cos(tickRot2) * innerTickRadius2;
            float outerX2 = (float) Math.sin(tickRot2) * outerTickRadius2;
            float outerY2 = (float) -Math.cos(tickRot2) * outerTickRadius2;
            canvas.drawLine(mCenterX + innerX2, mCenterY + innerY2,
                    mCenterX + outerX2, mCenterY + outerY2, mTickAndCirclePaint1);


            mTickAndCirclePaint1.setColor(getResources().getColor(R.color.blue));
            tickRot2 = (float) (mTime.minute * Math.PI * 2 / 60);
             innerTickRadius2 = mCenterX - 40;
             outerTickRadius2 = mCenterX;
             innerX2 = (float) Math.sin(tickRot2) * innerTickRadius2;
             innerY2 = (float) -Math.cos(tickRot2) * innerTickRadius2;
             outerX2 = (float) Math.sin(tickRot2) * outerTickRadius2;
             outerY2 = (float) -Math.cos(tickRot2) * outerTickRadius2;
            canvas.drawLine(mCenterX + innerX2, mCenterY + innerY2,
                    mCenterX + outerX2, mCenterY + outerY2, mTickAndCirclePaint1);

            if(!mAmbient) {
                mTickAndCirclePaint1.setColor(getResources().getColor(R.color.red));
                float tickRot1 = (float) ((mTime.second+1) * Math.PI * 2 / 60);
                float innerTickRadius1 = mCenterX - 15;
                float outerTickRadius1 = mCenterX;
                float innerX1 = (float) Math.sin(tickRot1) * innerTickRadius1;
                float innerY1 = (float) -Math.cos(tickRot1) * innerTickRadius1;
                float outerX1 = (float) Math.sin(tickRot1) * outerTickRadius1;
                float outerY1 = (float) -Math.cos(tickRot1) * outerTickRadius1;
                canvas.drawLine(mCenterX + innerX1, mCenterY + innerY1,
                        mCenterX + outerX1, mCenterY + outerY1, mTickAndCirclePaint1);

                for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                    float innerTickRadius;
                    if(tickIndex%5 == 0){
                        innerTickRadius = mCenterX - 9;
                    }else{
                        innerTickRadius = mCenterX - 2;
                    }
                    float tickRot = (float) (tickIndex * Math.PI * 2 / 60);

                    float outerTickRadius = mCenterX;
                    float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                    float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                    float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                    float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                    canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                            mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);


                }
            }

            final Rect textBounds = new Rect();

            if(!mAmbient) {
                if(mBackgroundBitmap!=null) {
                    canvas.drawBitmap(mBackgroundBitmap, mCenterX - (mBackgroundBitmap.getWidth()/2),mCenterY - 90, null);
                }

                String t = mTemperature+"\u00B0 / " + mTemperaturemin+"\u00B0";

                mWeatherPaint.getTextBounds(t, 0, t.length(), textBounds);
                canvas.drawText(t, mCenterX - textBounds.exactCenterX(), mCenterY - textBounds.exactCenterY()+60, mWeatherPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);


            mTextPaint.getTextBounds(text, 0, text.length(), textBounds);

            canvas.drawText(text, mCenterX - textBounds.exactCenterX(), mCenterY - textBounds.exactCenterY(), mTextPaint);

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        protected void fetchConfig(DataMap config) {
            if (config.containsKey(Consts.KEY_WEATHER_UPDATE_TIME)) {
                mWeatherInfoReceivedTime = config.getLong(Consts.KEY_WEATHER_UPDATE_TIME);
            }

            if (config.containsKey(Consts.KEY_WEATHER_CONDITION)) {
                String cond = config.getString(Consts.KEY_WEATHER_CONDITION);
                if (TextUtils.isEmpty(cond)) {
                    mWeatherCondition = null;
                } else {
                    mWeatherCondition = cond;
                }
            }

            if (config.containsKey(Consts.KEY_WEATHER_TEMPERATUREMAX)) {
                mTemperature = config.getInt(Consts.KEY_WEATHER_TEMPERATUREMAX);
            }

            if (config.containsKey(Consts.KEY_WEATHER_TEMPERATUREMIN)) {
                mTemperaturemin = config.getInt(Consts.KEY_WEATHER_TEMPERATUREMIN);
            }

            if (config.containsKey(Consts.KEY_WEATHER_WID)) {
                 //Bitmap getIconResourceForWeatherCondition(config.getInt(Consts.KEY_WEATHER_SUNSET))
                Drawable backgroundDrawable;
                if(config.getInt(Consts.KEY_WEATHER_WID)==0){
                    Resources resources = MyWatchFace.this.getResources();
                     backgroundDrawable = resources.getDrawable(R.drawable.ic_clear, null);
                }else{
                    Resources resources = MyWatchFace.this.getResources();
                     backgroundDrawable = resources.getDrawable(getIconResourceForWeatherCondition(config.getInt(Consts.KEY_WEATHER_WID)), null);
                }

                mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
            }

            invalidate();
        }

        protected void getConfig() {
            Wearable.NodeApi.getLocalNode(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {
                @Override
                public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                    Uri uri = new Uri.Builder()
                            .scheme("wear")
                            .path(Consts.PATH_WEATHER_INFO)
                            .authority(getLocalNodeResult.getNode().getId())
                            .build();

                    getConfig(uri);
                }
            });
        }

        protected void getConfig(Uri uri) {
            Wearable.DataApi.getDataItem(mGoogleApiClient, uri)
                    .setResultCallback(
                            new ResultCallback<DataApi.DataItemResult>() {
                                @Override
                                public void onResult(DataApi.DataItemResult dataItemResult) {
                                    Log.d("e", "Finish Config: " + dataItemResult.getStatus());
                                    if (dataItemResult.getStatus().isSuccess() && dataItemResult.getDataItem() != null) {
                                        fetchConfig(DataMapItem.fromDataItem(dataItemResult.getDataItem()).getDataMap());
                                    }
                                }
                            }
                    );
        }

        protected void requireWeatherInfo() {
            if (!mGoogleApiClient.isConnected())
                return;

            long timeMs = System.currentTimeMillis();

            // The weather info is still up to date.
            if ((timeMs - mWeatherInfoReceivedTime) <= mRequireInterval)
                return;

            // Try once in a min.
            if ((timeMs - mWeatherInfoRequiredTime) <= DateUtils.MINUTE_IN_MILLIS)
                return;

            mWeatherInfoRequiredTime = timeMs;

            retrieveDeviceNode("", Consts.PATH_WEATHER_REQUIRE);
        }

        public int getIconResourceForWeatherCondition(int weatherId) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.art_clear;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.ic_cloudy;
            }
            return -1;
        }

    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
