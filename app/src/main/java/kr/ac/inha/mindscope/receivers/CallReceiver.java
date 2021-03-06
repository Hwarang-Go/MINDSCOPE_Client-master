package kr.ac.inha.mindscope.receivers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import kr.ac.inha.mindscope.DbMgr;
import kr.ac.inha.mindscope.Utils;

public class CallReceiver extends PhonecallReceiver {
    public static final String TAG = "CallReceiver";
    final String CALL_TYPE_OUTGOING = "OUT";
    final String CALL_TYPE_INCOMING = "IN";
    public static boolean AudioRunningForCall = false;

    @Override
    protected void onOutgoingCallEnded(Context ctx, String number, long start, long end) {
        Utils.logThreadSignature(TAG + " onOutgoingCallEnded");
        Log.d(TAG, "onOutgoingCallEnded -> " + "number: " + number + "; start date: " + start + "; end date: " + end);
        long duration = (end - start) / 1000; // in seconds
        SharedPreferences prefs = ctx.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = prefs.getInt("CALLS", -1);
        assert dataSourceId != -1;
        DbMgr.saveMixedData(dataSourceId, start, 1.0f, start, end, CALL_TYPE_OUTGOING, duration);
        //finish the audio
        AudioRunningForCall = false;
    }

    @Override
    protected void onIncomingCallEnded(Context ctx, String number, long start, long end) {
        Utils.logThreadSignature(TAG + " onIncomingCallEnded");
        Log.d(TAG, "onIncomingCallEnded -> " + "number: " + number + "; start date: " + start + "; end date: " + end);
        long duration = (end - start) / 1000; // in seconds
        SharedPreferences prefs = ctx.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = prefs.getInt("CALLS", -1);
        assert dataSourceId != -1;
        DbMgr.saveMixedData(dataSourceId, start, 1.0f, start, end, CALL_TYPE_INCOMING, duration);
        //finish the audio
        AudioRunningForCall = false;
    }

    @Override
    protected void onIncomingCallReceived(Context ctx, String number, long start) {
        Utils.logThreadSignature(TAG + " onIncomingCallReceived");
        Log.d(TAG, "onIncomingCallReceived -> " + "number: " + number + "; start date: " + start);
        //start the audio
        AudioRunningForCall = true;
    }

    @Override
    protected void onIncomingCallAnswered(Context ctx, String number, long start) {
        Utils.logThreadSignature(TAG + " onIncomingCallAnswered");
        Log.d(TAG, "onIncomingCallAnswered -> " + "number: " + number + "; start date: " + start);
    }

    @Override
    protected void onOutgoingCallStarted(Context ctx, String number, long start) {
        Utils.logThreadSignature(TAG + " onOutgoingCallStarted");
        Log.d(TAG, "onOutgoingCallStarted -> " + "number: " + number + "; start date: " + start);
        //start the audio
        AudioRunningForCall = true;
    }

    @Override
    protected void onMissedCall(Context ctx, String number, long start) {
        Utils.logThreadSignature(TAG + " onMissedCall");
        Log.d(TAG, "onMissedCall -> " + "number: " + number + "; start date: " + start);
        //finish the audio
        AudioRunningForCall = false;
    }
}
