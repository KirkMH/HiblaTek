package com.asu.hiblatek;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.opencv.android.Utils;
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
    enum Direction { HORIZONTAL, VERTICAL }
    /**
     * Margin (in pixels) used in image analysis
     */
    private int MARGIN = 10;
    /**
     * Check every how many percent of the image
     */
    private int LOOP_EVERY_PERCENT = 3; // ORIG: 5
    /**
     * Line thickness used in annotating the original image.
     */
    private int ANNOTATION_THICKNESS = 3;
    private int ANNOTATION_LENGTH = 10;
    /**
     * The filename of the photo to be processed.
     */
    private final Bitmap bitmap;
    private Mat originalImage;
    private Mat monotoneImage;
    private Mat horizontallyAnnotated;
    private Mat verticallyAnnotated;
    /**
     * The resulting count.
     */
    private Count count;

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
//        this.context = context;
        this.count = new Count();
        this.bitmap = cropOuterBorder(bitmap);
        this.originalImage = new Mat (bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
        this.horizontallyAnnotated = new Mat (bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
        this.verticallyAnnotated = new Mat (bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(this.bitmap, this.originalImage);
        Utils.bitmapToMat(this.bitmap, this.horizontallyAnnotated);
        Utils.bitmapToMat(this.bitmap, this.verticallyAnnotated);
    }

    /**
     * Initiates the counting procedure.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public List<Bitmap> start() {
        List<Bitmap> results = new ArrayList<>();
        results.add(matToBitmap(this.originalImage));

        // convert bitmap image to Mat
        Mat tmp = new Mat (bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(bitmap, tmp);

        // convert to black and white
        Imgproc.threshold(tmp, tmp, 128, 255, Imgproc.THRESH_BINARY);

        Mat vMat = isolateVerticalLines(tmp);
        results.add(matToBitmap(vMat));
        results.add(matToBitmap(this.verticallyAnnotated));

        Mat hMat = isolateHorizontalLines(tmp);
        results.add(matToBitmap(hMat));
        results.add(matToBitmap(this.horizontallyAnnotated));

        return results;
    }

    /**
     * Converts a Mat into a Bitmap.
     * @param mat   Mat object to be converted.
     * @return      The Bitmap image.
     */
    private Bitmap matToBitmap(Mat mat) {
        if (mat.channels() == 1)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_GRAY2BGR);
        Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
        return bmp;
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
    private Bitmap cropOuterBorder(Bitmap src) {
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

        // verify if there is a need for cropping by checking the first row`
        boolean isPureBlackOrWhite = true;
        for (int j = 1; j < thresholdMat.cols(); j++) {
            if (thresholdMat.get(0, j)[0] != thresholdMat.get(0, j)[0]) {
                isPureBlackOrWhite = false;
                break;
            }
        }
        if (!isPureBlackOrWhite) return src; // no need to crop

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


    private void annotateImages(Direction direction, List<Point> begPoints, List<Point> endPoints, Scalar color) {
        Mat image = (direction == Direction.HORIZONTAL) ? this.horizontallyAnnotated : this.verticallyAnnotated;
        int end = Math.min(begPoints.size(), endPoints.size());
        for (int i = 0; i < end; i++) {
            Imgproc.line(image, begPoints.get(i), endPoints.get(i), color, ANNOTATION_THICKNESS);
        }
    }

    /**
     * Checks if the entire vertical line of the specified column is valid.
     * It has to have at least one black pixel to be considered as valid.
     * @param binaryImage
     * @param col
     * @return
     */
    private boolean isVerticalLineValid(Mat binaryImage, int col) {
        for (int i = 0; i < binaryImage.rows(); i++) {
            if (binaryImage.get(i, col)[0] == 0)
                return true;
        }
        return false;
    }

    /**
     * Checks if the entire horizontal line of the specified row is valid.
     * It has to have at least one black pixel to be considered as valid.
     * @param binaryImage
     * @param row
     * @return
     */
    private boolean isHorizontalLineValid(Mat binaryImage, int row) {
        for (int i = 0; i < binaryImage.cols(); i++) {
            if (binaryImage.get(row, i)[0] == 0)
                return true;
        }
        return false;
    }


    /**
     * Counts the number of vertical lines from the photo.
     * @param binaryImage      Processed image.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Mat isolateVerticalLines(Mat binaryImage) {
        Mat vBinImg = binaryImage.clone();
        // Preprocess: Enhance vertical lines using morphology
        Mat verticalStructure = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 20));
        Imgproc.erode(vBinImg, vBinImg, verticalStructure);
        Imgproc.dilate(vBinImg, vBinImg, verticalStructure);

        this.count.vertical = countVerticalLines(vBinImg);
        return vBinImg;
    }

    /**
     * Counts the number of white spaces along vertical, representing the fibers.
     * @param binary        The image to be analyzed.
     * @return              The number of vertical lines.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int countVerticalLines(Mat binary) {
        Mat binaryImage = binary.clone();
        int vCount = 0;
        final Scalar LIME = new Scalar(57, 255, 20);
        List<Point> begPoints = new ArrayList<>();
        List<Point> endPoints = new ArrayList<>();

        // loop through every LOOP_EVERY_PERCENT% of the image's rows and count the number of white pixels
        int increment = (int) (binaryImage.rows() * (LOOP_EVERY_PERCENT / 100.0));
        int last_row = 0;
        int rows = binaryImage.rows();

        for (int i = MARGIN; i < rows - MARGIN; i += increment) {
            if (!isHorizontalLineValid(binaryImage, i)) continue;

            int count = 0;
            boolean wasLastWhite = false;
            int cols = binaryImage.cols();
            for (int j = 0; j < cols; j++) {
                if (j == cols-1)
                    Log.v("K-test0102", "Reached end of col");
                if (binaryImage.get(i, j)[0] == 255) {
                    if (!wasLastWhite) {
                        begPoints.add(new Point(j, i));
                        endPoints.add(new Point(j, i + ANNOTATION_LENGTH));
                        count++;
                        wasLastWhite = true;
                    }
                }
                else if (wasLastWhite) {
                    wasLastWhite = false;
                }
            }
            Log.v("K-test1223", "Count at column " + i + ": " + count);
            if (count > vCount) {
                vCount = count;
            }
            last_row = i;
        }
        Log.v("K-test0102", "increment=" + increment);
        Log.v("K-test0102", "last_row=" + last_row);
        Log.v("K-test0102", "row count=" + binaryImage.rows());

        annotateImages(Direction.VERTICAL, begPoints, endPoints, LIME);
        Log.v("K-test1223", "Vertical Count: " + vCount);
        return vCount;
    }


    /**
     * Counts the number of vertical lines from the photo.
     * @param binaryImage   The current processed image.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Mat isolateHorizontalLines(Mat binaryImage) {
        Mat hBinImg = binaryImage.clone();

        // Preprocess: Enhance horizontal lines using morphology
        Mat horizontalStructure = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(20, 1));
        Imgproc.erode(hBinImg, hBinImg, horizontalStructure);
        Imgproc.dilate(hBinImg, hBinImg, horizontalStructure);

        int hCount = countHorizontalLines(hBinImg);
        System.out.println("Number of Horizontal Lines: " + hCount);

        count.horizontal = hCount;
        return hBinImg;
    }

    /**
     * Counts the number of white spaces along horizontal, which represents the fibers.
     * @param binaryImage   Image to process.
     * @return              Number of horizontal lines.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int countHorizontalLines(Mat binaryImage) {
        int hCount = 0;
        List<Point> begPoints = new ArrayList<>();
        List<Point> endPoints = new ArrayList<>();

        // loop through every LOOP_EVERY_PERCENT% of the image's columns and count the number of white pixels
        int increment = (int) (binaryImage.cols() * (LOOP_EVERY_PERCENT / 100.0));
        int last_col = 0;

        for (int i = MARGIN; i < binaryImage.cols() - MARGIN; i += increment) {
            if (!isVerticalLineValid(binaryImage, i)) continue;

            int count = 0;
            boolean wasLastWhite = false;
            for (int j = 10; j < binaryImage.rows(); j++) {
                if (binaryImage.get(j, i)[0] == 255) {
                    if (!wasLastWhite) {
                        begPoints.add(new Point(i, j));
                        endPoints.add(new Point(i + ANNOTATION_LENGTH, j));
                        count++;
                        wasLastWhite = true;
                    }
                }
                else if (wasLastWhite) {
                    wasLastWhite = false;
                }
            }
            Log.v("K-test1223", "Count at row " + i + ": " + count);
            if (count > hCount) {
                hCount = count;
            }
            last_col = i;
        }

        Log.v("K-test0102", "increment=" + increment);
        Log.v("K-test0102", "last_col=" + last_col);
        Log.v("K-test0102", "col count=" + binaryImage.cols());

        annotateImages(Direction.HORIZONTAL, begPoints, endPoints, new Scalar(218, 20, 255));
        Log.v("K-test1223", "Horizontal count: " + hCount);
        return hCount;
    }
}