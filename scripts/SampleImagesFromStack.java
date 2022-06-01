import deformablemesh.MeshImageStack;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FileInfoVirtualStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SampleImagesFromStack {

    public static void main(String[] args) throws IOException {
        int rate = 10; //sampling rate.
        int total = 16; //total frames saved.
        int first = 0; //first frame.
        for(String img: args){
            Path in =Paths.get(img).toAbsolutePath();
            Path folder = in.getParent().resolve("sampled-r_" + rate + "-f_" + first);
            Files.createDirectories(folder);

            ImagePlus plus = FileInfoVirtualStack.openVirtual(in.toString());
            ImagePlus out = plus.createImagePlus();
            ImageStack outStack = out.getStack();
            ImageStack inStack = plus.getStack();
            int c = plus.getNChannels();
            int s = plus.getNSlices();

            for(int i = 0; i<total; i++){
                int frame = i*rate + first;
                for(int slice = 0; slice < s*c; slice++){
                    int offset = frame*c*s + 1;
                    outStack.addSlice(outStack.getSliceLabel(offset + slice), inStack.getProcessor(offset + slice));
                }
            }

            out.setStack(outStack, c, s, total);
            Path destination = folder.resolve(in.getFileName());
            IJ.save(out, destination.toString());
        }
    }
}
