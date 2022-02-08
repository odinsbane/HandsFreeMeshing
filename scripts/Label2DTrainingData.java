
import crick.salbreuxlab.segmentation2d.Vertex;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import crick.salbreuxlab.segmentation2d.TwoDSegmentationSnake;
import ij.process.ShortProcessor;

/**
 * The current version will take a skeletonized 2d image and label it to be used with a cerberus model.
 * It will have a membrane label, vertex label and distance transform. There are some more options, but
 * those are the default.
 *
 * Created by smithm3 on 19/06/18.
 */
public class Label2DTrainingData {
    ImageStack labelledStack;
    int membraneLabel = 1;
    int vertexLabel = 3;
    int centerLabel = -1;
    int max = -1;
    int levels = -1;
    boolean categoricalDistanceTranseform = false;

    public void setMembraneLabel(int v){
        membraneLabel = v;
    }

    public void setVertexLabel(int v){
        vertexLabel = v;
    }

    public void setDistanceTransform(int max, int levels){
        this.max = max;
        this.levels = levels;
    }

    public void setCenterLabel(int cl){
        centerLabel = cl;
    }

    public void removeEdgePixels(ImageProcessor proc){
        int w = proc.getWidth();
        int h = proc.getHeight();
        for(int i = 0; i<w; i++){
            int pxT = proc.get(i, 0);
            if(pxT != 0){
                if(proc.get(i, 1)!=0){
                    //leave it
                } else{
                    proc.set(i, 0, 0);
                }
            }

            int pxB = proc.get(i, h-1);
            if(pxB != 0){
                if(proc.get(i, h-2)!=0){
                    //leave it
                } else{
                    proc.set(i, h-1, 0);
                }
            }

        }

        for(int i = 0; i<h; i++){
            int pxL = proc.get(0, i);
            if(pxL != 0){
                if(proc.get(1, i)!=0){
                    //leave it
                } else{
                    proc.set(0, i, 0);
                }
            }

            int pxR = proc.get(w-1, i);
            if(pxR != 0){
                if(proc.get(w-2, i)!=0){
                    //leave it
                } else{
                    proc.set(w-1, i, 0);
                }
            }

        }


    }
    ImageProcessor process(ImageProcessor proc){
        removeEdgePixels(proc);

        int w = proc.getWidth();
        int h = proc.getHeight();
        System.out.println(membraneLabel + ", " + vertexLabel + ", " + centerLabel);

        TwoDSegmentationSnake tdss = new TwoDSegmentationSnake(proc);
        ImageProcessor bytes = new ShortProcessor(w, h);
        int shift = 0;
        if(membraneLabel>0) {
            shift += 1;
            for (int i = 0; i < w * h; i++) {
                if (proc.get(i) != 0) {
                    bytes.set(i, membraneLabel); //membrane
                }
            }
        }



        if(vertexLabel>0) {
            shift += 1;
            tdss.findVertices();
            System.out.println(tdss.vertices.size());
            for (Vertex v : tdss.vertices) {
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        if (v.x + i < w && v.y + j < h && v.x + i >= 0 && v.y + j >= 0) {
                            if (bytes.get(v.x + i, v.y + j) == 1) {
                                bytes.set(v.x + i, v.y + j, vertexLabel);
                            }
                        }

                    }
                }
            }
        }

        if(centerLabel>0){
            shift++;
            Map<Integer, List<int[]>> regions = tdss.getLabelledRegions();
            for(List<int[]> region: regions.values()){
                double cx = 0;
                double cy = 0;
                for(int[] pt: region){
                    cx += pt[0];
                    cy += pt[1];
                }
                int lx = (int)(cx/region.size());
                int ly = (int)(cy/region.size());
                bytes.set(lx, ly, centerLabel);
            }
        }

        if(max>0){
            if(categoricalDistanceTranseform){
                tdss.distanceTransformCategorical(max, levels, bytes, shift);
            } else{
                tdss.distanceTransformActual(max, levels, bytes, shift);
            }
        }

        return bytes;

    }

    public ImagePlus labelImagePlus(ImagePlus plus) throws ExecutionException, InterruptedException {
        labelledStack = new ImageStack(plus.getWidth(), plus.getHeight());
        ImageStack input = plus.getStack();
        List<Future<ImageProcessor>> futures = new ArrayList<>();
        ExecutorService service = Executors.newFixedThreadPool(4);
        for(int i = 1; i<=input.size(); i++){
            final ImageProcessor proc = input.getProcessor(i);
            futures.add( service.submit(()->process(proc)) );
        }
        for(int i = 1; i<=input.size(); i++){
            labelledStack.addSlice(input.getSliceLabel(i), futures.get(i-1).get());
        }

        service.shutdown();
        return new ImagePlus("labelled", labelledStack);
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {


        Path path, labelpath;
        Label2DTrainingData l2d = new Label2DTrainingData();
        boolean headless = true;
        if(args.length>0){
            path = Paths.get(args[0]).toAbsolutePath();
            labelpath = Paths.get(args[1]).toAbsolutePath();

            l2d.setMembraneLabel(1);
            l2d.setVertexLabel(3);
            l2d.setCenterLabel(-1);
            l2d.setDistanceTransform(32, 32);
            l2d.setCategoricalDistanceTransform(false);

        } else{
            new ImageJ();
            headless = false;
            labelpath=null;
            GenericDialog gd = new GenericDialog("set label parameters");
            gd.addNumericField("membrane label", l2d.membraneLabel, 0);
            gd.addNumericField("vertex label", l2d.vertexLabel, 0);
            gd.addNumericField("center label", l2d.centerLabel, 0);
            gd.addNumericField("distance transform: max", l2d.max, 2);
            gd.addNumericField("distance transform: levels", l2d.levels, 2);
            gd.addCheckbox("categorical?", l2d.categoricalDistanceTranseform);

            gd.showDialog();
            int ml = (int)gd.getNextNumber();
            int vl = (int)gd.getNextNumber();
            int cl = (int)gd.getNextNumber();

            int cdt_max = (int)gd.getNextNumber();
            int cdt_levels = (int)gd.getNextNumber();
            boolean cat = gd.getNextBoolean();

            if(gd.wasCanceled()){
                System.exit(0);
            }

            path = Paths.get(IJ.getFilePath("select segmentation to label"));
            l2d.setMembraneLabel(ml);
            l2d.setVertexLabel(vl);
            l2d.setCenterLabel(cl);

            l2d.setDistanceTransform(cdt_max, cdt_levels);
            l2d.setCategoricalDistanceTransform(cat);

        }

        System.out.println(path.toAbsolutePath());
        ImagePlus plus = new ImagePlus(path.toAbsolutePath().toString());

        if(!headless) {
            plus.show();
        }

        ImagePlus labelled = l2d.labelImagePlus(plus);
        if(!headless){
            labelled.show();
        } else{
            IJ.save(labelled, labelpath.toString());
        }
    }

    private void setCategoricalDistanceTransform(boolean cat) {
        categoricalDistanceTranseform = cat;
    }


}
