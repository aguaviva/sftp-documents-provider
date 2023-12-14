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
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import com.example.android.common.logger.Log;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages documents and exposes them to the Android system for sharing.
 */
public class MyCloudProvider extends DocumentsProvider {
    private static final String TAG = "MyCloudProvider";

    public static final String AUTHORITY = "com.aguaviva.android.sftpstorageprovider.documents";

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

    // No official policy on how many to return, but make sure you do limit the number of recent
    // and search results.
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_LAST_MODIFIED = 5;

    private static final String ROOT = "root";

    private Pattern pattern = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d yyyy HH:mm");


    // A file object at the root of the file hierarchy.  Depending on your implementation, the root
    // does not need to be an existing file system directory.  For example, a tag-based document
    // provider might return a directory containing all tags, represented as child directories.

    String currentConnectionName = "";
    private SFTP sftp_client = null;
    private SFTPMT sftp_client_mt = null;

    @Override
    public boolean onCreate() {
        Log.v(TAG, "onCreate");

        helpers.Init(getContext());

        Ssh2.init_ssh();

        if (sftp_client==null)
            sftp_client = new SFTP_retry();

        if (sftp_client_mt==null)
            sftp_client_mt = new SFTPMT();

        return true;
    }

    private boolean connect_if_necessary(String connectionId)
    {
        synchronized (sftp_client_mt) {

            String connectionName = getConnectionName(connectionId);

            if (currentConnectionName.startsWith(connectionName) == false) {
                String hostname, username, root, pubKeyFilename, privKeyFilename;
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
                sftp_client_mt.Init(connection);

                sftp_client.Init(connection);

                Log.i(TAG, String.format("Connecting End"));

                currentConnectionName = connectionId;
            }
        }
        return true;
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

            final MatrixCursor.RowBuilder row = result.newRow();
            row.add(Root.COLUMN_ROOT_ID, ROOT);
            row.add(Root.COLUMN_TITLE, getContext().getString(R.string.app_name));
            row.add(Root.COLUMN_SUMMARY, connections[i].getName());
            row.add(Root.COLUMN_DOCUMENT_ID, connections[i].getName());
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
        return result;
    }
    // END_INCLUDE(query_roots)

    private String getPath(String documentId) {
        int l = documentId.indexOf("/");
        if (l>=0) {
            String remotePath = documentId.substring(l);
            assert(documentId.startsWith(currentConnectionName));
            return remotePath;
        }

        return "";
    }

    private String getConnectionName(String documentId) {
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


    // BEGIN_INCLUDE(query_document)
    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {

        synchronized(this) {
            Log.v(TAG, "Begin queryDocument: " + documentId);
            /*
            if (projection != null) {
                for (String s : projection) {
                    Log.v(TAG, s);
                }
            }
            */

            MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

            if (currentConnectionName.equals("") || currentConnectionName.startsWith(currentConnectionName)==false) {

                // if there is no connection, let's return something, connect in a thread and once there notify

                Log.v(TAG, "Waiting for connection....");

                final MatrixCursor.RowBuilder row = result.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, documentId);
                row.add(Document.COLUMN_DISPLAY_NAME, documentId);
                row.add(Document.COLUMN_MIME_TYPE, "application/octet-stream");
                row.add(Document.COLUMN_ICON, R.drawable.ic_launcher);

                Thread thread = new Thread(){
                    public void run(){
                        connect_if_necessary(documentId);
                        getContext().getContentResolver().notifyChange(DocumentsContract.buildDocumentUri(AUTHORITY, documentId), null);
                    }
                };
                thread.start();

            } else {
                includeId(result, documentId);
            }

            Log.v(TAG, "End queryDocument");
            return result;
        }
    }
    // END_INCLUDE(query_document)

