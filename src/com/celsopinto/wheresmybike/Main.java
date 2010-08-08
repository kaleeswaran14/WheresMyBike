package com.celsopinto.wheresmybike;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class Main 
	extends Activity
	implements LocationListener
{

	private static enum AppState 
	{
		STARTABLE,
		STOPPABLE
	}
	
	private AppState state = AppState.STARTABLE;
	
	private String remoteNr = null;
	
	private void toggleState(Button b)
	{
        final EditText et = (EditText)findViewById(R.id.myPhoneNr);
		switch(state)
		{
		case STARTABLE:
			b.setText("Stop");
			state = AppState.STOPPABLE;
			remoteNr = et.getText().toString();
	        registerIntent(this.getApplicationContext());
	        startLocationPolling();
			break;
		case STOPPABLE:
			cancelLocationPolling();
			unregisterIntent(this.getApplicationContext());
			b.setText("Start");
			state = AppState.STARTABLE;
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
				toggleState((Button)v);
			}
		});
    }
    
    @Override
    protected void onDestroy() 
    {
    	unregisterIntent(getApplicationContext());
    	super.onDestroy();
    }
    
	private static final String SMS = "android.provider.Telephony.SMS_RECEIVED";
	private static final String PHONE = "android.intent.action.PHONE_STATE";
	private  BroadcastReceiver listener = new BroadcastReceiver() 
	{

		@Override
		public void onReceive(Context ctx, Intent intent) 
		{
			if (intent.getAction().equals(SMS))
			{
				Bundle b = intent.getExtras();
				Object[] pdusObj = (Object[])b.get("pdus");
				SmsMessage[] messages = new SmsMessage[pdusObj.length];
				for(int i = 0; i< messages.length; i++)
					messages[i] = SmsMessage.createFromPdu((byte[])pdusObj[i]);
				
				for(SmsMessage sms : messages)
				{
					String nr = sms.getDisplayOriginatingAddress(),
						text = sms.getDisplayMessageBody();
					
					if (nr.endsWith(remoteNr))
						handleIncomingText(text);
				}
			}
			else if (intent.getAction().equals(PHONE))
			{
				TelephonyManager manager = (TelephonyManager)ctx.getSystemService(Context.TELEPHONY_SERVICE);
				manager.listen(new PhoneStateListener(){
					@Override
					public void onCallStateChanged(int state, String incomingNumber) {
						switch(state)
						{
						case TelephonyManager.CALL_STATE_RINGING:
						{
							Log.i("cpinto","call ringing from: " + incomingNumber);
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
    	IntentFilter filter = new IntentFilter(SMS);
    	filter.addAction(PHONE);
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
	private static float SENSITIVITY = 50;
	
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

	private void sendAlert(Location location) 
	{
		Log.i("cpinto",String.format("My Location: latitude: %1$.5f, longitude: %2$.5f. Map: http://maps.google.com/maps?q=%1$.5f,+%2$.5f",location.getLatitude(),location.getLongitude()));
		SmsManager.getDefault().sendTextMessage(getThisPhoneNr(), 
				null, 
				String.format("My Location: latitude: %1$.5f, longitude: %2$.5f. Map: http://maps.google.com/maps?q=%1$.5f,+%2$.5f",location.getLatitude(),location.getLongitude()), 
				null, 
				null);
	}
	
	private void handleIncomingText(String text) 
	{
		//TODO
	}
	
}