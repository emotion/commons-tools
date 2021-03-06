/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.emotion.commons.tailer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.Charset;

import static org.apache.commons.io.IOUtils.EOF;

/**
 * Simple implementation of the unix "tail -f" functionality.
 * 
 * <h2>1. Create a BytesTailerListener implementation</h2>
 * <p>
 * First you need to create a {@link BytesTailerListener} implementation
 * ({@link BytesTailerListenerAdapter} is provided for convenience so that you don't have to
 * implement every method).
 * </p>
 *
 * <p>For example:</p>
 * <pre>
 *  public class MyTailerListener extends BytesTailerListenerAdapter {
 *      public void handle(String line) {
 *          System.out.println(line);
 *      }
 *  }</pre>
 *
 * <h2>2. Using a BytesTailer</h2>
 *
 * <p>
 * You can create and use a BytesTailer in one of three ways:
 * </p>
 * <ul>
 *   <li>Using one of the static helper methods:
 *     <ul>
 *       <li>{@link BytesTailer#create(File, BytesTailerListener)}</li>
 *       <li>{@link BytesTailer#create(File, BytesTailerListener, long)}</li>
 *       <li>{@link BytesTailer#create(File, BytesTailerListener, long, boolean)}</li>
 *     </ul>
 *   </li>
 *   <li>Using an {@link java.util.concurrent.Executor}</li>
 *   <li>Using an {@link Thread}</li>
 * </ul>
 *
 * <p>
 * An example of each of these is shown below.
 * </p>
 *
 * <h3>2.1 Using the static helper method</h3>
 *
 * <pre>
 *      BytesTailerListener listener = new MyTailerListener();
 *      BytesTailer tailer = BytesTailer.create(file, listener, delay);</pre>
 *
 * <h3>2.2 Using an Executor</h3>
 *
 * <pre>
 *      BytesTailerListener listener = new MyTailerListener();
 *      BytesTailer tailer = new BytesTailer(file, listener, delay);
 *
 *      // stupid executor impl. for demo purposes
 *      Executor executor = new Executor() {
 *          public void execute(Runnable command) {
 *              command.run();
 *           }
 *      };
 *
 *      executor.execute(tailer);
 * </pre>
 *
 *
 * <h3>2.3 Using a Thread</h3>
 * <pre>
 *      BytesTailerListener listener = new MyTailerListener();
 *      BytesTailer tailer = new BytesTailer(file, listener, delay);
 *      Thread thread = new Thread(tailer);
 *      thread.setDaemon(true); // optional
 *      thread.start();</pre>
 *
 * <h2>3. Stopping a BytesTailer</h2>
 * <p>Remember to stop the tailer when you have done with it:</p>
 * <pre>
 *      tailer.stop();
 * </pre>
 *
 * <h2>4. Interrupting a BytesTailer</h2>
 * <p>You can interrupt the thread a tailer is running on by calling {@link Thread#interrupt()}.</p>
 * <pre>
 *      thread.interrupt();
 * </pre>
 * <p>If you interrupt a tailer, the tailer listener is called with the {@link InterruptedException}.</p>
 *
 * <p>The file is read using the default charset; this can be overriden if necessary</p>
 * @see BytesTailerListener
 * @see BytesTailerListenerAdapter
 * @version $Id: BytesTailer.java 1714076 2015-11-12 16:06:41Z krosenvold $
 * @since 2.0
 * @since 2.5 Updated behavior and documentation for {@link Thread#interrupt()}
 */
public class BytesTailer implements Runnable {

    private static final int DEFAULT_DELAY_MILLIS = 1000;

    private static final String RAF_MODE = "r";

    private static final int DEFAULT_BUFSIZE = 4096;

    // The default charset used for reading files
    private static final Charset DEFAULT_CHARSET = Charset.defaultCharset();

    /**
     * Buffer on top of RandomAccessFile.
     */
    private final byte inbuf[];

    /**
     * The file which will be tailed.
     */
    private final File file;

    /**
     * The character set that will be used to read the file.
     */
    private final Charset cset;

    /**
     * The amount of time to wait for the file to be updated.
     */
    private final long delayMillis;

    /**
     * Whether to tail from the end or start of file
     */
    private final boolean end;

    /**
     * The listener to notify of events when tailing.
     */
    private final BytesTailerListener listener;

    /**
     * Whether to close and reopen the file whilst waiting for more input.
     */
    private final boolean reOpen;

    /**
     * The tailer will run as long as this value is true.
     */
    private volatile boolean run = true;

