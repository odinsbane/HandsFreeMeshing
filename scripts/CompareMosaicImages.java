import deformablemesh.MeshImageStack;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.ImageStack;
import lightgraph.DataSet;
import lightgraph.Graph;
import lightgraph.GraphPoints;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            }
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
                if(rm == null){
                    //failed to map.
                    continue;
                }
                bLabel = rm.b;
                overlap = rm.size;

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


    static public List<double[]> histogram(List<double[]> table){
        int bins = 50;
        double min = 0;
        double max = 1.0;
        double dx = (max - min)/bins;
        double[] x = new double[bins];
        double[] y = new double[bins];
        for(int i = 0; i<bins; i++){
            x[i] = min + (0.5 + i)*dx;
        }
        double total = 0;
        for(double[] values: table){

            for(double d: values){
                int dex = (int)((d - min)/dx);
                if(dex >= 0 && dex < bins){
                    y[dex] += 1;
                    total++;
                }
            }

        }
        double f = 1/total/dx;
        for(int i = 0; i<y.length; i++){
            y[i] = y[i]*f;
        }

        double[] blockyx = new double[ x.length*2];
        double[] blockyy = new double[ y.length*2];

        for(int i = 0; i<x.length; i++){
            blockyx[2*i] = x[i]-dx/2;
            blockyx[2*i+1] = x[i]+dx/2;
            blockyy[2*i] = y[i];
            blockyy[2*i+1] = y[i];
        }

        return Arrays.asList(blockyx, blockyy);
    }

    static void plotHistograms(Map<String, List<double[]>> data){
        Graph g = new Graph();
        String title = "";
        int colorIndex = 0;
        for(String name: data.keySet()){
            List<double[]> curve = data.get(name);
            DataSet set = g.addData(curve.get(0), curve.get(1));
            set.setPoints(null);
            set.setLineWidth(2);
            title += name + "; ";
            colorIndex = ( colorIndex + 1 ) % colors.length;
        }
        g.setContentSize(170, 120);
        g.setXRange(0, 1);
        g.setYRange(0, 20);
        g.setXTicCount(2);
        g.setYTicCount(2);
        g.show(true, title);
    }
    static Color[] colors = {Color.RED, Color.BLUE, Color.YELLOW};
    static Color[] light = {
            new Color(255, 100, 100),
            new Color(100, 100, 255),
            new Color(255, 255, 100)
    };
    public static void plotSummary(Map<String, List<Map<Integer, List<double[]>>>> comparisons){
        Graph plot = new Graph();

        int colorIndex = 0;
        List<double[]> allAverages = new ArrayList<>();
        List<String> names = new ArrayList<>(comparisons.keySet());
        Map<String, List<double[]>> jiHistograms = new LinkedHashMap<>();
        for(String comparisonName: names){
            List<Map<Integer, List<double[]>>> output = comparisons.get(comparisonName);
            double[] averages = new double[4];
            int n = 0;
            GraphPoints[] good = {GraphPoints.hollowCircles(), GraphPoints.hollowTriangles(), GraphPoints.hollowSquares()};
            int stackNo = 0;
            List<double[]> jiTable = new ArrayList<>();
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
                jiTable.add(ji);


                DataSet set = plot.addData(cm, ji);
                set.setColor(light[colorIndex]);
                set.setLine(null);
                set.setPoints(good[stackNo]);
                stackNo = ++stackNo%good.length;
                n += cells;
            }
            averages[0] = averages[0]/n;
            averages[1] = averages[1]/n;
            averages[2] = Math.sqrt(averages[2]/n - averages[0]*averages[0]);
            averages[3] = Math.sqrt(averages[3]/n - averages[1]*averages[1]);
            allAverages.add(averages);
            colorIndex = (colorIndex + 1)%colors.length;
            jiHistograms.put(comparisonName, histogram(jiTable));
        }

        for(int i = 0; i<allAverages.size(); i++){
            colorIndex = i%colors.length;
            double[] averages = allAverages.get(i);
            String name = names.get(i).replace(".txt", "");
            DataSet set = plot.addData(new double[] {averages[1]}, new double[] {averages[0]});
            set.addXYErrorBars(new double[]{averages[3]}, new double[]{averages[2]});
            set.setLine(null);
            set.setPoints(GraphPoints.outlinedCircles());
            set.setPointSize(12);
            set.setPointWeight(3);
            set.setColor(colors[colorIndex]);
            set.setLabel(name);
            colorIndex = (colorIndex + 1)%colors.length;
        }
        plot.setContentSize(340, 240);
        plot.setXRange(0, 2.5);
        plot.setYRange(0, 1);
        plot.setXTicCount(6);
        plot.setYTicCount(3);
        plot.setXLabel("\u0394CM (\u03BCm)");
        plot.setYLabel("JI");
        plot.show(false, "JI vs CM Displacement");
        plotHistograms(jiHistograms);

    }
    void showErrors(){
        ImagePlus plus = truth.createImagePlus();
        int slices = truth.getNSlices();
        int frames = truth.getNFrames();
        plus.setStack(debugStack, 1, slices, frames);
        plus.setTitle("Red: good, Green: false positive, Blue: incorrectly mapped");
        plus.show();
    }


    void accumulateErrors(int frame, Map<Integer, RegionMap> bestMapped){
        //conditions.
        //Background labelled. Label pixels as Background. Labelled incorrectly. Labelled Correctly.
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
        if(DEBUG){
            new ImageJ();
        }
        Map<String, List<Map<Integer, List<double[]>>>> datasets = new LinkedHashMap<>();

        //Each name in the arguments is file name with a list of truth/prediction mosaic pairs.
        for(String comparison: args){
            //Each line contains a pair of images truth/prediction that will be evaluated.
            List<String> pairs = Files.readAllLines(Paths.get(comparison));
            List<Map<Integer, List<double[]>>> comparisonSet = new ArrayList<>(pairs.size());

            for(String pair: pairs){
                String[] tokens = pair.split("\\s");
                Path tp = Paths.get(tokens[0]);
                Path gp = Paths.get(tokens[1]);
                if(!Files.exists(tp)){
                    throw new IOException(tokens[0] + " not found! Aborting.");
                }
                if(!Files.exists(gp)){
                    throw new IOException(tokens[1] + " not found! Aborting.");
                }

                ImagePlus truth = new ImagePlus(tp.toAbsolutePath().toString());
                ImagePlus guess = new ImagePlus(gp.toAbsolutePath().toString());
                CompareMosaicImages cmi = new CompareMosaicImages(truth, guess);
                cmi.prepareMapping();
                //cellValues is a Frame, Data mapping. Data is one double[] per cell.
                comparisonSet.add(cmi.cellValues);
            }
            datasets.put(comparison, comparisonSet);
        }
        plotSummary(datasets);
    }

}
