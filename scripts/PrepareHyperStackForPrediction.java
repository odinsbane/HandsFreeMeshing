import loci.common.DebugTools;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.awt.Color;
import ij.process.LUT;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;

public class PrepareHyperStackForPrediction {

    public static void main(String[] args) throws IOException, FormatException {
        DebugTools.setRootLevel("OFF");
        Path src = Paths.get(args[0]).toAbsolutePath();
        Path outFolder = src.getParent().resolve("two-channel-byte");

        ImporterOptions options = new ImporterOptions();
        options.setVirtual(true);
        options.setId(src.toString());
        //options.setLocation(src.toString());
        ImagePlus plus = BF.openImagePlus(options)[0];
        //ImagePlus plus = new ImagePlus(src.toString());

        int[] selectedChannels = {2, 3};

        int frames = plus.getNFrames();
        int channels = plus.getNChannels();
        int slices = plus.getNSlices();

        ImageStack in = plus.getImageStack();
        Files.createDirectories(outFolder);
        for(int i = 0; i<frames; i++){
            ImageStack out = new ImageStack(in.getWidth(), in.getHeight());
            for(int z = 0; z < slices; z++){
                for(int c: selectedChannels){
                    int N = z*channels + i * channels * slices + c;
                    ImageProcessor processor = in.getProcessor( N );

                    out.addSlice( in.getSliceLabel( N ), processor.convertToByte(true));

                }
            }
            Path outpath = outFolder.resolve( src.getFileName().toString().replace(".tif", String.format("-2b-%04d.tif", i) ) );

            ImagePlus dup = plus.createImagePlus();
            dup.setStack(out, selectedChannels.length, slices, 1);
            dup.setLut(LUT.createLutFromColor(Color.WHITE));
            IJ.save(dup, outpath.toString());

        }





    }
}