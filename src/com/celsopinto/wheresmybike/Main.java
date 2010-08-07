package com.celsopinto.wheresmybike;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class Main extends Activity {
    /** Called when the activity is first created. */
	private static enum AppState {
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
			break;
		case STOPPABLE:
			unregisterIntent(this.getApplicationContext());
			b.setText("Start");
			state = AppState.STARTABLE;
			break;
		default:
			break;
		}
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TextView tv = (TextView)findViewById(R.id.alarmPhoneNr);
        Button button = (Button)findViewById(R.id.startButton);
        tv.setText(getThisPhoneNr());
        button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				toggleState((Button)v);
			}
		});
    }
    
    @Override
    protected void onDestroy() {
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
						Log.i("cpinto", String.format("Received %s", text));
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
    	Log.i("cpinto","listener started");
		Log.i("cpinto","waiting for: " + remoteNr);
    }
    
    private void unregisterIntent(Context ctx)
    {
    	ctx.unregisterReceiver(listener);
    	Log.i("cpinto","listener stopped");
    }
    
    
	private String getThisPhoneNr(){
    	return ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
	}

	/*
MyLocation myLocation = new MyLocation();
private void locationClick() {
    myLocation.getLocation(this, locationResult));
}

public LocationResult locationResult = new LocationResult(){
    @Override
    public void gotLocation(final Location location){
        //Got the location!
        });
    }
};	 
	 */
}