    /**
     * Creates a BytesTailer for the given file, starting from the beginning, with the default delay of 1.0s.
     * @param file The file to follow.
     * @param listener the BytesTailerListener to use.
     */
    public BytesTailer(final File file, final BytesTailerListener listener) {
        this(file, listener, DEFAULT_DELAY_MILLIS);
    }

    /**
     * Creates a BytesTailer for the given file, starting from the beginning.
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     */
    public BytesTailer(final File file, final BytesTailerListener listener, final long delayMillis) {
        this(file, listener, delayMillis, false);
    }

    /**
     * Creates a BytesTailer for the given file, with a delay other than the default 1.0s.
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     */
    public BytesTailer(final File file, final BytesTailerListener listener, final long delayMillis, final boolean end) {
        this(file, listener, delayMillis, end, DEFAULT_BUFSIZE);
    }

    /**
     * Creates a BytesTailer for the given file, with a delay other than the default 1.0s.
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param reOpen if true, close and reopen the file between reading chunks
     */
    public BytesTailer(final File file, final BytesTailerListener listener, final long delayMillis, final boolean end,
                       final boolean reOpen) {
        this(file, listener, delayMillis, end, reOpen, DEFAULT_BUFSIZE);
    }

    /**
     * Creates a BytesTailer for the given file, with a specified buffer size.
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param bufSize Buffer size
     */
    public BytesTailer(final File file, final BytesTailerListener listener, final long delayMillis, final boolean end,
                       final int bufSize) {
        this(file, listener, delayMillis, end, false, bufSize);
    }

    /**
     * Creates a BytesTailer for the given file, with a specified buffer size.
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param reOpen if true, close and reopen the file between reading chunks
     * @param bufSize Buffer size
     */
    public BytesTailer(final File file, final BytesTailerListener listener, final long delayMillis, final boolean end,
                       final boolean reOpen, final int bufSize) {
        this(file, DEFAULT_CHARSET, listener, delayMillis, end, reOpen, bufSize);
    }

    /**
     * Creates a BytesTailer for the given file, with a specified buffer size.
     * @param file the file to follow.
     * @param cset the Charset to be used for reading the file
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param reOpen if true, close and reopen the file between reading chunks
     * @param bufSize Buffer size
     */
    public BytesTailer(final File file, final Charset cset, final BytesTailerListener listener, final long delayMillis,
                       final boolean end, final boolean reOpen
            , final int bufSize) {
        this.file = file;
        this.delayMillis = delayMillis;
        this.end = end;

        this.inbuf = new byte[bufSize];

        // Save and prepare the listener
        this.listener = listener;
        listener.init(this);
        this.reOpen = reOpen;
        this.cset = cset; 
    }

    /**
     * Creates and starts a BytesTailer for the given file.
     *
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param bufSize buffer size.
     * @return The new tailer
     */
    public static BytesTailer create(final File file, final BytesTailerListener listener, final long delayMillis,
                                     final boolean end, final int bufSize) {
        return create(file, listener, delayMillis, end, false, bufSize);
    }

    /**
     * Creates and starts a BytesTailer for the given file.
     *
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param reOpen whether to close/reopen the file between chunks
     * @param bufSize buffer size.
     * @return The new tailer
     */
    public static BytesTailer create(final File file, final BytesTailerListener listener, final long delayMillis,
                                     final boolean end, final boolean reOpen,
                                     final int bufSize) {
        return create(file, DEFAULT_CHARSET, listener, delayMillis, end, reOpen, bufSize);
    }

    /**
     * Creates and starts a BytesTailer for the given file.
     *
     * @param file the file to follow.
     * @param charset the character set to use for reading the file
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param reOpen whether to close/reopen the file between chunks
     * @param bufSize buffer size.
     * @return The new tailer
     */
    public static BytesTailer create(final File file, final Charset charset, final BytesTailerListener listener,
                                     final long delayMillis, final boolean end, final boolean reOpen
            , final int bufSize) {
        final BytesTailer bytesTailer = new BytesTailer(file, charset, listener, delayMillis, end, reOpen, bufSize);
        final Thread thread = new Thread(bytesTailer);
        thread.setDaemon(true);
        thread.start();
        return bytesTailer;
    }

    /**
     * Creates and starts a BytesTailer for the given file with default buffer size.
     *
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @return The new tailer
     */
    public static BytesTailer create(final File file, final BytesTailerListener listener, final long delayMillis,
                                     final boolean end) {
        return create(file, listener, delayMillis, end, DEFAULT_BUFSIZE);
    }

    /**
     * Creates and starts a BytesTailer for the given file with default buffer size.
     *
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param reOpen whether to close/reopen the file between chunks
     * @return The new tailer
     */
    public static BytesTailer create(final File file, final BytesTailerListener listener, final long delayMillis,
                                     final boolean end, final boolean reOpen) {
        return create(file, listener, delayMillis, end, reOpen, DEFAULT_BUFSIZE);
    }

