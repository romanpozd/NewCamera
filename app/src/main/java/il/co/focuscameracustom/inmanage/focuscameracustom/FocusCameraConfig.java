package il.co.focuscameracustom.inmanage.focuscameracustom;

import android.hardware.Camera;

/**
 * Created by inmanage on 06/12/2015.
 */
public class FocusCameraConfig {

    private String imageName;
    private FocusCamera.ImageFormats format;
    private Camera.Parameters parameters;
    private int maxZoom, minZoom;

    private int autoFlashButton, onFlashButton, offFlashButton, takePictureButton;

    private int cameraOptionsBit;

    public FocusCameraConfig(){}

    public FocusCamera.ImageFormats getFormat() {
        return format;
    }


    public int getAutoFlashButton() {
        return autoFlashButton;
    }

    public int getOnFlashButton() {
        return onFlashButton;
    }

    public int getOffFlashButton() {
        return offFlashButton;
    }

    public int getTakePictureButton() {
        return takePictureButton;
    }


    public int getCameraOptionsBit() {
        return cameraOptionsBit;
    }

    public int getMaxZoom() {
        return maxZoom;
    }

    public int getMinZoom() {
        return minZoom;
    }

    public void setAutoFlashButton(int autoFlashButton) {
        this.autoFlashButton = autoFlashButton;
    }

    public void setOnFlashButton(int onFlashButton) {
        this.onFlashButton = onFlashButton;
    }

    public void setOffFlashButton(int offFlashButton) {
        this.offFlashButton = offFlashButton;
    }

    public void setTakePictureButton(int takePictureButton) {
        this.takePictureButton = takePictureButton;
    }


    public void setCameraOptionsBit(int cameraOptionsBit) {
        this.cameraOptionsBit = cameraOptionsBit;
    }

    public void setMaxZoom(int maxZoom) {
        this.maxZoom = maxZoom;
    }

    public void setMinZoom(int minZoom) {
        this.minZoom = minZoom;
    }

    public void setFormat(FocusCamera.ImageFormats format) {
        this.format = format;
    }

    public String getImageName() {
        return imageName;
    }

    public Camera.Parameters getParameters() {
        return parameters;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public void setParameters(Camera.Parameters parameters) {
        this.parameters = parameters;
    }

}
