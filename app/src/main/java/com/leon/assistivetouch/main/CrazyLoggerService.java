package com.leon.assistivetouch.main;

import android.app.IntentService;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.widget.Toast;


import com.leon.assistivetouch.main.data.LogLine;
import com.leon.assistivetouch.main.helper.PreferenceHelper;
import com.leon.assistivetouch.main.reader.LogcatReader;
import com.leon.assistivetouch.main.reader.LogcatReaderLoader;
import com.leon.assistivetouch.main.ui.TouchView;
import com.leon.assistivetouch.main.util.Settings;
import com.leon.assistivetouch.main.util.UtilLogger;

import java.util.Date;
import java.util.LinkedList;

/**
 * just writes a bunch of logs.  to be used during debugging and testing.
 * @author nolan
 *
 */
public class CrazyLoggerService extends IntentService {
	
	private static final long INTERVAL = 300;
	
	private static UtilLogger log = new UtilLogger(CrazyLoggerService.class);
	
	private boolean kill = false;
	private Settings mSetting;
	private LogcatReader reader2;
	TouchView mTouchView;
	public CrazyLoggerService() {
		super("CrazyLoggerService");

		LogReaderAsyncTask mLogReaderAsyncTask = new LogReaderAsyncTask();
		mLogReaderAsyncTask.execute(null);

		mSetting = Settings.getInstance(this);
		mTouchView = TouchView.getInstance(this);
	}
	private class LogReaderAsyncTask extends AsyncTask<Void,Void,Void> {


		private int counter = 0;
		private volatile boolean paused;
		private final Object lock = new Object();
		private boolean firstLineReceived;
		private boolean killed;
		private LogcatReader reader;

		private Runnable onFinished;

		@Override
		protected Void doInBackground(Void... params) {
			log.d("doInBackground()");

			try {
				// use "recordingMode" because we want to load all the existing lines at once
				// for a performance boost
				LogcatReaderLoader loader = LogcatReaderLoader.create(getApplicationContext(), true);
				reader = loader.loadReader();
				int maxLines = PreferenceHelper.getDisplayLimitPreference(getApplicationContext());

				String line;
				LinkedList<LogLine> initialLines = new LinkedList<LogLine>();
				while ((line = reader.readLine()) != null) {
					if (paused) {
						synchronized (lock) {
							if (paused) {
								lock.wait();
							}
						}
					}
					if(line.contains("hovering start(320)")){
						//Toast.makeText(getApplicationContext(),line,0).show();
						log.i("Log message start hovering");
						//mTouchView.showView();
						Intent i = new Intent();
						i.setAction(AssistiveTouchService.ASSISTIVE_TOUCH_START_ACTION);
						startService(i);
					}
					if(line.contains("stop hovering(320)")){
						//Toast.makeText(getApplicationContext(),line,0).show();
						log.i("Log message stop hovering");
						//mTouchView.removeView();
						Intent i = new Intent();
						i.setAction(AssistiveTouchService.ASSISTIVE_TOUCH_STOP_ACTION);
						startService(i);
						//sendBroadcast();
					}

				}
			} catch (InterruptedException e) {
				log.d(e, "expected error");
			} catch (Exception e) {
				log.d(e, "unexpected error");
			} finally {
				killReader();
				log.d("AsyncTask has died");
			}

			return null;
		}

		public void killReader() {
			if (!killed) {
				synchronized (lock) {
					if (!killed && reader != null) {
						reader.killQuietly();
						killed = true;
					}
				}
			}

		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			log.d("onPostExecute()");
			doWhenFinished();
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			log.d("onPreExecute()");


		}

		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);

			if (!firstLineReceived) {
				firstLineReceived = true;
			}

		}

		private void doWhenFinished() {
			if (paused) {
				unpause();
			}
			if (onFinished != null) {
				onFinished.run();
			}
		}

		public void pause() {
			synchronized (lock) {
				paused = true;
			}
		}

		public void unpause() {
			synchronized (lock) {
				paused = false;
				lock.notify();
			}
		}

		public boolean isPaused() {
			return paused;
		}

		public void setOnFinished(Runnable onFinished) {
			this.onFinished = onFinished;
		}


	}
	protected void onHandleIntent(Intent intent) {
	
		log.d("onHandleIntent()");
		
		while (!kill) {
		
			try {
				Thread.sleep(INTERVAL);
			} catch (InterruptedException e) {
				log.e(e, "error");
			}
			Date date = new Date();
			log.i("Log message " + date + " " + (date.getTime() % 1000));

		}

	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		kill = true;
	}
	
}
