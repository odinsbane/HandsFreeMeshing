import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.ImageStack;
import ij.IJ;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompareMosaicImages {
    ImagePlus truth;
    ImagePlus guess;
    
    static class Mapping{
        final int a, b;
        Mapping(int a, int b){
            this.a = a;
            this.b = b;
        }
        @Override
        public int hashCode(){
            return a + (b<<16);
        }
        @Override
        public boolean equals(Object o){
            if(o instanceof Mapping){
                Mapping m = (Mapping)o;
                return m.a==a && m.b==b;
            }
            return false;
        }
    }
    static class RegionMap{
        final int size;
        final int a, b;
        public RegionMap(Mapping m, int size){
            this.size = size;
            this.a = m.a;
            this.b = m.b;
        }
    }

    public void prepareMapping(){
        int frames = truth.getNFrames();
        ImageStack debugStack = new ImageStack(truth.getWidth(), truth.getHeight());
        System.out.println("frames: " + frames);
        for(int i = 0; i<frames; i++){
            Map<Integer, Integer> tPix = new HashMap<>();
            Map<Integer, Integer> gPix = new HashMap<>();
            Map<Mapping, Integer> mapCounter = new HashMap<>();
            int slices = truth.getNSlices();
            int n = truth.getWidth()*truth.getHeight();
            for(int z = 1; z<= slices; z++){
                //ImageProcessor proc = new ColorProcessor(truth.getWidth(), truth.getHeight());
                ImageProcessor tp = truth.getStack().getProcessor(z + slices*i);
                ImageProcessor gp = guess.getStack().getProcessor(z + slices*i);
                for(int p = 0; p<n; p++){
                    
                    int a = tp.get(p)&0xffffff;
                    int b = gp.get(p)&0xffffff;
                    int db = ( ( a & 255 ) << 16 ) + ( b & 255 );
                    //proc.set(p, db);
                    
                    tPix.compute(a, (value, previous)->{
                        if(previous==null){
                            previous = 0;
                        }
                        return previous + 1;
                    });

                    gPix.compute(b, (value, previous)->{
                        if(previous==null){
                            previous = 0;
                        }
                        return previous + 1;
                    });
                    
                    //No mappings to empty.
                    if( a == 0 || b == 0){
                        continue;
                    }
                    mapCounter.compute(new Mapping(a, b), (v, pr)->{
                        if(pr == null){
                            pr = 0;
                        }
                        return pr + 1;
                    });
                }
                //debugStack.addSlice(proc);
            }
            //ImagePlus debug = new ImagePlus("debug.tif", debugStack);
            //IJ.save( debug, "debug.tif");
            Map<Integer, RegionMap> best = new HashMap<>();
            for( Map.Entry<Mapping, Integer> mapped: mapCounter.entrySet()){
                RegionMap rm = new RegionMap(mapped.getKey(), mapped.getValue());
                if(best.containsKey(rm.a)){
                    RegionMap old = best.get(rm.a);
                    if(old.size < rm.size){
                        best.put(rm.a, rm);
                    }
                } else{
                    best.put(rm.a, rm);
                }
            }
            System.out.println("#: GT regions " + tPix.size() + ", guessed " + gPix.size());
            System.out.println("#index\ttlabel\tplabel\ttsize\tpsize\toverlap\n");
            int index = 0;
            List<Integer> keys = new ArrayList<>(tPix.keySet());
            keys.sort(Integer::compare);
            for(Integer aLabel: keys){
                RegionMap rm = best.get(aLabel);
                int bLabel, bSize, overlap;
                if( rm != null ){
                    bLabel = rm.b;
                    bSize = gPix.get(rm.b);
                    overlap = rm.size;
                } else{
                    bLabel = 0;
                    bSize = 0;
                    overlap = 0;
                }
                System.out.printf(
                    "%d\t%d\t%d\t%d\t%d\t%d\n", 
                    index++,
                    aLabel,
                    bLabel,
                    tPix.get(aLabel),
                    bSize,
                    overlap
                    );
            }
            
        }
    }
    CompareMosaicImages(ImagePlus truth, ImagePlus plus){
        this.truth = truth;
        this.guess = plus;
    }

    public static void main(String[] args){
        ImagePlus truth = new ImagePlus(Paths.get(args[0]).toAbsolutePath().toString());
        ImagePlus guess = new ImagePlus(Paths.get(args[1]).toAbsolutePath().toString());
        CompareMosaicImages cmi = new CompareMosaicImages(truth, guess);
        cmi.prepareMapping();
        
    }
}