    /**
     * Creates and starts a BytesTailer for the given file, starting at the beginning of the file
     *
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @param delayMillis the delay between checks of the file for new content in milliseconds.
     * @return The new tailer
     */
    public static BytesTailer create(final File file, final BytesTailerListener listener, final long delayMillis) {
        return create(file, listener, delayMillis, false);
    }

    /**
     * Creates and starts a BytesTailer for the given file, starting at the beginning of the file
     * with the default delay of 1.0s
     *
     * @param file the file to follow.
     * @param listener the BytesTailerListener to use.
     * @return The new tailer
     */
    public static BytesTailer create(final File file, final BytesTailerListener listener) {
        return create(file, listener, DEFAULT_DELAY_MILLIS, false);
    }

    /**
     * Return the file.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets whether to keep on running.
     *
     * @return whether to keep on running.
     * @since 2.5
     */
    protected boolean getRun() {
        return run;
    }

    /**
     * Return the delay in milliseconds.
     *
     * @return the delay in milliseconds.
     */
    public long getDelay() {
        return delayMillis;
    }

    /**
     * Follows changes in the file, calling the BytesTailerListener's handle method for each new line.
     */
    public void run() {
        RandomAccessFile reader = null;
        try {
            long last = 0; // The last time the file was checked for changes
            long position = 0; // position within the file
            // Open the file
            while (getRun() && reader == null) {
                try {
                    reader = new RandomAccessFile(file, RAF_MODE);
                } catch (final FileNotFoundException e) {
                    listener.fileNotFound();
                }
                if (reader == null) {
                    Thread.sleep(delayMillis);
                } else {
                    // The current position in the file
                    position = end ? file.length() : 0;
                    last = file.lastModified();
                    reader.seek(position);
                }
            }
            while (getRun()) {
                final boolean newer = FileUtils.isFileNewer(file, last); // IO-279, must be done first
                // Check the file length to see if it was rotated
                final long length = file.length();
                if (length < position) {
                    // File was rotated
                    listener.fileRotated();
                    // Reopen the reader after rotation
                    try {
                        // Ensure that the old file is closed iff we re-open it successfully
                        final RandomAccessFile save = reader;
                        reader = new RandomAccessFile(file, RAF_MODE);
                        // At this point, we're sure that the old file is rotated
                        // Finish scanning the old file and then we'll start with the new one
                        try {
                            readLines(save);
                        }  catch (IOException ioe) {
                            listener.handle(ioe);
                        }
                        position = 0;
                        // close old file explicitly rather than relying on GC picking up previous RAF
                        IOUtils.closeQuietly(save);
                    } catch (final FileNotFoundException e) {
                        // in this case we continue to use the previous reader and position values
                        listener.fileNotFound();
                    }
                    continue;
                } else {
                    // File was not rotated
                    // See if the file needs to be read again
                    if (length > position) {
                        // The file has more content than it did last time
                        position = readLines(reader);
                        last = file.lastModified();
                    } else if (newer) {
                        /*
                         * This can happen if the file is truncated or overwritten with the exact same length of
                         * information. In cases like this, the file position needs to be reset
                         */
                        position = 0;
                        reader.seek(position); // cannot be null here

                        // Now we can read new lines
                        position = readLines(reader);
                        last = file.lastModified();
                    }
                }
                if (reOpen) {
                    IOUtils.closeQuietly(reader);
                }
                Thread.sleep(delayMillis);
                if (getRun() && reOpen) {
                    reader = new RandomAccessFile(file, RAF_MODE);
                    reader.seek(position);
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            stop(e);
        } catch (final Exception e) {
            stop(e);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    /**
     * Stops the tailer with an exception
     * @param e The exception to send to listener
     */
    private void stop(final Exception e) {
        listener.handle(e);
        stop();
    }

    /**
     * Allows the tailer to complete its current loop and return.
     */
    public void stop() {
        this.run = false;
    }

    /**
     * Read new lines.
     *
     * @param reader The file to read
     * @return The new position after the lines have been read
     * @throws IOException if an I/O error occurs.
     */
    private long readLines(final RandomAccessFile reader) throws IOException {
        int num;
        while (getRun() && ((num = reader.read(inbuf)) != EOF)) {
            listener.handle(inbuf, num);
        }

        if (listener instanceof BytesTailerListenerAdapter) {
            ((BytesTailerListenerAdapter) listener).endOfFileReached();
        }

        return reader.getFilePointer();
    }

}
