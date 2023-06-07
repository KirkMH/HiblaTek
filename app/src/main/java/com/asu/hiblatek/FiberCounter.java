package com.asu.hiblatek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.*;
import java.util.List;


/**
 * This class counts the number of fibers from a photo using Marvin Framework.
 *
 * @author Kirk M. Hilario
 */
public class FiberCounter {
    /**
     * Used in logging statuses.
     */
    private final String TAG = "hiblatek.FiberCounter";
    /**
     * Assigned threshold value for the thresholding API.
     */
    private int THRESHOLD_VALUE = 150;
    /**
     * Assigned threshold length (in pixels) for a line to be valid.
     */
    private int LINE_THRESHOLD_VALUE = 15;
    /**
     * The minimum set width for a line to be valid; in pixels.
     */
    private int MIN_LINE_WIDTH = 10;
    /**
     * The maximum set width for a line to be valid; in pixels.
     */
    private int MAX_LINE_WIDTH = 100;
    /**
     * The filename of the photo to be processed.
     */
    private final Bitmap bitmap;
    /**
     * The resulting count.
     */
    private Count count;
    /**
     * The context/activity that instantiated this class.
     */
    private final Context context;

    private int minWidth = Integer.MAX_VALUE;
    private int maxWidth = Integer.MIN_VALUE;

    /**
     * This inner class will be used to collect all results
     * from two methods responsible for counting the number
     * of fbers horizontally and vertically.
     */
    static class Count {
        /**
         * The number of horizontal fibers.
         */
        int horizontal = 0;
        /**
         * The number of vertical fibers.
         */
        int vertical = 0;
        /**
         * The output image.
         */
        Mat imageOut = null;

        /**
         * Returns the total number of fibers.
         * @return the sum of horizontal and vertical counts
         */
        public int getTotal() {
            return horizontal + vertical;
        }

        @Override
        public String toString() {
            return Integer.toString(getTotal());
        }
    }

    /**
     * Creates a new instance of the {@code MyFiberCounter} class.
     * @param bitmap  The filename of the photo to be processed.
     */
    public FiberCounter(Bitmap bitmap, Context context) {
        this.context = context;
        this.bitmap = cropOuterBlack(bitmap);
    }

    /**
     * Initiates the counting procedure.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public List<Bitmap> start() {
        List<Bitmap> results = new ArrayList<>();
        results.add(bitmap);
        Mat tmp = new Mat (bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(bitmap, tmp);

        // grayscale image
        Imgproc.cvtColor(tmp, tmp, Imgproc.COLOR_RGB2GRAY);
        Bitmap grayscale = bitmap.copy(bitmap.getConfig(), true);
        Utils.matToBitmap(tmp, grayscale);
        results.add(grayscale);

        // option: increase image contrast
        tmp.convertTo(tmp, -1, 1.5, 0);
        Bitmap contrast = bitmap.copy(bitmap.getConfig(), true);
        Utils.matToBitmap(tmp, contrast);
        results.add(contrast);

        Mat matThres = null; bitmap.copy(bitmap.getConfig(), true);
        List<Count> counts = new ArrayList<>();
        Mat src = tmp.clone();
        for (int threshold = 250; threshold >= 100; threshold -= 25) {
            // threshold image
            Imgproc.threshold(src, tmp, threshold, 255, Imgproc.THRESH_BINARY);
            // erode to remove noise
            Mat kernel = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(10, 10));
            Imgproc.erode(tmp, tmp, kernel);

            // convert to bitmap
            Bitmap b = bitmap.copy(bitmap.getConfig(), true);
            Utils.matToBitmap(tmp, b);

            // check if the background is black -- will need to invert color
            Color c = getSafeIntColor(1, 1, b);
            if (c != null && c.equals(Color.valueOf(0, 0, 0))) {
                // invert color (since the background is black)
                Core.bitwise_not(tmp, tmp);
                Utils.matToBitmap(tmp, b);
            }
//            results.add(b);

            // Find lines and save an output image
            Count thisCount = countVerticalLines(b, b);
            thisCount = countHorizontalLines(b, thisCount);
            counts.add(thisCount);

            if (this.count == null || this.count.getTotal() < thisCount.getTotal()) {
                this.count = thisCount;
                matThres = thisCount.imageOut;
                THRESHOLD_VALUE = threshold;
            }
        }

        Bitmap threshold = Bitmap.createBitmap(
                matThres.width(),
                matThres.height(),
                bitmap.getConfig());
//        Bitmap threshold = bitmap.copy(bitmap.getConfig(), true);
        Utils.matToBitmap(matThres, threshold);
        results.add(threshold);

        Toast.makeText(context, "Counts: " + counts.toString(), Toast.LENGTH_SHORT).show();

        Log.v(TAG, "Counts: " + counts);
        Log.v(TAG, "Min Width: " + minWidth);
        Log.v(TAG, "Max Width: " + maxWidth);

        return results;
    }

    private boolean isLineValid(int start, int end) {
        int width = Math.abs(end - start) + 1;
        if (width < minWidth) minWidth = width;
        if (width > maxWidth) maxWidth = width;
        return (width >= MIN_LINE_WIDTH && width <= MAX_LINE_WIDTH);
    }

    /**
     * Returns the resulting count.
     * @return the resulting count
     */
    public Count getCount() {
        return this.count;
    }

