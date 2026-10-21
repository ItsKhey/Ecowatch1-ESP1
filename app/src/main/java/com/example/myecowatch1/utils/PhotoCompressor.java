package com.example.myecowatch1.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns a picked photo into a bounded JPEG on disk, ready to upload.
 *
 * <p>A modern camera photo is around 12 megapixels, and re-encoding one of those
 * at full size produced report attachments of roughly 3MB. Every list that showed
 * such a report then had to download all of it, even though the image appears in
 * a thumbnail a few hundred pixels wide, so the transfer cost was paid in full
 * for bytes nobody ever saw.
 *
 * <p>This bounds the longest edge, which keeps a report attachment in the low
 * hundreds of kilobytes while still looking sharp on a phone screen or a desktop
 * browser at any reasonable zoom.
 *
 * <p>Decoding is also done in two passes, sampling as it goes. Loading a 12MP
 * bitmap whole costs about 48MB of heap and is a reliable way to an
 * OutOfMemoryError on a low-memory device; sampling first avoids allocating that
 * at all.
 */
public final class PhotoCompressor {

    /**
     * Longest edge of an uploaded report photo, in pixels.
     *
     * Comfortably above a full-screen phone at any density, so nothing looks
     * soft, while being a fraction of a modern camera's resolution.
     */
    private static final int MAX_DIMENSION = 1600;

    /** Slightly below the source quality: the size saving is larger than the loss. */
    private static final int JPEG_QUALITY = 85;

    private PhotoCompressor() {
        // Utility class.
    }

    /**
     * Writes a scaled JPEG copy of {@code source} to {@code target}.
     *
     * @throws IOException when the image cannot be read, decoded or encoded.
     */
    public static File writeScaledJpeg(Context context, Uri source, File target) throws IOException {
        Size full = readDimensions(context, source);
        if (full == null || full.getWidth() <= 0 || full.getHeight() <= 0) {
            throw new IOException("Selected image could not be read");
        }

        Bitmap decoded = null;
        Bitmap scaled = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize =
                    sampleSizeFor(full.getWidth(), full.getHeight());
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decoded = decode(context, source, options);

            if (decoded == null) {
                throw new IOException("Selected image could not be decoded");
            }

            scaled = scaleToMaxDimension(decoded);

            try (FileOutputStream out = new FileOutputStream(target)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw new IOException("Selected image could not be encoded");
                }
                out.flush();
            }
            return target;
        } finally {
            if (scaled != null && scaled != decoded) {
                scaled.recycle();
            }
            if (decoded != null) {
                decoded.recycle();
            }
        }
    }

    /**
     * Reads only the pixel dimensions, allocating nothing.
     *
     * @return null when the stream cannot be opened or the bytes are not an
     *         image this decoder understands.
     */
    private static Size readDimensions(Context context, Uri source) {
        try (InputStream stream = openStream(context, source)) {
            if (stream == null) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, null, options);
            return new Size(options.outWidth, options.outHeight);
        } catch (IOException error) {
            return null;
        }
    }

    /**
     * Largest power-of-two subsample that still leaves the image at or above
     * {@link #MAX_DIMENSION}, so one final exact scale finishes the job.
     */
    private static int sampleSizeFor(int width, int height) {
        int longest = Math.max(width, height);
        int sample = 1;
        while (longest / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleToMaxDimension(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);

        if (longest <= MAX_DIMENSION) {
            return source;
        }

        float ratio = (float) MAX_DIMENSION / longest;
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));

        Matrix matrix = new Matrix();
        matrix.postScale(ratio, ratio);

        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true);
    }

    private static Bitmap decode(Context context, Uri source, BitmapFactory.Options options) {
        try (InputStream stream = openStream(context, source)) {
            if (stream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (IOException error) {
            return null;
        }
    }

    private static InputStream openStream(Context context, Uri source) {
        try {
            return context.getContentResolver().openInputStream(source);
        } catch (Exception error) {
            // A revoked permission or a file deleted behind our back arrives as an
            // exception from the resolver rather than as a null.
            return null;
        }
    }
}
