package com.leon.assistivetouch.main;

import java.nio.channels.GatheringByteChannel;
import java.util.Random;

import com.leon.assistivetouch.main.reader.LogcatReader;
import com.leon.assistivetouch.main.reader.LogcatReaderLoader;
import com.leon.assistivetouch.main.ui.TouchView;
import com.leon.assistivetouch.main.util.L;
import com.leon.assistivetouch.main.util.Settings;
import com.leon.assistivetouch.main.util.Util;
import com.leon.assistivetouch.main.util.UtilLogger;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;

/** 
 * 类名      AssistiveTouchService.java
 * 说明   虚拟按键的服务
 * 创建日期 2012-8-21
 * 作者  LiWenLong
 * Email lendylongli@gmail.com
 * 更新时间  $Date$
 * 最后更新者 $Author$
*/
public class AssistiveTouchService extends Service {

	private static final String TAG = "AssistiveTouchService";
	private static UtilLogger log = new UtilLogger(AssistiveTouchService.class);

	public static final String ASSISTIVE_TOUCH_START_ACTION = "com.leon.assistivetouch.assistive_start_action";
	public static final String ASSISTIVE_TOUCH_STOP_ACTION = "com.leon.assistivetouch.assistive_stop_action";
	public static final String ASSISTIVE_TOUCH_NOTIFY_ACTION = "com.leon.assistivetouch.assistive_notify_action";
	
	private static final int NOTIFATION_ID = new Random(System.currentTimeMillis()).nextInt() + 1000; 
	
	private Settings mSetting;
	TouchView mTouchView;
	
	@Override
	public void onCreate() {
		super.onCreate();
		L.d(TAG, "service on create");
		mSetting = Settings.getInstance(this);
		mTouchView = TouchView.getInstance(this);
		L.d(TAG, "id:" + NOTIFATION_ID);
		startForeground(NOTIFATION_ID, new Notification());


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

				String line;
				while ((line = reader.readLine()) != null) {
					if (paused) {
						synchronized (lock) {
							if (paused) {
								lock.wait();
							}
						}
					}
//					if(line.contains("hovering start("))
//						log.e(line+"start");
//						mTouchView.showView();
//					if(line.contains("hovering stop(")){
//						log.e(line+"stop");
//						mTouchView.removeView();
//					}
//					if(line.contains("hovering start(") && !isShow)
//						mTouchView.showView();
//
//					if(line.contains("hovering stop(") && isShow){
//						mTouchView.removeView();
//					}
//					if(isShow){
//							mTouchView.removeView();
//							isShow = false;
//						}else {
//							mTouchView.showView();
//							isShow = true;
//						}
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
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		L.d(TAG, "onstart command");
		String action = intent.getAction();
		if (Util.isStringNull(action)) {
			return START_STICKY;
		}
		if (action.equals(ASSISTIVE_TOUCH_START_ACTION)) {
			//if (isNeedInit()) {
				startTouchService();
			//}
		} else if (action.equals(ASSISTIVE_TOUCH_STOP_ACTION)) {
			stopTouchService();
		} else if (action.equals(ASSISTIVE_TOUCH_NOTIFY_ACTION)) {
			startTouchService();
		}
		return START_STICKY;
	}
	private boolean isShow = true;
	@Override
	public IBinder onBind(Intent intent) {
		L.d(TAG, "onBind");
//		LogReaderAsyncTask mLogReaderAsyncTask = new LogReaderAsyncTask();
//		mLogReaderAsyncTask.execute(null);

//		new Thread(new Runnable() {
//			@Override
//			public void run() {
//				while (true){
//					try {
//						Thread.sleep(500);
//						if(isShow){
//							mTouchView.removeView();
//							isShow = false;
//						}else {
//							mTouchView.showView();
//							isShow = true;
//						}
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
//				}
//
//			}
//		}).start();
		return mBinder;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		L.d(TAG, "onUnbind");
		if (!isServiceRunning()) {
			this.stopSelf();
		}
		return true;
	}
	
	@Override
	public void onDestroy() {
		L.d(TAG, "server destory");
		boolean enable = mSetting.isEnableAssistiveTouch();
		if (enable) {
			L.d(TAG, "程序被强制退出!");
			AlarmManager am = (AlarmManager)getApplicationContext()
					.getSystemService(ALARM_SERVICE);
			Intent i = new Intent(ASSISTIVE_TOUCH_START_ACTION);
			PendingIntent pi = PendingIntent.getService(getApplicationContext(), 0,
					i, Intent.FLAG_ACTIVITY_NEW_TASK);
			// 一秒后重启服务
			am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pi);
		}
		super.onDestroy();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	private final IAssistiveTouchService.Stub mBinder = new IAssistiveTouchService.Stub() {
		@Override
		public boolean isRunning() throws RemoteException {
			return isServiceRunning();
		}

		@Override
		public void start() throws RemoteException {
			startTouchService();
		}

		@Override
		public void stop() throws RemoteException {
			stopTouchService();
		}

		@Override
		public void refresh() throws RemoteException {
			mTouchView.reload();
		}
	};

	public void startTouchService () {
		L.d(TAG, "startTouchService");
		mSetting.setEnableAssistiveTouch(true);
		mTouchView.showView();
	}
	
	public void stopTouchService () {
		L.d(TAG, "stopTouchService");
		mSetting.setEnableAssistiveTouch(false);
		mTouchView.removeView();
	}

	public boolean isNeedInit () {
		boolean enable = mSetting.isEnableAssistiveTouch();
		if (enable && mTouchView.getShowingView() == 0) {
			return true;
		}
		return false;
	}
	
	public boolean isServiceRunning () {
		if (mTouchView.getShowingView() == 0 ) {
			return false;
		}
		return true;
	}
}
