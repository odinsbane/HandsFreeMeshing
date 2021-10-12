import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.Resizer;
import ij.process.ImageProcessor;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ScaleToPredict {

    public static void main(String[] args){
        Path in = Paths.get(args[0]).toAbsolutePath();
        ImagePlus plus = new ImagePlus(in.toString());
        plus.setOpenAsHyperStack(true);
        double zplane = plus.getFileInfo().pixelDepth;
        double xyplane = plus.getFileInfo().pixelHeight;
        if(xyplane != plus.getFileInfo().pixelWidth){
            throw new RuntimeException("image needs to have the same x-y resolution: " + xyplane + ", " + plus.getFileInfo().pixelWidth);
        }
        int planes = (int)(zplane/xyplane*plus.getNSlices());

        Resizer sizer = new Resizer();
        ImagePlus stacked = sizer.zScale(plus, planes, ImageProcessor.BILINEAR);
        stacked.setOpenAsHyperStack(true);
        String name = "iso-" + in.getFileName();
        IJ.save(stacked, in.getParent().resolve(name).toString());
    }
}
