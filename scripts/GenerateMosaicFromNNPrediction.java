import deformablemesh.MeshImageStack;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.simulations.FillingBinaryImage;
import deformablemesh.util.connectedcomponents.ConnectedComponents3D;
import deformablemesh.util.connectedcomponents.Region;
import deformablemesh.util.connectedcomponents.RegionGrowing;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import javax.swing.JFileChooser;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GenerateMosaicFromNNPrediction {
    ImagePlus original;
    int level = 10;
    int cutoff = 200;
    ImageStack output;
    GenerateMosaicFromNNPrediction(ImagePlus plus){
        this.original = plus;

    }

    public ImageStack getDistanceTransform(int frame){
        MeshImageStack mis = new MeshImageStack(original, frame, 2);
        return mis.getCurrentFrame().getStack();
    }

    public ImageStack getMask(int frame){
        MeshImageStack mis = new MeshImageStack(original, frame, 1);
        return mis.getCurrentFrame().getStack();
    }

    public ImageStack processFrame(int frame){

        ImageStack currentFrame = getDistanceTransform(frame);

        ImageStack threshed = new ImageStack(currentFrame.getWidth(), currentFrame.getHeight());
        for(int i = 1; i<= currentFrame.size(); i++){
            ImageProcessor proc = currentFrame.getProcessor(i).convertToShort(false).duplicate();
            proc.threshold(level);
            threshed.addSlice(proc);
        }
        List<Region> regions = ConnectedComponents3D.getRegions(threshed);
        List<Region> toRemove = new ArrayList<>();
        System.out.println(regions.size() + " regions detected");
        int small = 0;
        int size = 0;
        Integer biggest = -1;
        for (Region region : regions) {
            Integer key = region.getLabel();
            List<int[]> points = region.getPoints();

            if (points.size() < cutoff) {
                small++;
            }

            if (points.size() < cutoff) {

                toRemove.add(region);
                for (int[] pt : points) {
                    threshed.getProcessor(pt[2]).set(pt[0], pt[1], 0);
                }
            } else {
                for (int[] pt : points) {
                    threshed.getProcessor(pt[2]).set(pt[0], pt[1], key);
                }
            }

            if (points.size() > size) {
                size = points.size();
                biggest = key;
            }
        }
        System.out.println(small + " to small. Biggest: " + biggest + " size of: " + size);
        for (Region region : toRemove) {
            regions.remove(region);
        }
        ImageStack growing = new ImageStack(currentFrame.getWidth(), currentFrame.getHeight());
        ImageStack mask = getMask(frame);
        int nlev = 1;
        for(int i = 1; i<= currentFrame.size(); i++){
            ImageProcessor proc = currentFrame.getProcessor(i).convertToShort(false).duplicate();
            proc.threshold(nlev);
            maskOff(proc, mask.getProcessor(i));
            growing.addSlice(proc);
        }
        RegionGrowing rg = new RegionGrowing(threshed, growing);
        rg.setRegions(regions);
        for(int st = 0; st<100; st++){
            rg.step();
        }

        return threshed;
    }

    private void maskOff(ImageProcessor proc, ImageProcessor mask) {

        int n = proc.getWidth()*proc.getHeight();
        for(int i = 0; i<n; i++){
            if(mask.get(i) != 0){
                proc.set(i, 0);
            }
        }
    }

    public void showDup(String title, ImageStack stack){
        ImageStack tmp = new ImageStack(stack.getWidth(), stack.getHeight());
        for(int i = 1; i<= stack.getSize(); i++){
            tmp.addSlice(stack.getProcessor(i).duplicate());
        }
        new ImagePlus(title, tmp).show();
    }
    public void process(){
        output = new ImageStack(original.getWidth(), original.getHeight());
        int cutoff = 200;
        //Get the current image stack for this frame/channel.
        //create a thresholded version.
        for(int i = 0; i<original.getNFrames(); i++){
            ImageStack frameStack = processFrame(i);
            for( int k = 1; k<=frameStack.size(); k++){
                output.addSlice(frameStack.getProcessor(k));
            }
        }

    }

    public static void main(String[] args){
        ImagePlus imgPlus;
        Path in;
        if( args.length >= 1){
            in = Paths.get(args[0]).toAbsolutePath();
            imgPlus = new ImagePlus(in.toString());
        } else{
            ImageJ imageJ = new ImageJ();
            JFileChooser choose = new JFileChooser();
            int result = choose.showDialog(imageJ, "choose prediction");
            if(result == JFileChooser.CANCEL_OPTION){
                return;
            }
            in = choose.getSelectedFile().toPath().toAbsolutePath();
            imgPlus = new ImagePlus(in.toString());
            imgPlus.show();
        }


        GenerateMosaicFromNNPrediction gm = new GenerateMosaicFromNNPrediction(imgPlus);
        gm.process();
        String name = in.getFileName().toString().replace(".tif", "-mosaic.tif").replace("pred-", "bin-");
        Path out = in.getParent().resolve(name);
        ImagePlus plus = imgPlus.createImagePlus();
        plus.setStack(gm.output, 1, imgPlus.getNSlices(), imgPlus.getNFrames());
        IJ.save(plus, out.toString());
    }

}
