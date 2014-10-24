package com.stackallocated.imageupload;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class HistoryActivity extends Activity {
    private final static String TAG = "MainActivity";
    private final static int REQUEST_IMAGE_GET = 1;

    class HistoryCursorAdapter extends ResourceCursorAdapter {
        public HistoryCursorAdapter(Context context, int layout, Cursor c, int flags) {
            super(context, layout, c, flags);
        }

        @Override
        public void bindView(View view, final Context context, Cursor cursor) {
            final Uri uri = Uri.parse(cursor.getString(cursor.getColumnIndex(HistoryDatabase.IMAGES_COL_URL)));

            TextView urlView = (TextView)view.findViewById(R.id.history_list_item_url);
            urlView.setText(uri.getLastPathSegment());

            TextView dateView = (TextView)view.findViewById(R.id.history_list_item_date);
            long uploadedAt = cursor.getLong(cursor.getColumnIndex(HistoryDatabase.IMAGES_COL_UPLOADED_DATE));
            long now = System.currentTimeMillis();
            CharSequence dateText = null;
            if (now - uploadedAt < 24*60*60*1000) {
                dateText = DateUtils.getRelativeTimeSpanString(
                        uploadedAt,
                        now,
                        DateUtils.SECOND_IN_MILLIS,
                        0);
            } else {
                dateText = DateUtils.getRelativeDateTimeString(
                        getApplicationContext(),
                        uploadedAt,
                        DateUtils.DAY_IN_MILLIS,
                        DateUtils.WEEK_IN_MILLIS,
                        0);
            }
            dateView.setText(dateText);

            ImageView thumbnailView = (ImageView)view.findViewById(R.id.history_list_item_image);
            byte[] thumbnailData = cursor.getBlob(cursor.getColumnIndex(HistoryDatabase.IMAGES_COL_THUMBNAIL));
            Bitmap thumbnail = BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.length);
            thumbnailView.setImageBitmap(thumbnail);

            ImageButton button = (ImageButton)view.findViewById(R.id.history_list_item_copy);
            button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardURLReceiver.copyUriToClipboard(context, uri.toString());
                }
            });
        }
    }

    private void deleteImages(long[] ids) {
        HistoryDatabase.getInstance(this).deleteImages(ids);
    }

    private void updateHistoryCursor(CursorAdapter adapter) {
        Cursor images = HistoryDatabase.getInstance(this).getImages();
        adapter.changeCursor(images);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_history);

        final HistoryCursorAdapter adapter = new HistoryCursorAdapter(this, R.layout.history_list_item, null, 0);
        updateHistoryCursor(adapter);

        final ListView list = (ListView) findViewById(R.id.history_list);
        list.setAdapter(adapter);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        list.setMultiChoiceModeListener(new MultiChoiceModeListener() {
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.history_delete, menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId())  {
                    case R.id.action_delete_items:
                        // We can just have the DB delete them. There are no issues with
                        // race conditions because the IDs are the real row IDs.
                        deleteImages(list.getCheckedItemIds());
                        updateHistoryCursor(adapter);
                        mode.finish();
                        return true;
                }
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            }
        });
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
                    ListView view = (ListView)findViewById(R.id.history_list);
                    updateHistoryCursor((CursorAdapter)view.getAdapter());
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_clear_history_title)
                   .setMessage(R.string.dialog_clear_history_prompt)
                   .setNeutralButton(R.string.dialog_cancel, null)
                   .setPositiveButton(R.string.dialog_clear_history, clear_history_listener);
            builder.show();
            return true;
        } else if (id == R.id.action_upload) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_GET);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_GET && resultCode == RESULT_OK) {
            Uri imageuri = data.getData();
            Intent intent = new Intent(Intent.ACTION_SEND, null, this, UploadActivity.class);
            intent.putExtra(Intent.EXTRA_STREAM, imageuri);
            startActivity(intent);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