    // BEGIN_INCLUDE(query_child_documents)
    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                      String sortOrder) throws FileNotFoundException {

        synchronized(this) {
            Log.v(TAG, "Begin queryChildDocuments");
            Log.v(TAG, " parentDocumentId: " + parentDocumentId);
            Log.v(TAG, " sortOrder: " + sortOrder);

            final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

            if (currentConnectionName.equals("") || currentConnectionName.startsWith(currentConnectionName)==false) {
                connect_if_necessary(parentDocumentId);
            }


            int res = sftp_client.ls(getPath(parentDocumentId), new SFTP.onGetFileListener() {
                    @Override
                    public boolean listen(String entry) {
                        if (entry.endsWith(" .") || entry.endsWith(" ..")) {

                        } else {
                            includeFile(result, parentDocumentId, null, entry);
                        }
                        return true;
                    }
                });
            if (res<0)
                throw new FileNotFoundException();

            result.setNotificationUri(getContext().getContentResolver(),
                    DocumentsContract.buildDocumentUri(AUTHORITY, parentDocumentId));

            Log.v(TAG, "End queryChildDocuments ");
            return result;
        }

    }
    // END_INCLUDE(query_child_documents)


    // BEGIN_INCLUDE(open_document)
    @Override
    public ParcelFileDescriptor openDocument(final String documentId, final String mode, CancellationSignal signal)
            throws FileNotFoundException {

        Log.v(TAG, "openDocument, mode: " + mode);

        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            final ParcelFileDescriptor readEnd = pipe[0];
            final ParcelFileDescriptor writeEnd = pipe[1];

             synchronized (this) {
                if (mode.startsWith("r")) {
                    sftp_client_mt.get(getPath(documentId), writeEnd);
                    return readEnd;
                } else if (mode.startsWith("w")) {
                    sftp_client_mt.put(getPath(documentId), readEnd);
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

        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri(AUTHORITY, parentDocumentId), null);

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
        Log.v(TAG, "deleteDocument");
        int res = sftp_client.rm(getPath(documentId));
        if (res <0) {
            throw new FileNotFoundException(sftp_client.getLastError());
        }

        String parentId = getParent(documentId);
        Uri notifyUri = DocumentsContract.buildDocumentUri(AUTHORITY, parentId);
        getContext().getContentResolver().notifyChange(notifyUri, null);
    }
    // END_INCLUDE(delete_document)


    // BEGIN_INCLUDE(remove_document)
    @Override
    public void removeDocument(String documentId, String parentDocumentId)
            throws FileNotFoundException {
        Log.v(TAG, "removeDocument");
        deleteDocument(documentId);
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

            int res = sftp_client.cp(getPath(sourceDocumentId), getPath(targetParentDocumentId));
            if (res <0) {
                throw new FileNotFoundException(sftp_client.getLastError());
            }

            Uri notifyUri = DocumentsContract.buildDocumentUri(AUTHORITY, targetParentDocumentId);
            getContext().getContentResolver().notifyChange(notifyUri, null);

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

        int res = sftp_client.rename(getPath(sourceDocumentId), getPath(targetDocumentId) );
        if (res<0) {
            Log.e(TAG, "Error renaming" + String.format("%s -> %s\n", sourceDocumentId, targetDocumentId));
            throw new FileNotFoundException("Failed to move document " + sourceDocumentId);
        }

        // sync database
        /*
        String parentSourceDocumentId = getParent(sourceDocumentId);
        Uri sourceUri = DocumentsContract.buildDocumentUri(AUTHORITY, parentSourceDocumentId);
        getContext().getContentResolver().notifyChange(sourceUri, null);
        String parentTargetDocumentId = getParent(targetDocumentId);
        Uri targetUri = DocumentsContract.buildDocumentUri(AUTHORITY, parentTargetDocumentId);
        getContext().getContentResolver().notifyChange(targetUri, null);
        */
        return targetDocumentId;
    }
    // END_INCLUDE(moveDocument)

/*
    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        return getTypeForFile(file);
    }
*/
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

    private void includeId(MatrixCursor result, String docId) throws FileNotFoundException
    {
        String path, filename;

        String permissions  = sftp_client.stat(getPath(docId));
        if (permissions.startsWith("*")) {
            Log.e(TAG, String.format("get_permissions %s: %s\n", docId, permissions));
            Log.e(TAG, "Error " + sftp_client.getLastError());
            return;
        }

        boolean is_directory = permissions.startsWith("d") | permissions.startsWith("l");
        if (is_directory) {
            path = docId;
            int i = docId.lastIndexOf("/");
            if (i>=0)
                filename = docId.substring(i+1);
            else
                filename = "";
        } else {
            int i = docId.lastIndexOf("/");
            path = docId.substring(0,i);
            filename = docId.substring(i+1);
        }

        int res = sftp_client.ls(getPath(path), new SFTP.onGetFileListener() {
            @Override
            public boolean listen(String entry) {
                if (is_directory) {
                    if (entry.endsWith(" .")) {
                        Log.v(TAG, entry);
                        // replace . with directory name
                        includeFile(result, path, filename, entry);
                        return false;
                    }
                } else if (entry.endsWith(" " + filename)) {
                    Log.v(TAG, entry);
                    includeFile(result, path, filename, entry);
                    return false;
                }
                return true;
            }
        });
        if (res<0)
            throw new FileNotFoundException();

    }


    /**
     * Add a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param docId  the document ID representing the desired file (may be null if given file)
     * @param file   the File object representing the desired file (may be null if given docID)
     * @throws FileNotFoundException
     */
    private void includeFile(MatrixCursor result, String path, String displayName, String entry) {

        Matcher m = pattern.matcher(entry);
        if (m.matches()==false)
            Log.e(TAG, String.format("cant match `%s`", entry));

        String permissions = m.group(1);
        String blocks = m.group(2);
        String user = m.group(3);
        String group = m.group(4);

        //parse size
        long  file_size = 0;
        try {
            String s = m.group(5);
            file_size = Long.parseLong(s);
        } catch (NumberFormatException e) {
            Log.e(TAG, "cant parse " + m.group(5));
        }

        String file_name = m.group(9);

        if (displayName==null)
            displayName = file_name;

        String docId;
        if (file_name.equals(".")) {
            docId = path;
        }
        else {
            docId = path + "/" + file_name;
        }

        // get last access time and real permissions
        long dateTime = 0;
        //if (permissions.startsWith("l"))
        {
            String str[]  = sftp_client.stat(getPath(docId)).split(" ");
            permissions = str[0];
            dateTime = Long.parseLong(str[1]);
        }

        int flags = 0;
        String mimeType = "application/octet-stream";
        if (permissions.startsWith("d")) {
            mimeType = Document.MIME_TYPE_DIR;

            // Add FLAG_DIR_SUPPORTS_CREATE if the file is a writable directory.
            //if (file.isDirectory() && file.canWrite()) {
            //    flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
            //}
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (true) { //)(file.canWrite()) {
            // If the file is writable set FLAG_SUPPORTS_WRITE and
            // FLAG_SUPPORTS_DELETE
            //flags |= Document.FLAG_SUPPORTS_WRITE;
            //flags |= Document.FLAG_SUPPORTS_DELETE;

            // Add SDK specific flags if appropriate
            if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                flags |= Document.FLAG_SUPPORTS_RENAME;
            }
            if (SDK_INT >= Build.VERSION_CODES.N) {
                //flags |= Document.FLAG_SUPPORTS_REMOVE;
                flags |= Document.FLAG_SUPPORTS_MOVE;
                flags |= Document.FLAG_SUPPORTS_COPY;
            }

            mimeType = getTypeForName(docId);
        }

        //if (mimeType.startsWith("image/")) {
        //    // Allow the image to be represented by a thumbnail rather than an icon
        //    flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        //}

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file_size);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        if (dateTime>0)
            row.add(Document.COLUMN_LAST_MODIFIED, dateTime* 1000);
        row.add(Document.COLUMN_FLAGS, flags);

        // Add a custom icon
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
