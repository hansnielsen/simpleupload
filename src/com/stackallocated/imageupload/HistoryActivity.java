package com.stackallocated.imageupload;

import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class HistoryActivity extends Activity {
    private final static String TAG = "MainActivity";

    class HistoryCursorAdapter extends ResourceCursorAdapter {
        public HistoryCursorAdapter(Context context, int layout, Cursor c, int flags) {
            super(context, layout, c, flags);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView urlView = (TextView)view.findViewById(R.id.history_list_item_url);
            urlView.setText(cursor.getString(cursor.getColumnIndex(HistoryDatabase.IMAGES_COL_URL)));

            TextView dateView = (TextView)view.findViewById(R.id.history_list_item_date);
            Date date = new Date(cursor.getLong(cursor.getColumnIndex(HistoryDatabase.IMAGES_COL_UPLOADED_DATE)));
            dateView.setText(date.toString());

            ImageView thumbnailView = (ImageView)view.findViewById(R.id.history_list_item_image);
            byte[] thumbnailData = cursor.getBlob(cursor.getColumnIndex(HistoryDatabase.IMAGES_COL_THUMBNAIL));
            Bitmap thumbnail = BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.length);
            thumbnailView.setImageBitmap(thumbnail);
        }
    }

    private void displayHistory() {
        Cursor images = HistoryDatabase.getInstance(this).getImages();
        HistoryCursorAdapter adapter = new HistoryCursorAdapter(this, R.layout.history_list_item, images, 0);
        ListView list = (ListView) findViewById(R.id.history_list);
        list.setAdapter(adapter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_history);

        displayHistory();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_clear_history) {
            DialogInterface.OnClickListener clear_history_listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    HistoryDatabase.getInstance(getApplicationContext()).deleteAllImages();
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_clear_history_title)
                   .setMessage(R.string.dialog_clear_history_prompt)
                   .setNeutralButton(R.string.dialog_cancel, null)
                   .setPositiveButton(R.string.dialog_clear_history, clear_history_listener);
            builder.show();
        }
        return super.onOptionsItemSelected(item);
    }
}
