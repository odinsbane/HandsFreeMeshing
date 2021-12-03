import deformablemesh.*;
import deformablemesh.externalenergies.*;
import ij.*;
import java.io.File;
public class MeshPrediction{
    SegmentationController controls;
    ImagePlus plus;
    int threshold = 1;
    int dtChannel=2;
    MeshPrediction( SegmentationController controls, ImagePlus  plus){
        this.controls = controls;
        this.plus = plus;
    }
    static void deformAllMeshes(SegmentationController controls){
        
        int[] a = { 0 };
        int x = controls.getDeformationSteps();
        new Thread( () -> {
            try{
                while( x == controls.getDeformationSteps() || controls.getDeformationSteps() < 500 ){
                    Thread.sleep(100);
                } 
            } catch( Exception e ){ return;}
            finally{ controls.stopRunning();}
        }).start();
        System.out.println("deforming meshes");
        controls.deformAllMeshes();
        System.out.println("finished deforming");
        
    }
    
    public void setThreshold(int t){
        threshold = t;
    }
    public void run( ){
        
        controls.setOriginalPlus( plus, dtChannel);
        controls.setGamma(1000);
        controls.setWeight(-0.05);
        controls.setBeta(0.1);
        controls.setAlpha(1.0);
        controls.setImageEnergyType(ImageEnergyType.PerpendicularIntensity);
        

        String outName = controls.getShortImageName().replace(".tif", "") + "-headless.bmf";
        System.out.println( controls.getShortImageName() + " creating " + outName);
        
        
        
        
        for(int i = 0; i < controls.getNFrames(); i++){
            controls.toFrame(i);
            controls.guessMeshes(threshold);
            
            for(int remesh = 0; remesh<1; remesh++){
                deformAllMeshes(controls);
                controls.reMeshConnectionsAllMeshes(0.005, 0.02);                
            }
            System.out.println("saving meshes after " + (i + 1) + " frames");
            
            controls.saveMeshes(new File(outName));
        }
        System.exit(0);
        
    }
    
    public static void main(String[] args) throws Exception{
        SegmentationController controls = new SegmentationController( new SegmentationModel() );
        System.out.println("loading: " + args[0]);
        ImagePlus plus = new ImagePlus( new File(args[0]).getAbsolutePath() );
        MeshPrediction pred = new MeshPrediction(controls, plus);
        
        if( args.length > 1 ){
            pred.setThreshold(Integer.parseInt(args[1]));
        }
        controls.submit( pred::run );        
        
    }
    
}
