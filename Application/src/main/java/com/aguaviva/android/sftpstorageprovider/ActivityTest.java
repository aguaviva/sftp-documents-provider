package com.aguaviva.android.sftpstorageprovider;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

public class ActivityTest  extends FragmentActivity {
    private static final int OPEN_DIRECTORY_REQUEST_CODE = 1;
    private static final int num_downloads = 10;

    ListView simpleList;

    public class Item {
        private String itemName;
        private String itemDescription;

        public Item(String name, String description) {
            this.itemName = name;
            this.itemDescription = description;
        }

        public String getItemName() {
            return this.itemName;
        }

        public String getItemDescription() {
            return itemDescription;
        }
    }

    public class CustomListAdapter extends BaseAdapter {
        private Context context; //context
        private ArrayList<Item> items; //data source of the list adapter

        //public constructor
        public CustomListAdapter(Context context, ArrayList<Item> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size(); //returns total of items in the list
        }

        @Override
        public Object getItem(int position) {
            return items.get(position); //returns list item at the specified position
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // inflate the layout for each list row
            if (convertView == null) {
                convertView = LayoutInflater.from(context).
                        inflate(R.layout.list_item_progress, parent, false);
            }

            // get current item to be displayed
            Item currentItem = (Item) getItem(position);

            // get the TextView for item name and item description
            TextView textViewItemName = (TextView)
                    convertView.findViewById(R.id.text_view_item_name);
            TextView textViewItemDescription = (TextView)
                    convertView.findViewById(R.id.text_view_item_description);

            //sets the text for item name and item description from the current item object
            textViewItemName.setText(currentItem.getItemName());
            textViewItemDescription.setText(currentItem.getItemDescription());

            // returns the view for the current row
            return convertView;
        }
    }

    CustomListAdapter arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        simpleList = (ListView)findViewById(R.id.download_list_view);

        ArrayList<Item> list = new ArrayList<>();
        for (int i = 0; i < num_downloads; i++) {
            list.add(new Item(String.valueOf(i), "desc"));
        }

        arrayAdapter = new CustomListAdapter(this, list);
        simpleList.setAdapter(arrayAdapter);

        Button buttonFilePicker = (Button) findViewById(R.id.file_picker_button);
        buttonFilePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Pick a file"), OPEN_DIRECTORY_REQUEST_CODE);
            }
        });

        //Uri uri = Uri.parse("content://com.aguaviva.android.sftpstorageprovider.documents/document/xchip%2FDocuments%2FIMG_20231208_184425_hdr.jpg");
        //LoadUri(uri);
    }

    public void onActivityResult(int requestCode, int resultCode,Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);

        if (requestCode == OPEN_DIRECTORY_REQUEST_CODE /*&& resultCode ==this.RESULT_OK*/) {
            if (resultData != null) {
                final Uri uri = resultData.getData();
                Log.i("TAG", "Uri: " + uri.toString());
                LoadUri(uri);
            }
        }
    }

    private void LoadUri(Uri uri) {
        for(int i=0;i<10;i++) {
            int finalI = i;
            new Thread("Downloader_" + String.valueOf(finalI)) {
                @Override
                public void run() {
                    try {
                        writeFileContent(finalI, uri, "out_" + String.valueOf(finalI));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }.start();
        }
    }

    private String writeFileContent(int index, final Uri uri, String filename) throws IOException {
        InputStream selectedFileInputStream = getContentResolver().openInputStream(uri);
        if (selectedFileInputStream != null) {
            final File certCacheDir = new File(getFilesDir(), "tmp");
            boolean isCertCacheDirExists = certCacheDir.exists();
            if (!isCertCacheDirExists) {
                isCertCacheDirExists = certCacheDir.mkdirs();
            }
            OutputStream selectedFileOutPutStream = null;
            String filePath = null;
            if (isCertCacheDirExists) {
                filePath = certCacheDir.getAbsolutePath() + "/" + filename;
                selectedFileOutPutStream = new FileOutputStream(filePath);
                byte[] buffer = new byte[64*1024];
                int readBytes = 0;
                int length;

                //int file_length = selectedFileInputStream.available();

                long time_start = 0;
                long time_prev = time_start;
                long sizeInLapse = 0;
                while ((length = selectedFileInputStream.read(buffer)) != -1) {

                    sizeInLapse += length;
                    readBytes += length;

                    selectedFileOutPutStream.write(buffer, 0, length);

                    if (time_start == 0) {
                        time_start = System.currentTimeMillis();
                        time_prev = time_start;

                    } else {

                        long time_curr = System.currentTimeMillis();
                        long delta_time = time_curr - time_prev;
                        if (delta_time > 500 && delta_time > 0) {
                            time_prev = time_curr;
                            float bytesPerSecond = (float) sizeInLapse / ((float) delta_time / 1000.0f);
                            sizeInLapse = 0;
                            float finalTotal = readBytes;
                            float time_total = (time_curr - time_start) / 1000.0f;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Item item = (Item) arrayAdapter.getItem(index);
                                    item.itemName = String.format("%2d)  %10.0f KB %4.0f KB/s  ", index, finalTotal / 1024, bytesPerSecond / 1024);
                                    item.itemDescription = String.format("  time %4.2f s", time_total);

                                    //update view
                                    int visiblePosition = simpleList.getFirstVisiblePosition();
                                    View view = simpleList.getChildAt(index - visiblePosition);
                                    arrayAdapter.getView(index, view, simpleList);
                                }
                            });
                        }
                    }
                }

                selectedFileOutPutStream.flush();
                selectedFileOutPutStream.close();

                {
                    float time_total = (System.currentTimeMillis() - time_start) / 1000.0f;
                    float bytesPerSecond = (float) readBytes / time_total;
                    int finalReadBytes = readBytes;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Item item = (Item) arrayAdapter.getItem(index);
                            item.itemName = String.format("%2d)  %10d KB %4.0f KB/s  DONE", index, finalReadBytes / 1024, bytesPerSecond / 1024.0f);
                            item.itemDescription = String.format("  time %4.2f s", time_total);

                            //update view
                            int visiblePosition = simpleList.getFirstVisiblePosition();
                            View view = simpleList.getChildAt(index - visiblePosition);
                            arrayAdapter.getView(index, view, simpleList);
                        }
                    });
                }
                //delete file
                File f = new File(filePath);
                f.delete();
            }
        }
        selectedFileInputStream.close();
        return null;
    }

}
