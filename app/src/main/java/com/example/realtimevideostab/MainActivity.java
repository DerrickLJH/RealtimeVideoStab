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
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import static org.bytedeco.javacpp.helper.opencv_core.RGB;
import static org.bytedeco.javacpp.opencv_calib3d.findHomography;
import static org.bytedeco.javacpp.opencv_core.CV_32F;
import static org.bytedeco.javacpp.opencv_core.CV_64F;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import static org.bytedeco.javacpp.opencv_core.CV_8UC4;
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_EPS;
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_ITER;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.Size;
import static org.bytedeco.javacpp.opencv_core.Scalar;
import static org.bytedeco.javacpp.opencv_core.gemm;
import static org.bytedeco.javacpp.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.CV_BGRA2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.CV_RGBA2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.circle;
import static org.bytedeco.javacpp.opencv_imgproc.cornerSubPix;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.dilate;
import static org.bytedeco.javacpp.opencv_imgproc.getStructuringElement;
import static org.bytedeco.javacpp.opencv_imgproc.goodFeaturesToTrack;
import static org.bytedeco.javacpp.opencv_imgproc.line;
import static org.bytedeco.javacpp.opencv_imgproc.medianBlur;
import static org.bytedeco.javacpp.opencv_core.TermCriteria;
import static org.bytedeco.javacpp.opencv_imgproc.warpAffine;
import static org.bytedeco.javacpp.opencv_video.KalmanFilter;
import static org.bytedeco.javacpp.opencv_video.calcOpticalFlowPyrLK;
import static org.bytedeco.javacpp.opencv_core.Point;
import static org.bytedeco.javacpp.opencv_imgproc.putText;
import static org.bytedeco.javacpp.opencv_imgproc.rectangle;
import static org.bytedeco.javacpp.opencv_imgproc.resize;
import static org.bytedeco.javacpp.opencv_objdetect.CascadeClassifier;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

import static org.bytedeco.javacpp.opencv_video.KalmanFilter;
import static org.bytedeco.javacpp.opencv_video.calcOpticalFlowPyrLK;
import static org.bytedeco.javacpp.opencv_core.Point;
import static org.bytedeco.javacpp.opencv_imgproc.putText;
import static org.bytedeco.javacpp.opencv_imgproc.rectangle;
import static org.bytedeco.javacpp.opencv_imgproc.resize;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_video.estimateRigidTransform;

public class MainActivity extends AppCompatActivity implements CvCameraPreview.CvCameraViewListener {
    public static final String TAG = "MainActivity";
    private CvCameraPreview mOpenCvCameraView;
    private Mat currGrey, currFeatures, prevGrey, prevFeatures;
    private boolean getPrevFrame = false;
    private Mat prevFrame = new Mat();
    private Mat last_transformMatrix = new Mat();
    private static final int HORIZONTAL_BORDER_CROP = 20;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (checkPermission()) {

        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1001);
        }
        mOpenCvCameraView = (CvCameraPreview) findViewById(R.id.cameraView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1001: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED && grantResults[4] == PackageManager.PERMISSION_GRANTED) {
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
        int permissionCheck_Read = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck_Cam == PermissionChecker.PERMISSION_GRANTED && permissionCheck_Record == PermissionChecker.PERMISSION_GRANTED && permissionCheck_Write == PermissionChecker.PERMISSION_GRANTED && permissionCheck_Read == PermissionChecker.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        prevFrame = new Mat(height, width, CV_8UC4);
        currGrey = new Mat(height, width, CV_8UC1);
        prevGrey = new Mat(height, width, CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        currGrey.release();
    }

    @Override
    public Mat onCameraFrame(Mat currFrame) {
        int winSize = 15;
        TermCriteria term = new TermCriteria(TermCriteria.EPS | TermCriteria.MAX_ITER, 20, 0.03);

        if (prevFrame.data() == null) {
            prevFrame = currFrame;
            Log.i(TAG, "No data.");
        }
        cvtColor(prevFrame, prevGrey, CV_RGBA2GRAY);
        cvtColor(currFrame, currGrey, CV_BGRA2GRAY);
        // Improve quality and corner detection
        Log.i(TAG, "GOT CURRENT FRAME!" + currFrame.data());

        medianBlur(currGrey, currGrey, 5);
//        medianBlur(prevGrey, prevGrey, 5);
        prevFeatures = new Mat();
        // erode
        Mat dilate = getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        dilate(currGrey, currGrey, dilate);
        dilate(prevGrey, prevGrey, dilate);

        // Compute goodFeaturesToTrack()
        goodFeaturesToTrack(currGrey, prevFeatures, 500, 0.05, 5.0, null, 3, false, 0.04);
        if (prevFeatures.empty()) return currFrame;
        cornerSubPix(currGrey, prevFeatures, new Size(15, 15), new Size(-1, -1), term);

        // Compute Optical Flow using calcOpticalFlyPyrLK()
        Mat status = new Mat(); // status
        Mat err = new Mat(); // err
        Mat currFeatures = new Mat();
        Mat outPutHomography = null;

        calcOpticalFlowPyrLK(currGrey, currGrey, prevFeatures, currFeatures, status, err, new Size(winSize, winSize), 5, term, 0, 1e-4);

        //create indexer for the detected and tracked points
        FloatIndexer nextPointIndex;
        FloatIndexer prevPointIndex;

        FloatIndexer nextCleanPointIndex;
        FloatIndexer prevCleanPointIndex;

        //indexer for status returned by Lukas-Kanade.. status=0, implies tracking was not successfully.. status=1 implies otherwise
        UByteIndexer statusIndex;
        FloatIndexer errorIndex;

        statusIndex = status.createIndexer(true);
        errorIndex = err.createIndexer(true);
        nextPointIndex = currFeatures.createIndexer(true);
        prevPointIndex = prevFeatures.createIndexer(true);

        //delete bad points based on the returned status

        Mat prevCornersClean = new Mat(prevFeatures.size(), prevFeatures.type());
        Mat nextCornersClean = new Mat(currFeatures.size(), currFeatures.type());

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


                Point p0 = new Point(Math.round(prevCleanPointIndex.get(j, 0)),
                        Math.round(prevCleanPointIndex.get(j, 1)));
                Point p1 = new Point(Math.round(nextCleanPointIndex.get(j, 0)),
                        Math.round(nextCleanPointIndex.get(j, 1)));
                line(currFrame, p0, p1, new Scalar(0, 255, 0, 0),
                        2, 8, 0);

            }

        }

        nextCornersClean.pop_back(j - k + 1);
        prevCornersClean.pop_back(j - k + 1);


        // Estimate a rigid transformation
        Mat correctedMatrix = estimateRigidTransform(prevCornersClean,nextCornersClean,false);
        if (correctedMatrix.data() == null) {
            last_transformMatrix.copyTo(correctedMatrix);
        }
        // Smoothing using Kalman filter
        // Warping of the picture
        Mat corrected = new Mat();
        warpAffine(currFrame, corrected, correctedMatrix, currFrame.size());
        prevFrame.release();
        prevFrame = currFrame.clone();
        return corrected;

    }
}
