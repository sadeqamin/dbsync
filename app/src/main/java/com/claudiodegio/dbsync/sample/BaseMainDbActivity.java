package com.claudiodegio.dbsync.sample;


import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.claudiodegio.dbsync.DBSync;
import com.claudiodegio.dbsync.SyncResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.CreateFileActivityOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;
import com.google.android.gms.drive.OpenFileActivityOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Date;
import java.text.DateFormat;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import im.dino.dbinspector.activities.DbInspectorActivity;

public abstract class BaseMainDbActivity extends BaseActivity  {

    private final static String TAG = "BaseMainDbActivity";

    protected GoogleSignInClient mGoogleSignInClient;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;

    final int REQUEST_CODE_SIGN_IN = 101;
    final int REQUEST_CODE_SELECT_FILE = 200;
    final int REQUEST_CODE_NEW_FILE = 300;

    final String DRIVE_ID_FILE = "DRIVE_ID_FILE";

    @BindView(R.id.tvStatus)
    TextView mTvStatus;
    @BindView(R.id.tvStatus2)
    TextView mTvStatus2;
    @BindView(R.id.tvLastTimeStamp)
    TextView mTvLastSyncTimestamp;
    @BindView(R.id.tvCurrentTime)
    TextView mTvCurrentTime;

    @BindView(R.id.tvFileName)
    TextView mTvFileName;

    @BindView(R.id.btSync)
    Button mBtSync;
    @BindView(R.id.btSelectFileForSync)
    Button mBtSelectFileForSync;
    @BindView(R.id.btCreateFileForSync)
    Button mBtCreateFileForSync;
    @BindView(R.id.btResetLastSyncTimestamp)
    Button mBtResetLastSyncTimestamp;

    protected DriveId mDriveId;
    protected DBSync dbSync;

    Handler mMainHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ButterKnife.bind(this);

        if (GoogleSignIn.getLastSignedInAccount(this) != null) {
            // Use the last signed in account here since it already have a Drive scope.
            mDriveClient = Drive.getDriveClient(this, GoogleSignIn.getLastSignedInAccount(this));

            // Build a drive resource client.
            mDriveResourceClient =
                    Drive.getDriveResourceClient(this, GoogleSignIn.getLastSignedInAccount(this));
            successSingIn();
        } else {
            signIn();
        }

        mMainHandler = new Handler();

        mMainHandler.post(new UpdateCurrentTime());

        String driveId = getPreferences(Context.MODE_PRIVATE).getString(DRIVE_ID_FILE, null);

