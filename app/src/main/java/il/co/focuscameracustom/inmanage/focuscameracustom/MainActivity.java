package il.co.focuscameracustom.inmanage.focuscameracustom;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by inmanage on 03/12/2015.
 */
public class MainActivity extends AppCompatActivity {

    private FocusCamera focusCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        initCamera();
    }

    private void initViews() {
        focusCamera = (FocusCamera) findViewById(R.id.focusCamera);
    }

    private void initCamera() {
        if (focusCamera.isCameraHardwareExist()) {
            focusCamera.startCamera();

            focusCamera.setOnCameraListener(new FocusCamera.OnImageCaptureListener() {
                @Override
                public void imageCaptureSuccess(FocusImageResponse imageObject) {
                    String image64 = imageObject.getImagebase64();
                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.sample1);
                    focusCamera.setCropMode(FocusCamera.CropMode.CIRCLE);
                    focusCamera.startCropImage(bitmap);
                }
            });
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        focusCamera.stopCamera();
    }

}
