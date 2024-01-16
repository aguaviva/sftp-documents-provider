/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.aguaviva.android.sftpstorageprovider;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.aguaviva.android.libssh2.Connection;
import com.aguaviva.android.libssh2.SFTP;
import com.aguaviva.android.libssh2.SFTPMT;
import com.aguaviva.android.libssh2.SFTP_retry;
import com.aguaviva.android.libssh2.SftpSession;
import com.aguaviva.android.libssh2.Ssh2;
//import com.example.android.common.logger.Log;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.os.Looper;

/**
 * Manages documents and exposes them to the Android system for sharing.
 */
public class MyCloudProvider extends DocumentsProvider {
    private static final String TAG = "MyCloudProvider";

    // Use these as the default columns to return information about a root if no specific
    // columns are requested in a query.
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    };

    // Use these as the default columns to return information about a document if no specific
    // columns are requested in a query.
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
    };

    String currentConnectionName = "";
    private SFTP sftp_client = null;
    private SFTPMT sftp_client_mt = null;
    private SftpSession sftp_session = null;
    private StorageManager mStorageManager;

    class CacheEntry {
        public List<String> files = new ArrayList<String>();
        public boolean loading = true;

    }
    ConcurrentHashMap<String, CacheEntry> cache_ls = new ConcurrentHashMap<String, CacheEntry>();



    @Override
    public boolean onCreate() {
        Log.v(TAG, "onCreate");
        final Context context = getContext();
        mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

        helpers.Init(getContext());

        Ssh2.init_ssh();

        if (sftp_client==null)
            sftp_client = new SFTP_retry();

        if (sftp_client_mt==null)
            sftp_client_mt = new SFTPMT();

        if (sftp_session==null)
            sftp_session = new SftpSession(Looper.getMainLooper());

        return true;
    }

    public static final int SSH_CONNECTED = 0;
    public static final int SSH_DISCONNECTED = 1;
    public static final int SSH_CONNECTING = 2;
    private int ssh_state = SSH_DISCONNECTED;

    private void connect(String connectionName) {
        int port = -1;
        Log.i(TAG, String.format("Connecting Begin "));

        Connection connection;

        try {
            connection = helpers.loadConnection(connectionName);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Log.i(TAG, String.format("Resolving %s", connection.hostname));
        try {
            connection.hostname = InetAddress.getByName(connection.hostname).getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        Log.i(TAG, String.format("Resolved %s", connection.hostname));
        sftp_client_mt.Init(connection, 1);

        sftp_client.Connect(connection);
        sftp_session.Connect(connection);

        Log.i(TAG, String.format("Connecting End"));

        currentConnectionName = connectionName;
    }

    private MatrixCursor connectingMatrixCursor(String documentId) {
        MatrixCursor result = new MatrixCursor(DEFAULT_DOCUMENT_PROJECTION) {
            public String extra_info = null;

            @Override
            public Bundle getExtras() {
                Bundle bundle = new Bundle();
                bundle.putBoolean(DocumentsContract.EXTRA_LOADING, extra_info != null);
                bundle.putString(DocumentsContract.EXTRA_INFO, "queryDocument: Connecting to server ...");
                return bundle;
            }
        };

        result.setNotificationUri(getContext().getContentResolver(), DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, documentId));
        return result;
    }

    private MatrixCursor connectIfNecessary(String documentId)
    {
        synchronized (currentConnectionName) {

            if (ssh_state == SSH_CONNECTING ) {
                return connectingMatrixCursor(documentId);
            }

            String connectionName = getConnectionNameFrom(documentId);
            if (currentConnectionName.startsWith(connectionName) == false)
                ssh_state = SSH_DISCONNECTED;

            if (ssh_state == SSH_CONNECTED)
                return null;

            ssh_state = SSH_CONNECTING;

            cache_ls.clear();

            MatrixCursor result = connectingMatrixCursor(documentId);

            new Thread() {
                public void run() {
                    connect(connectionName);
                    ssh_state = SSH_CONNECTED;
                    MyCloudProvider.this.getContext().getContentResolver().notifyChange(DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, documentId), null);
                }
            }.start();

            return result;
        }
    }

    private boolean needsReconnect(String parentDocumentId) {
        synchronized (sftp_client_mt) {
            return (currentConnectionName.equals("") || currentConnectionName.startsWith(getConnectionNameFrom(parentDocumentId)) == false);
        }
    }

    // BEGIN_INCLUDE(query_roots)
    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        Log.v(TAG, "Begin queryRoots");

        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));

        // If user is not logged in, return an empty root cursor.  This removes our provider from
        // the list entirely.
        if (!isUserLoggedIn()) {
            return result;
        }

        File[] connections = helpers.getFilesConnections();
        for(int i=0;i<connections.length;i++) {

            Connection connection = null;
            String connectionName = connections[i].getName();
            Log.v(TAG, String.valueOf(i) + " - " + connectionName);
            try {
                connection = helpers.loadConnection(connectionName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            final MatrixCursor.RowBuilder row = result.newRow();
            row.add(Root.COLUMN_ROOT_ID, connectionName);
            row.add(Root.COLUMN_TITLE, getContext().getString(R.string.app_name));
            row.add(Root.COLUMN_SUMMARY, connectionName);
            row.add(Root.COLUMN_DOCUMENT_ID, connectionName);
            row.add(Root.COLUMN_ICON, R.drawable.ic_launcher);
            //row.add(Root.COLUMN_AVAILABLE_BYTES, mBaseDir.getFreeSpace());

            if (SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE
                        //| Root.FLAG_SUPPORTS_RECENTS
                        //| Root.FLAG_SUPPORTS_SEARCH
                );
            } else {
                row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE |
                        //Root.FLAG_SUPPORTS_RECENTS |
                        //Root.FLAG_SUPPORTS_SEARCH |
                        Root.FLAG_SUPPORTS_IS_CHILD);
            }
        }

        Log.v(TAG, "End queryRoots");
        Log.v(TAG, "");
        return result;
    }
    // END_INCLUDE(query_roots)

    private String getRemotePath(String documentId) {
        int l = documentId.indexOf("/");
        if (l>=0) {
            String remotePath = documentId.substring(l);
            //assert(documentId.startsWith(currentConnectionName));
            return remotePath;
        }

        return "";
    }

    private String getConnectionNameFrom(String documentId) {
        int l = documentId.indexOf("/");
        if (l>=0) {
            String connectionName = documentId.substring(0, l);
            return connectionName;
        }

        return documentId;
    }

    private String getParent(String documentId) {
        int l = documentId.lastIndexOf("/");
        String parentId = documentId.substring(0, l);
        return parentId;
    }

    private String getFilename(String documentId) {
        int l = documentId.lastIndexOf("/");
        String filenameId = documentId.substring(l+1);
        return filenameId;
    }

    private void notifyChange(String documentId) {
        synchronized (cache_ls) {
            getContext().getContentResolver().notifyChange(DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, documentId), null);
            cache_ls.remove(documentId);
        }
    }

    // BEGIN_INCLUDE(query_document)
    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {

        synchronized(this) {
            Log.v(TAG, "Begin queryDocument: " + documentId);

            boolean isRoot = getConnectionNameFrom(documentId).equals(documentId);
            if (isRoot) {

                MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
                Log.v(TAG, "Is root!");
                final MatrixCursor.RowBuilder row = result.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, documentId);
                row.add(Document.COLUMN_DISPLAY_NAME, documentId);
                row.add(Document.COLUMN_ICON, R.drawable.ic_launcher);
                row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
                row.add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE);

                result.setNotificationUri(getContext().getContentResolver(), DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, documentId));
                return result;

            } else {

                MatrixCursor result1 = connectIfNecessary(documentId);
                if (result1 != null)
                    return result1;

                {
                    MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

                    String entry  = sftp_client.stat(getRemotePath(documentId));
                    if (entry.startsWith("*")) {
                        Log.e(TAG, String.format("sftp_stat %s: %s\n", documentId, entry));
                        Log.e(TAG, "Error " + sftp_client.getLastError());
                    }

                    includeFile(result, getParent(documentId), entry + " " + getFilename(documentId));

                    result.setNotificationUri(getContext().getContentResolver(), DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, documentId));
                    return result;
                }
            }

        }
    }
    // END_INCLUDE(query_document)

    // BEGIN_INCLUDE(query_child_documents)
    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                      String sortOrder) throws FileNotFoundException {

        MatrixCursor result1 = connectIfNecessary(parentDocumentId);
        if (result1 != null)
            return result1;

        synchronized (cache_ls) {
            CacheEntry cacheEntry = cache_ls.get(parentDocumentId);
            if (cacheEntry != null) {
                if (cacheEntry.loading == true) {
                    // cache busy
                    return connectingMatrixCursor(parentDocumentId);
                } else {
                    // cache hit
                    final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
                    for (String entry : cacheEntry.files) {
                        includeFile(result, parentDocumentId, entry);
                    }

                    result.setNotificationUri(getContext().getContentResolver(), DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, parentDocumentId));
                    return result;
                }
            } else {
                // cache miss
                cacheEntry = new CacheEntry();
                cache_ls.put(parentDocumentId, cacheEntry); //this indicates WIP

                MatrixCursor wipCursor = connectingMatrixCursor(parentDocumentId);

                try {
                    CacheEntry finalCacheEntry = cacheEntry;
                    sftp_client_mt.ls(getRemotePath(parentDocumentId), new SFTP.onGetFileListener() {
                        @Override
                        public boolean listen(String entry) {
                            if (entry.endsWith(" .") || entry.endsWith(" ..")) {

                            } else {
                                finalCacheEntry.files.add(entry);
                            }
                            return true;
                        }

                        @Override
                        public void done() {
                            finalCacheEntry.loading = false;
                            getContext().getContentResolver().notifyChange(DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, parentDocumentId), null);
                        }
                    });
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return wipCursor;
            }
        }
    }
    // END_INCLUDE(query_child_documents)


    // BEGIN_INCLUDE(open_document)
    @Override
    public ParcelFileDescriptor openDocument(final String documentId, final String mode, CancellationSignal signal)
            throws FileNotFoundException {

        ParcelFileDescriptor pfd = null;

        //Log.v(TAG, "openDocument, mode: " + mode + " " + documentId);
        try {

            return pfd = sftp_session.GetParcelFileDescriptor(mStorageManager, getRemotePath(documentId), mode);

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
/*
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            final ParcelFileDescriptor readEnd = pipe[0];
            final ParcelFileDescriptor writeEnd = pipe[1];

             synchronized (this) {
                if (mode.startsWith("r")) {
                    sftp_client_mt.get(getRemotePath(documentId), writeEnd);
                    return readEnd;
                } else if (mode.startsWith("w")) {
                    sftp_client_mt.put(getRemotePath(documentId), readEnd);
                    notifyChange(getParent(documentId));
                    return writeEnd;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        ParcelFileDescriptor o = null;
        return o;
 */

    }
    // END_INCLUDE(open_document)

    // BEGIN_INCLUDE(is_child_document)
    //Test if a document is descendant (child, grandchild, etc) from the given parent.
    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return documentId.startsWith(parentDocumentId);
    }
    // END_INCLUDE(is_child_document)

    // BEGIN_INCLUDE(create_document)
    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        Log.v(TAG, "createDocument");

        getContext().getContentResolver().notifyChange(DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, parentDocumentId), null);

        notifyChange(parentDocumentId);

        return parentDocumentId + "/" + displayName;
    }
    // END_INCLUDE(create_document)

    /*
    // BEGIN_INCLUDE(rename_document)
    @Override
    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        Log.v(TAG, "renameDocument");
        if (displayName == null) {
            throw new FileNotFoundException("Failed to rename document, new name is null");
        }

        // Create the destination file in the same directory as the source file
        File sourceFile = getFileForDocId(documentId);
        File sourceParentFile = sourceFile.getParentFile();
        if (sourceParentFile == null) {
            throw new FileNotFoundException("Failed to rename document. File has no parent.");
        }
        File destFile = new File(sourceParentFile.getPath(), displayName);

        // Try to do the rename
        try {
            boolean renameSucceeded = sourceFile.renameTo(destFile);
            if (!renameSucceeded) {
                throw new FileNotFoundException("Failed to rename document. Renamed failed.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Rename exception : " + e.getLocalizedMessage() + e.getCause());
            throw new FileNotFoundException("Failed to rename document. Error: " + e.getMessage());
        }

        return getDocIdForFile(destFile);
    }
    // END_INCLUDE(rename_document)
*/

    // BEGIN_INCLUDE(delete_document)
    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        Log.v(TAG, "deleteDocument " + documentId);
        int res = sftp_client.rm(getRemotePath(documentId));
        if (res <0) {
            throw new FileNotFoundException(sftp_client.getLastError());
        }

        String parentDocumentId = getParent(documentId);
        notifyChange(parentDocumentId);
    }
    // END_INCLUDE(delete_document)


    // BEGIN_INCLUDE(remove_document)
    @Override
    public void removeDocument(String documentId, String parentDocumentId)
            throws FileNotFoundException {
        Log.v(TAG, "removeDocument " + documentId + " " + parentDocumentId);

        notifyChange(parentDocumentId);
    }
    // END_INCLUDE(remove_document)


    // BEGIN_INCLUDE(copyDocument)
    @Override
    public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        Log.v(TAG, "copyDocument");

        int i = sourceDocumentId.lastIndexOf("/");
        if (i>0) {
            String newDocumentID = targetParentDocumentId + sourceDocumentId.substring(i);

            int res = sftp_client.cp(getRemotePath(sourceDocumentId), getRemotePath(targetParentDocumentId));
            if (res <0) {
                throw new FileNotFoundException(sftp_client.getLastError());
            }

            notifyChange(targetParentDocumentId);

            return newDocumentID;
        }

        throw new FileNotFoundException("Failed to copy document " + sourceDocumentId);

    }
    // END_INCLUDE(copyDocument)


    // BEGIN_INCLUDE(moveDocument)
    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId,
                               String targetParentDocumentId) throws FileNotFoundException {
        Log.v(TAG, "moveDocument");

        String targetDocumentId = targetParentDocumentId +  sourceDocumentId.substring(sourceParentDocumentId.length());

        int res = sftp_client.rename(getRemotePath(sourceDocumentId), getRemotePath(targetDocumentId) );
        if (res<0) {
            Log.e(TAG, "Error renaming" + String.format("%s -> %s\n", sourceDocumentId, targetDocumentId));
            throw new FileNotFoundException("Failed to move document " + sourceDocumentId);
        }

        // sync database
        notifyChange(sourceParentDocumentId);
        notifyChange(targetParentDocumentId);

        return targetDocumentId;
    }
    // END_INCLUDE(moveDocument)

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        String filename = getFilename(documentId);
        return getTypeForName(filename);
    }

    /**
     * @param projection the requested root column projection
     * @return either the requested root column projection, or the default projection if the
     * requested projection is null.
     */
    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    /**
     * Get the MIME data type of a document, given its filename.
     *
     * @param name the filename of the document
     * @return the MIME data type of a document
     */
    private static String getTypeForName(String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    /**
     * Add a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param parentId  the document ID representing the desired file (may be null if given file)
     * @param entry   the string with all the data of the docId)
     * @throws FileNotFoundException
     */
    private void includeFile(MatrixCursor result, String parentId, String entry) {

        String[] fields = entry.split(" ",4);
        String permissions = fields[0];
        long dateTime = Long.parseLong(fields[1]);
        long filesize = Long.parseLong(fields[2]);
        String filename = fields[3];

        //we assume all is writable is writable :)

        int flags = 0;
        String mimeType = "application/octet-stream";
        if (permissions.startsWith("d")) {
            mimeType = Document.MIME_TYPE_DIR;
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (true) { //)(file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
            flags |= Document.FLAG_SUPPORTS_DELETE;

            // Add SDK specific flags if appropriate
            if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                flags |= Document.FLAG_SUPPORTS_RENAME;
            }
            if (SDK_INT >= Build.VERSION_CODES.N) {
                flags |= Document.FLAG_SUPPORTS_REMOVE;
                flags |= Document.FLAG_SUPPORTS_MOVE;
                flags |= Document.FLAG_SUPPORTS_COPY;
            }

            mimeType = getTypeForName(filename);
        }

        //if (mimeType.startsWith("image/")) {
        //    // Allow the image to be represented by a thumbnail rather than an icon
        //    flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        //}

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, parentId +"/" + filename);
        row.add(Document.COLUMN_DISPLAY_NAME, filename);
        row.add(Document.COLUMN_SIZE, filesize);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_LAST_MODIFIED, dateTime* 1000);
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_ICON, R.drawable.ic_launcher);
    }

     /**
     * Placeholder function to determine whether the user is logged in.
     */
    private boolean isUserLoggedIn() {
        final SharedPreferences sharedPreferences =
                getContext().getSharedPreferences(getContext().getString(R.string.app_name),
                        Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(getContext().getString(R.string.key_logged_in), false);
    }
}
