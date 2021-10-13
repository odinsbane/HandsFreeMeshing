import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
    import ij.process.ImageProcessor;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AccumulateZStacks {
    /**
     * For opening multiple images where a z-stack is one file.
     * @param args
     */

    static ImagePlus scaleXY(ImagePlus plus, double xyfactor, double zfactor){
        int channels = plus.getNChannels();
        ImageStack stack = plus.getStack();
        ImagePlus next = null;
        List<ImageStack> toCollate = new ArrayList<>();
        for(int i = 1; i<=channels; i++){
            ImagePlus pc1 = plus.createImagePlus();
            ImageStack cstack = new ImageStack(pc1.getWidth(), pc1.getHeight());
            for(int slice = 0; slice < plus.getNSlices(); slice++){
                ImageProcessor proc = stack.getProcessor( i + slice*2);
                cstack.addSlice(proc);
            }
            pc1.setStack(cstack, 1, plus.getNSlices(), 1);
            int nw = (int)(plus.getWidth()*xyfactor);
            int nh = (int)(plus.getHeight()*xyfactor);
            int nz = (int)(plus.getNSlices()*zfactor);
            next = Scaler.resize(pc1, nw, nh, nz, "bilinear");
            toCollate.add(next.getStack());
        }
        ImageStack collated = new ImageStack(next.getWidth(), next.getHeight());
        for(int i = 1; i<=plus.getNSlices(); i++){
            for(int c = 0; c<toCollate.size(); c++){
                collated.addSlice(toCollate.get(c).getProcessor(i));
            }
        }

        next.setStack(collated, channels, next.getNSlices(), 1);
        return next;
    }

    public static void main(String[] args){
        new ImageJ();
        ImageStack out = null;
        ImagePlus fin = null;

        int frames = 0;
        for(String imageStack: args){

            ImagePlus original = new ImagePlus( Paths.get(imageStack).toAbsolutePath().toString());
            original.setOpenAsHyperStack(true);

            ImagePlus next = scaleXY(original, 0.5, 1);
            if(out == null){
                out = new ImageStack(next.getWidth(), next.getHeight());

            }
            ImageStack nextStack = next.getStack();
            for(int i = 1; i <= nextStack.size(); i++){
                out.addSlice( nextStack.getSliceLabel(i),  nextStack.getProcessor(i) );
            }
            fin = next;
            frames++;
        }
        if(fin != null){
            fin.setOpenAsHyperStack(true);
            fin.setStack(out, fin.getNChannels(), fin.getNSlices(), frames);
            fin.show();
        }
    }

}
