package com.aguaviva.android.sftpstorageprovider;

import android.os.ParcelFileDescriptor;

import com.example.android.common.logger.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class SFTPMT {

    private static final String TAG = "MyCloudProvider-queue";
    private static final int NUM_THREADS = 5;
    private static final int TASK_PUT = 1;
    private static final int TASK_GET = 2;

    protected class SftpTask
    {
        public int type;
        public String documentId;
        public ParcelFileDescriptor parcelFileDescriptor;
        SftpTask(int type, String documentId, ParcelFileDescriptor parcelFileDescriptor)
        {
            this.type = type;
            this.documentId = documentId;
            this.parcelFileDescriptor = parcelFileDescriptor;
        }
    };

    private BlockingQueue<SftpTask> arrayQueue = new ArrayBlockingQueue<>(10);

    private static class TaskConsumer implements Runnable {

        int id = -1;
        private SFTP sftp;
        private final BlockingQueue<?> queue;
        private volatile boolean isRunning = true;

        public TaskConsumer(int id, SFTP sftp, BlockingQueue<?> queue) {
            this.id = id;
            this.queue = queue;
            this.sftp = sftp;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    SftpTask element = (SftpTask) queue.take();

                    String op = (element.type== TASK_GET)?"get":"put";

                    Log.i(TAG, String.format("w %d begin %s: %s", id, op, element.documentId));

                    if (element.type== TASK_GET)
                        sftp.get(element.documentId, element.parcelFileDescriptor, null);
                    else if (element.type== TASK_PUT)
                        sftp.put(element.documentId, element.parcelFileDescriptor, null);
                    else
                        Log.e(TAG, String.format("w %d unknown task type  %d", id, element.type));

                    Log.i(TAG, String.format("w %d end %s: %s", id, op, element.documentId));

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public SFTPMT() {
    }

    public boolean Init(String hostname, int port, String username, String pubKeyFilename, String privKeyFilename, String root) {

        // open connections in parallel
        Thread threads[] = new Thread[NUM_THREADS];
        for (int i = 0; i < NUM_THREADS; i++) {
            final int finalI = i;
            threads[finalI] = new Thread() {
                @Override
                public void run() {
                    SFTP sftp = new SFTP();
                    sftp.Init(hostname, port, username, pubKeyFilename, privKeyFilename, root);

                    Thread worker = new Thread(new TaskConsumer(finalI, sftp, arrayQueue));
                    worker.start();
                }
            };
            threads[finalI].start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch(InterruptedException e)
        {
            return false;
        }

        return true;
    }

    public void get(String documentId, ParcelFileDescriptor parcelFileDescriptor) throws InterruptedException {
        arrayQueue.put(new SftpTask(TASK_GET, documentId, parcelFileDescriptor));
    }

    public void put(String documentId, ParcelFileDescriptor parcelFileDescriptor) throws InterruptedException {
        arrayQueue.put(new SftpTask(TASK_PUT, documentId, parcelFileDescriptor));
    }

}
