import deformablemesh.*;
import deformablemesh.externalenergies.*;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.track.Track;
import ij.*;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

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
        int f = controls.getCurrentFrame();
        controls.deformAllMeshes(100);
        List<Track> tracks = controls.getAllTracks().stream().filter(
                t->t.containsKey(f)
           ).collect(Collectors.toList());
        for(Track track: tracks){
            DeformableMesh3D mesh = track.getMesh(f);
            double v = mesh.calculateVolume();
            if(v <= 0 || Double.isNaN(v)){
                controls.clearMeshFromTrack(track, f);
            }
        }
        controls.clearHistory();
    }
    
    public void setThreshold(int t){
        threshold = t;
    }
    public void run( ){
        controls.setOriginalPlus( plus, dtChannel);
        //the channel needs to be the DT channel!
        controls.setGamma(1000);
        controls.setWeight(-0.05);
        controls.setBeta(0.1);
        controls.setAlpha(1.0);
        controls.setImageEnergyType(ImageEnergyType.PerpendicularIntensity);

        double minLength = 0.75; //um
        double maxLength = 1.6; //um

        double minNU = minLength/controls.getMeshImageStack().SCALE;
        double maxNU = maxLength/controls.getMeshImageStack().SCALE;

        String outName = controls.getShortImageName().replace(".tif", "") + "-headless.bmf";
        System.out.println( controls.getShortImageName() + " creating " + outName);
        
        
        
        
        for(int i = 0; i < controls.getNFrames(); i++){
            controls.toFrame(i);
            controls.guessMeshes(threshold);
            
            for(int remesh = 0; remesh<1; remesh++){
                deformAllMeshes(controls);
                controls.reMeshConnectionsAllMeshes(minNU, maxNU);
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
