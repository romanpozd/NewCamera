package il.co.focuscameracustom.inmanage.focuscameracustom;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FocusCamera extends RelativeLayout {

    private static final String TAG = FocusCamera.class.getSimpleName();
    private Camera camera;
    private Button flashAutoButton, flashOnButton, flashOffButton, takeImageButton;
    private FocusImageResponse focusImageResponseObj;
    private FocusCameraConfig focusCameraConfig;

    // Listeners
    private OnImageCaptureListener onImageCaptureListener;
    private OnClickListener onClickListener;

    // Counters
    private static final int minUserZoom = 0;
    private float currentFingersDistance;
    private static int imageCounter = 0;
    private int zoomCounter = 0;


    public enum FlashMode {
        FLASH_MODE_AUTO(0), FLASH_MODE_OFF(1), FLASH_MODE_ON(2);

        private int value;

        FlashMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static String getFlashMode(FlashMode flashMode) {
            if (flashMode == null) {
                flashMode = FlashMode.FLASH_MODE_AUTO;
            }
            switch (flashMode) {
                case FLASH_MODE_ON:
                    return Camera.Parameters.FLASH_MODE_ON;
                case FLASH_MODE_OFF:
                    return Camera.Parameters.FLASH_MODE_OFF;
                default:
                    return Camera.Parameters.FLASH_MODE_AUTO;
            }
        }

        public static FlashMode fromInt(int i) {
            for (FlashMode b : FlashMode.values()) {
                if (b.getValue() == i) {
                    return b;
                }
            }
            return null;
        }
    }

    public enum ImageFormats {
        JPG(0), PNG(1);

        private int value;

        ImageFormats(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static String getImageFormats(ImageFormats imageFormats) {
            if (imageFormats == null) {
                imageFormats = ImageFormats.JPG;
            }
            switch (imageFormats) {
                case PNG:
                    return ".png";
                default:
                    return ".jpg";
            }
        }


        public static ImageFormats fromInt(int i) {
            for (ImageFormats b : ImageFormats.values()) {
                if (b.getValue() == i) {
                    return b;
                }
            }
            return null;
        }
    }


    public FocusCamera(Context context) {
        super(context);
        inflateLayout();
        createImagePathDir();
    }

    public FocusCamera(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflateLayout();
        init(attrs);
        createImagePathDir();
    }

    public FocusCamera(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inflateLayout();
        init(attrs);
        createImagePathDir();
    }

    private void init(AttributeSet attrs) {
        focusCameraConfig = new FocusCameraConfig();
        int maxZoomUserValue = 0, minZoomUserValue = 0;
        Camera.Parameters imageParameters = null;
        if (isCameraHardwareExist()) {
            camera = getCameraInstance();
            imageParameters = camera.getParameters();
        }
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.FocusCamera);
        final int N = a.getIndexCount();
        for (int i = 0; i < N; ++i) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.FocusCamera_fileName:
                    focusCameraConfig.setImageName(a.getString(attr));
                    break;

                case R.styleable.FocusCamera_fileFormat:
                    focusCameraConfig.setFormat(ImageFormats.fromInt(a.getInt(attr, ImageFormats.JPG.ordinal())));
                    break;
                case R.styleable.FocusCamera_flashMode:
                    if (imageParameters != null) {
                        String mode = FlashMode.getFlashMode(FlashMode.fromInt(a.getInt(attr, FlashMode.FLASH_MODE_AUTO.ordinal())));
                        imageParameters.setFlashMode(mode);
                    }
                    break;
                case R.styleable.FocusCamera_maxZoom:
                    focusCameraConfig.setMaxZoom(a.getInt(attr, imageParameters.getMaxZoom()));
                    break;
                case R.styleable.FocusCamera_minZoom:
                    focusCameraConfig.setMinZoom(a.getInt(attr, minZoomUserValue));
                    break;
            }
        }
        focusCameraConfig.setParameters(imageParameters);
        setStartZoom(imageParameters, maxZoomUserValue, minZoomUserValue);
        a.recycle();
    }

    private void setStartZoom(Camera.Parameters imageParameters, int maxZoomUserValue, int minZoomUserValue) {
        if (imageParameters != null && maxZoomUserValue != 0) {
            if (focusCameraConfig.getMaxZoom() > imageParameters.getMaxZoom()) {
                imageParameters.setZoom(Math.round((imageParameters.getMaxZoom() * minZoomUserValue) / maxZoomUserValue));
            } else {
                imageParameters.setZoom((imageParameters.getMaxZoom() * minZoomUserValue) / maxZoomUserValue);
            }
        }
    }

    private void inflateLayout() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.focuscamera_view, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        flashAutoButton = (Button) findViewById(R.id.flashAutoButton);
        flashOnButton = (Button) findViewById(R.id.flashOnButton);
        flashOffButton = (Button) findViewById(R.id.flashOffButton);
        takeImageButton = (Button) findViewById(R.id.takeImageButton);
        onClickListener = new OnClickListener() {

            @Override
            public void onClick(View v) {
                Camera.Parameters params = camera.getParameters();
                switch (v.getId()) {
                    case R.id.takeImageButton:
                        takeImageButton.setClickable(false);
                        camera.takePicture(null, null, pictureCallback);
                        break;
                    case R.id.flashAutoButton:
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                        break;
                    case R.id.flashOffButton:
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        break;
                    case R.id.flashOnButton:
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                        break;
                }
                camera.setParameters(params);
            }
        };
        flashAutoButton.setOnClickListener(onClickListener);
        flashOffButton.setOnClickListener(onClickListener);
        takeImageButton.setOnClickListener(onClickListener);
        flashOnButton.setOnClickListener(onClickListener);
    }

    /**
     * @return if device supported camera
     */
    public void startCamera() {
        try {
            camera.setParameters(focusCameraConfig.getParameters());
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        // Create Surface view and set it to FrameLayout created in xml
        CameraSurface focusCameraSurface = new CameraSurface(getContext(), camera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(focusCameraSurface);
    }

    public boolean isCameraHardwareExist() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    public void stopCamera() {
        camera.release();
    }

    private static Camera getCameraInstance() {
        Camera camera = null;
        try {
            camera = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return camera; // returns null if camera is unavailable
    }


    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            camera.startPreview();
            takeImageButton.setClickable(true);
            new FileAccessAsync().execute(data);
        }
    };

    public interface OnImageCaptureListener {
        void imageCaptureSuccess(FocusImageResponse imageObject);
    }

    public void setOnCameraListener(OnImageCaptureListener onImageCaptureListener) {
        this.onImageCaptureListener = onImageCaptureListener;
    }


    private String getPicturesPath() {
        return Environment.getExternalStorageDirectory().getPath() + "/Android/data/" + getContext().getPackageName() + "/pictures/";
    }

    private void createImagePathDir() {
        File file = new File(getPicturesPath());
        if (!file.exists()) {
            file.mkdir();
        }
    }

    private class FileAccessAsync extends AsyncTask<byte[], Void, String> {
        File imageFile;

        @Override
        protected void onPreExecute() {
            imageFile = new File(getPicturesPath(), focusCameraConfig.getImageName() + imageCounter++
                    + ImageFormats.getImageFormats(focusCameraConfig.getFormat()));
        }

        @Override
        protected String doInBackground(byte[]... params) {
            try {
                FileOutputStream fos = new FileOutputStream(imageFile);
                fos.write(params[0]);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d("tag", "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d("tag", "Error accessing file: " + e.getMessage());
            }

            // Decode Image to Base64
            String imageBase64 = Base64.encodeToString(params[0], Base64.DEFAULT);
            return imageBase64;
        }

        @Override
        protected void onPostExecute(String imageBase64) {
            super.onPostExecute(imageBase64);

            focusImageResponseObj = new FocusImageResponse();
            focusImageResponseObj.setImagebase64(imageBase64);
            focusImageResponseObj.setImageFile(imageFile);

            if (onImageCaptureListener != null) {
                onImageCaptureListener.imageCaptureSuccess(focusImageResponseObj);
            }
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE && focusCameraConfig.getParameters().isZoomSupported()) {
            zoomCamera(event, focusCameraConfig.getParameters());
        }
        return true;
    }


    private void zoomCamera(MotionEvent event, Camera.Parameters parameters) {
        if (parameters.getZoom() == minUserZoom) {
            zoomCounter = 1;
        }

        int currentZoom;
        if (focusCameraConfig.getMaxZoom() == 0 && focusCameraConfig.getMinZoom() == 0) {
            currentZoom = zoomCounter;
        } else {
            currentZoom = ((parameters.getMaxZoom() * zoomCounter) / focusCameraConfig.getMaxZoom());

            if (focusCameraConfig.getMaxZoom() > parameters.getMaxZoom()) {
                currentZoom = Math.round(currentZoom);
                if (currentZoom == 0) {
                    currentZoom = 1;
                }
            }
        }

        float newFingersDistance = getFingerSpacing(event);

        if (newFingersDistance > currentFingersDistance) {
            // Zoom in
            if (currentZoom < parameters.getMaxZoom() && currentFingersDistance != 0) {
                zoomCounter++;
            }
        } else if (newFingersDistance < currentFingersDistance) {
            // Zoom out
            if (currentZoom > 0 && currentZoom > focusCameraConfig.getMinZoom() && zoomCounter != 1) {
                zoomCounter--;
            }
        }

        // Update current distance
        currentFingersDistance = newFingersDistance;
        parameters.setZoom(currentZoom);

        try {
            camera.setParameters(parameters);
            focusCameraConfig.setParameters(parameters);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    private float getFingerSpacing(MotionEvent event) {
        if (event.getPointerCount() >= 2) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);

            return (float) Math.sqrt(x * x + y * y);
        }
        return 0;
    }

    public void setImageConfig(FocusCameraConfig imageConfig) {
        this.focusCameraConfig = imageConfig;
        setImageFormat(imageConfig.getFormat());
        setImageFileName(imageConfig.getImageName());
        setImageParameters(imageConfig.getParameters());
        setMaxZoom(imageConfig.getMaxZoom());
        setMinZoom(imageConfig.getMinZoom());
        setVisibleAutoFlashButton(imageConfig.getCameraOptionsBit());
        setVisibleOnFlashButton(imageConfig.getCameraOptionsBit());
        setVisibleOffFlashButton(imageConfig.getCameraOptionsBit());
        setVisibleTakePictureButton(imageConfig.getCameraOptionsBit());
    }

    /**
     * @param visible bit option
     */
    public void setVisibleAutoFlashButton(int visible) {
        flashAutoButton.setVisibility(visible & focusCameraConfig.getAutoFlashButton());
    }

    private void setVisibleOnFlashButton(int visible) {
        flashOnButton.setVisibility(visible & focusCameraConfig.getOnFlashButton());
    }

    private void setVisibleOffFlashButton(int visible) {
        flashOffButton.setVisibility(visible & focusCameraConfig.getOffFlashButton());
    }

    private void setVisibleTakePictureButton(int visible) {
        takeImageButton.setVisibility(visible & focusCameraConfig.getTakePictureButton());
    }

    public void setMaxZoom(int maxZoom) {
        focusCameraConfig.setMaxZoom(maxZoom);
    }

    public void setMinZoom(int minZoom) {
        focusCameraConfig.setMinZoom(minZoom);
    }

    public void setImageFormat(ImageFormats format) {
        focusCameraConfig.setFormat(format);
    }

    public void setImageFileName(String name) {
        focusCameraConfig.setImageName(name);
    }

    public void setImageParameters(Camera.Parameters parameters) {
        focusCameraConfig.setParameters(parameters);
    }

    public int getMaxZoom() {
        return focusCameraConfig.getMaxZoom();
    }

    public int getMinZoom() {
        return focusCameraConfig.getMinZoom();
    }

    public String getImageFileName() {
        return focusCameraConfig.getImageName();
    }

    public ImageFormats getImageFormat() {
        return focusCameraConfig.getFormat();
    }

    public Camera.Parameters getImageParameters() {
        return focusCameraConfig.getParameters();
    }

    private class CameraSurface extends SurfaceView implements SurfaceHolder.Callback {

        private final String TAG = CameraSurface.class.getSimpleName();
        private Camera mCamera;
        private SurfaceHolder mHolder;

        public CameraSurface(Context context, Camera mCamera) {
            super(context);
            this.mCamera = mCamera;
            mHolder = getHolder();

            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            // Add SurfaceHolder callback
            mHolder.addCallback(this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // Camera view created
            try {
                mCamera.setPreviewDisplay(holder);
                setCameraParams();
                // Start camera preview
                mCamera.startPreview();
            } catch (IOException e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // Camera orientation changed

            // Check if Surface view exists
            if (mHolder.getSurface() == null)
                return;

            // Stop camera before making any changes
            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                Log.d(TAG, "Tried to stop a non existent surface preview");
            }

            setCameraDisplayOrientation((Activity) getContext(), 0, mCamera);
            setCameraParams();
            // Start Surface preview with new settings
            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();
            } catch (IOException e) {
                Log.d(TAG, "Error starting camera preview after orientation change: " + e.getMessage());
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }

        private void setCameraParams() {
            // Modify camera parameters
            Camera.Parameters params = mCamera.getParameters();
            // Set focus mode to auto
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            // Set parameters to camera
            mCamera.setParameters(params);
        }

        public void setCameraDisplayOrientation(Activity activity,
                                                int cameraId, android.hardware.Camera camera) {
            android.hardware.Camera.CameraInfo info =
                    new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            int rotation = activity.getWindowManager().getDefaultDisplay()
                    .getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            int result;
            // Check if Front camera in use
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
            camera.setDisplayOrientation(result);
        }
    }
}
