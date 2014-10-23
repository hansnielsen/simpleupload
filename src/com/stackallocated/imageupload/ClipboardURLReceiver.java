package com.stackallocated.imageupload;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.widget.Toast;

public class ClipboardURLReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
	    copyUriToClipboard(context, intent.getAction());
	}

    public static void copyUriToClipboard(Context context, String uri) {
        final Resources res = context.getResources();

		Uri uriObject = Uri.parse(uri);
		ClipData clip = ClipData.newPlainText(res.getString(R.string.clipboardreceiver_label), uriObject.toString());
		ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		cm.setPrimaryClip(clip);

		Toast toast = Toast.makeText(context, res.getString(R.string.clipboardreceiver_copied), Toast.LENGTH_SHORT);
		toast.show();
    }
}