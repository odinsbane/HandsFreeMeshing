import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.awt.Image;
import java.nio.file.Paths;

public class ZAbberationShifting {
    public static void main(String[] args){
        double slide_plane = 0;
        double scale = 1.2;
        int channelToScale = 1; //second channel
        ImagePlus imp = new ImagePlus(Paths.get(args[0]).toAbsolutePath().toString());
        int c = imp.getNChannels();
        int n = imp.getNSlices();
        int t = imp.getNFrames();

        int w = imp.getWidth();
        int h = imp.getHeight();

        ImageStack stack = imp.getStack();
        for(int j = 0; j<t; j++){
            ImageStack scaled = new ImageStack(imp.getWidth(), imp.getHeight());
            int offset = n*c*j + 1;
            for(int k = 0; k<n; k++){
                //z=0 is the top and z=n-1 is the bottom.

                double scaledDepth = (n - 1 - k)*scale;
                double scaledIndex = n - 1 - scaledDepth;

                int low, high;
                double r;
                if(scaledIndex < 0) {
                    low = 0;
                    high = 0;
                    r = 0;
                } else if(scaledIndex >= n - 1){
                    low = n-1;
                    high = n-1;
                    r = 0;
                } else{
                    low = (int)scaledIndex;
                    high = low + 1;
                    r = scaledIndex - low;
                }

                ImageProcessor above = stack.getProcessor(c*high + channelToScale + offset);
                ImageProcessor below = stack.getProcessor(c*low + channelToScale + offset);
                ImageProcessor dest = below.duplicate();

                for(int i = 0; i<w*h; i++){
                    int a = above.get(i);
                    int b = below.get(i);
                    int terp = (int)((a - b)*r + b);
                    dest.set(i, terp);
                }
                scaled.addSlice(dest);
            }
            for(int k = 0; k<n; k++){
                stack.setProcessor(scaled.getProcessor(k+1), offset + k*c + channelToScale);
            }





        }
        new ImageJ();
        imp.show();


    }
}
