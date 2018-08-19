package com.cg.lrceditor;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class HomePage extends AppCompatActivity implements HomePageListAdapter.LyricFileSelectListener {

    private static final int WRITE_EXTERNAL_REQUEST = 1;
    private boolean storagePermissionAlreadyGranted = false;
    private boolean scannedOnce = false;

    private String saveLocation;
    private Uri saveUri;

    private HomePageListAdapter mAdapter;

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HomePage.this, CreateActivity.class);
                startActivity(intent);
            }
        });

        saveLocation = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE)
                .getString("saveLocation", Environment.getExternalStorageDirectory().getPath() + "/Lyrics");

        ready_fileIO();
        if (storagePermissionAlreadyGranted) {
            scan_lyrics();
        }

        actionModeCallback = new ActionModeCallback();

        setupAds();
    }

    private void setupAds() {
        MobileAds.initialize(this, "ca-app-pub-3940256099942544~3347511713");

        AdView mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    @Override
    protected void onResume() {
        super.onResume();
        saveLocation = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE)
                .getString("saveLocation", Environment.getExternalStorageDirectory().getPath() + "/Lyrics");
        String uriString = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE)
                .getString("saveUri", null);
        if (uriString != null)
            saveUri = Uri.parse(uriString);

        if (storagePermissionAlreadyGranted)
            scan_lyrics();
    }

    private void scan_lyrics() {
        File f = new File(saveLocation);

        TextView empty_textview = findViewById(R.id.empty_message_textview);
        RecyclerView r = findViewById(R.id.recyclerview);

        if (!f.exists()) {
            if (!f.mkdir()) {
                Toast.makeText(this, "Lyrics folder creation failed at " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
                Toast.makeText(this, "Make sure you have granted permissions", Toast.LENGTH_LONG).show();
                finish();
            }
            empty_textview.setVisibility(View.VISIBLE);
            r.setVisibility(View.GONE);
        } else {
            int count;
            try {
                count = f.listFiles().length;
            } catch(NullPointerException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to scan lyrics! Try changing the save location", Toast.LENGTH_LONG).show();
                Toast.makeText(this, "Please send a bug report with as much detail as possible", Toast.LENGTH_LONG).show();
                return;
            }
            if (count > 0) {
                boolean lyricsAvailable = false;
                for (File file : f.listFiles())
                    if (file.getName().endsWith(".lrc")) {
                        lyricsAvailable = true;
                        break;
                    }

                if (lyricsAvailable) {
                    empty_textview.setVisibility(View.GONE);
                    r.setVisibility(View.VISIBLE);

                    ready_recyclerView(f);
                } else {
                    empty_textview.setVisibility(View.VISIBLE);
                    r.setVisibility(View.GONE);
                }
            } else {
                empty_textview.setVisibility(View.VISIBLE);
                r.setVisibility(View.GONE);
            }
        }
    }

    private void ready_recyclerView(File f) {
        RecyclerView recyclerView = findViewById(R.id.recyclerview);

        LinkedList<File> list = new LinkedList<>();
        File[] fileList = f.listFiles();
        Arrays.sort(fileList);
        for (File file : fileList) {
            if (file.getName().endsWith(".lrc")) {
                list.addLast(file);
            }
        }

        if (mAdapter == null) {
            mAdapter = new HomePageListAdapter(this, list);
            recyclerView.setAdapter(mAdapter);
            mAdapter.setClickListener(this);
        } else {
            mAdapter.mFileList = list;
            mAdapter.notifyDataSetChanged();
        }

        if (scannedOnce) {
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);

        scannedOnce = true;
    }

    private void ready_fileIO() {
        if (!isExternalStorageWritable()) {
            Toast.makeText(this, "ERROR: Storage unavailable/busy", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT < 23 || grantPermission()) /* 23 = Marshmellow */
            storagePermissionAlreadyGranted = true;
    }

    private boolean grantPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            displayDialog();
            return false;
        }

        return true;
    }

    private void displayDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage("This app needs the read/write permission for viewing and saving the lyric files");
        dialog.setTitle("Need permissions");
        dialog.setCancelable(false);
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ActivityCompat.requestPermissions(HomePage.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_REQUEST);
            }
        });
        dialog.show();

    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == WRITE_EXTERNAL_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        scan_lyrics();
                        return;
                    } else {
                        Toast.makeText(this, "LRC Editor cannot function without the storage permission", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home_page, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_refresh:
                scan_lyrics();
                Toast.makeText(this, "List refreshed!", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void fileSelected(String fileName) {
        LyricReader r = new LyricReader(saveLocation, fileName);
        if (!r.readLyrics()) {
            Toast.makeText(this, r.getErrorMsg(), Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("LYRICS", r.getLyrics());
        intent.putExtra("TIMESTAMPS", r.getTimestamps());
        intent.putExtra("SONG METADATA", r.getSongMetaData());

        startActivity(intent);
    }

    @Override
    public void onLyricItemSelected(int position) {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback);
        }

        toggleSelection(position);
    }

    @Override
    public void onLyricItemClicked(int position) {
        if (actionMode == null)
            return;

        toggleSelection(position);
    }

    private void toggleSelection(int position) {
        mAdapter.toggleSelection(position);
        int count = mAdapter.getSelectionCount();

        if (count == 0) {
            actionMode.finish();
            actionMode = null;
        } else {
            Menu menu = actionMode.getMenu();
            MenuItem itemRename = menu.findItem(R.id.action_rename_homepage);
            if (count >= 2) {
                itemRename.setVisible(false);
            } else {
                itemRename.setVisible(true);
            }

            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    private void removeLyricFiles() {
        new AlertDialog.Builder(this)
                .setTitle("Confirmation")
                .setMessage("Are you sure you want to delete the selected LRC files?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<Integer> selectedItemPositions =
                                mAdapter.getSelectedItems();
                        DocumentFile pickedDir = getPersistableDocumentFile();

                        boolean deleteFailure = false;

                        for (int i = selectedItemPositions.size() - 1; i >= 0; i--) {
                            DocumentFile file = pickedDir.findFile(mAdapter.mFileList.get(selectedItemPositions.get(i)).getName());
                            if (file == null || !file.delete())
                                deleteFailure = true;
                        }
                        scan_lyrics();

                        if (deleteFailure) {
                            Toast.makeText(getApplicationContext(), "Failed to delete some/all the selected LRC files!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "Deleted selected LRC files successfully", Toast.LENGTH_LONG).show();
                        }

                        actionMode.finish();
                        actionMode = null;
                    }
                })
                .setNegativeButton("No", null)
                .create()
                .show();

    }

    private void renameLyricFile() {
        LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_layout, null);
        final EditText editText = view.findViewById(R.id.dialog_edittext);
        TextView textView = view.findViewById(R.id.dialog_prompt);

        final String fileName = mAdapter.mFileList.get(mAdapter.getSelectedItems().get(0)).getName();
        textView.setText(getString(R.string.new_file_name_prompt));
        editText.setText(fileName);

        editText.setHint(getString(R.string.new_file_name_hint));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setNewFileName(editText.getText().toString(), fileName);
                        actionMode.finish();
                        actionMode = null;
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    private void setNewFileName(String newName, String fileName) {
        DocumentFile pickedDir = getPersistableDocumentFile();

        if (new File(saveLocation, newName).exists())
            Toast.makeText(this, "File name already exists. Prefix might be added", Toast.LENGTH_LONG).show();

        DocumentFile file = pickedDir.findFile(fileName);
        if (file != null && file.renameTo(newName)) {
            Toast.makeText(this, "Renamed file successfully", Toast.LENGTH_LONG).show();
            scan_lyrics();
        } else
            Toast.makeText(this, "Rename failed!", Toast.LENGTH_LONG).show();

    }

    private DocumentFile getPersistableDocumentFile() {
        DocumentFile pickedDir;
        try {
            pickedDir = DocumentFile.fromTreeUri(this, saveUri);
            try {
                getContentResolver().takePersistableUriPermission(saveUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            pickedDir = DocumentFile.fromFile(new File(saveLocation));
        }

        return pickedDir;
    }

    private void selectAll() {
        mAdapter.selectAll();
        int count = mAdapter.getSelectionCount();

        if (count >= 2)
            actionMode.getMenu().findItem(R.id.action_rename_homepage).setVisible(false);

        actionMode.setTitle(String.valueOf(count));
        actionMode.invalidate();
    }

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.contextual_toolbar_homepageactivity, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int lyric_change;
            switch (item.getItemId()) {
                case R.id.action_delete_homepage:
                    removeLyricFiles();
                    return true;

                case R.id.action_rename_homepage:
                    renameLyricFile();
                    return true;

                case R.id.action_select_all_homepage:
                    selectAll();
                    return true;

                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mAdapter.clearSelections();
            actionMode = null;
        }
    }
}
