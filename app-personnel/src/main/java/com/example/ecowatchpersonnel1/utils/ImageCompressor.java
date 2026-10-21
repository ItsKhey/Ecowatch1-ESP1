package com.example.ecowatchpersonnel1.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns a camera or gallery image into a bounded, upright JPEG on disk.
 *
 * <p>A modern phone camera produces roughly 12 megapixels. Uploading that whole
 * would cost seconds on a mobile connection for a photo that is only ever seen
 * as a thumbnail, so the longest edge is bounded and the result re-encoded.
 *
 * <p>Decoding happens in two passes: the dimensions are read first and a
 * power-of-two subsample is chosen, so a large image is never allocated at full
 * size. The EXIF orientation is applied last, because a photo taken in portrait
 * is usually stored landscape with a rotation flag, and ignoring it would upload
 * the evidence sideways.
 */
public final class ImageCompressor {

    /** Longest edge of an uploaded photo, in pixels. */
    private static final int MAX_DIMENSION = 1600;

    /** Slightly below source quality; the size saving outweighs the loss. */
    private static final int JPEG_QUALITY = 85;

    private ImageCompressor() {
        // Utility class.
    }

    /**
     * Writes an upright, scaled JPEG copy of {@code source} to {@code target}.
     *
     * @throws IOException when the image cannot be read, decoded or encoded.
     */
    public static File writeScaledJpeg(Context context, Uri source, File target)
            throws IOException {
        Size full = readDimensions(context, source);
        if (full == null || full.getWidth() <= 0 || full.getHeight() <= 0) {
            throw new IOException("Selected image could not be read");
        }

        Bitmap decoded = null;
        Bitmap scaled = null;
        Bitmap oriented = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSizeFor(full.getWidth(), full.getHeight());
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decoded = decode(context, source, options);
            if (decoded == null) {
                throw new IOException("Selected image could not be decoded");
            }

            scaled = scaleToMaxDimension(decoded);
            oriented = applyOrientation(scaled, readOrientation(context, source));

            try (FileOutputStream out = new FileOutputStream(target)) {
                if (!oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw new IOException("Selected image could not be encoded");
                }
                out.flush();
            }
            return target;
        } finally {
            if (oriented != null && oriented != scaled) oriented.recycle();
            if (scaled != null && scaled != decoded) scaled.recycle();
            if (decoded != null) decoded.recycle();
        }
    }

    private static Size readDimensions(Context context, Uri source) {
        try (InputStream stream = context.getContentResolver().openInputStream(source)) {
            if (stream == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, null, options);
            return new Size(options.outWidth, options.outHeight);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    /** Largest power-of-two subsample that still leaves the image above the cap. */
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
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    private static Bitmap applyOrientation(Bitmap source, int orientation) {
        if (orientation == ExifInterface.ORIENTATION_NORMAL
                || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return source;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1f, 1f);
                break;
            default:
                return source;
        }

        try {
            Bitmap rotated = Bitmap.createBitmap(source, 0, 0,
                    source.getWidth(), source.getHeight(), matrix, true);
            return rotated == null ? source : rotated;
        } catch (OutOfMemoryError | IllegalArgumentException error) {
            return source;
        }
    }

    private static int readOrientation(Context context, Uri source) {
        try (InputStream stream = context.getContentResolver().openInputStream(source)) {
            if (stream == null) return ExifInterface.ORIENTATION_NORMAL;
            ExifInterface exif = new ExifInterface(stream);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException error) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap decode(Context context, Uri source, BitmapFactory.Options options) {
        try (InputStream stream = context.getContentResolver().openInputStream(source)) {
            if (stream == null) return null;
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }
}
