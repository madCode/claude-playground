package com.app.carry.device;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * One folder of session files, served the way a phone's storage serves the folder the owner
 * picks. It lives in the test APK, a package of its own, so it can do what the system picker
 * does: grant Carry a persistable read and write grant on the tree. Carry then holds it exactly
 * as it holds a picked folder, through {@code takePersistableUriPermission}.
 *
 * <p>Java, not Kotlin: Android runs these providers in the test APK's own process, which has no
 * Kotlin runtime (the test APK leaves it to the app, whose process the tests run in).
 *
 * <p>Driven through {@link Control}, since Carry's process can't reach this provider before it
 * holds the grant. Nothing outside a device test reaches either.
 */
public class TestFolder extends DocumentsProvider {
    public static final String AUTHORITY = "com.app.carry.device.folder";
    private static final String CONTROL = "com.app.carry.device.control";
    private static final String ROOT = "gym";
    private static final String GRANT = "grant";
    private static final String PUT = "put";
    private static final String CLEAR = "clear";
    private static final String TEXT = "text";
    private static final String[] COLUMNS = {
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
    };

    public static Uri tree() {
        return DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT);
    }

    /** Empties the folder and grants it to {@code packageName}, as picking it in the system picker would. */
    public static void grantTo(Context context, String packageName) {
        control(context, CLEAR, "", null);
        control(context, GRANT, packageName, null);
    }

    public static void put(Context context, String name, String text) {
        Bundle extras = new Bundle();
        extras.putString(TEXT, text);
        control(context, PUT, name, extras);
    }

    private static void control(Context context, String method, String arg, Bundle extras) {
        Bundle answer = context.getContentResolver().call(Uri.parse("content://" + CONTROL), method, arg, extras);
        if (answer == null) throw new IllegalStateException("The test folder didn't answer " + method);
    }

    private static File folderIn(Context context) {
        File dir = new File(context.getFilesDir(), ROOT);
        dir.mkdirs();
        return dir;
    }

    private File file(String documentId) throws FileNotFoundException {
        File dir = folderIn(getContext());
        if (documentId.equals(ROOT)) return dir;
        if (!documentId.startsWith(ROOT + "/")) throw new FileNotFoundException("Not in this folder: " + documentId);
        return new File(dir, documentId.substring(ROOT.length() + 1));
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor queryRoots(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[0]);
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        row(cursor, documentId);
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        File[] files = file(parentDocumentId).listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) row(cursor, ROOT + "/" + f.getName());
        }
        return cursor;
    }

    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        return documentId.startsWith(parentDocumentId + "/");
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode));
    }

    private void row(MatrixCursor cursor, String documentId) throws FileNotFoundException {
        File f = file(documentId);
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(Document.COLUMN_DISPLAY_NAME, f.getName());
        row.add(Document.COLUMN_MIME_TYPE, f.isDirectory() ? Document.MIME_TYPE_DIR : "application/json");
        row.add(Document.COLUMN_FLAGS, f.isDirectory() ? 0 : Document.FLAG_SUPPORTS_WRITE);
        row.add(Document.COLUMN_SIZE, f.length());
    }

    /** {@code grant} (to the package in {@code arg}), {@code put} (the file named {@code arg}, with the text in extras), {@code clear}. */
    public static class Control extends ContentProvider {
        @Override public boolean onCreate() {
            return true;
        }

        @Override public Bundle call(String method, String arg, Bundle extras) {
            Context context = getContext();
            File dir = folderIn(context);
            switch (method) {
                case GRANT:
                    context.grantUriPermission(arg, tree(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    break;
                case PUT:
                    try (FileOutputStream out = new FileOutputStream(new File(dir, arg))) {
                        out.write(extras.getString(TEXT).getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    break;
                case CLEAR:
                    File[] files = dir.listFiles();
                    if (files != null) for (File f : files) f.delete();
                    break;
                default:
                    throw new IllegalArgumentException("No such call: " + method);
            }
            return new Bundle();
        }

        @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
            return null;
        }

        @Override public String getType(Uri uri) {
            return null;
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override public int delete(Uri uri, String s, String[] a) {
            return 0;
        }

        @Override public int update(Uri uri, ContentValues v, String s, String[] a) {
            return 0;
        }
    }
}
