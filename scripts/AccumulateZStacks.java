import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.plugin.Scaler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
public class AccumulateZStacks {
    static final Pattern pat = Pattern.compile("\\d+");

    static int getFrame(String s){
        Matcher m = pat.matcher(s);
        String token = "-1";
        while(m.find()){
            //get the last one.
            token = m.group(0);
        }
        return Integer.parseInt(token);
    }

    /**
     * For opening multiple images where a z-stack is one file.
     * @param plus
     * @param xyfactor
     * @param zfactor
     */

    static ImagePlus scaleXY(ImagePlus plus, double xyfactor, double zfactor){
        int channels = plus.getNChannels();
        ImageStack stack = plus.getStack();
        ImagePlus next = null;
        int[] selectedChannels = {3};
        List<ImageStack> toCollate = new ArrayList<>();
        for(int i: selectedChannels){
            ImagePlus pc1 = plus.createImagePlus();
            ImageStack cstack = new ImageStack(plus.getWidth(), plus.getHeight());
            for(int slice = 0; slice < plus.getNSlices(); slice++){
                
                ImageProcessor proc = stack.getProcessor( i + slice*channels);
                System.out.println(proc.getWidth() + ", " + proc.getHeight() + " // - // " + stack.getWidth() + ", " + stack.getHeight() + " // - //" + cstack.getWidth() + ", " + cstack.getHeight());
                cstack.addSlice(proc);
            }
            pc1.setStack(cstack, 1, plus.getNSlices(), 1);
            int nw = (int)(plus.getWidth()*xyfactor);
            int nh = (int)(plus.getHeight()*xyfactor);
            int nz = (int)(plus.getNSlices()*zfactor);
            if(nw == plus.getWidth() && nh == plus.getHeight() && nz == plus.getNSlices()){
                next = pc1;
            } else{
                next = Scaler.resize(pc1, nw, nh, nz, "bilinear");
            }
            toCollate.add(next.getStack());
        }
        ImageStack collated = new ImageStack(next.getWidth(), next.getHeight());
        for(int i = 1; i<=next.getNSlices(); i++){
            for(int c = 0; c<toCollate.size(); c++){
                collated.addSlice(toCollate.get(c).getProcessor(i));
            }
        }

        next.setStack(collated, selectedChannels.length, next.getNSlices(), 1);
        return next;
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        double XY_SCALE = 1.0;
        double Z_SCALE = 1.0;
        Runtime runtime = Runtime.getRuntime();
        int procs = runtime.availableProcessors();
        long mem = runtime.maxMemory();
        System.out.println(procs + " cores " + mem + " max mem.");
        List<String> names = new ArrayList<>( );

        boolean headless = false;

        if( args.length > 0){
            names.addAll( Arrays.asList(args) );
            headless = true;
        } else{
            String dir = IJ.getDirectory("Select folder containing images");
            try {
                names.addAll( Files.list(Paths.get(dir)).filter(p->p.toString().endsWith(".tif")).map(Path::toString).collect( Collectors.toList() ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("working on " + names.size() + " files");
        names.sort(Comparator.comparingInt(AccumulateZStacks::getFrame));
        int frames = 0;
        ExecutorService service = Executors.newFixedThreadPool(procs);

        List<Future<ImagePlus>> futures= names.stream().map(
            imageStack -> service.submit(
                ()-> {
                    System.out.println("processing :: " + imageStack);
                    ImagePlus original = new ImagePlus(Paths.get(imageStack).toAbsolutePath().toString());
                    original.setOpenAsHyperStack(true);
                    return scaleXY(original, XY_SCALE, Z_SCALE);
                })
        ).collect(Collectors.toList());

        ImageStack out = null;
        ImagePlus fin = null;
        for(Future<ImagePlus> result: futures){
            ImagePlus next = result.get();
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
        service.shutdown();

        if(fin != null){

            fin.setOpenAsHyperStack(true);
            fin.setStack(out, fin.getNChannels(), fin.getNSlices(), frames);
            if(headless) {
                IJ.save(fin, "accumulated-stack.tif");
            } else{
                new ImageJ();
                fin.show();
            }
        }

    }

}
