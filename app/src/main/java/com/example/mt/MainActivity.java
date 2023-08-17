package com.example.mt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonParseException;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int READ_REQUEST_CODE = 42;
    private static final int PERMISSION_REQUEST_CODE = 43;

    private static final int REQUEST_CODE_SIGN_IN = 1;
    private static final int REQUEST_CODE_OPEN_DOCUMENT = 2;

    private static final int REQUEST_CODE_PERMISSION = 0x3;

    private GoogleSignInClient googleSignInClient;
    private DriveClient driveClient;

    private static final int PICK_FILE_REQUEST = 1;
    private static final String COLAB_URL = "https://colab.research.google.com/";
    private static final String COLAB_UPLOAD_URL = "https://colab.research.google.com/upload";
    private static final String BOUNDARY = "*****";
    private static final String CRLF = "\r\n";

    private static final String FILE_FIELD_NAME = "file";

    private TextView selectedFileTextView;
    private TextView keyBar;
    private ListView chordsListView;
    private Button playButton;
    private Button scrollButton;
    private Button fasterButton;
    private Button slowerButton;
    private Button mSelectButton;

    private Button btnLp;

    ProgressBar progressBar;



    private List<String> chordsList = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private int currentPosition = 0;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find views
        selectedFileTextView = findViewById(R.id.selected_file_text_view);
        chordsListView = findViewById(R.id.list_ho);
        playButton = findViewById(R.id.btn_play);
        scrollButton = findViewById(R.id.btn_scroll);
        fasterButton = findViewById(R.id.btn_fast);
        slowerButton = findViewById(R.id.btn_slow);
        keyBar = findViewById(R.id.tvk);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        mSelectButton = (Button) findViewById(R.id.btn_upload_midi);
        mSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/mid");
                startActivityForResult(intent, PICK_FILE_REQUEST);

            }
        });



        findViewById(R.id.btn_convert).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Convert chords to list
                progressBar.setVisibility(View.VISIBLE);

                loadChordsData();




                Handler handler = new Handler();

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                    }
                };
                handler.postDelayed(runnable, 3000);
                displayChords();
            }
        });




        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isPlaying && mediaPlayer != null) {
                    mediaPlayer.start();
                    isPlaying = true;
                    playButton.setText("Stop");
                }
                else {mediaPlayer.pause();
                    mediaPlayer.seekTo(0);
                    isPlaying = false;
                    playButton.setText("Play");

                }
            }

        });

        scrollButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Scroll down chords list view slowly
                final int totalScrollTime = 100000; // Total duration of the scroll animation in milliseconds
                final int scrollInterval = 3000; // Duration of each small scroll in milliseconds
                final int pixelsToScroll = 150; // Number of pixels to scroll in each small scroll
                final Handler handler = new Handler();

                final Runnable runnable = new Runnable() {
                    int currentScroll = 0;

                    @Override
                    public void run() {
                        if (currentScroll < chordsListView.getHeight()) {
                            chordsListView.smoothScrollBy(pixelsToScroll, scrollInterval);
                            currentScroll += pixelsToScroll;
                            handler.postDelayed(this, scrollInterval);
                        }
                    }
                };
                handler.postDelayed(runnable, scrollInterval);
            }
        });

        fasterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Increase playback speed
                if (mediaPlayer != null) {
                    float speed = mediaPlayer.getPlaybackParams().getSpeed();
                    mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed * 1.25f));
                }
            }
        });

        slowerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Decrease playback speed
                if (mediaPlayer != null) {
                    float speed = mediaPlayer.getPlaybackParams().getSpeed();
                    mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed * 0.8f));
                }
            }
        });

        // Check and request necessary permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSION);
        } else {
            // Permissions already granted, proceed with sign-in
            signInToDrive();
        }
    }

    // Handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with sign-in
                signInToDrive();
            } else {
            }
        }
    }

    // Call this method to start the sign-in flow
    private void signInToDrive() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Drive.SCOPE_FILE)
                        .build();

        googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
        startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);



        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            String fileName = getFileName(uri) + " was selected";
            TextView selectedFileTextView = findViewById(R.id.selected_file_text_view);
            selectedFileTextView.setText(fileName);

            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(getApplicationContext(), uri);
                mediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

