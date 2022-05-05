import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.SegmentationModel;
import deformablemesh.externalenergies.ImageEnergyType;
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class NormalizeMeshes {
    Path folder, output;
    SegmentationController controller = new SegmentationController(new SegmentationModel());
    List<Path> paths;
    double maxLength;
    public NormalizeMeshes(List<Path> paths){
        this.paths = paths;
    }
    public void setSourceFolder(Path folder){
        this.folder = folder;
    }
    public void setOutputFolder(Path output){
        this.output = output;
    }
    public void setMaxLength(double ml ){
        maxLength = ml;
    }


    public void run(){
        controller.submit(()->{
            try{
                processPaths(paths);
            } catch(Exception e){
                throw new RuntimeException(e);
            }
        });
    }
    public void processPaths(List<Path> paths) throws IOException {
        for(Path meshpath: paths){
            Path imgPath = folder.resolve(meshpath.getFileName().toString().replace(".bmf", ".tif"));

            controller.setWeight(2e-6);
            controller.setGamma(1000);
            controller.setBeta(0.1);
            controller.setAlpha(1.0);
            controller.setPressure(0);
            controller.setStericNeighborWeight(0);

            if(Files.exists(imgPath)) {
                System.out.println(meshpath + " // " + imgPath);
                ImagePlus plus = new ImagePlus(imgPath.toAbsolutePath().toString());
                List<Track> meshes = MeshReader.loadMeshes(meshpath.toFile());
                for (int z = 1; z <= plus.getStack().getSize(); z++) {
                    ImageProcessor ip = plus.getStack().getProcessor(z);
                    plus.getStack().getProcessor(z).blurGaussian(3.0);
                }
                controller.setMeshTracks(meshes);
                controller.setOriginalPlus(plus);

                double ml = maxLength / controller.getMeshImageStack().SCALE;
                for (int i = 0; i < plus.getNFrames(); i++) {
                    controller.toFrame(i);
                    controller.reMeshConnectionsAllMeshes(ml / 3, ml);
                    List<Exception> exc = controller.getExecutionErrors();
                    if (exc.size() > 0) {
                        System.out.println(exc.size() + " errors");
                        exc.forEach(e -> e.printStackTrace());
                        return;
                    }
                    controller.clearHistory();
                    controller.setImageEnergyType(ImageEnergyType.PerpendicularGradient);
                    controller.deformAllMeshes(250);
                    controller.setImageEnergyType(ImageEnergyType.SmoothingForce);
                    controller.deformAllMeshes(250);
                    exc = controller.getExecutionErrors();
                    if (exc.size() > 0) {
                        System.out.println(exc.size() + "remesh errors");
                        exc.forEach(e -> e.printStackTrace());
                        return;
                    }
                    controller.clearHistory();
                }
            }
            MeshWriter.saveMeshes(output.resolve(meshpath.getFileName()).toFile(), controller.getAllTracks());
            System.out.println("finished file: " + meshpath.getFileName().toString());
        }
        controller.shutdown();
    }

    public static void main(String[] args) throws IOException {
        Path folder = Paths.get(args[0]);
        Path output = folder.resolve("normalized-meshes");
        Files.createDirectories(output);

        List<Path> paths = Files.list(folder).filter(
                p -> p.getFileName().toString().endsWith(".bmf")
        ).collect(Collectors.toList());

        NormalizeMeshes nmesh = new NormalizeMeshes(paths);
        nmesh.setSourceFolder(folder);
        nmesh.setOutputFolder(output);
        nmesh.setMaxLength(4.0);

        nmesh.run();

    }
}
