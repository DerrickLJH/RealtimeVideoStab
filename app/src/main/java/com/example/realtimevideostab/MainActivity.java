package com.example.realtimevideostab;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;

import org.bytedeco.javacv.OpenCVFrameConverter;

import static org.bytedeco.javacpp.opencv_calib3d.findHomography;
import static org.bytedeco.javacpp.opencv_core.CV_64F;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.gemm;
import static org.bytedeco.javacpp.opencv_core.invert;
import static org.bytedeco.javacpp.opencv_core.transpose;
import static org.bytedeco.javacpp.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.CV_RGB2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.filter2D;
import static org.bytedeco.javacpp.opencv_imgproc.getGaussianKernel;
import static org.bytedeco.javacpp.opencv_imgproc.goodFeaturesToTrack;
import static org.bytedeco.javacpp.opencv_imgproc.warpPerspective;
import static org.bytedeco.javacpp.opencv_video.calcOpticalFlowPyrLK;

public class MainActivity extends AppCompatActivity implements CvCameraPreview.CvCameraViewListener{
    public static final String TAG = "MainActivity";
    private CvCameraPreview cameraView;
    private static final int HORIZONTAL_BORDER_CROP = 20;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkPermission()) {

        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1001);
        }
        cameraView = (CvCameraPreview) findViewById(R.id.camera_view);
        cameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {
    }

    @Override
    public Mat onCameraFrame(Mat mat) {
        Mat grey = new Mat();
        cvtColor(mat, grey, CV_BGR2GRAY);
        return mat;
    }

    OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();

    public Mat stabilizeImage(final Mat vFrame) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {

                    int filterWindow = 30;
                    double stD = 30;

                    Mat outImagePrev = vFrame.clone(); // Returns NullPointer as first frame is an audio frame, is a keyFrame.
                    Mat outImageNext = null;

                    Mat blackOutImagePrev = new Mat(outImagePrev.rows(), CV_8UC1);
                    Mat blackOutImageNext = new Mat(outImagePrev.size(), CV_8UC1);

                    Mat prevCorners = new Mat();
                    Mat nextCorners = new Mat();
                    Mat status = new Mat();
                    Mat err = new Mat();
                    Mat outPutHomography = null;


                    //initialize matrix to storee transform values
                    Mat trajectoryC = new Mat(9,1, CV_64F);
                    Mat trajectorySmooth = new Mat(trajectoryC.size(), trajectoryC.type());

                    //we use the indexers to access the elements in matrices
                    DoubleIndexer trajectoryCIndexer = trajectoryC.createIndexer(true);
                    ;
                    DoubleIndexer trajectorySmoothCIndexer;

                    //initialize the array used for cumulating transfoms. e.g Frame4 = H3*H2*H1* Frame1
                    Mat hMultiplier = Mat.eye(3, 3, CV_64F).asMat();
                    Mat hSmoothed = Mat.eye(3, 3, CV_64F).asMat();

                    //create indexer for the homography matrices
                    DoubleIndexer hMultiplierIndexer;
                    DoubleIndexer hSmoothedIndexer;

                    //create indexer for the detected and tracked points
                    FloatIndexer nextPointIndex;
                    FloatIndexer prevPointIndex;

                    FloatIndexer nextCleanPointIndex;
                    FloatIndexer prevCleanPointIndex;


                    //indexer for status returned by Lukas-Kanade.. status=0, implies tracking was not successfully.. status=1 implies otherwise
                    UByteIndexer statusIndex;

                    //obtain per frame homography asper matlab
                        //we are working with pairs of frames, move to next if we don't have a next frame
                        outImageNext = outImagePrev.clone();

                        //convert images.. Feature location and tracking is done on grayscale
                        cvtColor(outImagePrev, blackOutImagePrev, CV_RGB2GRAY);
                        cvtColor(outImageNext, blackOutImageNext, CV_RGB2GRAY);

                        // LK Feature Tracking
                        //detect features in frame
                        goodFeaturesToTrack(blackOutImagePrev, prevCorners, 400, 0.1, 30);

                        //Track in next Frame
                        calcOpticalFlowPyrLK(blackOutImagePrev, blackOutImageNext, prevCorners, nextCorners, status, err);


                        statusIndex = status.createIndexer(true);
                        nextPointIndex = nextCorners.createIndexer(true);
                        prevPointIndex = prevCorners.createIndexer(true);

                        //delete bad points based on the returned status

                        Mat prevCornersClean = new Mat(prevCorners.size(), prevCorners.type());
                        Mat nextCornersClean = new Mat(nextCorners.size(), nextCorners.type());

                        nextCleanPointIndex = nextCornersClean.createIndexer(true);
                        prevCleanPointIndex = prevCornersClean.createIndexer(true);

                        int k = 0;
                        int j;

                        for (j = 0; j < status.rows(); j++) {

                            if (statusIndex.get(j) != 0) {

                                nextCleanPointIndex.put(k, 0, nextPointIndex.get(j, 0));
                                nextCleanPointIndex.put(k, 1, nextPointIndex.get(j, 1));
                                prevCleanPointIndex.put(k, 0, prevPointIndex.get(j, 0));
                                prevCleanPointIndex.put(k, 1, prevPointIndex.get(j, 1));

                                k++;
                            }

                        }

                        //delete unused space in the corner matrix
                        nextCornersClean.pop_back(j - k + 1);
                        prevCornersClean.pop_back(j - k + 1);


                        //find homography
                        outPutHomography = findHomography(prevCornersClean, nextCornersClean);

                        //cumulate Homography H_n, H_n-1, ... , H_2, H_1
                        gemm(outPutHomography, hMultiplier, 1, hMultiplier, 0, hMultiplier, 0);

                        hMultiplierIndexer = hMultiplier.createIndexer(true);

                        hMultiplierIndexer.put(0, 0, hMultiplierIndexer.get(0, 0) / hMultiplierIndexer.get(2, 2)); //0
                        hMultiplierIndexer.put(0, 1, hMultiplierIndexer.get(0, 1) / hMultiplierIndexer.get(2, 2)); //1
                        hMultiplierIndexer.put(0, 2, hMultiplierIndexer.get(0, 2) / hMultiplierIndexer.get(2, 2)); //2
                        hMultiplierIndexer.put(1, 0, hMultiplierIndexer.get(1, 0) / hMultiplierIndexer.get(2, 2)); //3
                        hMultiplierIndexer.put(1, 1, hMultiplierIndexer.get(1, 1) / hMultiplierIndexer.get(2, 2)); //4
                        hMultiplierIndexer.put(1, 2, hMultiplierIndexer.get(1, 2) / hMultiplierIndexer.get(2, 2)); //5
                        hMultiplierIndexer.put(2, 0, hMultiplierIndexer.get(2, 0) / hMultiplierIndexer.get(2, 2)); //6
                        hMultiplierIndexer.put(2, 1, hMultiplierIndexer.get(2, 1) / hMultiplierIndexer.get(2, 2)); //7
                        hMultiplierIndexer.put(2, 2, hMultiplierIndexer.get(2, 2) / hMultiplierIndexer.get(2, 2)); //8


                        trajectoryCIndexer.put(0, 1, hMultiplierIndexer.get(0, 0)); //0
                        trajectoryCIndexer.put(1, 1, hMultiplierIndexer.get(0, 1)); //1
                        trajectoryCIndexer.put(2, 1, hMultiplierIndexer.get(0, 2)); //2
                        trajectoryCIndexer.put(3, 1, hMultiplierIndexer.get(1, 0)); //3
                        trajectoryCIndexer.put(4, 1, hMultiplierIndexer.get(1, 1)); //4
                        trajectoryCIndexer.put(5, 1, hMultiplierIndexer.get(1, 2)); //5
                        trajectoryCIndexer.put(6, 1, hMultiplierIndexer.get(2, 0)); //6
                        trajectoryCIndexer.put(7, 1, hMultiplierIndexer.get(2, 1)); //7
                        trajectoryCIndexer.put(8, 1, hMultiplierIndexer.get(2, 2)); //8


                        outImagePrev.release();

                        outImagePrev = outImageNext.clone();


                    Mat gaussianKenel = getGaussianKernel(filterWindow, -1);
                    transpose(gaussianKenel, gaussianKenel);// need vertical


                    //Gaussian Smoothening
                    filter2D(trajectoryC, trajectorySmooth, -1, gaussianKenel);
                    //Log.d(debugTag, "cols " + gaussianKenel.cols() + "rows: " + gaussianKenel.rows() + "cha: " + gaussianKenel.channels());

                    trajectorySmoothCIndexer = trajectorySmooth.createIndexer(true);
                    hMultiplierIndexer = hMultiplier.createIndexer(true);
                    hSmoothedIndexer = hSmoothed.createIndexer(true);

                        //obtain the smothed homography
                        hSmoothedIndexer.put(0, 0, trajectorySmoothCIndexer.get(0, 1)); //0
                        hSmoothedIndexer.put(0, 1, trajectorySmoothCIndexer.get(1, 1)); //1
                        hSmoothedIndexer.put(0, 2, trajectorySmoothCIndexer.get(2, 1)); //2
                        hSmoothedIndexer.put(1, 0, trajectorySmoothCIndexer.get(3, 1)); //3
                        hSmoothedIndexer.put(1, 1, trajectorySmoothCIndexer.get(4, 1)); //4
                        hSmoothedIndexer.put(1, 2, trajectorySmoothCIndexer.get(5, 1)); //5
                        hSmoothedIndexer.put(2, 0, trajectorySmoothCIndexer.get(6, 1)); //6
                        hSmoothedIndexer.put(2, 1, trajectorySmoothCIndexer.get(7, 1)); //7
                        hSmoothedIndexer.put(2, 2, trajectorySmoothCIndexer.get(8, 1)); //8


                        //obtain previous homography
                        hMultiplierIndexer.put(0, 0, trajectoryCIndexer.get(0,  - 1)); //0
                        hMultiplierIndexer.put(0, 1, trajectoryCIndexer.get(1,   - 1)); //1
                        hMultiplierIndexer.put(0, 2, trajectoryCIndexer.get(2, 1)); //2
                        hMultiplierIndexer.put(1, 0, trajectoryCIndexer.get(3, 1)); //3
                        hMultiplierIndexer.put(1, 1, trajectoryCIndexer.get(4, 1)); //4
                        hMultiplierIndexer.put(1, 2, trajectoryCIndexer.get(5, 1)); //5
                        hMultiplierIndexer.put(2, 0, trajectoryCIndexer.get(6, 1)); //6
                        hMultiplierIndexer.put(2, 1, trajectoryCIndexer.get(7, 1)); //7
                        hMultiplierIndexer.put(2, 2, trajectoryCIndexer.get(8, 1)); //8


                        //invert the previous
                        invert(hMultiplier, hMultiplier);
                        hMultiplierIndexer = hMultiplier.createIndexer(true);

                        //left multiply smoothed with inverse of previous
                        gemm(hSmoothed, hMultiplier, 1, hMultiplier, 0, hMultiplier, 0);

                        int vert_border = HORIZONTAL_BORDER_CROP * outImageNext.rows() / outImageNext.cols();
                        warpPerspective(outImageNext, outImagePrev, hMultiplier, outImagePrev.size()); //out Image previous now contains our warped image

                        //finally write image into Frame

                } catch (Exception e) {
                    Log.e("javacv", "video grabFrame failed: " + e);
                }

            }
        });
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1001: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(MainActivity.this, "Permission not granted", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    private boolean checkPermission() {
        int permissionCheck_Record = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.RECORD_AUDIO);
        int permissionCheck_Cam = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CAMERA);
        int permissionCheck_Write = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck_Cam == PermissionChecker.PERMISSION_GRANTED && permissionCheck_Record == PermissionChecker.PERMISSION_GRANTED && permissionCheck_Write == PermissionChecker.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }
}