        if (driveId != null) {
            mDriveId = DriveId.decodeFromString(driveId);
            readMetadata();
        }
    }

    private void signIn() {
        Log.i(TAG, "Start sign in");
        mGoogleSignInClient = buildGoogleSignInClient();
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    /** Build a Google SignIn client. */
    private GoogleSignInClient buildGoogleSignInClient() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Drive.SCOPE_FILE)
                        .build();
        return GoogleSignIn.getClient(this, signInOptions);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (dbSync != null) {
            dbSync.dispose();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult requestCode:" + requestCode + " resultCode:" +resultCode);

        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN:
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "Signed in successfully.");
                    // Use the last signed in account here since it already have a Drive scope.
                    mDriveClient = Drive.getDriveClient(this, GoogleSignIn.getLastSignedInAccount(this));

                    // Build a drive resource client.
                    mDriveResourceClient =
                            Drive.getDriveResourceClient(this, GoogleSignIn.getLastSignedInAccount(this));

                    successSingIn();
                }
                break;

            case REQUEST_CODE_SELECT_FILE:
            case REQUEST_CODE_NEW_FILE:
                if (resultCode == RESULT_OK) {

                    mDriveId = data.getParcelableExtra(CreateFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID);

                    Log.d(TAG, "driveId: " + mDriveId.encodeToString());

                    SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
                    editor.putString(DRIVE_ID_FILE, mDriveId.encodeToString());
                    editor.commit();

                    mTvStatus2.setText("GDrive Client - Connected - File Selected");
                    mBtSync.setEnabled(true);
                    mBtResetLastSyncTimestamp.setEnabled(true);
                    readMetadata();

                    if (dbSync != null) {
                        dbSync.dispose();
                    }
                    onPostSelectFile();
                }
                break;
        }
    }

    @OnClick(R.id.btSelectFileForSync)
    public void actionSelectFileForSync() {
        try {
            OpenFileActivityOptions openFileActivityOptions =
                    new OpenFileActivityOptions.Builder()
                    .setActivityTitle("Select file for sync")
                    .build();

            mDriveClient.newOpenFileActivityIntentSender(openFileActivityOptions)
                    .addOnSuccessListener(this, intentSender -> {
                        try {
                            startIntentSenderForResult(intentSender, REQUEST_CODE_SELECT_FILE, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            Toast.makeText(BaseMainDbActivity.this, "Unable to create file", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

        } catch (Exception e) {
            Log.w(TAG, "Unable to send intent", e);
        }
    }


    @OnClick(R.id.btCreateFileForSync)
    public void actionCreateFileForSync() {
        try {
            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle("Create file for sync")
                    .setMimeType("text/plain")
                    .setStarred(true)
                    .build();

            CreateFileActivityOptions createOptions =
                    new CreateFileActivityOptions.Builder()
                            .setInitialMetadata(changeSet)
                            .build();

            mDriveClient.newCreateFileActivityIntentSender(createOptions)
                    .addOnSuccessListener(this, intentSender -> {
                        try {
                        startIntentSenderForResult(
                                intentSender, REQUEST_CODE_NEW_FILE, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Toast.makeText(BaseMainDbActivity.this, "Unable to create file", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    });

        } catch (Exception e) {
            Log.w(TAG, "Unable to send intent", e);
        }
    }
    protected void updateLastSyncTimeStamp(){
        if (dbSync != null) {
            long lastSyncStatus = dbSync.getLastSyncTimestamp();

            Date date = new Date(lastSyncStatus);

            mTvLastSyncTimestamp.setText("Timestamp: " + lastSyncStatus + " - " +Utility.formatDateTimeNoTimeZone(date));
        }
    }

    @OnClick(R.id.btResetLastSyncTimestamp)
    public void resetLastSyncTimestamp(){

        if (dbSync != null) {
            dbSync.resetLastSyncTimestamp();
            updateLastSyncTimeStamp();
        }
    }

    @OnClick(R.id.btToDbManager)
    public void goToDBManager(){
        startActivity(new Intent(this, DbInspectorActivity.class));
    }

    @OnClick(R.id.btSync)
    public void btSync(){
        mTvStatus.setText("In Progress...");
        new SyncTask().execute();
    }

    public abstract void onPostSync();

    public abstract void onPostSelectFile();


    private void readMetadata(){


        mDriveResourceClient.getMetadata(mDriveId.asDriveFile())
                .addOnSuccessListener(this, metadata -> mTvFileName.setText("File name: " + metadata.getOriginalFilename()));
    }

    class UpdateCurrentTime implements Runnable {

        @Override
        public void run() {
            mTvCurrentTime.setText("CurrentTime: " +Utility.formatDateTimeNoTimeZone(new Date()));

            mMainHandler.postDelayed(this, 500);
        }
    }

    class SyncTask extends AsyncTask<Void, Void, SyncResult> {

        @Override
        protected SyncResult doInBackground(Void... params) {
            return  dbSync.sync();
        }

        @Override
        protected void onPostExecute(SyncResult result) {

            if (result.getStatus().isSuccess()) {
                mTvStatus.setText("OK - Insert: " +  result.getCounter().getRecordInserted() + " - Update: " +  result.getCounter().getRecordUpdated());
                updateLastSyncTimeStamp();
            } else {
                mTvStatus.setText("Fail: " + result.getStatus().getStatusCode() + "\n" + result.getStatus().getStatusMessage());
                mTvLastSyncTimestamp.setText("");
            }

            onPostSync();
        }
    }


    private void successSingIn() {
        mTvStatus2.setText("GDrive Client - Connected");
        mBtSelectFileForSync.setEnabled(true);
        mBtCreateFileForSync.setEnabled(true);

        if (mDriveId != null) {
            mTvStatus2.setText("GDrive Client - Connected - File Selected");
            mBtSync.setEnabled(true);
            mBtResetLastSyncTimestamp.setEnabled(true);
            readMetadata();

            onPostSelectFile();
        }
    }
}
