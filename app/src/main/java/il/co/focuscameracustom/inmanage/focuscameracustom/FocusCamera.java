package il.co.focuscameracustom.inmanage.focuscameracustom;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FocusCamera extends RelativeLayout {

    // Constants ///////////////////////////////////////////////////////////////////////////////////

    private static final int HANDLE_SIZE_IN_DP = 10;
    private static final int MIN_FRAME_SIZE_IN_DP = 100;
    private static final int FRAME_STROKE_WEIGHT_IN_DP = 1;
    private static final int GUIDE_STROKE_WEIGHT_IN_DP = 1;

    private final int TRANSPARENT = getResources().getColor(android.R.color.transparent);
    private final int TRANSLUCENT_WHITE = 0xBBFFFFFF;
    private final int WHITE = 0xFFFFFFFF;
    private final int TRANSLUCENT_BLACK = 0xBB000000;

    // Member variables ////////////////////////////////////////////////////////////////////////////

    private int mViewWidth = 0;
    private int mViewHeight = 0;
    private float mScale = 1.0f;
    private float mAngle = 0.0f;
    private float mImgWidth = 0.0f;
    private float mImgHeight = 0.0f;
    private boolean mIsInitialized = false;
    private Matrix mMatrix = null;
    private Paint mPaintTransparent;
    private Paint mPaintFrame;
    private Paint mPaintBitmap;
    private RectF mFrameRect;
    private RectF mImageRect;
    private PointF mCenter = new PointF();
    private float mLastX, mLastY;

    // Instance variables for customizable attributes //////////////////////////////////////////////

    private TouchArea mTouchArea = TouchArea.OUT_OF_BOUNDS;
    private CropMode mCropMode = CropMode.RATIO_FREE;
    private ShowMode mGuideShowMode = ShowMode.SHOW_ALWAYS;
    private ShowMode mHandleShowMode = ShowMode.SHOW_ALWAYS;
    private float mMinFrameSize;
    private int mHandleSize;
    private int mTouchPadding = 0;
    private boolean mShowGuide = true;
    private boolean mShowHandle = true;
    private boolean mIsCropEnabled = true;
    private boolean mIsEnabled = true;
    private float mFrameStrokeWeight = 3.0f;
    private float mGuideStrokeWeight = 3.0f;
    private int mBackgroundColor;
    private int mOverlayColor;
    private int mFrameColor;
    private int mHandleColor;
    private int mGuideColor;
    private double mInitialFrameScale = 0.5; // 0.01 ~ 1.0, 0.75 is default value
    private Bitmap bitmap;



    private static final String TAG = FocusCamera.class.getSimpleName();
    private Camera camera;
    private Button flashAutoButton, flashOnButton, flashOffButton, takeImageButton;
    private FocusImageResponse focusImageResponseObj;
    private FocusCameraConfig focusCameraConfig;
    private RelativeLayout cameraRelativeLayout;
    private LinearLayout imageCropLayout;

    // Listeners
    private OnImageCaptureListener onImageCaptureListener;
    private OnClickListener cameraOnClickListener;

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
        cameraRelativeLayout = (RelativeLayout) findViewById(R.id.cameraRelativeLayout);
        imageCropLayout = (LinearLayout) findViewById(R.id.imageCropLayout);

        flashAutoButton = (Button) findViewById(R.id.flashAutoButton);
        flashOnButton = (Button) findViewById(R.id.flashOnButton);
        flashOffButton = (Button) findViewById(R.id.flashOffButton);
        takeImageButton = (Button) findViewById(R.id.takeImageButton);

        cameraOnClickListener = new OnClickListener() {

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
        flashAutoButton.setOnClickListener(cameraOnClickListener);
        flashOffButton.setOnClickListener(cameraOnClickListener);
        takeImageButton.setOnClickListener(cameraOnClickListener);
        flashOnButton.setOnClickListener(cameraOnClickListener);
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

    public void startCropImage(Bitmap bitmap) {

        if (camera != null) {
            stopCamera();
        }
        cameraRelativeLayout.setVisibility(View.GONE);
        imageCropLayout.setVisibility(View.VISIBLE);
        CropImageView cropImageView = new CropImageView(getContext(), bitmap);
        FrameLayout imageCrop = (FrameLayout) findViewById(R.id.cropImageFrame);
        imageCrop.addView(cropImageView);

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

    public class CropImageView extends ImageView {

        // Constructor
        public CropImageView(Context context, Bitmap b) {
            super(context);
            bitmap = b;
            float mDensity = getDensity();
            mHandleSize = (int) (mDensity * HANDLE_SIZE_IN_DP);
            mMinFrameSize = mDensity * MIN_FRAME_SIZE_IN_DP;
            mFrameStrokeWeight = mDensity * FRAME_STROKE_WEIGHT_IN_DP;
            mGuideStrokeWeight = mDensity * GUIDE_STROKE_WEIGHT_IN_DP;

            mPaintFrame = new Paint();
            mPaintTransparent = new Paint();
            mPaintBitmap = new Paint();
            mPaintBitmap.setFilterBitmap(true);

            mMatrix = new Matrix();
            mScale = 1.0f;
            mBackgroundColor = TRANSPARENT;
            mFrameColor = WHITE;
            mOverlayColor = TRANSLUCENT_BLACK;
            mHandleColor = WHITE;
            mGuideColor = TRANSLUCENT_WHITE;
            setImageBitmap(bitmap);
        }




        @Override
        public void setImageBitmap(Bitmap bitmap) {
            mIsInitialized = false;
            super.setImageBitmap(bitmap);
            updateDrawableInfo();
        }

        private void updateDrawableInfo() {
            Drawable d = getDrawable();
            if (d != null) {
                initLayout(mViewWidth, mViewHeight);
            }
        }
        // Lifecycle methods ///////////////////////////////////////////////////////////////////////////


        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            final int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
            final int viewHeight = MeasureSpec.getSize(heightMeasureSpec);

            setMeasuredDimension(viewWidth, viewHeight);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            mViewWidth = r - l - getPaddingLeft() - getPaddingRight();
            mViewHeight = b - t - getPaddingTop() - getPaddingBottom();
            if (getDrawable() != null) initLayout(mViewWidth, mViewHeight);
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (mIsInitialized) {
                setMatrix();
                Matrix localMatrix1 = new Matrix();
                localMatrix1.postConcat(mMatrix);
                Bitmap bm = bitmap;
                if (bm != null) {
                    canvas.drawBitmap(bm, localMatrix1, mPaintBitmap);
                    // draw edit frame
                    drawEditFrame(canvas);
                }
            }
        }


        // Drawing method //////////////////////////////////////////////////////////////////////////////

        private void drawEditFrame(Canvas canvas) {
            if (!mIsCropEnabled) return;

            if (mCropMode == CropMode.CIRCLE) {
                mPaintTransparent.setFilterBitmap(true);
                mPaintTransparent.setColor(mOverlayColor);
                mPaintTransparent.setStyle(Paint.Style.FILL);

                Path path = new Path();
                path.addRect(mImageRect.left, mImageRect.top, mImageRect.right, mImageRect.bottom, Path.Direction.CW);
                path.addCircle((mFrameRect.left + mFrameRect.right) / 2, (mFrameRect.top + mFrameRect.bottom) / 2, (mFrameRect.right - mFrameRect.left) / 2, Path.Direction.CCW);
                canvas.drawPath(path, mPaintTransparent);

            } else {
                mPaintTransparent.setFilterBitmap(true);
                mPaintTransparent.setColor(mOverlayColor);
                mPaintTransparent.setStyle(Paint.Style.FILL);

                canvas.drawRect(mImageRect.left, mImageRect.top, mImageRect.right, mFrameRect.top, mPaintTransparent);
                canvas.drawRect(mImageRect.left, mFrameRect.bottom, mImageRect.right, mImageRect.bottom, mPaintTransparent);
                canvas.drawRect(mImageRect.left, mFrameRect.top, mFrameRect.left, mFrameRect.bottom, mPaintTransparent);
                canvas.drawRect(mFrameRect.right, mFrameRect.top, mImageRect.right, mFrameRect.bottom, mPaintTransparent);
            }

            mPaintFrame.setAntiAlias(true);
            mPaintFrame.setFilterBitmap(true);
            mPaintFrame.setStyle(Paint.Style.STROKE);
            mPaintFrame.setColor(mFrameColor);
            mPaintFrame.setStrokeWidth(mFrameStrokeWeight);

            canvas.drawRect(mFrameRect.left, mFrameRect.top, mFrameRect.right, mFrameRect.bottom, mPaintFrame);

            if (mShowGuide) {
                mPaintFrame.setColor(mGuideColor);
                mPaintFrame.setStrokeWidth(mGuideStrokeWeight);
                float h1 = mFrameRect.left + (mFrameRect.right - mFrameRect.left) / 3.0f;
                float h2 = mFrameRect.right - (mFrameRect.right - mFrameRect.left) / 3.0f;
                float v1 = mFrameRect.top + (mFrameRect.bottom - mFrameRect.top) / 3.0f;
                float v2 = mFrameRect.bottom - (mFrameRect.bottom - mFrameRect.top) / 3.0f;

                canvas.drawLine(h1, mFrameRect.top, h1, mFrameRect.bottom, mPaintFrame);
                canvas.drawLine(h2, mFrameRect.top, h2, mFrameRect.bottom, mPaintFrame);
                canvas.drawLine(mFrameRect.left, v1, mFrameRect.right, v1, mPaintFrame);
                canvas.drawLine(mFrameRect.left, v2, mFrameRect.right, v2, mPaintFrame);
            }

            if (mShowHandle) {
                mPaintFrame.setStyle(Paint.Style.FILL);
                mPaintFrame.setColor(mHandleColor);
                canvas.drawCircle(mFrameRect.left, mFrameRect.top, mHandleSize, mPaintFrame);
                canvas.drawCircle(mFrameRect.right, mFrameRect.top, mHandleSize, mPaintFrame);
                canvas.drawCircle(mFrameRect.left, mFrameRect.bottom, mHandleSize, mPaintFrame);
                canvas.drawCircle(mFrameRect.right, mFrameRect.bottom, mHandleSize, mPaintFrame);
            }
        }

        private void setMatrix() {
            mMatrix.reset();
            mMatrix.setTranslate(mCenter.x - mImgWidth * 0.5f, mCenter.y - mImgHeight * 0.5f);
            mMatrix.postScale(mScale, mScale, mCenter.x, mCenter.y);
            mMatrix.postRotate(mAngle, mCenter.x, mCenter.y);
        }

        // Initializer /////////////////////////////////////////////////////////////////////////////////

        private void initLayout(int viewW, int viewH) {
            mImgWidth = getDrawable().getIntrinsicWidth();
            mImgHeight = getDrawable().getIntrinsicHeight();
            if (mImgWidth <= 0) mImgWidth = viewW;
            if (mImgHeight <= 0) mImgHeight = viewH;
            float w = (float) viewW;
            float h = (float) viewH;
            float viewRatio = w / h;
            float imgRatio = mImgWidth / mImgHeight;
            float scale = 1.0f;
            if (imgRatio >= viewRatio) {
                scale = w / mImgWidth;
            } else if (imgRatio < viewRatio) {
                scale = h / mImgHeight;
            }
            setCenter(new PointF(getPaddingLeft() + w * 0.5f, getPaddingTop() + h * 0.5f));
            setScale(scale);
            initCropFrame();
            adjustRatio();
            mIsInitialized = true;
        }

        private void initCropFrame() {
            setMatrix();
            float[] arrayOfFloat = new float[8];
            arrayOfFloat[0] = 0.0f;
            arrayOfFloat[1] = 0.0f;
            arrayOfFloat[2] = 0.0f;
            arrayOfFloat[3] = mImgHeight;
            arrayOfFloat[4] = mImgWidth;
            arrayOfFloat[5] = 0.0f;
            arrayOfFloat[6] = mImgWidth;
            arrayOfFloat[7] = mImgHeight;

            mMatrix.mapPoints(arrayOfFloat);

            float l = arrayOfFloat[0];
            float t = arrayOfFloat[1];
            float r = arrayOfFloat[6];
            float b = arrayOfFloat[7];

            mFrameRect = new RectF(l, t, r, b);
            mImageRect = new RectF(l, t, r, b);
        }

        // Touch Event /////////////////////////////////////////////////////////////////////////////////

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!mIsInitialized) return false;
            if (!mIsCropEnabled) return false;
            if (!mIsEnabled) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    onDown(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    onMove(event);
                    if (mTouchArea != TouchArea.OUT_OF_BOUNDS) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    onCancel();
                    return true;
                case MotionEvent.ACTION_UP:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    onUp(event);
                    return true;
            }
            return false;
        }


        private void onDown(MotionEvent e) {
            invalidate();
            mLastX = e.getX();
            mLastY = e.getY();
            checkTouchArea(e.getX(), e.getY());
        }

        private void onMove(MotionEvent e) {
            float diffX = e.getX() - mLastX;
            float diffY = e.getY() - mLastY;
            switch (mTouchArea) {
                case CENTER:
                    moveFrame(diffX, diffY);
                    break;
                case LEFT_TOP:
                    moveHandleLT(diffX, diffY);
                    break;
                case RIGHT_TOP:
                    moveHandleRT(diffX, diffY);
                    break;
                case LEFT_BOTTOM:
                    moveHandleLB(diffX, diffY);
                    break;
                case RIGHT_BOTTOM:
                    moveHandleRB(diffX, diffY);
                    break;
                case OUT_OF_BOUNDS:
                    break;
            }
            invalidate();
            mLastX = e.getX();
            mLastY = e.getY();
        }

        private void onUp(MotionEvent e) {
            mTouchArea = TouchArea.OUT_OF_BOUNDS;
            invalidate();
        }

        private void onCancel() {
            mTouchArea = TouchArea.OUT_OF_BOUNDS;
            invalidate();
        }

        // Hit test ////////////////////////////////////////////////////////////////////////////////////

        private void checkTouchArea(float x, float y) {
            if (isInsideCornerLeftTop(x, y)) {
                mTouchArea = TouchArea.LEFT_TOP;
                return;
            }
            if (isInsideCornerRightTop(x, y)) {
                mTouchArea = TouchArea.RIGHT_TOP;
                return;
            }
            if (isInsideCornerLeftBottom(x, y)) {
                mTouchArea = TouchArea.LEFT_BOTTOM;
                return;
            }
            if (isInsideCornerRightBottom(x, y)) {
                mTouchArea = TouchArea.RIGHT_BOTTOM;
                return;
            }
            if (isInsideFrame(x, y)) {
                mTouchArea = TouchArea.CENTER;
                return;
            }
            mTouchArea = TouchArea.OUT_OF_BOUNDS;
        }

        private boolean isInsideFrame(float x, float y) {
            if (mFrameRect.left <= x && mFrameRect.right >= x) {
                if (mFrameRect.top <= y && mFrameRect.bottom >= y) {
                    mTouchArea = TouchArea.CENTER;
                    return true;
                }
            }
            return false;
        }

        private boolean isInsideCornerLeftTop(float x, float y) {
            float dx = x - mFrameRect.left;
            float dy = y - mFrameRect.top;
            float d = dx * dx + dy * dy;
            return sq(mHandleSize + mTouchPadding) >= d;
        }

        private boolean isInsideCornerRightTop(float x, float y) {
            float dx = x - mFrameRect.right;
            float dy = y - mFrameRect.top;
            float d = dx * dx + dy * dy;
            return sq(mHandleSize + mTouchPadding) >= d;
        }

        private boolean isInsideCornerLeftBottom(float x, float y) {
            float dx = x - mFrameRect.left;
            float dy = y - mFrameRect.bottom;
            float d = dx * dx + dy * dy;
            return sq(mHandleSize + mTouchPadding) >= d;
        }

        private boolean isInsideCornerRightBottom(float x, float y) {
            float dx = x - mFrameRect.right;
            float dy = y - mFrameRect.bottom;
            float d = dx * dx + dy * dy;
            return sq(mHandleSize + mTouchPadding) >= d;
        }

        // Adjust frame ////////////////////////////////////////////////////////////////////////////////

        private void moveFrame(float x, float y) {
            mFrameRect.left += x;
            mFrameRect.right += x;
            mFrameRect.top += y;
            mFrameRect.bottom += y;
            checkMoveBounds();
        }

        private void moveHandleLT(float diffX, float diffY) {
            if (mCropMode == CropMode.RATIO_FREE) {
                mFrameRect.left += diffX;
                mFrameRect.top += diffY;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.left -= offsetX;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.top -= offsetY;
                }
                checkScaleBounds();
            } else {
                float dx = diffX;
                float dy = diffX * getRatioY() / getRatioX();
                mFrameRect.left += dx;
                mFrameRect.top += dy;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.left -= offsetX;
                    float offsetY = offsetX * getRatioY() / getRatioX();
                    mFrameRect.top -= offsetY;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.top -= offsetY;
                    float offsetX = offsetY * getRatioX() / getRatioY();
                    mFrameRect.left -= offsetX;
                }
                float ox, oy;
                if (!isInsideHorizontal(mFrameRect.left)) {
                    ox = mImageRect.left - mFrameRect.left;
                    mFrameRect.left += ox;
                    oy = ox * getRatioY() / getRatioX();
                    mFrameRect.top += oy;
                }
                if (!isInsideVertical(mFrameRect.top)) {
                    oy = mImageRect.top - mFrameRect.top;
                    mFrameRect.top += oy;
                    ox = oy * getRatioX() / getRatioY();
                    mFrameRect.left += ox;
                }
            }
        }

        private void moveHandleRT(float diffX, float diffY) {
            if (mCropMode == CropMode.RATIO_FREE) {
                mFrameRect.right += diffX;
                mFrameRect.top += diffY;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.right += offsetX;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.top -= offsetY;
                }
                checkScaleBounds();
            } else {
                float dx = diffX;
                float dy = diffX * getRatioY() / getRatioX();
                mFrameRect.right += dx;
                mFrameRect.top -= dy;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.right += offsetX;
                    float offsetY = offsetX * getRatioY() / getRatioX();
                    mFrameRect.top -= offsetY;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.top -= offsetY;
                    float offsetX = offsetY * getRatioX() / getRatioY();
                    mFrameRect.right += offsetX;
                }
                float ox, oy;
                if (!isInsideHorizontal(mFrameRect.right)) {
                    ox = mFrameRect.right - mImageRect.right;
                    mFrameRect.right -= ox;
                    oy = ox * getRatioY() / getRatioX();
                    mFrameRect.top += oy;
                }
                if (!isInsideVertical(mFrameRect.top)) {
                    oy = mImageRect.top - mFrameRect.top;
                    mFrameRect.top += oy;
                    ox = oy * getRatioX() / getRatioY();
                    mFrameRect.right -= ox;
                }
            }
        }

        private void moveHandleLB(float diffX, float diffY) {
            if (mCropMode == CropMode.RATIO_FREE) {
                mFrameRect.left += diffX;
                mFrameRect.bottom += diffY;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.left -= offsetX;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.bottom += offsetY;
                }
                checkScaleBounds();
            } else {
                float dx = diffX;
                float dy = diffX * getRatioY() / getRatioX();
                mFrameRect.left += dx;
                mFrameRect.bottom -= dy;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.left -= offsetX;
                    float offsetY = offsetX * getRatioY() / getRatioX();
                    mFrameRect.bottom += offsetY;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.bottom += offsetY;
                    float offsetX = offsetY * getRatioX() / getRatioY();
                    mFrameRect.left -= offsetX;
                }
                float ox, oy;
                if (!isInsideHorizontal(mFrameRect.left)) {
                    ox = mImageRect.left - mFrameRect.left;
                    mFrameRect.left += ox;
                    oy = ox * getRatioY() / getRatioX();
                    mFrameRect.bottom -= oy;
                }
                if (!isInsideVertical(mFrameRect.bottom)) {
                    oy = mFrameRect.bottom - mImageRect.bottom;
                    mFrameRect.bottom -= oy;
                    ox = oy * getRatioX() / getRatioY();
                    mFrameRect.left += ox;
                }
            }
        }

        private void moveHandleRB(float diffX, float diffY) {
            if (mCropMode == CropMode.RATIO_FREE) {
                mFrameRect.right += diffX;
                mFrameRect.bottom += diffY;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.right += offsetX;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.bottom += offsetY;
                }
                checkScaleBounds();
            } else {
                float dx = diffX;
                float dy = diffX * getRatioY() / getRatioX();
                mFrameRect.right += dx;
                mFrameRect.bottom += dy;
                if (isWidthTooSmall()) {
                    float offsetX = mMinFrameSize - getFrameW();
                    mFrameRect.right += offsetX;
                    float offsetY = offsetX * getRatioY() / getRatioX();
                    mFrameRect.bottom += offsetY;
                }
                if (isHeightTooSmall()) {
                    float offsetY = mMinFrameSize - getFrameH();
                    mFrameRect.bottom += offsetY;
                    float offsetX = offsetY * getRatioX() / getRatioY();
                    mFrameRect.right += offsetX;
                }
                float ox, oy;
                if (!isInsideHorizontal(mFrameRect.right)) {
                    ox = mFrameRect.right - mImageRect.right;
                    mFrameRect.right -= ox;
                    oy = ox * getRatioY() / getRatioX();
                    mFrameRect.bottom -= oy;
                }
                if (!isInsideVertical(mFrameRect.bottom)) {
                    oy = mFrameRect.bottom - mImageRect.bottom;
                    mFrameRect.bottom -= oy;
                    ox = oy * getRatioX() / getRatioY();
                    mFrameRect.right -= ox;
                }
            }
        }

        // Frame position correction ///////////////////////////////////////////////////////////////////

        private void checkScaleBounds() {
            float lDiff = mFrameRect.left - mImageRect.left;
            float rDiff = mFrameRect.right - mImageRect.right;
            float tDiff = mFrameRect.top - mImageRect.top;
            float bDiff = mFrameRect.bottom - mImageRect.bottom;

            if (lDiff < 0) {
                mFrameRect.left -= lDiff;
            }
            if (rDiff > 0) {
                mFrameRect.right -= rDiff;
            }
            if (tDiff < 0) {
                mFrameRect.top -= tDiff;
            }
            if (bDiff > 0) {
                mFrameRect.bottom -= bDiff;
            }
        }

        private void checkMoveBounds() {
            float diff = mFrameRect.left - mImageRect.left;
            if (diff < 0) {
                mFrameRect.left -= diff;
                mFrameRect.right -= diff;
            }
            diff = mFrameRect.right - mImageRect.right;
            if (diff > 0) {
                mFrameRect.left -= diff;
                mFrameRect.right -= diff;
            }
            diff = mFrameRect.top - mImageRect.top;
            if (diff < 0) {
                mFrameRect.top -= diff;
                mFrameRect.bottom -= diff;
            }
            diff = mFrameRect.bottom - mImageRect.bottom;
            if (diff > 0) {
                mFrameRect.top -= diff;
                mFrameRect.bottom -= diff;
            }
        }

        private boolean isInsideHorizontal(float x) {
            return mImageRect.left <= x && mImageRect.right >= x;
        }

        private boolean isInsideVertical(float y) {
            return mImageRect.top <= y && mImageRect.bottom >= y;
        }

        private boolean isWidthTooSmall() {
            return getFrameW() < mMinFrameSize;
        }

        private boolean isHeightTooSmall() {
            return getFrameH() < mMinFrameSize;
        }


        // Utility methods /////////////////////////////////////////////////////////////////////////////

        private float getDensity() {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.density;
        }

        private float sq(float value) {
            return value * value;
        }

    }

    // Enum ////////////////////////////////////////////////////////////////////////////////////////

    public enum TouchArea {
        OUT_OF_BOUNDS, CENTER, LEFT_TOP, RIGHT_TOP, LEFT_BOTTOM, RIGHT_BOTTOM;
    }

    public enum CropMode {
        RATIO_FREE(0), CIRCLE(1);
        private final int ID;

        private CropMode(final int id) {
            this.ID = id;
        }

        public int getId() {
            return ID;
        }
    }

    public enum ShowMode {
        SHOW_ALWAYS(1);
        private final int ID;

        private ShowMode(final int id) {
            this.ID = id;
        }

        public int getId() {
            return ID;
        }
    }

    public enum RotateDegrees {
        ROTATE_90D(90), ROTATE_180D(180), ROTATE_270D(270);

        private final int VALUE;

        private RotateDegrees(final int value) {
            this.VALUE = value;
        }

        public int getValue() {
            return VALUE;
        }
    }

    private float getRatioX(float w) {
        switch (mCropMode) {
            case CIRCLE:
                return 1.0f;
            default:
                return w;
        }
    }

    private float getRatioY(float h) {
        switch (mCropMode) {
            case CIRCLE:
                return 1.0f;
            default:
                return h;
        }
    }

    private float getRatioX() {
        return 1.0f;
    }

    private float getRatioY() {
        return 1.0f;

    }

    // Public methods //////////////////////////////////////////////////////////////////////////////


    // Frame aspect ratio correction ///////////////////////////////////////////////////////////////

    public void adjustRatio() {
        if (mImageRect == null) return;
        float imgW = mImageRect.right - mImageRect.left;
        float imgH = mImageRect.bottom - mImageRect.top;
        float frameW = getRatioX(imgW);
        float frameH = getRatioY(imgH);
        float imgRatio = imgW / imgH;
        float frameRatio = frameW / frameH;
        float l = mImageRect.left, t = mImageRect.top, r = mImageRect.right, b = mImageRect.bottom;
        if (frameRatio >= imgRatio) {
            l = mImageRect.left;
            r = mImageRect.right;
            float hy = (mImageRect.top + mImageRect.bottom) * 0.5f;
            float hh = (imgW / frameRatio) * 0.5f;
            t = hy - hh;
            b = hy + hh;
        } else if (frameRatio < imgRatio) {
            t = mImageRect.top;
            b = mImageRect.bottom;
            float hx = (mImageRect.left + mImageRect.right) * 0.5f;
            float hw = imgH * frameRatio * 0.5f;
            l = hx - hw;
            r = hx + hw;
        }
        float w = r - l;
        float h = b - t;
        float cx = l + w / 2;
        float cy = t + h / 2;
        float sw = w * (float) mInitialFrameScale;
        float sh = h * (float) mInitialFrameScale;
        mFrameRect = new RectF(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2);
        invalidate();
    }

    /**
     * Get source image bitmap
     *
     * @return src bitmap
     */
    public Bitmap getImageBitmap() {
        return getBitmap();
    }


    /**
     * Get cropped image bitmap
     *
     * @return cropped image bitmap
     */
    public Bitmap getCroppedBitmap() {
        Bitmap source = bitmap;
        if (source == null) return null;

        int x, y, w, h;
        float l = (mFrameRect.left / mScale);
        float t = (mFrameRect.top / mScale);
        float r = (mFrameRect.right / mScale);
        float b = (mFrameRect.bottom / mScale);
        x = Math.round(l - (mImageRect.left / mScale));
        y = Math.round(t - (mImageRect.top / mScale));
        w = Math.round(r - l);
        h = Math.round(b - t);

        Bitmap cropped = Bitmap.createBitmap(source, x, y, w, h, null, false);
        if (mCropMode != CropMode.CIRCLE) return cropped;
        return getCircularBitmap(cropped);
    }

    /**
     * Get cropped rect image bitmap
     * <p/>
     * This method always returns rect image.
     * (If you need a square image with CropMode.CIRCLE, you can use this method.)
     *
     * @return cropped image bitmap
     */
    public Bitmap getRectBitmap() {
        Bitmap source = bitmap;
        if (source == null) return null;

        int x, y, w, h;
        float l = (mFrameRect.left / mScale);
        float t = (mFrameRect.top / mScale);
        float r = (mFrameRect.right / mScale);
        float b = (mFrameRect.bottom / mScale);
        x = Math.round(l - (mImageRect.left / mScale));
        y = Math.round(t - (mImageRect.top / mScale));
        w = Math.round(r - l);
        h = Math.round(b - t);

        return Bitmap.createBitmap(source, x, y, w, h, null, false);
    }

    private Bitmap getBitmap() {
        return bitmap;
    }

    /**
     * Crop the square image in a circular
     *
     * @param square image bitmap
     * @return circular image bitmap
     */
    public Bitmap getCircularBitmap(Bitmap square) {
        if (square == null) return null;
        Bitmap output = Bitmap.createBitmap(square.getWidth(), square.getHeight(),
                Bitmap.Config.ARGB_8888);

        final Rect rect = new Rect(0, 0, square.getWidth(), square.getHeight());
        Canvas canvas = new Canvas(output);

        int halfWidth = square.getWidth() / 2;
        int halfHeight = square.getHeight() / 2;

        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);

        canvas.drawCircle(halfWidth, halfHeight, Math.min(halfWidth, halfHeight), paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        canvas.drawBitmap(square, rect, rect, paint);
        return output;
    }


    /**
     * Get frame position relative to the source bitmap.
     *
     * @return crop area boundaries.
     */
    public RectF getActualCropRect() {
        float offsetX = (mImageRect.left / mScale);
        float offsetY = (mImageRect.top / mScale);
        float l = (mFrameRect.left / mScale) - offsetX;
        float t = (mFrameRect.top / mScale) - offsetY;
        float r = (mFrameRect.right / mScale) - offsetX;
        float b = (mFrameRect.bottom / mScale) - offsetY;
        return new RectF(l, t, r, b);
    }

    /**
     * Set crop mode
     *
     * @param mode crop mode
     */
    public void setCropMode(CropMode mode) {
        mCropMode = mode;
        adjustRatio();

    }


    /**
     * Set image overlay color
     *
     * @param overlayColor color resId or color int(ex. 0xFFFFFFFF)
     */
    public void setOverlayColor(int overlayColor) {
        this.mOverlayColor = overlayColor;
        invalidate();
    }

    /**
     * Set crop frame color
     *
     * @param frameColor color resId or color int(ex. 0xFFFFFFFF)
     */
    public void setFrameColor(int frameColor) {
        this.mFrameColor = frameColor;
        invalidate();
    }

    /**
     * Set handle color
     *
     * @param handleColor color resId or color int(ex. 0xFFFFFFFF)
     */
    public void setHandleColor(int handleColor) {
        this.mHandleColor = handleColor;
        invalidate();
    }

    /**
     * Set guide color
     *
     * @param guideColor color resId or color int(ex. 0xFFFFFFFF)
     */
    public void setGuideColor(int guideColor) {
        this.mGuideColor = guideColor;
        invalidate();
    }

    /**
     * Set view background color
     *
     * @param bgColor color resId or color int(ex. 0xFFFFFFFF)
     */
    public void setBackgroundColor(int bgColor) {
        this.mBackgroundColor = bgColor;
        super.setBackgroundColor(this.mBackgroundColor);
        invalidate();
    }


    /**
     * Set crop frame minimum size in pixels.
     *
     * @param minPx crop frame minimum size in pixels
     */
    public void setMinFrameSizeInPx(int minPx) {
        mMinFrameSize = minPx;
    }




    /**
     * Set whether to show crop frame.
     *
     * @param enabled should show crop frame?
     */
    public void setCropEnabled(boolean enabled) {
        mIsCropEnabled = enabled;
        invalidate();
    }

    /**
     * Set locking the crop frame.
     *
     * @param enabled should lock crop frame?
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mIsEnabled = enabled;
    }


    private void setScale(float mScale) {
        this.mScale = mScale;
    }

    private void setCenter(PointF mCenter) {
        this.mCenter = mCenter;
    }

    private float getFrameW() {
        return (mFrameRect.right - mFrameRect.left);
    }

    private float getFrameH() {
        return (mFrameRect.bottom - mFrameRect.top);
    }


}
