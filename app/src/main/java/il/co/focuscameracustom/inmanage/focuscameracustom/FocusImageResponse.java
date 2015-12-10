package il.co.focuscameracustom.inmanage.focuscameracustom;

import java.io.File;

/**
 * Created by inmanage on 06/12/2015.
 */
public class FocusImageResponse {

    private File imageFile;
    private String imagebase64;

    public FocusImageResponse(){}

    public File getImageFile() {
        return imageFile;
    }


    public String getImagebase64() {
        return imagebase64;
    }


    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
    }

    public void setImagebase64(String imagebase64) {
        this.imagebase64 = imagebase64;
    }


}
