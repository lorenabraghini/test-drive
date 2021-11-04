package com.example.googledrivedemo

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.util.CollectionUtils.setOf
import com.google.android.gms.drive.*
import okio.Okio
import java.io.File
import java.io.IOException

class GoogleDriveService(private val activity: Activity, private val config: GoogleDriveConfig) {

    companion object {
        var documentMimeTypes = arrayListOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

        val SCOPES = setOf<Scope>(Drive.SCOPE_FILE, Drive.SCOPE_APPFOLDER)
        const val REQUEST_CODE_OPEN_ITEM = 100
        const val REQUEST_CODE_SIGN_IN = 101
        const val TAG = "GoogleDriveService"
    }

    var serviceListener: ServiceListener? = null //1
    private var driveClient: DriveClient? = null //2
    private var driveResourceClient: DriveResourceClient? = null //3
    private var signInAccount: GoogleSignInAccount? = null //4

    private val googleSignInClient: GoogleSignInClient by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        for (scope in SCOPES) {
            builder.requestScopes(scope)
        }
        val signInOptions = builder.build()
        GoogleSignIn.getClient(activity, signInOptions)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_SIGN_IN -> {
                if (data != null) {
                    handleSignIn(data)
                } else {
                    serviceListener?.cancelled()
                }
            }

            REQUEST_CODE_OPEN_ITEM -> {
                if (data != null) {
                    openItem(data)
                } else {
                    serviceListener?.cancelled()
                }
            }
        }
    }

    private fun handleSignIn(data: Intent) {
        val getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data)
        if (getAccountTask.isSuccessful) {
            initializeDriveClient(getAccountTask.result)
        } else {
            serviceListener?.handleError(Exception("Sign-in failed.", getAccountTask.exception))
        }
    }

    private fun initializeDriveClient(signInAccount: GoogleSignInAccount) {
        driveClient = Drive.getDriveClient(activity.applicationContext, signInAccount)
        driveResourceClient = Drive.getDriveResourceClient(activity.applicationContext, signInAccount)
        serviceListener?.loggedIn()
    }

    private fun openItem(data: Intent) {
        val driveId = data.getParcelableExtra<DriveId>(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID)
        downloadFile(driveId)
    }

    private fun downloadFile(data: DriveId?) {
        if (data == null) {
            Log.e(TAG, "downloadFile data is null")
            return
        }
        val drive = data.asDriveFile()
        var fileName = "test"
        driveResourceClient?.getMetadata(drive)?.addOnSuccessListener {
            fileName = it.originalFilename
        }
        val openFileTask = driveResourceClient?.openFile(drive, DriveFile.MODE_READ_ONLY)
        openFileTask?.continueWithTask { task ->
            val contents = task.result
            contents.inputStream.use {
                try {
                    //This is the app's download directory, not the phones
                    val storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val tempFile = File(storageDir, fileName)
                    tempFile.createNewFile()
                    val sink = Okio.buffer(Okio.sink(tempFile))
                    sink.writeAll(Okio.source(it))
                    sink.close()

                    serviceListener?.fileDownloaded(tempFile)
                } catch (e: IOException) {
                    Log.e(TAG, "Problems saving file", e)
                    serviceListener?.handleError(e)
                }
            }
            driveResourceClient?.discardContents(contents)
        }?.addOnFailureListener { e ->
            // Handle failure
            Log.e(TAG, "Unable to read contents", e)
            serviceListener?.handleError(e)
        }
    }

    fun pickFiles(driveId: DriveId?) {
        val builder = OpenFileActivityOptions.Builder()
        if (config.mimeTypes != null) {
            builder.setMimeType(config.mimeTypes)
        } else {
            builder.setMimeType(documentMimeTypes)
        }
        if (config.activityTitle != null && config.activityTitle.isNotEmpty()) {
            builder.setActivityTitle(config.activityTitle)
        }
        if (driveId != null) {
            builder.setActivityStartFolder(driveId)
        }
        val openOptions = builder.build()
        pickItem(openOptions)
    }

    private fun pickItem(openOptions: OpenFileActivityOptions) {
        val openTask = driveClient?.newOpenFileActivityIntentSender(openOptions)
        openTask?.let {
            openTask.continueWith { task ->
                ActivityCompat.startIntentSenderForResult(activity, task.result, REQUEST_CODE_OPEN_ITEM,
                        null, 0, 0, 0, null)
            }
        }
    }

    fun checkLoginStatus() {
        val requiredScopes = HashSet<Scope>(2)
        requiredScopes.add(Drive.SCOPE_FILE)
        requiredScopes.add(Drive.SCOPE_APPFOLDER)
        signInAccount = GoogleSignIn.getLastSignedInAccount(activity)
        val containsScope = signInAccount?.grantedScopes?.containsAll(requiredScopes)
        val account = signInAccount
        if (account != null && containsScope == true) {
            initializeDriveClient(account)
        }
    }

    fun auth() {
        activity.startActivityForResult(googleSignInClient.signInIntent, REQUEST_CODE_SIGN_IN)
    }

    fun logout() {
        googleSignInClient.signOut()
        signInAccount = null
    }
}