package com.example.googledrivedemo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInApi;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import okio.BufferedSink;
import okio.Okio;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class MainActivity extends AppCompatActivity implements ServiceListener {

    Button login;
    Button start;
    Button logout;
    TextView status;

    private final int LOGGED_IN = 1;
    private final int LOGGED_OUT = 0;
    private int state = LOGGED_OUT;

    private GoogleDriveService googleDriveService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        login = (Button) findViewById(R.id.login);
        start = (Button) findViewById(R.id.start);
        logout = (Button) findViewById(R.id.logout);
        status = (TextView) findViewById(R.id.status);
        List<String> documentMimeTypes = new LinkedList<>(Arrays.asList("application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        GoogleDriveConfig config = new GoogleDriveConfig(
                getString(R.string.source_google_drive),
                documentMimeTypes
        );

        googleDriveService = new GoogleDriveService(this, config);

        //2
        googleDriveService.setServiceListener(this);

        //3
        googleDriveService.checkLoginStatus();

        //4
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                googleDriveService.auth();
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                googleDriveService.pickFiles(null);
            }
        });
        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                googleDriveService.logout();
                state = LOGGED_OUT;
                setButtons();
            }
        });


        //5
        setButtons();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        googleDriveService.onActivityResult(requestCode, resultCode, data);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
//        context = this;
        return super.onCreateView(name, context, attrs);
    }

    @Override
    public void loggedIn() {
        state = LOGGED_IN;
        setButtons();
    }

    @Override
    public void fileDownloaded(@NotNull File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkURI = FileProvider.getUriForFile(
                this,
                this.getPackageName() + ".provider",
                file);
        Uri uri = Uri.fromFile(file);
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        intent.setDataAndType(apkURI, mimeType);
        intent.setFlags(FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(this.getPackageManager()) != null) {
            startActivity(intent);
        } else {
            View main_layout = findViewById(R.id.main_layout);
            Snackbar.make(main_layout, R.string.not_open_file, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelled() {
        View main_layout = findViewById(R.id.main_layout);
        Snackbar.make(main_layout, R.string.status_user_cancelled, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void handleError(@NotNull Exception exception) {
        View main_layout = findViewById(R.id.main_layout);
        String errorMessage = getString(R.string.status_error, exception.getMessage());
        Snackbar.make(main_layout, errorMessage, Snackbar.LENGTH_LONG).show();
    }

    private void setButtons() {
        if(state == LOGGED_OUT){
            status.setText(getString(R.string.status_logged_out));
            start.setEnabled(false);
            logout.setEnabled(false);
            login.setEnabled(true);
        } else {
            status.setText(getString(R.string.status_logged_in));
            start.setEnabled(true);
            logout.setEnabled(true);
            login.setEnabled(false);
        }
    }
}