/*
        Button btnLp = findViewById(R.id.btn_lp);
        btnLp.setOnClickListener(new View.OnClickListener() {
            public static String[] transposeChords(String[] chords, int semitones) {
                String[] transposedChords = new String[chords.length];
                for (int i = 0; i < chords.length; i++) {
                    transposedChords[i] = transposeChord(chords[i], semitones);
                }
                return transposedChords;
            }

            private static String transposeChord(String chord, int semitones) {
                try {
                    JSONArray chordsArray = new JSONArray();
                    String[] chords = new String[chordsArray.length()];
                    for (int i = 0; i < chordsArray.length(); i++) {
                        chords[i] = chordsArray.getString(i);
                    }
                    String[] transposedChords = transposeChords(chords, semitones);
                    JSONArray transposedChordsArray = new JSONArray(transposedChords);
                    return transposedChordsArray.toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }

            public static String transposeChords(String jsonChords, int semitones) {
                try {
                    JSONArray chordsArray = new JSONArray(jsonChords);
                    String[] chords = new String[chordsArray.length()];
                    for (int i = 0; i < chordsArray.length(); i++) {
                        chords[i] = chordsArray.getString(i);
                    }
                    String[] transposedChords = transposeChords(chords, semitones);
                    JSONArray transposedChordsArray = new JSONArray(transposedChords);
                    return transposedChordsArray.toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }

        });
 */

   }

    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void displayChords() {
        // Display chords in list view



        List<String> chordRows = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < chordsList.size(); i += 2) {
            j=j+1;
            int endIndex = Math.min(i + 2, chordsList.size());
            List<String> rowChords = chordsList.subList(i, endIndex);
            String row = j + "                        " +String.format("%-35s%s", rowChords.toArray());
            chordRows.add(row);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chordRows);
        chordsListView.setAdapter(adapter);
        chordsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(MainActivity.this, chordsList.get(position), Toast.LENGTH_SHORT).show();
            }
        });

    }




    private void renew() {

        List<String> chordRows = new ArrayList<>();
        for (int i = 0; i < chordsList.size(); i += 4) {
            int endIndex = Math.min(i + 4, chordsList.size());
            List<String> rowChords = chordsList.subList(i, endIndex);
            String row = String.format("%-10s%-10s%-10s%s", rowChords.toArray());
            chordRows.add(row);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chordRows);
        adapter.clear();
        adapter.addAll(chordsList);
        adapter.notifyDataSetChanged();
        chordsListView.setAdapter(adapter);
        chordsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(MainActivity.this, chordsList.get(position), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void downloadChordsFileFromDrive() {
        Drive.DriveApi.getRootFolder(driveClient.asGoogleApiClient())
                .listChildren(driveClient.asGoogleApiClient())
                .setResultCallback(childrenResult -> {
                    if (!childrenResult.getStatus().isSuccess()) {
                        // Handle the listing of children failure
                        Log.e(TAG, "Failed to list children of root folder");
                        return;
                    }

                    MetadataBuffer metadataBuffer = null;
                    try {
                        metadataBuffer = childrenResult.getMetadataBuffer();
                        for (Metadata metadata : metadataBuffer) {
                            if (metadata.getTitle().equals("chords.json")) {
                                DriveFile chordsFile = metadata.getDriveId().asDriveFile();

                                chordsFile.open(driveClient.asGoogleApiClient(), DriveFile.MODE_READ_ONLY, null)
                                        .setResultCallback(openResult -> {
                                            if (!openResult.getStatus().isSuccess()) {
                                                // Handle the file open failure
                                                Log.e(TAG, "Failed to open the chords file");
                                                return;
                                            }

                                            DriveContents driveContents = openResult.getDriveContents();
                                            InputStream inputStream = driveContents.getInputStream();

                                        });
                                break;
                            }
                        }
                    } finally {
                        if (metadataBuffer != null)
                            metadataBuffer.release();
                    }
                });
    }


    private void uploadFile(final File file) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(COLAB_UPLOAD_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

                    DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                    dos.writeBytes("--" + BOUNDARY + CRLF);
                    dos.writeBytes("Content-Disposition: form-data; name=\"" + FILE_FIELD_NAME + "\"; filename=\"" + file.getName() + "\"" + CRLF);
                    dos.writeBytes(CRLF);

                    FileInputStream fis = new FileInputStream(file);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                    fis.close();

                    dos.writeBytes(CRLF);
                    dos.writeBytes("--" + BOUNDARY + "--" + CRLF);
                    dos.flush();
                    dos.close();

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.i("UPLOAD", "Upload successful");
                    } else {
                        Log.i("UPLOAD", "Upload failed with response code " + responseCode);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void loadChordsData() {
        try {
            // Read the JSON data from the file
            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open("chords.json");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            // Convert the JSON data to a string
            String json = new String(buffer, "UTF-8");

            // Parse the JSON string
            JSONObject jsonObject = new JSONObject(json);
            String Key = jsonObject.optString("key");
            JSONArray jsonChords = jsonObject.optJSONArray("chords");



            TextView keyBar = findViewById(R.id.tvk);
            keyBar.setText("Key:" + Key);
            Gson gson = new Gson();
            Type type = new TypeToken<List<String>>(){}.getType();

            chordsList = gson.fromJson(String.valueOf(jsonChords), type);

        } catch (IOException | JSONException e) {

        }
    }
}

