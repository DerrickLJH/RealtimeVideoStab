# Real-time Video Stabilization

This is an unfinished app that would allow the camera view to stabilize frame by frame.

# Getting Started

Upon installing application and enabling permissions, restart the app again to access the app properly.

## Understanding Realtime Video Stabilization

Based on my research, I know that real-time video stabilizing is possible with Kalman Filter, which takes the frames and predicts where the points of the current frame will move, smoothing the motion.
There are videos and examples of OpenCV's Realtime Video Stabilization done in C++, yet to find any examples of real time Android mobile real time video stabilization.

## Workflow:

Step 1: Obtain the previous and current frame of the camera view.

Step 2: Compute goodFeaturesToTrack()

Step 3: Keep only good points

Step 4: Estimate a rigid transformation

Step 5: Smoothing using Kalman Filter

Step 6: Warping of the picture

## Issues

Currently having, difficulties with getting the previous frame in the onCameraFrame() function, however, Workflow Step 2 - 4 is working fine. No success in application Kalman filter yet. Frame rate drops when proccessing realtime.

## References

Nghia Ho's videostabKalman:  
http://nghiaho.com/uploads/videostabKalman.cpp

C++/OpenCV - Kalman filter for video stabilization :  
https://stackoverflow.com/questions/27296749/c-opencv-kalman-filter-for-video-stabilization

## Examples 

https://abhitronix.github.io/2018/11/30/humanoid-AEAM-3  
https://www.youtube.com/watch?v=RDmwz750ESU  
https://www.youtube.com/watch?v=TXXPHSeKo1I  

