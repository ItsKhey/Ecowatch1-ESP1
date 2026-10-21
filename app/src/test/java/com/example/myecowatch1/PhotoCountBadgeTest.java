package com.example.myecowatch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.myecowatch1.utils.PhotoCountBadge;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The badge exists to answer "did this report come in with more than one photo",
 * so the threshold and the exact count have to be stable. Both apps ship this
 * same rule, and these cases pin the shared contract.
 */
public class PhotoCountBadgeTest {

    private static List<String> photos(int count) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            urls.add("https://res.cloudinary.com/demo/image/upload/photo" + i + ".jpg");
        }
        return urls;
    }

    @Test
    public void staysHiddenForASinglePhotoReport() {
        assertFalse(PhotoCountBadge.shouldShow(photos(1)));
        assertEquals("", PhotoCountBadge.format(photos(1)));
    }

    @Test
    public void showsFromTwoPhotosUpwards() {
        for (int count = 2; count <= 10; count++) {
            assertTrue("expected a badge for " + count + " photos",
                    PhotoCountBadge.shouldShow(photos(count)));
            assertEquals(String.valueOf(count), PhotoCountBadge.format(photos(count)));
        }
    }

    @Test
    public void staysHiddenWhenThereAreNoPhotos() {
        assertFalse(PhotoCountBadge.shouldShow(Collections.<String>emptyList()));
        assertFalse(PhotoCountBadge.shouldShow(null));
        assertEquals(0, PhotoCountBadge.displayCount(null));
    }

    @Test
    public void countsEveryDistinctPhotoInTheList() {
        // The server orders the list cover-first, so the badge count is simply
        // its size once duplicates and blanks are removed by the setter.
        assertEquals(5, PhotoCountBadge.displayCount(photos(5)));
    }

    @Test
    public void thresholdIsTwo() {
        assertEquals(2, PhotoCountBadge.MIN_PHOTOS_FOR_BADGE);
    }
}
