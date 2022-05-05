
import deformablemesh.MeshImageStack;
import deformablemesh.externalenergies.PerpendicularIntensityEnergy;
import deformablemesh.geometry.ConnectionRemesher;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.MeshTracker;
import deformablemesh.track.Track;
import deformablemesh.util.*;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class BatchDeformMeshes {
    ImagePlus prediction;
    Path cwd;
    List<Track> tracks;
    int removed = 0;
    double minLength = 0.7; //um
    double maxLength = 1.5; //um

    double minNU, maxNU;
    
    

    public void setTracks(List<Track> tracks){
        this.tracks = tracks;
    }
    public void setDistanceTransform(ImagePlus plus){
        this.prediction = plus;

        
    }
    public void setCwd(Path p){
        cwd = p;
    }

    static int broken = 0;

    private Integer processFrame(int i){
        MeshImageStack dt;
        MeshImageStack mask;

        dt = new MeshImageStack(prediction, i, 2);
        mask = new MeshImageStack(prediction, i, 1);
        minNU = minLength/dt.SCALE;
        maxNU = maxLength/dt.SCALE;

        System.out.println("starting: " + i);
        long start = System.currentTimeMillis();
        ImagePlus backgroundDt = mask.getCurrentFrame();
        DistanceTransformMosaicImage dtmi = new DistanceTransformMosaicImage(backgroundDt);
        dtmi.findBlobs();
        dtmi.createCascades();
        MeshImageStack bgDt = new MeshImageStack(dtmi.createLabeledImage());
        for (Track t : tracks) {
            if (!t.containsKey(i)) {
                continue;
            }
            DeformableMesh3D mesh = t.getMesh(i);
            //change from 2 energies to 1 energy with a summed image.
            PerpendicularIntensityEnergy img = new PerpendicularIntensityEnergy(dt, mesh, -0.1);
            PerpendicularIntensityEnergy bg = new PerpendicularIntensityEnergy( bgDt, mesh, -0.1);
            
            mesh.addExternalEnergy(img);
            mesh.addExternalEnergy(bg);
            
            mesh.GAMMA = 1000;
            mesh.ALPHA = 1;
            mesh.BETA = 0.1;

            for (int step = 0; step < 4; step++) {
                for (int sub = 0; sub < 200; sub++) {
                    mesh.update();
                    mesh.confine(dt.getLimits());
                }
                mesh.clearEnergies();
                ConnectionRemesher rem = new ConnectionRemesher();
                rem.setMinAndMaxLengths(minNU, maxNU);
                try {
                    mesh = rem.remesh(mesh);
                } catch(Exception e){
                    String bmName = "DEBUG-"+ (broken++) + ".bmf";
                    try{
                        MeshWriter.saveMesh(bmName, mesh );
                        System.out.println("Broken remesh! Mesh file written.");

                    } catch (IOException ex){
                        System.out.println("could not write broken mesh! " + bmName);
                        ex.printStackTrace();
                    }
                    e.printStackTrace();
                    break;
                }
                img = new PerpendicularIntensityEnergy(dt, mesh, -0.1);
                mesh.addExternalEnergy(img);
                mesh.GAMMA = 1000;
                mesh.ALPHA = 1;
                mesh.BETA = 0.1;
            }
            double v = mesh.calculateVolume();

            if(v < 0 || Double.isNaN(v)){
                //remove the original.
                t.remove(t.getMesh(i));
                removed++;
            } else{
                //replace it.
                t.addMesh(i, mesh);
            }
        }
        System.out.println("finished: " + ((System.currentTimeMillis() - start)/1000l) );
        
        try {
            String name = prediction.getTitle();
            MeshWriter.saveMeshes(cwd.resolve("refined-" + name.replace(".tif", ".bmf")).toFile(), tracks);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return i;

    }
    
    void saveMeshes(){
        try {
            String name = prediction.getTitle();
            MeshWriter.saveMeshes(cwd.resolve("refined-" + name.replace(".tif", ".bmf")).toFile(), tracks);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deformMeshes(List<Track> tracks) throws Exception{
        
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<Integer>> results = new ArrayList<>();
        
        for (int frame = 0; frame < prediction.getNFrames(); frame++) {
            final int i = frame;
            results.add(executor.submit( () -> processFrame(i) ));
        }
        for(Future<Integer> result: results){
            
            if(result.isDone()){
                try{ 
                    result.get();
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
                continue;
            }
            try{ 
                Integer value = result.get();
                saveMeshes();
                if(value.equals( prediction.getNFrames() - 1)){
                    //this is the final version.
                    
                }
            }catch(Exception e){
                throw new RuntimeException(e);
            }
            
        }
        
        executor.shutdown();
        saveMeshes();
    }

    public void run(){
        
        try{
            deformMeshes(tracks);
        } catch (Exception e){
            e.printStackTrace();
            System.exit(-1);
        }
        System.out.println("done refining");
    }

    public static ImagePlus getVirtualStack(Path location) throws IOException, FormatException {
        String id = location.toAbsolutePath().toString();
        ImporterOptions options = new ImporterOptions();
        options.setVirtual(true);
        options.setOpenAllSeries(true);
        options.setId(id);
        long start = System.currentTimeMillis();
        ImagePlus[] pluses = BF.openImagePlus(options);

        return pluses[0];
    }

    public static void main(String[] args) throws Exception{
        //new ImageJ();
        ImagePlus distance;
        Path cwd;
        List<Track> tracks;
        BatchDeformMeshes x = new BatchDeformMeshes();

        if(args.length>0){
            distance =getVirtualStack(Paths.get(args[0]));
            cwd = Paths.get(args[0]).toAbsolutePath().getParent();
            File meshes = Paths.get(args[1]).toAbsolutePath().toFile();
            tracks = MeshReader.loadMeshes(meshes);

        } else{
            Path p = Paths.get(IJ.getFilePath("select predction for auto-refining meshes")).toAbsolutePath();
            cwd = p.getParent();
            distance = getVirtualStack(p);

            File meshfile = Paths.get(
                    IJ.getFilePath("Meshes to be deformed")
            ).toAbsolutePath().toFile();

            tracks = MeshReader.loadMeshes(meshfile);

        }
        x.setCwd(cwd);
        x.setDistanceTransform(distance);
        x.setTracks(tracks);
        x.run();
        System.exit(0); //There are some threads around.
    }


}
