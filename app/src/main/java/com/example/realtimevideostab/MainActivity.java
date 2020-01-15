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
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_EPS;
import static org.bytedeco.javacpp.opencv_core.CV_TERMCRIT_ITER;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.Size;
import static org.bytedeco.javacpp.opencv_core.Scalar;
import static org.bytedeco.javacpp.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.circle;
import static org.bytedeco.javacpp.opencv_imgproc.cornerSubPix;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.dilate;
import static org.bytedeco.javacpp.opencv_imgproc.getStructuringElement;
import static org.bytedeco.javacpp.opencv_imgproc.goodFeaturesToTrack;
import static org.bytedeco.javacpp.opencv_imgproc.line;
import static org.bytedeco.javacpp.opencv_imgproc.medianBlur;
import static org.bytedeco.javacpp.opencv_core.TermCriteria;
import static org.bytedeco.javacpp.opencv_video.KalmanFilter;
import static org.bytedeco.javacpp.opencv_video.calcOpticalFlowPyrLK;
import static org.bytedeco.javacpp.opencv_core.Point;
import static org.bytedeco.javacpp.opencv_imgproc.putText;
import static org.bytedeco.javacpp.opencv_imgproc.rectangle;
import static org.bytedeco.javacpp.opencv_imgproc.resize;
import static org.bytedeco.javacpp.opencv_objdetect.CascadeClassifier;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_video.estimateRigidTransform;


public class MainActivity extends AppCompatActivity implements CvCameraPreview.CvCameraViewListener {
    public static final String TAG = "MainActivity";
    private CvCameraPreview mOpenCvCameraView;
    private Mat grey, features, prevFeatures;
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
        grey = new Mat(height, width, CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        grey.release();
    }

    @Override
    public Mat onCameraFrame(Mat mat) {
        cvtColor(mat, grey, CV_BGR2GRAY);
        // Improve quality and corner detection

        int winSize = 15;
        TermCriteria term = new TermCriteria(TermCriteria.EPS | TermCriteria.MAX_ITER, 20, 0.03);

        medianBlur(grey, grey, 5);
        features = new Mat();
        prevFeatures = new Mat();
        // erode
        Mat dilate = getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        dilate(grey, grey, dilate);

        // Compute goodFeaturesToTrack()
        goodFeaturesToTrack(grey, features, 500, 0.05, 5.0, null, 3, false, 0.04);
        if (features.empty()) return mat;
        cornerSubPix(grey, features, new Size(15, 15), new Size(-1, -1), term);

        // Compute Optical Flow using calcOpticalFlyPyrLK()
        Mat features_found = new Mat(); // status
        Mat feature_err = new Mat(); // err
        Mat cornersB = new Mat();

        calcOpticalFlowPyrLK(grey, grey, features, cornersB, features_found, feature_err, new Size(winSize, winSize), 5, term, 0, 1e-4);

        // Make an image of the results
        FloatIndexer cornersAidx = features.createIndexer();
        FloatIndexer cornersBidx = cornersB.createIndexer();
        UByteIndexer features_found_idx = features_found.createIndexer();
        FloatIndexer feature_errors_idx = feature_err.createIndexer();
        for (int i = 0; i < cornersAidx.sizes()[0]; i++) {
            if (features_found_idx.get(i) == 0 || feature_errors_idx.get(i) > 550) {
                Log.e(TAG, "Error is " + feature_errors_idx.get(i));
                continue;
            }
            Log.i(TAG, "Got it!");
            Point p0 = new Point(Math.round(cornersAidx.get(i, 0)),
                    Math.round(cornersAidx.get(i, 1)));
            Point p1 = new Point(Math.round(cornersBidx.get(i, 0)),
                    Math.round(cornersBidx.get(i, 1)));
            line(mat, p0, p1, new Scalar(0, 255, 0,0),
                    2, 8, 0);
        }

        // Keep only good points
//        calcOpticalFlowPyrLK(gray_1, gray_2, features_1, features_2, status, err,
//                wins_size, max_level, term_crit);
//
//        long i, k;
//        for( i = k = 0; i < features_found.size(); i++ ){
//            if (!features_found[i]) continue;
//            features[k] = features[i];
//            cornersB[k] = cornersB[i];
//            feature_err[k] = feature_err[i];
//            k++;
//        }
//        cornersB.resize(k);
//        features_found.resize(k);
//        feature_err.resize(k);

        // Estimate a rigid transformation
        Mat transformMatrix = estimateRigidTransform(features, cornersB,false);

        // Smoothing using Kalman filter

        // Warping of the picture

        return mat;
    }

}
