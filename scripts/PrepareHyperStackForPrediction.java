import ij.ImageJ;
import ij.process.ImageConverter;
import ij.process.ImageStatistics;
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
import java.util.ArrayList;
import java.util.List;

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
            List<ImageStack> cStacks = new ArrayList<>();
            for(int ci = 0; ci<selectedChannels.length; ci++){
                ImageStack stack = new ImageStack(plus.getWidth(), plus.getHeight());
                int brightestSlice = slices/2;
                double max = 0;
                for(int z = 0; z < slices; z++){
                    int N = z*channels + i * channels * slices + selectedChannels[ci];
                    ImageProcessor processor = in.getProcessor( N );
                    stack.addSlice(in.getSliceLabel(N), processor);
                    ImageStatistics is = processor.getStatistics();
                    if(is.max > max){
                        max = is.max;
                        brightestSlice = z;
                    }
                }
                ImagePlus tmp = plus.createImagePlus();
                tmp.setStack(stack, 1, slices, 1);
                tmp.setSlice(brightestSlice);
                tmp.resetDisplayRange();
                ImageConverter converter = new ImageConverter(tmp);
                ImageConverter.setDoScaling(true);
                converter.convertToGray8();
                cStacks.add(tmp.getImageStack());
            }
            ImageStack out = new ImageStack(in.getWidth(), in.getHeight());
            for(int z = 0; z < slices; z++){
                for(int ci = 0; ci<selectedChannels.length; ci++){
                    ImageStack stack = cStacks.get(ci);
                    ImageProcessor processor = stack.getProcessor( z + 1 );
                    out.addSlice( stack.getSliceLabel(z+1), processor);
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