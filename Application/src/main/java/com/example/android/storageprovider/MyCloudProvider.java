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


package com.example.android.storageprovider;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.example.android.common.logger.Log;

import org.json.JSONException;
import org.json.JSONObject;

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

    SimpleDateFormat [] formatter = new SimpleDateFormat[]{new SimpleDateFormat("MMM d HH:mm"), new SimpleDateFormat("MMM d yyyy")};


    // A file object at the root of the file hierarchy.  Depending on your implementation, the root
    // does not need to be an existing file system directory.  For example, a tag-based document
    // provider might return a directory containing all tags, represented as child directories.

    String currentConnectionName = "";
    private SFTP sftp_client = null;
    private SFTPMT sftp_client_mt = null;

    @Override
    public boolean onCreate() {
        Log.v(TAG, "onCreate");

        Ssh2.init_ssh();

        if (sftp_client!=null)
            sftp_client = new SFTP(getContext());

        if (sftp_client_mt!=null)
            sftp_client_mt = new SFTPMT(getContext());

        return true;
    }

    private boolean connect_if_necessary(String connectionName)
    {
        synchronized (sftp_client_mt) {
            if (currentConnectionName.equals(connectionName) == false) {
                String hostname, username, root, pubKeyFilename, privKeyFilename;
                int port = -1;
                Log.i(TAG, String.format("Connecting Begin "));
                try {
                    String connJson = helpers.loadConnection(connectionName);
                    JSONObject jsonObject = new JSONObject(connJson);
                    hostname = jsonObject.get("hostname").toString();
                    username = jsonObject.get("username").toString();
                    port = jsonObject.getInt("port");
                    root = jsonObject.get("root").toString();

                    String keyname = jsonObject.get("keyname").toString();
                    pubKeyFilename = getContext().getFilesDir() + helpers.GetPublicKeyPath(keyname);
                    privKeyFilename = getContext().getFilesDir() + helpers.GetPrivateKeyPath(keyname);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                String hostip = null;
                try {
                    hostip = InetAddress.getByName(hostname).getHostAddress();
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }

                sftp_client_mt.Init(hostip, port, username, pubKeyFilename, privKeyFilename, root);

                sftp_client.Init(hostip, port, username, pubKeyFilename, privKeyFilename, root);

                Log.i(TAG, String.format("Connecting End"));

                currentConnectionName = connectionName;
            }
        }
        return true;
    }

    // BEGIN_INCLUDE(query_roots)
    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        Log.v(TAG, "queryRoots");

        // Create a cursor with either the requested fields, or the default projection.  This
        // cursor is returned to the Android system picker UI and used to display all roots from
        // this provider.
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));

        // If user is not logged in, return an empty root cursor.  This removes our provider from
        // the list entirely.
        if (!isUserLoggedIn()) {
            return result;
        }

        // It's possible to have multiple roots (e.g. for multiple accounts in the same app) -
        // just add multiple cursor rows.
        // Construct one row for a root called "MyCloud".

        File[] connections = helpers.getFilesConnections();
        for(int i=0;i<connections.length;i++) {
            final MatrixCursor.RowBuilder row = result.newRow();

            row.add(Root.COLUMN_ROOT_ID, ROOT);

            // FLAG_SUPPORTS_CREATE means at least one directory under the root supports creating
            // documents.  FLAG_SUPPORTS_RECENTS means your application's most recently used
            // documents will show up in the "Recents" category.  FLAG_SUPPORTS_SEARCH allows users
            // to search all documents the application shares. FLAG_SUPPORTS_IS_CHILD allows
            // testing parent child relationships, available after SDK 21 (Lollipop).
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

            // COLUMN_TITLE is the root title (e.g. what will be displayed to identify your provider).
            row.add(Root.COLUMN_TITLE, getContext().getString(R.string.app_name));
            row.add(Root.COLUMN_SUMMARY, connections[i].getName());

            // This document id must be unique within this provider and consistent across time.  The
            // system picker UI may save it and refer to it later.
            row.add(Root.COLUMN_DOCUMENT_ID, connections[i].getName());

            // The child MIME types are used to filter the roots and only present to the user roots
            // that contain the desired type somewhere in their file hierarchy.
            //row.add(Root.COLUMN_MIME_TYPES, getChildMimeTypes(mBaseDir));
            //row.add(Root.COLUMN_AVAILABLE_BYTES, mBaseDir.getFreeSpace());
            row.add(Root.COLUMN_ICON, R.drawable.ic_launcher);
        }

        return result;
    }
    // END_INCLUDE(query_roots)

    private String fixPath(String documentId) {
        int l = currentConnectionName.length();
        String remotePath = documentId.substring(l);
        return remotePath;
    }

    // BEGIN_INCLUDE(query_document)
    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {

        synchronized(this) {
            Log.v(TAG, "Begin queryDocument: " + documentId);
            if (projection != null) {
                for (String s : projection) {
                    Log.v(TAG, s);
                }
            }

            if (currentConnectionName.equals("") || currentConnectionName.startsWith(currentConnectionName)==false)
                connect_if_necessary(documentId);

            MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
            includeId(result, documentId);

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
                //final File parent = getFileForDocId(parentDocumentId);
                //for (File file : parent.listFiles()) {
                //    includeFile(result, null, file);
                //}

            try {
                sftp_client.ls(fixPath(parentDocumentId), new SFTP.Listener() {
                    @Override
                    public boolean listen(String entry) {
                        if (entry.endsWith(" .") || entry.endsWith(" ..")) {

                        } else {
                            includeFile(result, parentDocumentId, null, entry);
                        }
                        return true;
                    }
                });
            } catch(Exception e) {
                Log.e(TAG, e.getMessage());
            }

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
                    sftp_client_mt.get(fixPath(documentId), writeEnd);
                    return readEnd;
                } else if (mode.startsWith("w")) {
                    sftp_client_mt.put(fixPath(documentId), readEnd);
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
        //Log.v(TAG, "isChildDocument");

        boolean is_child = documentId.startsWith(parentDocumentId+"/");

        //Log.v(TAG, String.format("%s %s %b", parentDocumentId, documentId, is_child));

        if (is_child)
        {
            return true;
        }

        return false;
    }
    // END_INCLUDE(is_child_document)
    // BEGIN_INCLUDE(create_document)
    @Override
    public String createDocument(String documentId, String mimeType, String displayName)
            throws FileNotFoundException {
        Log.v(TAG, "createDocument");
/*
        File parent = getFileForDocId(documentId);
        File file = new File(parent.getPath(), displayName);
        try {
            // Create the new File to copy into
            boolean wasNewFileCreated = false;
            if (file.createNewFile()) {
                if (file.setWritable(true) && file.setReadable(true)) {
                    wasNewFileCreated = true;
                }
            }

            if (!wasNewFileCreated) {
                throw new FileNotFoundException("Failed to create document with name " +
                        displayName +" and documentId " + documentId);
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to create document with name " +
                    displayName +" and documentId " + documentId);
        }
        return getDocIdForFile(file);

 */
        return documentId + "/" + displayName;
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
/*
    // BEGIN_INCLUDE(delete_document)
    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        Log.v(TAG, "deleteDocument");
        File file = getFileForDocId(documentId);
        if (file.delete()) {
            Log.i(TAG, "Deleted file with id " + documentId);
        } else {
            throw new FileNotFoundException("Failed to delete document with id " + documentId);
        }
    }
    // END_INCLUDE(delete_document)
*/
/*
    // BEGIN_INCLUDE(remove_document)
    @Override
    public void removeDocument(String documentId, String parentDocumentId)
            throws FileNotFoundException {
        Log.v(TAG, "removeDocument");
        File parent = getFileForDocId(parentDocumentId);
        File file = getFileForDocId(documentId);

        if (file == null) {
            throw new FileNotFoundException("Failed to delete document with id " + documentId);
        }

        // removeDocument is the same as deleteDocument but allows the parent to be specified
        // Check here if the specified parentDocumentId matches the true parent of documentId
        boolean doesFileParentMatch = false;
        File fileParent = file.getParentFile();

        if (fileParent == null || fileParent.equals(parent)) {
            doesFileParentMatch = true;
        }

        // Remove the file if parent matches or file and parent are equal
        if (parent.equals(file) || doesFileParentMatch) {
            if (file.delete()) {
                Log.i(TAG, "Deleted file with id " + documentId);
            } else {
                throw new FileNotFoundException("Failed to delete document with id " + documentId);
            }
        } else {
            throw new FileNotFoundException("Failed to delete document with id " + documentId);
        }
    }
    // END_INCLUDE(remove_document)
*/

    // BEGIN_INCLUDE(copyDocument)
    @Override
    public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        Log.v(TAG, "copyDocument");

        int i = sourceDocumentId.lastIndexOf("/");
        if (i>0) {
            String newDocumentID = targetParentDocumentId + sourceDocumentId.substring(i);

            String command = String.format("command %s %s", fixPath(sourceDocumentId), fixPath(targetParentDocumentId));
            int res = sftp_client.exec(command);
            if (res < 0) {
                throw new FileNotFoundException("Failed to exec " + command + " " + sftp_client.getLastError() );
            }
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

        int res = sftp_client.rename(fixPath(sourceDocumentId), fixPath(targetDocumentId) );
        if (res<0) {
            Log.e(TAG, "Error renaming" + String.format("%s -> %s\n", sourceDocumentId, targetDocumentId));
            throw new FileNotFoundException("Failed to move document " + sourceDocumentId);
        }

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

        String permissions  = sftp_client.stat(fixPath(docId));
        if (permissions.startsWith("*")) {
            Log.e(TAG, "*********************" + String.format("get_permissions: %s\n", docId));
            Log.e(TAG, "*********************" + sftp_client.getLastError());
            Log.e(TAG, "*********************" + permissions);
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

        try {
            sftp_client.ls(fixPath(path), new SFTP.Listener() {
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
        } catch(Exception e) {
            Log.e(TAG, e.getMessage());
        }

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
        if (permissions.startsWith("l") || permissions.startsWith("-") ) {
            String str[]  = sftp_client.stat(fixPath(docId)).split(" ");
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
