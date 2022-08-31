import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FileInfoVirtualStack;
import ij.process.LUT;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SplitStackIntoFrames {

    public static void main(String[] args) throws IOException {

        for(String img: args){
            Path in =Paths.get(img).toAbsolutePath();
            String name = in.getFileName().toString();
            Path folder = in.getParent().resolve(name.replace(".tif", ""));
            Files.createDirectories(folder);

            ImagePlus plus = FileInfoVirtualStack.openVirtual(in.toString());
            ImageStack inStack = plus.getStack();
            int c = plus.getNChannels();
            int s = plus.getNSlices();
            System.out.println("processing: " + name);
            System.out.println("\n channels: " + c + " slices: " + s + " frames: " + plus.getNFrames());
            for(int i = 0; i<plus.getNFrames(); i++){
                ImagePlus out = plus.createImagePlus();
                ImageStack outStack = out.getStack();
                for(int slice = 0; slice < s*c; slice++){
                    int offset = i*c*s + 1;
                    outStack.addSlice(inStack.getSliceLabel(offset + slice), inStack.getProcessor(offset + slice));
                }
                out.setStack(outStack, c, s, 1);
                out.setLut(LUT.createLutFromColor(Color.WHITE));
                Path destination = folder.resolve(name.replace(".tif", String.format("_t-%04d.tif", i)));
                IJ.save(out, destination.toString());
            }

        }
    }
}