    /**
     * Crops the image to remove the surrounding black part.
     * @param src   Source Bitmap image.
     * @return      Cropped Bitmap image.
     */
    private Bitmap cropOuterBlack(Bitmap src) {
        // Load the image as a Mat object
        Mat imageMat = new Mat();
        Utils.bitmapToMat(src, imageMat);

        // Convert the image to grayscale if it's in color
        Mat grayMat = new Mat();
        if (imageMat.channels() > 1) {
            Imgproc.cvtColor(imageMat, grayMat, Imgproc.COLOR_BGR2GRAY);
        } else {
            grayMat = imageMat;
        }

        // Threshold the image to separate the black part
        Mat thresholdMat = new Mat();
        double thresholdValue = 1; // Adjust the threshold value if needed
        double maxBinaryValue = 255; // Maximum value for binary threshold
        Imgproc.threshold(grayMat, thresholdMat, thresholdValue, maxBinaryValue, Imgproc.THRESH_BINARY);

        // Find the contours of the black part
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresholdMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find the bounding rectangle of the black part
        Rect boundingRect = Imgproc.boundingRect(contours.get(0));

        // Crop the image using the bounding rectangle
        Mat croppedMat = new Mat(imageMat, boundingRect);

        // Convert the cropped Mat back to a bitmap
        Bitmap croppedBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(croppedMat, croppedBitmap);

        return croppedBitmap;
    }

    /**
     * Counts the number of vertical lines from the photo.
     * @param binImage      Processed image.
     * @param originImage   Original image.
     * @return              Count instance.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Count countVerticalLines(Bitmap binImage, Bitmap originImage) {
        Mat imageOut = new Mat (originImage.getHeight(), originImage.getWidth(), CvType.CV_8UC1);
        Utils.bitmapToMat(originImage, imageOut);

        Color color;
        ArrayList<Integer> list = new ArrayList<>();

        // loop through each percent
        // loop through every 5% of the image
        for (int percent = 5; percent <= 95; percent += 5) {
            int count = 0;
            int x = 0;
             int y = (int) (binImage.getHeight() * (percent / 100.0));
            // navigate through the entire image width
            while (x < binImage.getWidth()) {
                color = binImage.getColor(x, y);

                // is it black?
                if (color.equals(Color.valueOf(0,0,0))) {
                    int x2 = getEndOfLineWidth(x, y, binImage) - 1;
                    Scalar red = new Scalar(255, 0, 0, 255);
                    Imgproc.rectangle(imageOut, new Point(x, y-2), new Point(x2, y+2), red, 5);
                    // increment when line width is within the threshold
                    if (isLineValid(x, x2)) {
                        int height = getEndOfLineHeight(x, y, binImage) - y - 1;
                        if (height >= LINE_THRESHOLD_VALUE) {
                            count++;
                            // add some markings
                            Scalar magenta = new Scalar(255, 0, 255, 255);
                            Imgproc.rectangle(imageOut, new Point(x, y), new Point(x2, y+height), magenta, 5);
                        }
//                        count++;
//                        // add some markings
//                        Scalar magenta = new Scalar(255, 0, 255, 255);
//                        Imgproc.rectangle(imageOut, new Point(x, y-2), new Point(x2, y+2), magenta, 10);
                    }
                    x = x2;
                }
                x++;
            }
            // add the row's count to the list of counts
            list.add(count);
        }

        // using MODE to get final count; otherwise, use MEDIAN
        int v = Collections.max(list); // modeOrMedian(list);

        // build return value
        Count count = new Count();
        count.vertical = v;
        count.imageOut = imageOut;

        return count;
    }


    /**
     * Counts the number of vertical lines from the photo.
     * @param binImage      Processed image.
     * @param count         Count instance (result) so far.
     * @return              Count instance.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Count countHorizontalLines(Bitmap binImage, Count count) {
        Mat imageOut = count.imageOut.clone(); // new Mat (count.imageOut.getWidth(), count.imageOut.getHeight(), CvType.CV_8UC1);

        // PLAN: take count for 10%, 25%, 50%, 75%, and 90% of image
        Color color;
        ArrayList<Integer> list = new ArrayList<>();

        // loop through every 5% of the image
        for (int percent = 5; percent <= 95; percent += 5) {// : width_percent_list) {
            int ctr = 0;
            int y = 0;
            int x = (int) (binImage.getWidth() * (percent / 100.0));

            // navigate through the entire height
            while (y < binImage.getHeight()) {
                color = binImage.getColor(x, y);

                // is it black?
                try {
                    if (color.equals(Color.valueOf(0, 0, 0))) {
                        int y2 = getEndOfLineHeight(x, y, binImage) - 1;
                        // increment count when the width is within the range
                        if (isLineValid(y, y2)) {
                            Scalar red = new Scalar(255, 0, 0, 255);
                            Imgproc.rectangle(imageOut, new Point(x-2, y), new Point(x+2, y2), red, 5);
                            int width = getEndOfLineWidth(x, y, binImage) - x - 1;
                            if (width >= LINE_THRESHOLD_VALUE) {
                                ctr++;
                                // add some markings
                                Scalar magenta = new Scalar(255, 0, 255, 255);
                                Imgproc.rectangle(imageOut, new Point(x, y-2), new Point(x+width, y+2), magenta, 1);
                            }
//                            ctr++;
//                            // add some markings
//                            Scalar magenta = new Scalar(255, 0, 0, 255);
//                            Imgproc.rectangle(imageOut, new Point(x-2, y), new Point(x+2, y2), magenta, 10);
                        }
                        y = y2;
                    }
                    y++;
                } catch (Exception e) {
                    // for debugging purposes
                    Log.v(TAG, "x: " + x + ", y = " + y);
                    break;
                }
            }
            // the column's count to the list of counts
            list.add(ctr);
        }

        // using MODE to get final count; otherwise, use MEDIAN
        int h = Collections.max(list); // modeOrMedian(list);
        // display some output (OPTIONAL)
        System.out.println("Horizontal fibers result: " + list);
        System.out.println("Horizontal count: " + h);

        // build return value
        count.horizontal = h;
//        Utils.matToBitmap(imageOut, count.imageOut);
        count.imageOut = imageOut;

        return count;
    }

    /**
     * Returns the position where the vertical line ends horizontally
     * (starting position + width).
     * @param x         Starting x position.
     * @param y         Starting y position.
     * @param image     Image to read.
     * @return          Ending x position.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int getEndOfLineWidth(int x, int y, Bitmap image) {
        // read the image's width until a non-black pixel is encountered
        while (x < image.getWidth()) {
            Color c = getSafeIntColor(x,y,image);
            if(c != null && c.equals(Color.valueOf(0, 0, 0))){
                x++;
            }
            else {
                break;
            }
        }
        return x;
    }


    /**
     * Returns the position where the horizontal line ends vertically
     * (starting position + height).
     * @param x         Starting x position.
     * @param y         Starting y position.
     * @param image     Image to read.
     * @return          Ending y position.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int getEndOfLineHeight(int x, int y, Bitmap image) {
        // read the image's height until a non-black pixel is encountered
        while (y < image.getHeight()) {
            Color c = getSafeIntColor(x,y,image);
            if(c != null && c.equals(Color.valueOf(0, 0,0))){
                y++;
            }
            else {
                break;
            }
        }
        return y;
    }

    /**
     * Returns the integer color of the provided position.
     * Returns -1 if the position is invalid.
     * @param x         x position.
     * @param y         y position.
     * @param image     Image to read.
     * @return          Color of the pixel.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Color getSafeIntColor(int x, int y, Bitmap image){
        // validate position first
        if(x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()){
            return image.getColor(x, y);
        }
        return null;
    }

    /**
     * Returns the maximum value from the list.
     * @param list list of values
     * @return the maximum value from the list
     */
    public int getMax(List<Integer> list) {
        int max = list.get(0);

        for (int x : list) {
            if (x > max)
                max = x;
        }

        return max;
    }

