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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
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

    static final String PATH = "/weather_info";
    static final String WEATHER_ID = "WEATHER_ID";
    static final String MIN_TEMP = "MIN_TEMP";
    static final String MAX_TEMP = "MAX_TEMP";
    static final String DATA_CHECK = "Checking received data";
    private Bitmap Icon;
    private String maxTemp;
    private String minTemp;
    Date date;
    DateFormat dateFormat;

    private GoogleApiClient googleApiClient;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
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

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        Paint backgroundPaint;
        Paint textPaint;
        Paint hourPaint;
        Paint minPaint;
        Paint datePaint;
        Paint maxTempPaint;
        Paint minTempPaint;

        boolean mAmbient;
        Calendar calendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float XOffset;
        float timeYOffset;
        float dateYOffset;
        float lineSeparatorYOffset;
        float weatherIconYOffset;
        float weatherYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this).addApi(Wearable.API)
                    .addConnectionCallbacks(this).addOnConnectionFailedListener(this)
                    .build();
            googleApiClient.connect();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)

                    .build());
            Resources resources = MyWatchFace.this.getResources();

            timeYOffset = resources.getDimension(R.dimen.time_y_offset);
            dateYOffset = resources.getDimension(R.dimen.date_offset);
            lineSeparatorYOffset = resources.getDimension(R.dimen.separator_y_offset);
            weatherIconYOffset = resources.getDimension(R.dimen.weather_icon_y_offset);
            weatherYOffset = resources.getDimension(R.dimen.weather_y_offset);
            backgroundPaint = new Paint();
            backgroundPaint.setColor(resources.getColor(R.color.background));
            textPaint = new Paint();
            textPaint = createTextPaint(resources.getColor(R.color.primary_text));
            datePaint = createTextPaint(resources.getColor(R.color.primary_text));
            hourPaint = createTextPaint(resources.getColor(R.color.primary_text));
            minPaint = createTextPaint(resources.getColor(R.color.primary_text));
            maxTempPaint = createTextPaint(resources.getColor(R.color.primary_text));
            minTempPaint = createTextPaint(resources.getColor(R.color.primary_text));
            Icon = BitmapFactory.decodeResource(getResources(),R.drawable.art_clear);
            calendar = Calendar.getInstance();
            date = new Date();
            dateFormat = new SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault());
            dateFormat.setCalendar(calendar);
            minTemp = getString(R.string.default_temp);
            maxTemp = getString(R.string.default_temp);

        }
        //text
        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            return createTextPaint(textColor,NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                if (googleApiClient != null && googleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(googleApiClient, this);
                    googleApiClient.disconnect();
                }
            }
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
            XOffset = resources.getDimension(R.dimen.x_offset);
            float textSize = resources.getDimension(R.dimen.text_size);

            float dateTextSize = resources.getDimension(R.dimen.date_text_size);
            float tempTextSize = resources.getDimension(R.dimen.temp_text_size);

            hourPaint.setTextSize(textSize);
            minPaint.setTextSize(textSize);
            datePaint.setTextSize(dateTextSize);
            textPaint.setTextSize(textSize);
            maxTempPaint.setTextSize(tempTextSize);
            minTempPaint.setTextSize(tempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    textPaint.setAntiAlias(!inAmbientMode);
                    datePaint.setAntiAlias(!inAmbientMode);
                    hourPaint.setAntiAlias(!inAmbientMode);
                    minPaint.setAntiAlias(!inAmbientMode);
                    maxTempPaint.setAntiAlias(!inAmbientMode);
                    minTempPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);
            date.setTime(now);

            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            String hourText = String.format("%02d:", hour);
            String minuteText = String.format("%02d", minute);

            float centerX = bounds.centerX();
            float hourSize = hourPaint.measureText(hourText);
            float minuteSize = minPaint.measureText(minuteText);
            float hourXOffset = centerX - (hourSize + minuteSize)/2;
            float minuteXOffset = centerX + (hourSize - minuteSize)/2;

            canvas.drawText(hourText, hourXOffset, timeYOffset, hourPaint);
            canvas.drawText(minuteText, minuteXOffset, timeYOffset, minPaint);


            String dateString = dateFormat.format(date);
            canvas.drawText(dateString, centerX - datePaint.measureText(dateString)/2, dateYOffset, datePaint);

            canvas.drawLine(bounds.centerX() - 25, lineSeparatorYOffset, bounds.centerX() + 25, lineSeparatorYOffset, datePaint);

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(Icon, 40, 40, true);
            String maxTempString = maxTemp;
            String minTempString = minTemp;
            Log.v("Logging here", minTemp + " and " + maxTemp);
            float maxTempMeasureText = maxTempPaint.measureText(maxTempString);
            float maxTempXPosition = centerX - maxTempPaint.measureText(maxTempString) / 2;
            float minTempXPosition = maxTempXPosition + maxTempMeasureText + 10;
            if (!isInAmbientMode()) {
                float iconXPosition = maxTempXPosition - (resizedBitmap.getWidth() + 10);
                canvas.drawBitmap(resizedBitmap, iconXPosition, weatherIconYOffset, new Paint());
            }

            canvas.drawText(maxTempString, maxTempXPosition, weatherYOffset, maxTempPaint);
            canvas.drawText(minTempString, minTempXPosition, weatherYOffset, minTempPaint);
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

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(googleApiClient, Engine.this);
            Log.v("Wearable connection","Connected");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.v("Wearable connection","Suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.v("Wearable connection","Failed");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent: dataEventBuffer) {
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if(dataItem.getUri().getPath().equals(PATH)) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        int weatherId = dataMap.getInt(WEATHER_ID);
                        Icon = BitmapFactory.decodeResource(getResources(), IconHelper.getArtResourceForWeatherCondition(weatherId));
                        maxTemp = dataMap.getString(MAX_TEMP);
                        minTemp = dataMap.getString(MIN_TEMP);
                        Log.v("Received ", maxTemp + " " + minTemp + " " + weatherId);
                        invalidate();
                    }
                }
            }
        }
    }
}