import deformablemesh.MeshImageStack;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.ImageStack;
import ij.IJ;
import lightgraph.DataSet;
import lightgraph.Graph;
import lightgraph.GraphPoints;

import java.awt.Color;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CompareMosaicImages {
    ImagePlus truth;
    ImagePlus guess;
    ImageStack debugStack;
    Map<Integer, List<double[]>> cellValues = new HashMap<>();
    final static boolean DEBUG=false;

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
    static class Region{
        final int size;
        final double x, y, z;
        public Region(){
            size = 0;
            x = 0;
            y = 0;
            z = 0;
            //empty region.
        }
        public Region(int size, double x, double y, double z){
            this.size = size;
            this.x = x;
            this.z = z;
            this.y = y;
        }
        Region accumulate(double x, double y, double z){
            return new Region(size+1, this.x + x, this.y + y, this.z + z);
        }
    }
    public void prepareMapping(){
        int frames = truth.getNFrames();
        System.out.println("frames: " + frames);
        MeshImageStack mis = new MeshImageStack(truth);
        int w = truth.getWidth();
        int h = truth.getHeight();

        for(int i = 0; i<frames; i++){
            List<double[]> values = new ArrayList<>();
            Map<Integer, Region> tPix = new HashMap<>();
            Map<Integer, Region> gPix = new HashMap<>();
            Map<Mapping, Integer> mapCounter = new HashMap<>();
            int slices = truth.getNSlices();
            int n = truth.getWidth()*truth.getHeight();
            for(int z = 1; z<= slices; z++){
                double[] imPt = new double[]{0, 0, z-1};
                //ImageProcessor proc = new ColorProcessor(truth.getWidth(), truth.getHeight());
                ImageProcessor tp = truth.getStack().getProcessor(z + slices*i);
                ImageProcessor gp = guess.getStack().getProcessor(z + slices*i);
                for(int p = 0; p<n; p++){
                    imPt[0] = p%w;
                    imPt[1] = p/w;
                    double[] xyz = mis.getNormalizedCoordinate(imPt);
                    int a = tp.get(p)&0xffffff;
                    int b = gp.get(p)&0xffffff;
                    int db = ( ( a & 255 ) << 16 ) + ( b & 255 );
                    //proc.set(p, db);
                    tPix.compute(a, (value, previous)->{
                        if(previous==null){
                            previous = new Region();
                        }
                        return previous.accumulate(xyz[0], xyz[1], xyz[2]);
                    });

                    gPix.compute(b, (value, previous)->{
                        if(previous==null){
                            previous = new Region();
                        }
                        return previous.accumulate(xyz[0], xyz[1], xyz[2]);
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
            int index = 0;
            List<Integer> keys = new ArrayList<>(tPix.keySet());
            keys.sort(Integer::compare);
            for(Integer aLabel: keys){
                if(aLabel == 0) continue;
                RegionMap rm = best.get(aLabel);
                int bLabel, overlap;
                if( rm != null ){
                    bLabel = rm.b;
                    overlap = rm.size;
                } else{
                    bLabel = 0;
                    overlap = 0;
                }
                Region a = tPix.get(aLabel);
                Region b = gPix.get(bLabel);
                double dx = (b.x/b.size - a.x/a.size)*mis.SCALE;
                double dy = (b.y/b.size - a.y/a.size)*mis.SCALE;
                double dz = (b.z/b.size - a.z/a.size)*mis.SCALE;

                values.add(new double[]{
                        index++, aLabel, bLabel, a.size, b.size, overlap, dx, dy, dz
                });

            }
            cellValues.put(i, values);
            if(DEBUG) accumulateErrors(i, best);
        }
        if(DEBUG) showErrors();
    }
    public static void plotSummary(List<Map<Integer, List<double[]>>> output){
        Graph plot = new Graph();
        double[] averages = new double[4];
        int n = 0;
        GraphPoints[] good = {GraphPoints.hollowCircles(), GraphPoints.hollowTriangles(), GraphPoints.hollowSquares()};
        int stackNo = 0;
        for(Map<Integer, List<double[]>> movieValues: output){
            int cells = movieValues.values().stream().mapToInt(List::size).sum();
            double[] ji = new double[cells];
            double[] cm = new double[cells];
            int dex = 0;
            for(Integer frame: movieValues.keySet()){
                List<double[]> cellValues = movieValues.get(frame);
                for(double[] cv: cellValues){
                    //index++, aLabel, bLabel, a.size, b.size, overlap, dx, dy, dz
                    double j = cv[5] /(cv[4] + cv[3] - cv[5]);
                    double d = Math.sqrt(cv[6]*cv[6] + cv[7]*cv[7] + cv[8]*cv[8]);
                    ji[dex] = j;
                    cm[dex] = d;
                    dex++;
                    averages[0] += j;
                    averages[1] += d;
                    averages[2] += j*j;
                    averages[3] += d*d;
                }

            }
            DataSet set = plot.addData(cm, ji);
            set.setColor(Color.LIGHT_GRAY);
            set.setLine(null);
            set.setPoints(good[stackNo]);
            stackNo = ++stackNo%good.length;
            n += cells;
        }
        averages[0] = averages[0]/n;
        averages[1] = averages[1]/n;
        averages[2] = Math.sqrt(averages[2]/n - averages[0]*averages[0]);
        averages[3] = Math.sqrt(averages[3]/n - averages[1]*averages[1]);
        DataSet set = plot.addData(new double[] {averages[1]}, new double[] {averages[0]});
        set.addXYErrorBars(new double[]{averages[3]}, new double[]{averages[2]});
        set.setLine(null);
        set.setPoints(GraphPoints.outlinedCircles());
        set.setPointSize(12);
        set.setPointWeight(3);
        set.setColor(Color.RED);
        plot.setContentSize(340, 240);
        plot.setXRange(0, 2.5);
        plot.setYRange(0, 1);
        plot.setXTicCount(6);
        plot.setYTicCount(3);
        plot.setXLabel("\u0394CM (\u03BCm)");
        plot.setYLabel("JI");
        plot.show(false, "JI vs CM Displacement");

    }
    void showErrors(){
        new ImageJ();
        ImagePlus plus = truth.createImagePlus();
        int slices = truth.getNSlices();
        int frames = truth.getNFrames();
        plus.setStack(debugStack, 1, slices, frames);
        plus.setTitle("Red: good, Green: false positive, Blue: incorrectly mapped");
        plus.show();
    }


    void accumulateErrors(int frame, Map<Integer, RegionMap> bestMapped){
        //conditions.
        //Background labelled. Label Labelled as Background. Labelled incorrectly. Labelled Correctly.
        //false positive label.
        if(debugStack==null){
            debugStack = new ImageStack(truth.getWidth(), truth.getHeight());
        }

        int slices = truth.getNSlices();
        Set<Integer> mapped = bestMapped.values().stream().map(rm->rm.b).collect(Collectors.toSet());

        int n = truth.getWidth()*truth.getHeight();
        for(int z = 1; z<= slices; z++){
            ImageProcessor proc = new ColorProcessor(truth.getWidth(), truth.getHeight());
            ImageProcessor tp = truth.getStack().getProcessor(z + slices*frame);
            ImageProcessor gp = guess.getStack().getProcessor(z + slices*frame);
            for(int p = 0; p<n; p++){
                int rgb = 0;
                int a = tp.get(p)&0xffffff;
                int b = gp.get(p)&0xffffff;
                if(a==0 && b==0){
                    continue;
                }

                if(a != 0){
                    //comparing to ground truth.

                    if(b==0){
                        //unlabelled
                        rgb = 128;
                    } else{
                        RegionMap mapping = bestMapped.get(a);
                        if(b == mapping.b){
                            //correct
                            rgb = 255 << 16;
                        } else{
                            rgb = 255;
                            //incorrect.
                            if(!mapped.contains(b)){
                                //false positive.
                                rgb += (255<<8);
                            }
                        }
                    }
                } else{
                    //miss labelled.
                    rgb = 64;
                    if(!mapped.contains(b)){
                        //false positive labels.
                        rgb += (255<<8);
                    }
                }

                proc.set(p, rgb);
            }
            debugStack.addSlice(proc);
        }
    }

    CompareMosaicImages(ImagePlus truth, ImagePlus plus){
        this.truth = truth;
        this.guess = plus;
    }
    public static void main(String[] args) throws IOException {

        for(String comparison: args){
            List<String> pairs = Files.readAllLines(Paths.get(comparison));
            List<Map<Integer, List<double[]>>> allValues = new ArrayList<>(pairs.size());
            for(String pair: pairs){
                String[] tokens = pair.split("\\s");

                ImagePlus truth = new ImagePlus(Paths.get(tokens[0]).toAbsolutePath().toString());
                ImagePlus guess = new ImagePlus(Paths.get(tokens[1]).toAbsolutePath().toString());
                CompareMosaicImages cmi = new CompareMosaicImages(truth, guess);
                cmi.prepareMapping();
                allValues.add(cmi.cellValues);
            }
            plotSummary(allValues);
        }

    }
}