    /**
     * Returns the mode of the list if exists; otherwise, it returns the median.
     * When the list is multimodal, it returns the median of the modes.
     * When the list/modes is an even number, the item at (n/2)+1 will be returned.
     * @param list  List of counts/numbers.
     * @return      The mode of the median.
     */
    public int modeOrMedian(List<Integer> list) {
        // Calculate the frequency of each number
        HashMap<Integer, Integer> frequencyMap = new HashMap<>();

        // Count the frequency of each element in the list
        for (int i = 0; i < list.size(); i++) {
            int num = list.get(i);
            if (frequencyMap.containsKey(num)) {
                frequencyMap.put(num, frequencyMap.get(num) + 1);
            } else {
                frequencyMap.put(num, 1);
            }
        }

        // Find the element with the highest frequency
        int maxFrequency = 0;
        int mode = 0;
        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxFrequency) {
                maxFrequency = entry.getValue();
                mode = entry.getKey();
            }
        }

        // check if there is a mode
        boolean hasMode = false;
        ArrayList<Integer> modes = new ArrayList<>();
        Collection<Integer> frequencies = frequencyMap.values();
        if (maxFrequency > 1) {
            hasMode = true;
            // check if it is multimodal
            if (Collections.frequency(frequencies, maxFrequency) > 1) {
                // add to a list of modes
                for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
                    if (entry.getValue() == maxFrequency)
                        modes.add(entry.getKey());
                }
            }
        }

        // If there is a mode, return it; otherwise, return the median
        if (!hasMode || modes.size() > 0) {
            List<Integer> myList = !hasMode ? list : modes;
            int n = myList.size();

            Collections.sort(myList);
            try {
                if (n % 2 == 0) {
                    mode = myList.get((n / 2) + 1);
                } else {
                    mode = myList.get(n / 2);
                }
            } catch (Exception e) {
                System.out.println("Mode length: " + n);
                System.out.println("Result: " + ((n/2)+1));
            }
        }

        return mode;
    }

}