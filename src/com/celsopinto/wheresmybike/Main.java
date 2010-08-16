package com.celsopinto.wheresmybike;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class Main 
	extends Activity
	implements LocationListener
{

	private static final boolean DEBUG = true;
	
	private static enum AppState 
	{
		STARTABLE,
		STOPPABLE
	}

	private static final String LOG_TAG ="WheresMyBike";
	
	private AppState state = AppState.STARTABLE;
	
	private String remoteNr = null;
	
	private void startAlarm()
	{
		if (state == AppState.STOPPABLE) return;
		
        Button b = (Button)findViewById(R.id.startButton);
        EditText et = (EditText)findViewById(R.id.myPhoneNr);
		b.setText("Stop");
		state = AppState.STOPPABLE;
		remoteNr = et.getText().toString();
        registerIntent(this.getApplicationContext());
        startLocationPolling();
		sendText("Alarm started");
	}
	
	private void stopAlarm()
	{
		if (state == AppState.STARTABLE) return;
		
        Button b = (Button)findViewById(R.id.startButton);
		cancelLocationPolling();
		unregisterIntent(this.getApplicationContext());
		b.setText("Start");
		state = AppState.STARTABLE;
		sendText("Alarm stopped");
	}
	
	private void toggleState()
	{
		switch(state)
		{
		case STARTABLE:
			startAlarm();
			break;
		case STOPPABLE:
			stopAlarm();
			break;
		default:
			break;
		}
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TextView tv = (TextView)findViewById(R.id.alarmPhoneNr);
        Button button = (Button)findViewById(R.id.startButton);
        tv.setText(getThisPhoneNr());
        button.setOnClickListener(new View.OnClickListener() 
	        {
				@Override
				public void onClick(View v) {
					toggleState();
				}
			});
    }
    
    @Override
    protected void onDestroy() 
    {
    	stopAlarm();
    	super.onDestroy();
    }
    
	private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
	private static final String PHONE_CALL = "android.intent.action.PHONE_STATE";
	private static final String BATTERY_LOW = Intent.ACTION_BATTERY_LOW;
	
	private  BroadcastReceiver listener = new BroadcastReceiver() 
		{
	
			@Override
			public void onReceive(Context ctx, Intent intent) 
			{
				if (intent.getAction().equals(BATTERY_LOW))
				{
					sendText("Help, I'm running out of battery! :-(");
				}
				else if (intent.getAction().equals(SMS_RECEIVED))
				{
					Bundle b = intent.getExtras();
					Object[] pdusObj = (Object[])b.get("pdus");
					SmsMessage[] messages = new SmsMessage[pdusObj.length];
					for(int i = 0; i< messages.length; i++)
						messages[i] = SmsMessage.createFromPdu((byte[])pdusObj[i]);

					boolean smsHandled = false;
					for(SmsMessage sms : messages)
					{
						String nr = sms.getDisplayOriginatingAddress(),
							text = sms.getDisplayMessageBody();
						
						if (nr.endsWith(remoteNr))
						{
							handleIncomingText(text);
							smsHandled = true;
						}
					}
					if (smsHandled)
						abortBroadcast();
				}
				else if (intent.getAction().equals(PHONE_CALL))
				{
					TelephonyManager manager = (TelephonyManager)ctx.getSystemService(Context.TELEPHONY_SERVICE);
					manager.listen(new PhoneStateListener(){
						@Override
						public void onCallStateChanged(int state, String incomingNumber) {
							switch(state)
							{
							case TelephonyManager.CALL_STATE_RINGING:
							{
								Log.i(LOG_TAG,"call ringing from: " + incomingNumber);
								if (incomingNumber.endsWith(remoteNr))
									toggleState();
								break;
							}
							default:
								break;
							}
						}
					}, PhoneStateListener.LISTEN_CALL_STATE);
				}
			}
	
		};
	
	private void registerIntent(Context ctx)
    {
    	IntentFilter filter = new IntentFilter(SMS_RECEIVED);
    	filter.addAction(PHONE_CALL);
    	filter.addAction(BATTERY_LOW);
    	filter.setPriority(123123123);
    	ctx.registerReceiver(listener, filter);
    }
    
    private void unregisterIntent(Context ctx)
    {
    	ctx.unregisterReceiver(listener);
    }
    
    
	private String getThisPhoneNr()
	{
    	return ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
	}

	@Override
	public void onLocationChanged(Location l) 
	{
		detectMotion(l);
	}
	
	@Override
	public void onProviderDisabled(String provider) 
	{
	}
	
	@Override
	public void onProviderEnabled(String provider) 
	{
	}
	
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) 
	{
	}
	
	/* the location change sensitivity in meters */
	private static float SENSITIVITY = 1;
	
	private void startLocationPolling()
	{
		cancelLocationPolling();
		LocationManager mgr = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
		mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, SENSITIVITY, this);
	}
	
	private void cancelLocationPolling()
	{
		LocationManager mgr = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
		mgr.removeUpdates(this);
	}
	
	private Location lastKnownLocation = null;
	private void detectMotion(Location location)
	{
		if (lastKnownLocation != null)
		{
			final double EARTH_RADIUS = 6371007.2;
			double latDiff = Math.toRadians(location.getLatitude()-lastKnownLocation.getLatitude());
			double longDiff = Math.toRadians(location.getLongitude()-lastKnownLocation.getLongitude());
			double a = 	Math.sin(latDiff/2) * Math.sin(latDiff/2) +
						Math.cos(Math.toRadians(lastKnownLocation.getLatitude())) * Math.cos(Math.toRadians(location.getLatitude())) *
						Math.sin(longDiff/2) * Math.sin(longDiff/2);
			double c = 2 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
			double d = EARTH_RADIUS * c;
			if (d > SENSITIVITY)
				sendAlert(location);

		}
		
		lastKnownLocation = location;
		
	}

	private final String LOCATION_MESSAGE = "Currently at latitude: %1$.5f, longitude: %2$.5f. Map: http://maps.google.com/maps?q=%1$.5f,+%2$.5f";
	private void sendAlert(Location location) 
	{
		sendText(String.format(LOCATION_MESSAGE,location.getLatitude(),location.getLongitude()));
	}
	
	private Handler uiHandler = new Handler() 
		{
			public void handleMessage(Message msg)
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(Main.this);
				builder.setMessage(msg.obj.toString())
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface d, int which)
						{
							d.dismiss();
						}
					});
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		};
	private void sendText(String message)
	{
		if (DEBUG)
		{
			Log.i(LOG_TAG,message);
			Message m = new Message();
			m.obj = message;
			uiHandler.sendMessage(m);
		}
		else
		{
			SmsManager.getDefault().sendTextMessage(getThisPhoneNr(), 
					null, 
					message, 
					null, 
					null);
		}
	}
	
	private static final String 
		COMMAND_START = "start",
		COMMAND_STOP = "stop",
		COMMAND_LOCATION = "loc";
	
	private void handleIncomingText(String text) 
	{
		text = text.toLowerCase();
		if (COMMAND_START.equals(text))
			startAlarm();
		else if (COMMAND_STOP.equals(text))
			stopAlarm();
		else if (COMMAND_LOCATION.equals(text))
		{
			if (lastKnownLocation != null)
				sendAlert(lastKnownLocation);
			else
				sendText("I'm still where you left me.");
		}
	
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		startActivity(new Intent(this,Help.class));
		return true;
	}
}